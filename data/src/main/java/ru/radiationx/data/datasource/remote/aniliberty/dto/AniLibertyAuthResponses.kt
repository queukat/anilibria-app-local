package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AniLibertyOtpGetResponse(
    @Json(name = "code") val code: Int?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "expired_at") val expiredAt: String?,
    @Json(name = "ttl") val ttl: Int?,
    @Json(name = "device_id") val deviceId: String?,
    @Json(name = "created_at") val createdAt: String?,
)

/**
 * Универсальная модель токена:
 * разные эндпоинты могут отдавать разные названия полей.
 */
@JsonClass(generateAdapter = true)
data class AniLibertyAuthTokenResponse(
    @Json(name = "token") val token: String?,
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "expires_in") val expiresIn: Long?,
    @Json(name = "session_token") val sessionToken: String?,
    @Json(name = "refresh_token") val refreshToken: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertySocialLoginResponse(
    @Json(name = "url") val url: String?,
    @Json(name = "auth_url") val authUrl: String?,
    @Json(name = "state") val state: String?,
    @Json(name = "provider") val provider: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertySocialAuthenticateResponse(
    @Json(name = "token") val token: String?,
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "expires_in") val expiresIn: Long?,
    @Json(name = "session_token") val sessionToken: String?,
    @Json(name = "refresh_token") val refreshToken: String?,
)
