package ru.radiationx.anilibria.common

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.entity.domain.release.Release

class TvCollectionFiltersTest {

    @Test
    fun completedStatusCode_isRecognizedAsCompleted() {
        val release = mockk<Release>()
        every { release.statusCode } returns Release.STATUS_CODE_COMPLETE
        every { release.status } returns null

        assertTrue(release.isCompletedForTvCollectionFilters())
    }

    @Test
    fun notOngoingStatusCode_isNotRecognizedAsCompleted() {
        val release = mockk<Release>()
        every { release.statusCode } returns Release.STATUS_CODE_NOT_ONGOING
        every { release.status } returns "Анонс"

        assertFalse(release.isCompletedForTvCollectionFilters())
    }

    @Test
    fun explicitCompletedStatusText_isUsedAsFallback() {
        val release = mockk<Release>()
        every { release.statusCode } returns null
        every { release.status } returns "Релиз завершен"

        assertTrue(release.isCompletedForTvCollectionFilters())
    }

    @Test
    fun unknownStatusWithoutCompletedText_isNotRecognizedAsCompleted() {
        val release = mockk<Release>()
        every { release.statusCode } returns null
        every { release.status } returns "Онгоинг"

        assertFalse(release.isCompletedForTvCollectionFilters())
    }

    @Test
    fun recencyComparator_prioritizesYearOverTorrentFreshness() {
        val olderButFresh = mockRelease(
            title = "older-but-fresh",
            year = "2025",
            season = "Осень",
            torrentUpdate = 10_000,
        )
        val newerButStale = mockRelease(
            title = "newer-but-stale",
            year = "2026",
            season = "Зима",
            torrentUpdate = 10,
        )

        val sorted = listOf(olderButFresh, newerButStale).sortedWith(tvCollectionRecencyComparator())

        assertEquals(listOf("newer-but-stale", "older-but-fresh"), sorted.map { it.title })
    }

    @Test
    fun recencyComparator_usesSeasonWithinSameYear() {
        val winter = mockRelease(
            id = 1,
            title = "winter",
            year = "2026",
            season = "Зима",
            torrentUpdate = 100,
        )
        val autumn = mockRelease(
            id = 2,
            title = "autumn",
            year = "2026",
            season = "Осень",
            torrentUpdate = 1,
        )

        val sorted = listOf(winter, autumn).sortedWith(tvCollectionRecencyComparator())

        assertEquals(listOf("autumn", "winter"), sorted.map { it.title })
    }

    @Test
    fun recencyComparator_ignoresTorrentFreshnessWithinSameSeason() {
        val olderIdButFresh = mockRelease(
            id = 10,
            title = "older-id-but-fresh",
            year = "2026",
            season = "Весна",
            torrentUpdate = 10_000,
        )
        val newerIdButStale = mockRelease(
            id = 11,
            title = "newer-id-but-stale",
            year = "2026",
            season = "Весна",
            torrentUpdate = 10,
        )

        val sorted = listOf(olderIdButFresh, newerIdButStale).sortedWith(tvCollectionRecencyComparator())

        assertEquals(listOf("newer-id-but-stale", "older-id-but-fresh"), sorted.map { it.title })
    }

    private fun mockRelease(
        id: Int = 1,
        title: String,
        year: String?,
        season: String?,
        torrentUpdate: Int,
    ): Release {
        val release = mockk<Release>()
        every { release.id.id } returns id
        every { release.title } returns title
        every { release.year } returns year
        every { release.season } returns season
        every { release.torrentUpdate } returns torrentUpdate
        every { release.statusCode } returns null
        every { release.status } returns null
        return release
    }
}
