package ru.radiationx.anilibria.common

import android.content.Context
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.feed.FeedItem
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.youtube.YoutubeItem
import ru.radiationx.shared.ktx.android.relativeDate
import ru.radiationx.shared.ktx.capitalizeDefault
import ru.radiationx.shared.ktx.decapitalizeDefault
import java.util.Date
import javax.inject.Inject

class CardsDataConverter @Inject constructor(
    private val context: Context,
) {

    fun toCard(releaseItem: Release) = releaseItem.run {
        val torrentDate = torrentUpdate.takeIf { it != 0 }?.let { Date(it * 1000L) }
        val seasonText = "${year.orEmpty()} ${season.orEmpty()}"
        val genreText = genres.firstOrNull()?.capitalizeDefault()
        val seriesText = "Серии: ${series?.trim() ?: "Не доступно"}"
        val updateText = torrentDate?.let {
            "Обновлен ${it.relativeDate(context).decapitalizeDefault()}"
        }
        val descItems = listOfNotNull(seasonText, genreText, seriesText, updateText)
        LibriaCard(
            title.orEmpty(),
            descItems.joinToString(" • "),
            poster.orEmpty(),
            LibriaCard.Type.Release(releaseItem.id)
        )
    }

    fun toCard(youtubeItem: YoutubeItem) = youtubeItem.run {
        LibriaCard(
            title.orEmpty(),
            "Вышел ${Date(timestamp * 1000L).relativeDate(context).decapitalizeDefault()}",
            image.orEmpty(),
            LibriaCard.Type.Youtube(youtubeItem.link)
        )
    }

    fun toCard(feedItem: FeedItem): LibriaCard = feedItem.run {
        when {
            release != null -> toCard(release!!)
            youtube != null -> toCard(youtube!!)
            else -> throw RuntimeException("WataFuq")
        }
    }

    /**
     * Конвертация релиза из AniLiberty API v1 в карточку для TV.
     *
     * Возвращает null, если в ответе нет id (без него невозможно корректно открыть детали).
     */
    fun toCardOrNull(releaseItem: AniLibertyRelease): LibriaCard? {
        val releaseId = releaseItem.id?.value ?: return null

        val titleText = releaseItem.name?.main
            ?: releaseItem.name?.english
            ?: releaseItem.name?.alternative
            ?: "id$releaseId"

        val imageRaw = releaseItem.poster?.optimized?.preview
            ?: releaseItem.poster?.optimized?.thumbnail
            ?: releaseItem.poster?.preview
            ?: releaseItem.poster?.thumbnail
            ?: ""

        // Важно: AniLiberty может отдавать относительные пути `/...`
        val imageText = imageRaw.toAbsoluteAniLibertyUrl().orEmpty()

        val yearSeasonText = listOfNotNull(
            releaseItem.year?.toString(),
            releaseItem.season?.description?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ").trim()

        val genreText = releaseItem.genres
            ?.firstOrNull()
            ?.name
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.capitalizeDefault()

        val seriesText = buildSeriesText(releaseItem)

        val descItems = listOfNotNull(
            yearSeasonText.takeIf { it.isNotBlank() },
            genreText,
            seriesText,
        )

        return LibriaCard(
            title = titleText,
            description = descItems.joinToString(" • "),
            image = imageText,
            type = LibriaCard.Type.Release(ReleaseId(releaseId)),
        )
    }

    private fun buildSeriesText(releaseItem: AniLibertyRelease): String? {
        val total = releaseItem.episodesTotal?.takeIf { it > 0 }
        val latest = releaseItem.latestEpisode?.ordinal?.takeIf { it > 0.0 }

        return when {
            total != null && latest != null -> "Серии: ${formatEpisodeOrdinal(latest)} / $total"
            total != null -> "Серии: $total"
            latest != null -> "Серия: ${formatEpisodeOrdinal(latest)}"
            else -> null
        }
    }

    private fun formatEpisodeOrdinal(value: Double): String {
        val str = value.toString()
        return if (str.endsWith(".0")) str.dropLast(2) else str
    }
}
