package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Устойчивая модель элемента истории просмотров.
 * Swagger: responses.v1.accounts.users.me.views.history
 *
 * В swagger поле называется release_episode (allOf: episode + release).
 */
@JsonClass(generateAdapter = true)
data class AniLibertyUserViewHistoryItem(
    @Json(name = "release_episode_id") val releaseEpisodeId: String? = null,
    @Json(name = "release_id") val releaseId: Int? = null,

    @Json(name = "time") val time: Double? = null,
    @Json(name = "is_watched") val isWatched: Boolean? = null,

    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,

    @Json(name = "release_episode") val releaseEpisode: ReleaseEpisodeWithRelease? = null,
) {

    /**
     * Для удобства использования на верхнем уровне.
     * Если UI уже ждёт episode и release, можно не менять вызывающий код.
     */
    val episode: AniLibertyEpisode?
        get() = releaseEpisode?.toEpisode()

    val release: AniLibertyRelease?
        get() = releaseEpisode?.release

    @JsonClass(generateAdapter = true)
    data class ReleaseEpisodeWithRelease(
        @Json(name = "id") val id: String? = null,
        @Json(name = "name") val name: String? = null,
        @Json(name = "ordinal") val ordinal: Double? = null,
        @Json(name = "ending") val ending: AniLibertyEpisodeSkip? = null,
        @Json(name = "opening") val opening: AniLibertyEpisodeSkip? = null,
        @Json(name = "preview") val preview: AniLibertyImageWithOptimized? = null,
        @Json(name = "hls_480") val hls480: String? = null,
        @Json(name = "hls_720") val hls720: String? = null,
        @Json(name = "hls_1080") val hls1080: String? = null,
        @Json(name = "duration") val duration: Double? = null,
        @Json(name = "rutube_id") val rutubeId: String? = null,
        @Json(name = "youtube_id") val youtubeId: String? = null,
        @Json(name = "updated_at") val updatedAt: String? = null,
        @Json(name = "sort_order") val sortOrder: Double? = null,
        @Json(name = "release_id") val releaseId: Double? = null,
        @Json(name = "name_english") val nameEnglish: String? = null,

        @Json(name = "release") val release: AniLibertyRelease? = null,
    ) {
        fun toEpisode(): AniLibertyEpisode = AniLibertyEpisode(
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
