package ru.radiationx.anilibria.common

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.entity.domain.types.ReleaseId
import java.text.NumberFormat
import javax.inject.Inject

class AniLibertyDetailDataConverter @Inject constructor() {

    fun toDetail(
        releaseId: ReleaseId,
        r: AniLibertyRelease,
        isFavorite: Boolean,
        hasViewed: Boolean,
    ): LibriaDetails {

        val titleRu = r.name?.main.orEmpty()
        val titleEn = r.name?.english ?: r.name?.alternative.orEmpty()

        val genres = r.genres
            ?.mapNotNull { it.name?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(3)
            ?.joinToString(", ")

        val yearSeason = listOfNotNull(
            r.year?.toString(),
            r.season?.description ?: r.season?.value
        ).joinToString(" ")

        val type = r.type?.description ?: r.type?.value

        val episodesText = r.episodesTotal
            ?.takeIf { it > 0 }
            ?.let { "Серии: $it" }
            ?: "Серии: Нет данных"

        val durationText = r.averageDurationOfEpisode
            ?.takeIf { it > 0 }
            ?.let { "$it мин." }

        val ageText = r.ageRating?.label ?: r.ageRating?.value
        val publishText = r.publishDay?.description ?: r.publishDay?.value

        val extra = listOfNotNull(
            genres,
            yearSeason.ifBlank { null },
            type,
            episodesText,
            durationText,
            publishText?.let { "Выход: $it" },
            ageText?.let { "Рейтинг: $it" },
        ).joinToString(" • ")

        val description = r.description.orEmpty().trim()

        val announce = buildAnnounce(r)

        val image = r.poster?.optimized?.preview
            ?: r.poster?.preview
            ?: r.poster?.thumbnail
            ?: ""

        val favoriteCount = NumberFormat.getNumberInstance().format(r.addedInUsersFavorites ?: 0)

        val hasEpisodes = (r.episodes?.isNotEmpty() == true) ||
                ((r.episodesTotal ?: 0) > 0) ||
                (r.latestEpisode != null)

        val hasFullHd = (r.episodes?.any { !it.hls1080.isNullOrBlank() } == true) ||
                (r.torrents?.any { (it.quality?.description ?: it.quality?.value).orEmpty().contains("1080") } == true)

        val hasWebPlayer = !r.externalPlayer.isNullOrBlank() ||
                (r.episodes?.any { !it.youtubeId.isNullOrBlank() || !it.rutubeId.isNullOrBlank() } == true)

        return LibriaDetails(
            id = releaseId,
            titleRu = titleRu,
            titleEn = titleEn,
            extra = extra,
            description = description,
            announce = announce,
            image = image,
            favoriteCount = favoriteCount,
            hasFullHd = hasFullHd,
            isFavorite = isFavorite,
            hasEpisodes = hasEpisodes,
            hasViewed = hasViewed,
            hasWebPlayer = hasWebPlayer
        )
    }

    private fun buildAnnounce(r: AniLibertyRelease): String {
        val blocks = mutableListOf<String>()

        if (r.isBlockedByGeo == true) blocks += "Недоступно в вашем регионе"
        if (r.isBlockedByCopyrights == true) blocks += "Недоступно по запросу правообладателя"

        val notif = r.notification?.trim()?.takeIf { it.isNotEmpty() }
        if (notif != null) blocks += notif

        if (r.isOngoing == false) blocks += "Релиз завершен"
        if (r.isInProduction == true) blocks += "В производстве"

        return blocks.distinct().joinToString(" • ")
    }
}
