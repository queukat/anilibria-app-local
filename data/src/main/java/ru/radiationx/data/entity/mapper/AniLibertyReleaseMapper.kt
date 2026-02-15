package ru.radiationx.data.entity.mapper

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.entity.domain.release.BlockedInfo
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.system.ApiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val ANI_LIBERTY_HOST = "https://aniliberty.top"

/**
 * Best-effort mapping from AniLiberty v1 release (wire) to legacy domain [Release].
 *
 * Notes:
 * - The legacy domain model is much richer than v1 "list" response.
 * - For favorites screens we only need a safe subset (title/poster/genres/year/season + favorite flag).
 * - Heavy lists (episodes/torrents) are left empty.
 */
fun AniLibertyRelease.toLegacyReleaseOrNull(
    apiUtils: ApiUtils,
    isFavorite: Boolean = true,
): Release? {
    val idValue = id?.value ?: return null

    val titleRu = name?.main
        ?.let { apiUtils.escapeHtml(it).toString() }
        ?.trim()
        .orEmpty()

    val titleEn = (name?.english ?: name?.alternative)
        ?.let { apiUtils.escapeHtml(it).toString() }
        ?.trim()
        .orEmpty()

    val codeValue = alias?.value?.trim()?.takeIf { it.isNotEmpty() } ?: idValue.toString()

    val posterUrl = (
        poster?.optimized?.preview
            ?: poster?.optimized?.thumbnail
            ?: poster?.preview
            ?: poster?.thumbnail
        ).toAbsoluteAniLibertyUrl()

    val typeText = type?.description?.trim()?.takeIf { it.isNotEmpty() }
        ?: type?.value?.value?.trim()?.takeIf { it.isNotEmpty() }

    val genresText = genres
        ?.mapNotNull { it.name?.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    val seasonText = season?.description?.trim()?.takeIf { it.isNotEmpty() }
        ?: season?.value?.value?.trim()?.takeIf { it.isNotEmpty() }

    val yearText = year?.toString()

    val latest = latestEpisode?.ordinal?.takeIf { it > 0.0 }
    val total = episodesTotal?.takeIf { it > 0 }

    val seriesText = when {
        latest != null && total != null -> "${formatEpisodeOrdinal(latest)} из $total"
        total != null -> total.toString()
        latest != null -> formatEpisodeOrdinal(latest)
        else -> null
    }

    val statusCode = when (isOngoing) {
        false -> Release.STATUS_CODE_COMPLETE
        true -> Release.STATUS_CODE_PROGRESS
        null -> Release.STATUS_CODE_NOTHING
    }

    val blocked = (isBlockedByGeo == true) || (isBlockedByCopyrights == true)
    val blockedReason = when {
        isBlockedByGeo == true -> "Недоступно в вашем регионе"
        isBlockedByCopyrights == true -> "Недоступно по запросу правообладателя"
        else -> null
    }

    val updatedEpochSec = parseIsoToEpochSeconds(updatedAt)
        .takeIf { it > 0 }
        ?: parseIsoToEpochSeconds(freshAt)
            .takeIf { it > 0 }
        ?: parseIsoToEpochSeconds(createdAt)

    return Release(
        id = ReleaseId(idValue),
        code = ReleaseCode(codeValue),
        names = listOfNotNull(
            titleRu.takeIf { it.isNotBlank() },
            titleEn.takeIf { it.isNotBlank() },
        ).ifEmpty { listOf(codeValue) },
        series = seriesText,
        poster = posterUrl,
        torrentUpdate = updatedEpochSec,
        status = null,
        statusCode = statusCode,
        types = listOfNotNull(typeText),
        genres = genresText,
        voices = emptyList(),
        members = null,
        year = yearText,
        season = seasonText,
        days = emptyList(),
        description = description?.trim(),
        announce = notification?.trim(),
        favoriteInfo = FavoriteInfo(
            rating = addedInUsersFavorites ?: 0,
            isAdded = isFavorite,
        ),
        link = externalPlayer?.trim()?.takeIf { it.isNotEmpty() },
        franchises = emptyList(),

        showDonateDialog = false,
        blockedInfo = BlockedInfo(isBlocked = blocked, reason = blockedReason),
        moonwalkLink = externalPlayer?.trim()?.takeIf { it.isNotEmpty() },
        episodes = emptyList(),
        sourceEpisodes = emptyList(),
        externalPlaylists = emptyList(),
        rutubePlaylist = emptyList(),
        torrents = emptyList(),
    )
}

private fun formatEpisodeOrdinal(value: Double): String {
    val str = value.toString()
    return if (str.endsWith(".0")) str.dropLast(2) else str
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
 * Parses ISO-ish datetime strings into epoch seconds.
 *
 * Backend usually returns ISO-8601, but can include microseconds or miss timezone.
 * We normalize and try a small set of formats.
 */
private fun parseIsoToEpochSeconds(raw: String?): Int {
    val s0 = raw?.trim().orEmpty()
    if (s0.isEmpty()) return 0

    // Normalize: replace space with 'T'
    var s = s0.replace(' ', 'T')

    // Ensure timezone: if missing, treat as UTC.
    val hasZone = s.endsWith("Z") || Regex("""[+-]\d\d:?\d\d$""").containsMatchIn(s)
    if (!hasZone) s += "Z"


    // Truncate fractional seconds to millis (SimpleDateFormat can't parse variable micros reliably).
    s = Regex("""(\.\d{3})\d+(Z|[+-].*)$""").replace(s, "$1$2")


    val fmts = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
    )

    for (p in fmts) {
        try {
            val df = SimpleDateFormat(p, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val d: Date = df.parse(s) ?: continue
            val sec = (d.time / 1000L).toInt()
            if (sec > 0) return sec
        } catch (_: Throwable) {
        }
    }
    return 0
}
