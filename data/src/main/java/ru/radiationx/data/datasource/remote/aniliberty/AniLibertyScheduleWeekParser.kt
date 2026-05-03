package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

internal fun parseScheduleWeekResponseJson(
    json: String,
    moshi: Moshi,
    onUnsupportedPayload: ((Throwable?) -> Unit)? = null,
): AniLibertyScheduleWeekResponse {
    var parseError: Throwable? = null

    val objectResponse =
        runCatching {
            moshi.adapter(AniLibertyScheduleWeekResponse::class.java).fromJson(json)
        }.onFailure {
            parseError = it
        }.getOrNull()
    if (objectResponse?.data != null) {
        return objectResponse
    }

    val flatArrayType =
        Types.newParameterizedType(
            List::class.java,
            AniLibertyReleaseInSchedule::class.java,
        )
    val flatArrayResponse =
        runCatching {
            moshi.adapter<List<AniLibertyReleaseInSchedule>>(flatArrayType).fromJson(json)
        }.onFailure {
            parseError = parseError ?: it
        }.getOrNull()
    if (flatArrayResponse != null) {
        return AniLibertyScheduleWeekResponse(data = flatArrayResponse)
    }

    val nestedArrayType =
        Types.newParameterizedType(
            List::class.java,
            flatArrayType,
        )
    val nestedArrayResponse =
        runCatching {
            moshi.adapter<List<List<AniLibertyReleaseInSchedule>>>(nestedArrayType).fromJson(json)
        }.onFailure {
            parseError = parseError ?: it
        }.getOrNull()
    if (nestedArrayResponse != null) {
        return AniLibertyScheduleWeekResponse(data = nestedArrayResponse.flatten())
    }

    onUnsupportedPayload?.invoke(parseError)
    return AniLibertyScheduleWeekResponse(data = emptyList())
}

object AniLibertyScheduleWeekPayloadParser {
    fun parse(
        json: String,
        moshi: Moshi,
        onUnsupportedPayload: ((Throwable?) -> Unit)? = null,
    ): AniLibertyScheduleWeekResponse {
        return parseScheduleWeekResponseJson(
            json = json,
            moshi = moshi,
            onUnsupportedPayload = onUnsupportedPayload,
        )
    }
}
