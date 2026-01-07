package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Swagger: models.anime.franchises.v1.franchise
 */
@JsonClass(generateAdapter = true)
data class AniLibertyFranchise(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "name_english") val nameEnglish: String?,
    @Json(name = "image") val image: AniLibertyImageWithOptimized?,
    @Json(name = "rating") val rating: Double?,
    @Json(name = "last_year") val lastYear: Int?,
    @Json(name = "first_year") val firstYear: Int?,
    @Json(name = "total_releases") val totalReleases: Int?,
    @Json(name = "total_episodes") val totalEpisodes: Int?,
    @Json(name = "total_duration") val totalDuration: String?,
    @Json(name = "total_duration_in_seconds") val totalDurationInSeconds: Int?,
)

/**
 * Swagger: models.anime.franchises.v1.franchise.release (+ optional release in some responses)
 */
@JsonClass(generateAdapter = true)
data class AniLibertyFranchiseReleaseItem(
    @Json(name = "id") val id: String?,
    @Json(name = "sort_order") val sortOrder: Int?,
    @Json(name = "release_id") val releaseId: Int?,
    @Json(name = "franchise_id") val franchiseId: String?,

    @Json(name = "release") val release: AniLibertyRelease?,
)

/**
 * Swagger: responses.v1.anime.franchise
 * базовые поля франшизы + franchise_releases
 */
@JsonClass(generateAdapter = true)
data class AniLibertyFranchiseDetails(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "name_english") val nameEnglish: String?,
    @Json(name = "image") val image: AniLibertyImageWithOptimized?,
    @Json(name = "rating") val rating: Double?,
    @Json(name = "last_year") val lastYear: Int?,
    @Json(name = "first_year") val firstYear: Int?,
    @Json(name = "total_releases") val totalReleases: Int?,
    @Json(name = "total_episodes") val totalEpisodes: Int?,
    @Json(name = "total_duration") val totalDuration: String?,
    @Json(name = "total_duration_in_seconds") val totalDurationInSeconds: Int?,

    @Json(name = "franchise_releases") val franchiseReleases: List<AniLibertyFranchiseReleaseItem>?,
)

/**
 * Swagger: responses.v1.anime.franchises.byRelease
 * массив объектов, каждый такой же как details (по схеме allOf).
 */
typealias AniLibertyFranchisesByRelease = List<AniLibertyFranchiseDetails>
