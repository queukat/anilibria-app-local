package ru.radiationx.anilibria.common

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.entity.domain.types.ReleaseId

fun AniLibertyRelease.toLibriaDetails(
    id: ReleaseId,
    isFavorite: Boolean,
    hasViewed: Boolean,
): LibriaDetails {

    val titleRu = name?.main.orEmpty()
    val titleEn = name?.english ?: name?.alternative.orEmpty()

    val yearText = year?.toString()
    val seasonText = season?.description ?: season?.value
    val typeText = type?.description ?: type?.value
    val ageText = ageRating?.label ?: ageRating?.value
    val publishText = publishDay?.description ?: publishDay?.value

    val durationText = averageDurationOfEpisode
        ?.takeIf { it > 0 }
        ?.let { "$it мин." }

    val episodesText = episodesTotal
        ?.takeIf { it > 0 }
        ?.let { "$it эп." }

    val genresText = genres
        ?.mapNotNull { it.name }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

    val extraParts = listOfNotNull(
        yearText,
        seasonText,
        typeText,
        episodesText,
        durationText,
        publishText?.let { "выходит: $it" },
        ageText?.let { "рейтинг: $it" },
        genresText,
    )

    val extra = extraParts.joinToString(separator = ", ")

    val blockedAnnounce = when {
        isBlockedByGeo == true -> "Недоступно в вашем регионе."
        isBlockedByCopyrights == true -> "Недоступно по запросу правообладателя."
        else -> null
    }

    val announce = blockedAnnounce ?: notification.orEmpty()

    val image = poster?.optimized?.preview
        ?: poster?.preview
        ?: poster?.thumbnail
        ?: ""

    val favoriteCount = (addedInUsersFavorites ?: 0).toString()

    val hasEpisodes = (episodes?.isNotEmpty() == true) || ((episodesTotal ?: 0) > 0) || (latestEpisode != null)

    val hasFullHd = (episodes?.any { !it.hls1080.isNullOrBlank() } == true) ||
            (torrents?.any { (it.quality?.description ?: it.quality?.value).orEmpty().contains("1080") } == true)

    val hasWebPlayer = !externalPlayer.isNullOrBlank() ||
            (episodes?.any { !it.youtubeId.isNullOrBlank() || !it.rutubeId.isNullOrBlank() } == true)

    return LibriaDetails(
        id = id,
        titleRu = titleRu,
        titleEn = titleEn,
        extra = extra,
        description = description.orEmpty(),
        announce = announce,
        image = image,
        favoriteCount = favoriteCount,
        hasFullHd = hasFullHd,
        isFavorite = isFavorite,
        hasEpisodes = hasEpisodes,
        hasViewed = hasViewed,
        hasWebPlayer = hasWebPlayer,
    )
}
