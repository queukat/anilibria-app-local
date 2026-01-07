package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AniLibertyOtpGetBody(
    @Json(name = "device_id") val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class AniLibertyOtpAcceptBody(
    @Json(name = "code") val code: Int,
)

@JsonClass(generateAdapter = true)
data class AniLibertyOtpLoginBody(
    @Json(name = "code") val code: Int,
    @Json(name = "device_id") val deviceId: String,
)

@JsonClass(generateAdapter = true)
data class AniLibertyAuthLoginBody(
    @Json(name = "login") val login: String,
    @Json(name = "password") val password: String,
)

@JsonClass(generateAdapter = true)
data class AniLibertyEmailBody(
    @Json(name = "email") val email: String,
)

@JsonClass(generateAdapter = true)
data class AniLibertyPasswordResetBody(
    @Json(name = "token") val token: String,
    @Json(name = "password") val password: String,
    @Json(name = "password_confirmation") val passwordConfirmation: String,
)
