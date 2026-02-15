package ru.radiationx.anilibria.common

private const val ANI_LIBERTY_HOST = "https://aniliberty.top"

/**
 * AniLiberty часто отдаёт относительные пути вида `/storage/...`.
 * Coil такие строки не воспринимает как URL, поэтому для UI приводим к абсолютному.
 */
internal fun String?.toAbsoluteAniLibertyUrl(): String? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null

    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        return raw
    }

    return when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("/") -> "$ANI_LIBERTY_HOST$raw"
        else -> "$ANI_LIBERTY_HOST/$raw"
    }
}
