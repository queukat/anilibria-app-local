package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AniLibertyReferenceValueDescription(
    @Json(name = "value") val value: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyReferenceValueLabelDescription(
    @Json(name = "value") val value: String?,
    @Json(name = "label") val label: String?,
    @Json(name = "is_adult") val isAdult: Boolean?,
    @Json(name = "description") val description: String?,
)
