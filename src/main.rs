mod beatmap_info;
mod config;
mod download_type;
mod map_downloader;
mod since_date;
use std::error::Error;
use std::io::{self, Write};
use std::sync::Arc;

use config::{Config, LoadResult};
use map_downloader::MapDownloader;
use since_date::SinceDate;

use crate::download_type::DownloadType;

const CONFIG_FILE: &str = "download_config.json";

#[tokio::main]
async fn main() {
    if let Err(e) = run().await {
        eprintln!("错误: {e}");
        wait_for_enter();
    }
}

async fn run() -> Result<(), Box<dyn std::error::Error>> {
    let config = match Config::load_or_create(CONFIG_FILE)? {
        LoadResult::Loaded(c) => c,
        LoadResult::Created => {
            println!("检测到配置文件缺失...");
            println!("生成设置列表中...");
            println!("请稍后在配置文件({CONFIG_FILE})中添加osu!apikey和osu路径");
            println!("请按 Enter 键退出....");
            wait_for_enter();
            return Ok(());
        }
    };

    let songs_dir = std::path::Path::new(&config.osu_path).join("Songs");
    if !songs_dir.exists() {
        println!("osu!路径错误! 请重新设置osu!路径");
        wait_for_enter();
        return Ok(());
    }

    let mode = get_mode()?;
    let download_type = get_download_type()?;
    let years = get_date("请输入年份(eg:2011,默认为2007):", 2007)?;
    let months = get_date("请输入月份(eg:1,默认为1):", 1)?;
    let days = get_date("请输入日期(eg:1,默认为1):", 1)?;

    let since_date = SinceDate::new(years, months, days);

    let downloader = Arc::new(MapDownloader::new(
        config.api_key,
        since_date,
        config.osu_path,
        mode,
        download_type,
    )?);

    let term = console::Term::stdout();
    term.clear_screen()?;
    let _ = term.hide_cursor();

    let result = downloader.start().await;

    let _ = term.show_cursor();

    if let Err(ref e) = result {
        println!("运行时错误: {e}");
    }

    wait_for_enter();
    Ok(())
}

fn get_mode() -> Result<i32, Box<dyn std::error::Error>> {
    print!("请选择需要下载的模式(std=0,taiko=1,ctb=2,mania=3),空默认为std:");
    io::stdout().flush()?;

    loop {
        let mut input = String::new();
        io::stdin().read_line(&mut input)?;
        let trimmed = input.trim();
        if trimmed.is_empty() {
            return Ok(0);
        }
        match trimmed.parse::<i32>() {
            Ok(n) if (0..=3).contains(&n) => return Ok(n),
            _ => println!("无效数字,请重新输入"),
        }
    }
}

fn get_download_type() -> Result<DownloadType, Box<dyn Error>> {
    print!("请选择需要下载的方式(full=0,novideo=1,mini=2),空默认为full:");
    io::stdout().flush()?;

    loop {
        let mut input = String::new();
        io::stdin().read_line(&mut input)?;
        let trimmed = input.trim();
        if trimmed.is_empty() {
            return Ok(DownloadType::FULL);
        }
        match trimmed.parse::<i32>() {
            Ok(n) => match n {
                0 => return Ok(DownloadType::FULL),
                1 => return Ok(DownloadType::NoVideo),
                2 => return Ok(DownloadType::MINI),
                _ => return Ok(DownloadType::FULL),
            },
            _ => println!("无效数字,请重新输入"),
        }
    }
}

fn get_date(tip: &str, default: i32) -> Result<i32, Box<dyn std::error::Error>> {
    loop {
        print!("{tip}");
        io::stdout().flush()?;
        let mut input = String::new();
        io::stdin().read_line(&mut input)?;
        let trimmed = input.trim();
        if trimmed.is_empty() {
            return Ok(default);
        }
        match trimmed.parse::<i32>() {
            Ok(n) => return Ok(n),
            Err(_) => println!("无效数字,请重新输入"),
        }
    }
}

fn wait_for_enter() {
    let mut buf = String::new();
    print!("按 Enter 键退出...");
    let _ = io::stdout().flush();
    let _ = io::stdin().read_line(&mut buf);
}
