package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AniLibertyAppStatus(
    @Json(name = "status") val status: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "min_version") val minVersion: String? = null,
    @Json(name = "latest_version") val latestVersion: String? = null,
    @Json(name = "maintenance") val maintenance: Boolean? = null,
)

/**
 * /app/search/releases
 * Swagger: required query + include/exclude
 */
data class AniLibertyAppSearchReleasesRequest(
    val query: String,
    val fields: AniLibertyFieldSpec? = null,
) {
    init {
        require(query.isNotBlank()) { "AniLibertyAppSearchReleasesRequest.query must not be blank" }
    }
}
