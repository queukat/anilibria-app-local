package ru.radiationx.data.entity.mapper

import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyUserProfile
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyAuthTokenResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpGetResponse
import ru.radiationx.data.entity.domain.auth.OtpInfo
import ru.radiationx.data.entity.domain.auth.OtpNotFoundException
import ru.radiationx.data.entity.domain.auth.SocialAuth
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.data.entity.response.auth.OtpInfoResponse
import ru.radiationx.data.entity.response.auth.SocialAuthResponse
import ru.radiationx.data.entity.response.other.ProfileResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun OtpInfoResponse.toDomain(): OtpInfo = OtpInfo(
    code = code,
    description = description,
    expiresAt = expiredAt.secToDate(),
    remainingTime = remainingTime.secToMillis()
)

fun SocialAuthResponse.toDomain(): SocialAuth = SocialAuth(
    key = key,
    title = title,
    socialUrl = socialUrl,
    resultPattern = resultPattern,
    errorUrlPattern = errorUrlPattern
)

fun ProfileResponse.toDomain(apiConfig: ApiConfig): ProfileItem = ProfileItem(
    id,
    nick.orEmpty(),
    avatarUrl?.appendBaseUrl(apiConfig.baseImagesUrl)
)

private const val ANI_LIBERTY_HOST = "https://aniliberty.top"

/**
 * Маппинг профиля AniLiberty v1 в доменную модель.
 */
fun AniLibertyUserProfile.toDomain(): ProfileItem = ProfileItem(
    id = id ?: 0,
    nick = (nickname ?: login ?: email).orEmpty(),
    avatarUrl = (
        avatar?.optimized?.preview
            ?: avatar?.preview
            ?: avatar?.optimized?.thumbnail
            ?: avatar?.thumbnail
        ).toAbsoluteAniLibertyUrl()
)

/**
 * Маппинг OTP (AniLiberty v1) в доменную модель.
 *
 * В v1 нет готового `description`, поэтому подставляем дефолтный текст.
 */
fun AniLibertyOtpGetResponse.toDomain(): OtpInfo {
    val otpCode = otp?.code?.trim().orEmpty()
    if (otpCode.isEmpty()) {
        throw OtpNotFoundException("OTP code is empty")
    }

    // Backend returns seconds; may be fractional (e.g. 299.806666)
    val remainingSeconds = (remainingTime ?: 0.0).coerceAtLeast(0.0)
    val remainingMs = (remainingSeconds * 1000.0).toLong()

    val expiresAtMs = otp?.expiredAt.parseAniLibertyDateMillisOrNull()
        ?: (System.currentTimeMillis() + remainingMs)

    val description = "Введите этот код на сайте AniLiberty или в приложении, чтобы подтвердить вход на устройстве."

    return OtpInfo(
        code = otpCode,
        description = description,
        expiresAt = Date(expiresAtMs),
        remainingTime = remainingMs
    )
}

/**
 * Достаёт токен из ответа AniLiberty.
 *
 * Сервер может отдавать поле `token`, `access_token` или `session_token` —
 * сохраняем первое непустое.
 *
 * В хранилище кладём «сырой» токен без префикса `Bearer`.
 */
fun AniLibertyAuthTokenResponse.extractTokenOrNull(): String? {
    val raw = sequenceOf(token, accessToken, sessionToken)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?: return null

    return raw.removeBearerPrefix()
}

private fun String.removeBearerPrefix(): String {
    val s = trim()
    return if (s.startsWith("Bearer ", ignoreCase = true)) {
        s.substringAfter("Bearer ", missingDelimiterValue = s).trim()
    } else {
        s
    }
}

private fun String?.toAbsoluteAniLibertyUrl(): String? {
    val s = this?.trim().orEmpty()
    if (s.isEmpty()) return null
    return when {
        s.startsWith("http://") || s.startsWith("https://") -> s
        s.startsWith("//") -> "https:$s"
        s.startsWith("/") -> ANI_LIBERTY_HOST + s
        else -> s
    }
}

/**
 * Пытается распарсить дату/время, приходящие из AniLiberty API, в миллисекунды.
 *
 * Поддерживает:
 *  - epoch seconds / epoch millis
 *  - ISO-8601 (несколько распространённых вариантов)
 *  - `yyyy-MM-dd HH:mm:ss`
 */
private fun String?.parseAniLibertyDateMillisOrNull(): Long? {
    val s = this?.trim().orEmpty()
    if (s.isEmpty()) return null

    // epoch time
    s.toLongOrNull()?.let { raw ->
        return when {
            raw >= 1_000_000_000_000L -> raw // millis
            raw >= 1_000_000_000L -> raw * 1000L // seconds
            else -> raw
        }
    }

    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
    )

    for (pattern in patterns) {
        runCatching {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                // Для шаблонов без timezone уходим в UTC, чтобы результат не зависел от локали устройства.
                if (!pattern.contains("X")) {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
            sdf.parse(s)?.time
        }.getOrNull()?.let { return it }
    }

    return null
}
