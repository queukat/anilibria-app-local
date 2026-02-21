package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi

internal fun parseScheduleWeekResponseJson(
    json: String,
    moshi: Moshi,
    onUnsupportedPayload: ((Throwable?) -> Unit)? = null,
): AniLibertyScheduleWeekResponse {
    var parseError: Throwable? = null

    val objectResponse = runCatching {
        moshi.adapter(AniLibertyScheduleWeekResponse::class.java).fromJson(json)
    }.onFailure {
        parseError = it
    }.getOrNull()
    if (objectResponse?.data != null) {
        return objectResponse
    }

    val arrayResponse = runCatching {
        json.fetchListOrNestedList<AniLibertyReleaseInSchedule>(moshi)
    }.onFailure {
        parseError = parseError ?: it
    }.getOrNull()
    if (arrayResponse != null) {
        return AniLibertyScheduleWeekResponse(data = arrayResponse)
    }

    onUnsupportedPayload?.invoke(parseError)
    return AniLibertyScheduleWeekResponse(data = emptyList())
}
