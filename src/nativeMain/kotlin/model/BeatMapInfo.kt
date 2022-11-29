package model
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class BeatMapInfo(
    @SerialName("approved")
    val approved: String,
    @SerialName("approved_date")
    val approvedDate: String,
    @SerialName("artist")
    val artist: String,
    @SerialName("artist_unicode")
    val artistUnicode: String,
    @SerialName("audio_unavailable")
    val audioUnavailable: String,
    @SerialName("beatmap_id")
    val beatMapId: String,
    @SerialName("beatmapset_id")
    val beatMapSetId: String,
    @SerialName("bpm")
    val bpm: String,
    @SerialName("count_normal")
    val countNormal: String,
    @SerialName("count_slider")
    val countSlider: String,
    @SerialName("count_spinner")
    val countSpinner: String,
    @SerialName("creator")
    val creator: String,
    @SerialName("creator_id")
    val creatorId: String,
    @SerialName("diff_aim")
    val diffAim: String,
    @SerialName("diff_approach")
    val diffApproach: String,
    @SerialName("diff_drain")
    val diffDrain: String,
    @SerialName("diff_overall")
    val diffOverall: String,
    @SerialName("diff_size")
    val diffSize: String,
    @SerialName("diff_speed")
    val diffSpeed: String,
    @SerialName("difficultyrating")
    val difficultyRating: String,
    @SerialName("download_unavailable")
    val downloadUnavailable: String,
    @SerialName("favourite_count")
    val favouriteCount: String,
    @SerialName("file_md5")
    val fileMd5: String,
    @SerialName("genre_id")
    val genreId: String,
    @SerialName("hit_length")
    val hitLength: String,
    @SerialName("language_id")
    val languageId: String,
    @SerialName("last_update")
    val lastUpdate: String,
    @SerialName("max_combo")
    val maxCombo: String,
    @SerialName("mode")
    val mode: String,
    @SerialName("packs")
    val packs: String?,
    @SerialName("passcount")
    val passCount: String,
    @SerialName("playcount")
    val playCount: String,
    @SerialName("rating")
    val rating: String,
    @SerialName("source")
    val source: String,
    @SerialName("storyboard")
    val storyboard: String,
    @SerialName("submit_date")
    val submitDate: String,
    @SerialName("tags")
    val tags: String,
    @SerialName("title")
    val title: String,
    @SerialName("title_unicode")
    val titleUnicode: String,
    @SerialName("total_length")
    val totalLength: String,
    @SerialName("version")
    val version: String,
    @SerialName("video")
    val video: String
)