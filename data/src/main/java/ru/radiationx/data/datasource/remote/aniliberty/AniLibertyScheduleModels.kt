package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Models for schedule endpoints according to OpenAPI:
 *
 * GET /anime/schedule/now  -> { today: [...], tomorrow: [...], yesterday: [...] }
 * GET /anime/schedule/week -> currently can be either:
 * 1) { data: [...] }
 * 2) [ [...], [...], ... ]
 *
 * items are models.anime.schedule.v1.releaseInSchedule
 */
@JsonClass(generateAdapter = true)
data class AniLibertyReleaseInSchedule(
    @Json(name = "release") val release: AniLibertyRelease? = null,
    @Json(name = "full_season_is_released") val fullSeasonIsReleased: Boolean? = null,
    @Json(name = "published_release_episode") val publishedReleaseEpisode: AniLibertyEpisode? = null,
    @Json(name = "next_release_episode_number") val nextReleaseEpisodeNumber: Int? = null,
)

@JsonClass(generateAdapter = true)
data class AniLibertyScheduleNowResponse(
    @Json(name = "today") val today: List<AniLibertyReleaseInSchedule>? = null,
    @Json(name = "tomorrow") val tomorrow: List<AniLibertyReleaseInSchedule>? = null,
    @Json(name = "yesterday") val yesterday: List<AniLibertyReleaseInSchedule>? = null,
)

@JsonClass(generateAdapter = true)
data class AniLibertyScheduleWeekResponse(
    @Json(name = "data") val data: List<AniLibertyReleaseInSchedule>? = null,
)
