package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * POST /accounts/otp/get
 * OpenAPI: responses.api.v1.accounts.otp.get
 * {
 *   "otp": { "code": "058701", "user_id": 1337, "device_id": "...", "expired_at": "..." },
 *   "remaining_time": 120.0
 * }
 */
@JsonClass(generateAdapter = true)
data class AniLibertyOtpGetResponse(
    @Json(name = "otp") val otp: AniLibertyOtp? = null,
    /**
     * Backend may return fractional seconds (e.g. 299.806666), so we parse as Double.
     */
    @Json(name = "remaining_time") val remainingTime: Double? = null,
)

@JsonClass(generateAdapter = true)
data class AniLibertyOtp(
    @Json(name = "code") val code: String? = null,
    @Json(name = "user_id") val userId: Int? = null,
    @Json(name = "device_id") val deviceId: String? = null,
    @Json(name = "expired_at") val expiredAt: String? = null,
)

/**
 * Универсальная модель токена:
 * разные эндпоинты могут отдавать разные названия полей.
 *
 * NOTE: OpenAPI сейчас возвращает просто { token }, но этот DTO безопасен:
 * лишние nullable-поля останутся null.
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
