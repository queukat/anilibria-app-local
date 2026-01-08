package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * POST /accounts/users/me/favorites/releases
 * Сейчас нужно хотя бы чтобы тип существовал и сериализовался.
 * Поля можно расширить, когда будешь точно сверять swagger/бэкенд.
 */
@JsonClass(generateAdapter = true)
data class AniLibertyFavoriteReleasesBody(
    @Json(name = "page") val page: Int? = null,
    @Json(name = "limit") val limit: Int? = null,
    @Json(name = "f") val f: AniLibertyCommonFiltersBody? = null,
    @Json(name = "include") val include: String? = null,
    @Json(name = "exclude") val exclude: String? = null,
)
