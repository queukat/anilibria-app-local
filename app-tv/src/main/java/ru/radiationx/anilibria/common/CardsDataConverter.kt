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

class CardsDataConverter
    @Inject
    constructor(
        private val context: Context,
    ) {
        fun toReleaseCard(releaseItem: Release) =
            releaseItem.run {
                val torrentDate = torrentUpdate.takeIf { it != 0 }?.let { Date(it * 1000L) }
                val seasonText = "${year.orEmpty()} ${season.orEmpty()}"
                val genreText = genres.firstOrNull()?.capitalizeDefault()
                val seriesText = "Серии: ${resolveTvSeriesText()}"
                val updateText =
                    torrentDate?.let {
                        "Обновлен ${it.relativeDate(context).decapitalizeDefault()}"
                    }
                val descItems = listOfNotNull(seasonText, genreText, seriesText, updateText)
                LibriaCard(
                    title.orEmpty(),
                    descItems.joinToString(" • "),
                    poster.orEmpty(),
                    LibriaCard.Type.Release(releaseItem.id),
                    relativeTimestampSec = torrentUpdate.toLong().takeIf { it != 0L },
                    relativePrefix = "Обновлен",
                )
            }

        fun toYoutubeCard(youtubeItem: YoutubeItem) =
            youtubeItem.run {
                LibriaCard(
                    title.orEmpty(),
                    "Вышел ${Date(timestamp * 1000L).relativeDate(context).decapitalizeDefault()}",
                    image.orEmpty(),
                    LibriaCard.Type.Youtube(youtubeItem.link),
                    relativeTimestampSec = timestamp.toLong(),
                    relativePrefix = "Вышел",
                )
            }

        fun toFeedCard(feedItem: FeedItem): LibriaCard =
            feedItem.run {
                when {
                    release != null -> toReleaseCard(release!!)
                    youtube != null -> toYoutubeCard(youtube!!)
                    else -> error("Feed item does not contain release or youtube payload")
                }
            }

        fun toCard(releaseItem: Release): LibriaCard = toReleaseCard(releaseItem)

        fun toCard(youtubeItem: YoutubeItem): LibriaCard = toYoutubeCard(youtubeItem)

        fun toCard(feedItem: FeedItem): LibriaCard = toFeedCard(feedItem)
    }
