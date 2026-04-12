package ru.radiationx.anilibria.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem

class AniLibertyViewHistoryCardMapperTest {
    @Test
    fun toContinueCardOrNull_mapsDomainItem() {
        val item =
            UserViewHistoryItem(
                releaseId = ReleaseId(42),
                titleMain = "Naruto",
                titleEnglish = "Naruto",
                titleAlternative = null,
                posterPreview = "/img/poster.jpg",
                posterThumbnail = null,
                episodeOrdinal = 7.5,
                timeSeconds = 153.0,
                isWatched = false,
            )

        val card = AniLibertyViewHistoryCardMapper.toContinueCardOrNull(item)

        assertNotNull(card)
        assertEquals("Naruto", card?.title)
        assertEquals("https://aniliberty.top/img/poster.jpg", card?.image)
        assertEquals("Вы остановились на серии 7.5 • 2:33", card?.description)
    }

    @Test
    fun toHistoryCardOrNull_marksWatchedEntry() {
        val item =
            UserViewHistoryItem(
                releaseId = ReleaseId(99),
                titleMain = null,
                titleEnglish = "Bleach",
                titleAlternative = null,
                posterPreview = null,
                posterThumbnail = "covers/bleach.jpg",
                episodeOrdinal = 12.0,
                timeSeconds = 10.0,
                isWatched = true,
            )

        val card = AniLibertyViewHistoryCardMapper.toHistoryCardOrNull(item)

        assertNotNull(card)
        assertEquals("Bleach", card?.title)
        assertEquals("https://aniliberty.top/covers/bleach.jpg", card?.image)
        assertEquals("Просмотрено • серия 12", card?.description)
    }
}
