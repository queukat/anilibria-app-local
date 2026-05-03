package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * GET /app/status
 * OpenAPI: responses.v1.app.status
 */
@JsonClass(generateAdapter = true)
data class AniLibertyAppStatus(
    @Json(name = "request") val request: Request? = null,
    @Json(name = "is_alive") val isAlive: Boolean? = null,
    @Json(name = "available_api_endpoints") val availableApiEndpoints: List<String>? = null,
) {
    @JsonClass(generateAdapter = true)
    data class Request(
        @Json(name = "ip") val ip: String? = null,
        @Json(name = "country") val country: String? = null,
        @Json(name = "iso_code") val isoCode: String? = null,
        @Json(name = "timezone") val timezone: String? = null,
    )
}

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
