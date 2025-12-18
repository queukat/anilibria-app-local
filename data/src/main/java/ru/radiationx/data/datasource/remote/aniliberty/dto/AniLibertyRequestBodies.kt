package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AniLibertyReleaseIdBody(
    @Json(name = "release_id") val releaseId: Int,
)

@JsonClass(generateAdapter = true)
data class AniLibertyCollectionAddBody(
    @Json(name = "release_id") val releaseId: Int,
    @Json(name = "type_of_collection") val typeOfCollection: String,
)
