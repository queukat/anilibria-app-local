package ru.radiationx.data.entity.mapper

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyEpisode
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyEpisodeSkip
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseMember
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseMemberRoleType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyTorrent
import ru.radiationx.data.entity.domain.release.BlockedInfo
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.ExternalEpisode
import ru.radiationx.data.entity.domain.release.ExternalPlaylist
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.Franchise
import ru.radiationx.data.entity.domain.release.Members
import ru.radiationx.data.entity.domain.release.PlayerSkips
import ru.radiationx.data.entity.domain.release.QualityInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.RutubeEpisode
import ru.radiationx.data.entity.domain.release.SourceEpisode
import ru.radiationx.data.entity.domain.release.TorrentItem
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.types.TorrentId
import ru.radiationx.data.system.ApiUtils
import java.math.BigDecimal
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

    val latest = latestEpisode?.toDisplayOrder()?.takeIf { it > 0.0 }
        ?: episodes
            .orEmpty()
            .mapNotNull { it.toDisplayOrder()?.takeIf { order -> order > 0.0 } }
            .maxOrNull()
    val total = episodesTotal?.takeIf { it > 0 }
    val hasPublishedEpisodes = latest != null || episodes.orEmpty().isNotEmpty() || total != null
    val hasSchedule = publishDay?.value?.value != null
    val statusCode = resolveLegacyStatusCode(
        isOngoing = isOngoing,
        isInProduction = isInProduction,
        hasPublishedEpisodes = hasPublishedEpisodes,
        hasSchedule = hasSchedule,
    )
    val ongoing = statusCode == Release.STATUS_CODE_PROGRESS

    val seriesText = when {
        total != null && latest != null && ongoing == true -> "${formatEpisodeOrdinal(latest)} из $total"
        total != null && ongoing == false -> total.toString()
        total != null && latest != null -> {
            if (latest < total.toDouble()) {
                "${formatEpisodeOrdinal(latest)} из $total"
            } else {
                total.toString()
            }
        }
        total != null -> total.toString()
        latest != null -> formatEpisodeOrdinal(latest)
        else -> null
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
        days = publishDay?.value?.value?.let { listOf(it.toString()) }.orEmpty(),
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

/**
 * Maps AniLiberty release to legacy full release model used by TV detail/player flows.
 */
fun AniLibertyRelease.toLegacyFullReleaseOrNull(
    apiUtils: ApiUtils,
    isFavorite: Boolean = true,
    franchises: List<Franchise> = emptyList(),
): Release? {
    val base = toLegacyReleaseOrNull(
        apiUtils = apiUtils,
        isFavorite = isFavorite,
    ) ?: return null

    val releaseId = base.id
    val sortedEpisodes = episodes.orEmpty().sortedByDisplayOrderAsc()

    val episodesDomain = sortedEpisodes
        .mapNotNull { it.toLegacyEpisodeOrNull(releaseId, apiUtils) }
    val sourceEpisodesDomain = sortedEpisodes
        .mapNotNull { it.toLegacySourceEpisodeOrNull(releaseId, apiUtils) }
    val rutubeEpisodesDomain = sortedEpisodes
        .mapNotNull { it.toLegacyRutubeEpisodeOrNull(releaseId, apiUtils) }
    val torrentsDomain = torrents.orEmpty()
        .mapNotNull { it.toLegacyTorrentOrNull(releaseId) }

    return base.copy(
        members = members.toLegacyMembersOrNull(apiUtils),
        franchises = franchises,
        episodes = episodesDomain,
        sourceEpisodes = sourceEpisodesDomain,
        externalPlaylists = sortedEpisodes.toYoutubeExternalPlaylistOrEmpty(releaseId, apiUtils),
        rutubePlaylist = rutubeEpisodesDomain,
        torrents = torrentsDomain,
    )
}

object AniLibertyLegacyReleaseMapper {
    fun toLegacyReleaseOrNull(
        release: AniLibertyRelease,
        apiUtils: ApiUtils,
        isFavorite: Boolean = true,
    ): Release? = release.toLegacyReleaseOrNull(
        apiUtils = apiUtils,
        isFavorite = isFavorite,
    )

    fun toLegacyFullReleaseOrNull(
        release: AniLibertyRelease,
        apiUtils: ApiUtils,
        isFavorite: Boolean = true,
        franchises: List<Franchise> = emptyList(),
    ): Release? = release.toLegacyFullReleaseOrNull(
        apiUtils = apiUtils,
        isFavorite = isFavorite,
        franchises = franchises,
    )
}

private fun formatEpisodeOrdinal(value: Double): String {
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

private fun Int.toEpochDate(): Date = Date(toLong() * 1000L)

private fun resolveLegacyStatusCode(
    isOngoing: Boolean?,
    isInProduction: Boolean?,
    hasPublishedEpisodes: Boolean,
    hasSchedule: Boolean,
): String {
    if (isOngoing == true || isInProduction == true) {
        return Release.STATUS_CODE_PROGRESS
    }

    val hasExplicitStoppedState = isOngoing == false || isInProduction == false
    if (!hasExplicitStoppedState) {
        return Release.STATUS_CODE_NOTHING
    }

    return if (!hasPublishedEpisodes && hasSchedule) {
        Release.STATUS_CODE_NOT_ONGOING
    } else {
        Release.STATUS_CODE_COMPLETE
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

private fun List<AniLibertyEpisode>?.toYoutubeExternalPlaylistOrEmpty(
    releaseId: ReleaseId,
    apiUtils: ApiUtils,
): List<ExternalPlaylist> {
    val youtubeEpisodes = this.orEmpty()
        .mapNotNull { episode ->
            val youtubeId = episode.youtubeId?.trim().orEmpty().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val episodeId = episode.toEpisodeIdOrNull(releaseId) ?: return@mapNotNull null
            ExternalEpisode(
                id = episodeId,
                title = episode.toCombinedTitle(apiUtils),
                url = "https://www.youtube.com/watch?v=$youtubeId",
            )
        }
    if (youtubeEpisodes.isEmpty()) return emptyList()
    return listOf(
        ExternalPlaylist(
            tag = "youtube",
            title = "YouTube",
            actionText = "Открыть",
            episodes = youtubeEpisodes,
        )
    )
}

private fun List<AniLibertyReleaseMember>?.toLegacyMembersOrNull(apiUtils: ApiUtils): Members? {
    val timing = mutableListOf<String>()
    val voicing = mutableListOf<String>()
    val editing = mutableListOf<String>()
    val decorating = mutableListOf<String>()
    val translating = mutableListOf<String>()

    this.orEmpty().forEach { member ->
        val nickname = member.nickname
            ?.let { apiUtils.escapeHtml(it).toString() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@forEach

        when (member.role?.value?.value) {
            AniLibertyReleaseMemberRoleType.Timing.value -> timing += nickname
            AniLibertyReleaseMemberRoleType.Voicing.value -> voicing += nickname
            AniLibertyReleaseMemberRoleType.Editing.value -> editing += nickname
            AniLibertyReleaseMemberRoleType.Decorating.value -> decorating += nickname
            AniLibertyReleaseMemberRoleType.Translating.value -> translating += nickname
        }
    }

    val hasAny = timing.isNotEmpty() ||
        voicing.isNotEmpty() ||
        editing.isNotEmpty() ||
        decorating.isNotEmpty() ||
        translating.isNotEmpty()

    if (!hasAny) return null

    return Members(
        timing = timing,
        voicing = voicing,
        editing = editing,
        decorating = decorating,
        translating = translating,
    )
}

private fun AniLibertyEpisode.toLegacyEpisodeOrNull(
    releaseId: ReleaseId,
    apiUtils: ApiUtils,
): Episode? {
    val quality = QualityInfo(
        urlSd = hls480?.trim()?.takeIf { it.isNotEmpty() },
        urlHd = hls720?.trim()?.takeIf { it.isNotEmpty() },
        urlFullHd = hls1080?.trim()?.takeIf { it.isNotEmpty() },
    )
    if (quality.available.isEmpty()) return null

    val episodeId = toEpisodeIdOrNull(releaseId) ?: return null

    return Episode(
        id = episodeId,
        title = toCombinedTitle(apiUtils),
        qualityInfo = quality,
        updatedAt = parseIsoToEpochSeconds(updatedAt).takeIf { it > 0 }?.toEpochDate(),
        skips = toSkipsOrNull(),
    )
}

private fun AniLibertyEpisode.toLegacySourceEpisodeOrNull(
    releaseId: ReleaseId,
    apiUtils: ApiUtils,
): SourceEpisode? {
    val quality = QualityInfo(
        urlSd = hls480?.trim()?.takeIf { it.isNotEmpty() },
        urlHd = hls720?.trim()?.takeIf { it.isNotEmpty() },
        urlFullHd = hls1080?.trim()?.takeIf { it.isNotEmpty() },
    )
    if (quality.available.isEmpty()) return null

    val episodeId = toEpisodeIdOrNull(releaseId) ?: return null

    return SourceEpisode(
        id = episodeId,
        title = toCombinedTitle(apiUtils),
        updatedAt = parseIsoToEpochSeconds(updatedAt).takeIf { it > 0 }?.toEpochDate(),
        qualityInfo = quality,
    )
}

private fun AniLibertyEpisode.toLegacyRutubeEpisodeOrNull(
    releaseId: ReleaseId,
    apiUtils: ApiUtils,
): RutubeEpisode? {
    val rutube = rutubeId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val episodeId = toEpisodeIdOrNull(releaseId) ?: return null

    return RutubeEpisode(
        id = episodeId,
        title = toCombinedTitle(apiUtils),
        updatedAt = parseIsoToEpochSeconds(updatedAt).takeIf { it > 0 }?.toEpochDate(),
        rutubeId = rutube,
        url = "https://rutube.ru/play/embed/$rutube",
    )
}

private fun AniLibertyEpisode.toEpisodeIdOrNull(releaseId: ReleaseId): EpisodeId? {
    val value = toDisplayOrder()
        ?.let(::formatEpisodeOrdinal)
        ?: id?.value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    value ?: return null
    return EpisodeId(value, releaseId)
}

private fun AniLibertyEpisode.toCombinedTitle(apiUtils: ApiUtils): String? {
    val titleMain = name
        ?.let { apiUtils.escapeHtml(it).toString() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val titleEnglish = nameEnglish
        ?.let { apiUtils.escapeHtml(it).toString() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val title = listOfNotNull(titleMain, titleEnglish).joinToString(" • ")
    val ordinalTitle = toDisplayOrder()
        ?.let(::formatEpisodeOrdinal)
        ?.let { ordinal ->
            title.takeIf { it.isNotEmpty() }?.let { "$ordinal • $it" }
        }

    return ordinalTitle
        ?: title.takeIf { it.isNotEmpty() }
        ?: toDisplayOrder()?.let { "Серия ${formatEpisodeOrdinal(it)}" }
}

private fun AniLibertyEpisode.toDisplayOrder(): Double? = sortOrder ?: ordinal

private fun List<AniLibertyEpisode>.sortedByDisplayOrderAsc(): List<AniLibertyEpisode> {
    return sortedWith(
        compareBy<AniLibertyEpisode>(
            { it.toDisplayOrder() == null },
            { it.toDisplayOrder() ?: Double.MAX_VALUE },
            { it.id?.value.orEmpty() },
        )
    )
}

private fun AniLibertyEpisode.toSkipsOrNull(): PlayerSkips? {
    val openingSkip = opening.toLegacySkipOrNull()
    val endingSkip = ending.toLegacySkipOrNull()
    if (openingSkip == null && endingSkip == null) return null
    return PlayerSkips(
        opening = openingSkip,
        ending = endingSkip,
    )
}

private fun AniLibertyEpisodeSkip?.toLegacySkipOrNull(): PlayerSkips.Skip? {
    val startSec = this?.start ?: return null
    val stopSec = this.stop ?: return null
    if (startSec < 0 || stopSec <= startSec) return null
    return PlayerSkips.Skip(
        start = (startSec * 1000.0).toLong(),
        end = (stopSec * 1000.0).toLong(),
    )
}

private fun AniLibertyTorrent.toLegacyTorrentOrNull(releaseId: ReleaseId): TorrentItem? {
    val torrentId = id ?: return null

    val qualityText = quality?.description
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: quality?.value?.trim()?.takeIf { it.isNotEmpty() }
    val seriesText = label?.trim()?.takeIf { it.isNotEmpty() }
        ?: type?.description?.trim()?.takeIf { it.isNotEmpty() }

    val epoch = parseIsoToEpochSeconds(updatedAt).takeIf { it > 0 }
        ?: parseIsoToEpochSeconds(createdAt).takeIf { it > 0 }

    return TorrentItem(
        id = TorrentId(torrentId, releaseId),
        hash = hash?.trim()?.takeIf { it.isNotEmpty() },
        leechers = leechers ?: 0,
        seeders = seeders ?: 0,
        completed = completedTimes ?: 0,
        quality = qualityText,
        series = seriesText,
        size = size ?: 0L,
        url = magnet?.trim()?.takeIf { it.isNotEmpty() },
        date = epoch?.toEpochDate(),
    )
}
