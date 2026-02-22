package ru.radiationx.anilibria.common

import android.content.Context
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
        val episodesFromType = types
            .firstOrNull()
            ?.let(::extractEpisodesCountFromTypeText)
        val seriesValue = series?.trim()?.takeIf { it.isNotEmpty() }
            ?: episodes.size.takeIf { it > 0 }?.toString()
            ?: episodesFromType
            ?: when (statusCode) {
                Release.STATUS_CODE_COMPLETE -> "Завершен"
                Release.STATUS_CODE_PROGRESS -> "Онгоинг"
                else -> "Неизвестно"
            }
        val seriesText = "Серии: $seriesValue"
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

    private fun extractEpisodesCountFromTypeText(typeText: String): String? {
        val regex = Regex("""\((\d+)\s*эп""", RegexOption.IGNORE_CASE)
        return regex.find(typeText)?.groupValues?.getOrNull(1)
    }
}
