use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone)]
pub struct Config {
    #[serde(default = "default_api_key")]
    pub api_key: String,
    #[serde(default = "default_osu_path")]
    pub osu_path: String,
}

fn default_api_key() -> String {
    "input your key".to_string()
}

fn default_osu_path() -> String {
    "input your osu! path(eg. E:\\\\osu!)".to_string()
}

pub enum LoadResult {
    Loaded(Config),
    Created,
}

impl Config {
    pub fn load_or_create(path: &str) -> Result<LoadResult, Box<dyn std::error::Error>> {
        if std::path::Path::new(path).exists() {
            let content = std::fs::read_to_string(path)?;
            let config: Config = serde_json::from_str(&content)?;
            Ok(LoadResult::Loaded(config))
        } else {
            let config = Config {
                api_key: default_api_key(),
                osu_path: default_osu_path(),
            };
            let json = serde_json::to_string_pretty(&config)?;
            std::fs::write(path, json)?;
            Ok(LoadResult::Created)
        }
    }
}
