package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AniLibertyUserViewHistoryItem(
    @Json(name = "release_episode_id") val releaseEpisodeId: AniLibertyReleaseEpisodeId?,
    @Json(name = "release_id") val releaseId: AniLibertyReleaseId?,
    @Json(name = "time") val time: Double?,
    @Json(name = "is_watched") val isWatched: Boolean?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "release_episode") val releaseEpisode: ReleaseEpisodeWithRelease?,
) {
    val episode: AniLibertyEpisode?
        get() = releaseEpisode?.toEpisode()

    val release: AniLibertyRelease?
        get() = releaseEpisode?.release

    @JsonClass(generateAdapter = true)
    data class ReleaseEpisodeWithRelease(
        @Json(name = "id") val id: AniLibertyReleaseEpisodeId?,
        @Json(name = "name") val name: String?,
        @Json(name = "ordinal") val ordinal: Double?,
        @Json(name = "ending") val ending: AniLibertyEpisodeSkip?,
        @Json(name = "opening") val opening: AniLibertyEpisodeSkip?,
        @Json(name = "preview") val preview: AniLibertyImageWithOptimized?,
        @Json(name = "hls_480") val hls480: String?,
        @Json(name = "hls_720") val hls720: String?,
        @Json(name = "hls_1080") val hls1080: String?,
        @Json(name = "duration") val duration: Double?,
        @Json(name = "rutube_id") val rutubeId: String?,
        @Json(name = "youtube_id") val youtubeId: String?,
        @Json(name = "updated_at") val updatedAt: String?,
        @Json(name = "sort_order") val sortOrder: Double?,
        @Json(name = "release_id") val releaseId: AniLibertyReleaseId?,
        @Json(name = "name_english") val nameEnglish: String?,
        @Json(name = "release") val release: AniLibertyRelease?,
    ) {
        fun toEpisode(): AniLibertyEpisode =
            AniLibertyEpisode(
                id = id,
                name = name,
                ordinal = ordinal,
                ending = ending,
                opening = opening,
                preview = preview,
                hls480 = hls480,
                hls720 = hls720,
                hls1080 = hls1080,
                duration = duration,
                rutubeId = rutubeId,
                youtubeId = youtubeId,
                updatedAt = updatedAt,
                sortOrder = sortOrder,
                releaseId = releaseId,
                nameEnglish = nameEnglish,
            )
    }
}
