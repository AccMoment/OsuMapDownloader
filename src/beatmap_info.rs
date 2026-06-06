use serde::Deserialize;

#[derive(Deserialize, Debug, Clone)]
pub struct BeatMapInfo {
    pub approved: String,
    pub approved_date: String,
    pub beatmapset_id: String,
}
