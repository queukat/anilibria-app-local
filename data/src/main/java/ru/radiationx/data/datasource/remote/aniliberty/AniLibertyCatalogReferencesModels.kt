package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AniLibertyCatalogReferenceAgeRating(
    @Json(name = "value") val value: AniLibertyAgeRating?,
    @Json(name = "label") val label: String?,
    @Json(name = "is_adult") val isAdult: Boolean?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyCatalogReferenceProductionStatus(
    @Json(name = "value") val value: AniLibertyCatalogProductionStatus?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyCatalogReferencePublishStatus(
    @Json(name = "value") val value: AniLibertyCatalogPublishStatus?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyCatalogReferenceSeason(
    @Json(name = "value") val value: AniLibertySeason?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyCatalogReferenceSorting(
    @Json(name = "value") val value: AniLibertyCatalogSorting?,
    @Json(name = "label") val label: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyCatalogReferenceType(
    @Json(name = "value") val value: AniLibertyReleaseType?,
    @Json(name = "description") val description: String?,
)
