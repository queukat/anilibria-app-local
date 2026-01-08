package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Scaffold wire model.
 * Adjust when you have exact swagger for schedule endpoints.
 */
@JsonClass(generateAdapter = true)
data class AniLibertyScheduleDay(
    @Json(name = "day") val day: AniLibertyPublishDay?,
    @Json(name = "releases") val releases: List<AniLibertyRelease>?,
)

typealias AniLibertyScheduleNowResponse = List<AniLibertyRelease>
typealias AniLibertyScheduleWeekResponse = List<AniLibertyScheduleDay>
