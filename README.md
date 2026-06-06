# OsuMapDownloader (Rust)

osu! 谱面下载工具 — 从 osu! API 获取谱面列表，通过 sayobot CDN 并发下载 `.osz` 文件。

## 如何使用

1. 首次运行程序会生成 `download_config.json`，请在文件中填写 `apiKey` 和 `osuPath`
   - `apiKey`: 在 [https://osu.ppy.sh/p/api/](https://osu.ppy.sh/p/api/) 申请
   - `osuPath`: osu! 安装路径，例如 `C:\\osu!`

2. 填写完成后重新运行程序，按提示选择模式和起始日期即可开始下载

## 构建

```bash
cargo build --release
```

## 运行

```bash
cargo run --release
```
