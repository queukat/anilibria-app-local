package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Media / Ads models according to OpenAPI.
 */

/**
 * GET /media/vasts
 * OpenAPI: models.ads.vasts.v1.vast
 */
@JsonClass(generateAdapter = true)
data class AniLibertyVast(
    @Json(name = "id") val id: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "ad_erid") val adErid: String? = null,
    @Json(name = "ad_company_itn") val adCompanyItn: String? = null,
    @Json(name = "ad_company_name") val adCompanyName: String? = null,
)

/**
 * GET /media/promotions
 * OpenAPI: responses.v1.media.promotions -> { data: [...] }
 */
@JsonClass(generateAdapter = true)
data class AniLibertyMediaPromotionsResponse(
    @Json(name = "data") val data: List<AniLibertyPromotion>? = null,
)

@JsonClass(generateAdapter = true)
data class AniLibertyPromotion(
    @Json(name = "id") val id: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "url_label") val urlLabel: String? = null,
    @Json(name = "image") val image: AniLibertyImageWithOptimized? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "is_ad") val isAd: Boolean? = null,
    @Json(name = "ad_erid") val adErid: String? = null,
    @Json(name = "ad_origin") val adOrigin: String? = null,
    @Json(name = "release") val release: AniLibertyRelease? = null,
    @Json(name = "has_overlay") val hasOverlay: Boolean? = null,
)

/**
 * GET /media/videos
 * OpenAPI: responses.v1.media.videos -> { data: [...] }
 */
@JsonClass(generateAdapter = true)
data class AniLibertyMediaVideosResponse(
    @Json(name = "data") val data: List<AniLibertyVideo>? = null,
)

@JsonClass(generateAdapter = true)
data class AniLibertyVideo(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "views") val views: Int? = null,
    @Json(name = "image") val image: AniLibertyImageWithOptimized? = null,
    @Json(name = "comments") val comments: Int? = null,
    @Json(name = "video_id") val videoId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "is_announce") val isAnnounce: Boolean? = null,

    @Json(name = "origin") val origin: AniLibertyVideoOrigin? = null,
)

@JsonClass(generateAdapter = true)
data class AniLibertyVideoOrigin(
    @Json(name = "id") val id: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "type") val type: AniLibertyVideoOriginType? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "is_announce") val isAnnounce: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class AniLibertyVideoOriginType(
    @Json(name = "value") val value: String? = null,
    @Json(name = "description") val description: String? = null,
)
