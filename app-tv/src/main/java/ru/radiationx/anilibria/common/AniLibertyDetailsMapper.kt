package ru.radiationx.anilibria.common

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPublishDay
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertySeason
import ru.radiationx.data.entity.domain.types.ReleaseId

fun AniLibertyRelease.toLibriaDetails(
    id: ReleaseId,
    isFavorite: Boolean,
    hasViewed: Boolean,
): LibriaDetails {

    val titleRu = name?.main.orEmpty()
    val titleEn = name?.english ?: name?.alternative.orEmpty()

    val yearText = year?.toString()?.takeIfNotBlank()

    val seasonText = season?.description?.takeIfNotBlank()
        ?: seasonToRu(season?.value)

    val typeText = type?.description?.takeIfNotBlank()
        ?: type?.value?.value?.takeIfNotBlank()

    val ageText = ageRating?.label?.takeIfNotBlank()
        ?: ageRating?.value?.value?.takeIfNotBlank()

    val publishText = publishDay?.description?.takeIfNotBlank()
        ?: publishDayToRu(publishDay?.value)

    val durationText = averageDurationOfEpisode
        ?.takeIf { it > 0 }
        ?.let { "$it мин." }

    val episodesText = episodesTotal
        ?.takeIf { it > 0 }
        ?.let { "$it эп." }

    val genresText = genres
        ?.mapNotNull { it.name?.trim() }
        ?.filter { it.isNotEmpty() }
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

    val hasEpisodes = (episodes?.isNotEmpty() == true) ||
        ((episodesTotal ?: 0) > 0) ||
        (latestEpisode != null)

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

private fun String?.takeIfNotBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun publishDayToRu(day: AniLibertyPublishDay?): String? {
    return when (day?.value) {
        1 -> "Понедельник"
        2 -> "Вторник"
        3 -> "Среда"
        4 -> "Четверг"
        5 -> "Пятница"
        6 -> "Суббота"
        7 -> "Воскресенье"
        else -> null
    }
}

private fun seasonToRu(season: AniLibertySeason?): String? {
    return when (season?.value) {
        "winter" -> "зима"
        "spring" -> "весна"
        "summer" -> "лето"
        "autumn" -> "осень"
        else -> season?.value
    }
}
