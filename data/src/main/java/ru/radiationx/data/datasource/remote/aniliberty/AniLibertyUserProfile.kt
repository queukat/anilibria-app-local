package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Устойчивая модель профиля /accounts/users/me/profile.
 * Swagger: models.users.v1.user (схема целиком не приложена).
 *
 * Добавляй поля по мере того как они реально нужны в UI/домене.
 */
@JsonClass(generateAdapter = true)
data class AniLibertyUserProfile(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "login") val login: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "nickname") val nickname: String? = null,
    @Json(name = "avatar") val avatar: AniLibertyImageWithOptimized? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = null,
)
