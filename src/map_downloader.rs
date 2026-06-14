use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicI32, Ordering};
use std::time::Duration;

use indicatif::{MultiProgress, ProgressBar, ProgressDrawTarget, ProgressStyle};
use reqwest::Client;
use tokio::io::AsyncWriteExt;
use tokio::sync::mpsc;
use tokio::time::Instant;

use crate::beatmap_info::BeatMapInfo;
use crate::download_type::DownloadType;
use crate::since_date::SinceDate;
const BEATMAPS_API: &str = "https://osu.ppy.sh/api/get_beatmaps";
const SAYO_FULL_DOWNLOAD_API: &str = "https://dl.sayobot.cn/beatmaps/download/full/";
const SAYO_NO_VIDEO_DOWNLOAD_API: &str = "https://dl.sayobot.cn/beatmaps/download/novideo/";
const SAYO_MINI_VIDEO_DOWNLOAD_API: &str = "https://dl.sayobot.cn/beatmaps/download/mini/";
const DOWNLOAD_THREADS: usize = 5;
const PROGRESS_INTERVAL_MS: u64 = 120;

/// 下载行的模板：{msg} + 青色进度条
const BAR_TEMPLATE: &str = "{msg} \x1b[38;5;81m[{bar:20.cyan/blue}]\x1b[0m";

pub struct MapDownloader {
    api_key: String,
    since_time: SinceDate,
    songs_dir: PathBuf,
    mode: i32,
    download_type: DownloadType,
    client: Client,
    mp: Arc<MultiProgress>,
}

impl MapDownloader {
    pub fn new(
        api_key: String,
        since_time: SinceDate,
        osu_path: String,
        mode: i32,
        download_type: DownloadType,
    ) -> Result<Self, Box<dyn std::error::Error>> {
        let client = Client::builder()
            .timeout(Duration::from_secs(60 * 15))
            .connect_timeout(Duration::from_secs(60))
            .build()?;
        let mp = MultiProgress::with_draw_target(ProgressDrawTarget::stdout());
        let songs_dir = Path::new(&osu_path).join("Songs");
        Ok(MapDownloader {
            api_key,
            since_time,
            songs_dir,
            mode,
            download_type,
            client,
            mp: Arc::new(mp),
        })
    }

    pub async fn start(self: &Arc<Self>) -> Result<(), Box<dyn std::error::Error>> {
        tokio::fs::create_dir_all(&self.songs_dir).await?;

        let (tx, rx) = mpsc::channel::<i32>(5);
        let rx = Arc::new(tokio::sync::Mutex::new(rx));

        let total_count = Arc::new(AtomicI32::new(0));
        let current_count = Arc::new(AtomicI32::new(0));

        // 顶部状态行（纯文本）
        let status = self.mp.add(ProgressBar::new(0));
        status.set_style(ProgressStyle::with_template("{msg}")?);

        // 预分配 5 个固定下载行
        let download_bars: Vec<ProgressBar> = (0..DOWNLOAD_THREADS)
            .map(|_| {
                let pb = self.mp.add(ProgressBar::new(0));
                pb.set_style(
                    ProgressStyle::with_template(BAR_TEMPLATE)
                        .unwrap()
                        .progress_chars("=>-"),
                );
                pb
            })
            .collect();

        let producer_handle = {
            let this = Arc::clone(self);
            let total_count = Arc::clone(&total_count);
            let status = status.clone();
            tokio::spawn(async move {
                if let Err(e) = this.produce_map_list(tx, &total_count, &status).await {
                    eprintln!("Producer error: {e}");
                }
            })
        };

        let mut consumer_handles = Vec::with_capacity(DOWNLOAD_THREADS);
        for (i, pb) in download_bars.into_iter().enumerate() {
            let this = Arc::clone(self);
            let rx = Arc::clone(&rx);
            let total_count = Arc::clone(&total_count);
            let current_count = Arc::clone(&current_count);
            consumer_handles.push(tokio::spawn(async move {
                this.download_loop(i, &rx, pb, &total_count, &current_count)
                    .await;
            }));
        }

        producer_handle.await?;

        for handle in consumer_handles {
            handle.await?;
        }

        status.finish_with_message("all downloads completed");

        Ok(())
    }

    async fn produce_map_list(
        &self,
        tx: mpsc::Sender<i32>,
        total_count: &AtomicI32,
        status: &ProgressBar,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let mut map_count = 500;
        let mut date = self.since_time.to_string();
        let songs = get_local_beatmap_set_ids(&self.songs_dir).await;

        while map_count != 0 {
            status.set_message(format!("request from osu!api to query maps....[{date}]"));

            let response = self
                .client
                .get(BEATMAPS_API)
                .query(&[
                    ("k", self.api_key.as_str()),
                    ("m", &self.mode.to_string()),
                    ("since", &date),
                ])
                .send()
                .await?;

            let result: Vec<BeatMapInfo> = response.json().await?;

            map_count = result.len();

            // 当获取数量为0时，就意味着读取结束
            if map_count != 0 {
                if let Some(last) = result.last() {
                    date.clone_from(&last.approved_date);
                }
            } else {
                break;
            }

            let map_list: Vec<i32> = result
                .iter()
                .filter(|m| m.approved.parse::<i32>().map(|a| a > 0).unwrap_or(false))
                .filter_map(|m| m.beatmapset_id.parse::<i32>().ok())
                .collect::<std::collections::HashSet<i32>>()
                .into_iter()
                .filter(|id| !songs.contains(id))
                .collect();

            total_count.fetch_add(map_list.len() as i32, Ordering::SeqCst);

            for id in map_list {
                if tx.send(id).await.is_err() {
                    return Ok(());
                }
            }
        }

        Ok(())
    }

    /// 每个消费者线程固定使用一个 ProgressBar，循环复用
    async fn download_loop(
        &self,
        _thread_idx: usize,
        rx: &Arc<tokio::sync::Mutex<mpsc::Receiver<i32>>>,
        pb: ProgressBar,
        total_count: &AtomicI32,
        current_count: &AtomicI32,
    ) {
        loop {
            let map_id = {
                let mut rx = rx.lock().await;
                rx.recv().await
            };

            match map_id {
                Some(id) => {
                    if let Err(e) = self.download_map(id, &pb, total_count, current_count).await {
                        let cnt = current_count.fetch_add(1, Ordering::SeqCst) + 1;
                        let total = total_count.load(Ordering::SeqCst);
                        pb.set_style(
                            ProgressStyle::with_template("{msg}")
                                .unwrap()
                                .progress_chars(""),
                        );
                        pb.set_length(1);
                        pb.set_position(1);
                        pb.set_message(format!(
                            "\x1b[38;5;196m下载失败,发生错误(map:{id}) [{cnt}/{total}]\x1b[0m"
                        ));
                        eprintln!("map {id} error: {e}");
                        // 恢复下载模板，准备下一个
                        pb.set_style(
                            ProgressStyle::with_template(BAR_TEMPLATE)
                                .unwrap()
                                .progress_chars("=>-"),
                        );
                    }
                    tokio::time::sleep(Duration::from_millis(100)).await;
                }
                None => break,
            }
        }
        pb.finish_with_message("");
    }

    async fn download_map(
        &self,
        id: i32,
        pb: &ProgressBar,
        total_count: &AtomicI32,
        current_count: &AtomicI32,
    ) -> Result<(), Box<dyn std::error::Error>> {
        let url = match &self.download_type {
            DownloadType::FULL => format!("{SAYO_FULL_DOWNLOAD_API}{id}"),
            DownloadType::NoVideo => format!("{SAYO_NO_VIDEO_DOWNLOAD_API}{id}"),
            DownloadType::MINI => format!("{SAYO_MINI_VIDEO_DOWNLOAD_API}{id}"),
        };

        let mut response = self
            .client
            .get(&url)
            .header("Referer", "https://github.com/AccMoment/OsuMapDownloader")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 \
                 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36 Edg/107.0.1418.56",
            )
            .send()
            .await?;

        let file_name =
            extract_filename_from_response(&response).unwrap_or_else(|| format!("{id}.osz"));

        let content_length = response.content_length().unwrap_or(0);
        if content_length == 0 {
            return Err("content length is 0".into());
        }
        let content_length_mb = bytes_to_mb(content_length);

        let file_path = self.songs_dir.join(&file_name);

        let mut file = tokio::fs::OpenOptions::new()
            .create(true)
            .truncate(false)
            .write(true)
            .open(&file_path)
            .await?;

        // 初始化本行进度条
        pb.set_length(content_length);
        pb.set_position(0);
        pb.set_message(format!("正在下载 {file_name} ({content_length_mb}M)"));

        let total = total_count.load(Ordering::SeqCst);
        let mut downloaded: u64 = 0;
        let mut last_update = Instant::now();

        while let Some(chunk) = response.chunk().await? {
            file.write_all(&chunk).await?;
            downloaded += chunk.len() as u64;

            if last_update.elapsed().as_millis() as u64 >= PROGRESS_INTERVAL_MS {
                last_update = Instant::now();
                let current_mb = bytes_to_mb(downloaded);
                pb.set_position(downloaded);
                pb.set_message(format!(
                    "正在下载 {file_name} ({current_mb}M/{content_length_mb}M)"
                ));
                file.flush().await?;
            }
        }

        file.flush().await?;
        let cnt = current_count.fetch_add(1, Ordering::SeqCst) + 1;

        pb.set_position(content_length);
        pb.set_message(format!(
            "正在下载 {file_name} \x1b[38;5;81m(已完成)[{cnt}/{total}]\x1b[0m"
        ));

        Ok(())
    }
}

// ── helpers ────────────────────────────────────────────────────────────────

async fn get_local_beatmap_set_ids(songs_dir: &Path) -> Vec<i32> {
    let mut ids = Vec::new();
    let mut entries = match tokio::fs::read_dir(songs_dir).await {
        Ok(e) => e,
        Err(e) => {
            eprintln!("读取 Songs 目录失败: {e}");
            return ids;
        }
    };

    while let Ok(Some(entry)) = entries.next_entry().await {
        let name = entry.file_name().to_string_lossy().to_string();
        let first_part = name.split(' ').next().unwrap_or("");
        let digits: String = first_part.chars().filter(|c| c.is_ascii_digit()).collect();
        if !digits.is_empty() {
            if let Ok(id) = digits.parse::<i32>() {
                ids.push(id);
            }
        }
    }
    ids
}

fn extract_filename_from_response(response: &reqwest::Response) -> Option<String> {
    let cd = response.headers().get("content-disposition")?;
    let cd_str = cd.to_str().ok()?;
    let start = cd_str.find("filename=\"")? + 10;
    let end = cd_str[start..].find("\";")?;
    let raw = &cd_str[start..start + end];
    urlencoding::decode(raw).ok().map(|cow| cow.into_owned())
}

#[inline]
fn bytes_to_mb(bytes: u64) -> String {
    let mb = bytes as f64 / 1024.0 / 1024.0;
    format!("{:.2}", mb)
}
