package ru.radiationx.data.entity.mapper

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyEpisode
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyImage
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyImageWithOptimized
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPublishDay
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseAlias
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertySeason
import ru.radiationx.data.system.ApiUtils

class AniLibertyReleaseMapperTest {

    @Test
    fun toLegacyReleaseOrNull_mapsKeyFields() {
        val release = AniLibertyRelease(
            id = AniLibertyReleaseId(10),
            alias = AniLibertyReleaseAlias("naruto"),
            name = AniLibertyRelease.Name(
                main = "Наруто",
                english = "Naruto",
                alternative = null,
            ),
            type = null,
            year = 2024,
            season = AniLibertyRelease.Season(
                value = AniLibertySeason.Spring,
                description = "Весна",
            ),
            poster = AniLibertyImageWithOptimized(
                preview = "/preview.jpg",
                thumbnail = null,
                optimized = AniLibertyImage(
                    preview = "/optimized.jpg",
                    thumbnail = null,
                ),
            ),
            freshAt = null,
            createdAt = null,
            updatedAt = null,
            isOngoing = true,
            ageRating = null,
            publishDay = AniLibertyRelease.PublishDay(
                value = AniLibertyPublishDay.Monday,
                description = "Понедельник",
            ),
            description = "desc",
            notification = null,
            episodesTotal = 12,
            externalPlayer = null,
            isInProduction = null,
            isBlockedByGeo = false,
            isBlockedByCopyrights = false,
            addedInUsersFavorites = 5,
            averageDurationOfEpisode = null,
            plannedCount = null,
            watchedCount = null,
            watchingCount = null,
            postponedCount = null,
            abandonedCount = null,
            genres = emptyList(),
            members = emptyList(),
            episodes = emptyList(),
            torrents = emptyList(),
            sponsor = null,
            latestEpisode = AniLibertyEpisode(
                id = null,
                name = null,
                ordinal = 3.0,
                ending = null,
                opening = null,
                preview = null,
                hls480 = null,
                hls720 = null,
                hls1080 = null,
                duration = null,
                rutubeId = null,
                youtubeId = null,
                updatedAt = null,
                sortOrder = null,
                releaseId = null,
                nameEnglish = null,
            ),
        )

        val apiUtils = mockk<ApiUtils>()
        every { apiUtils.escapeHtml(any()) } answers { firstArg<String?>() }
        val mapped = release.toLegacyReleaseOrNull(apiUtils = apiUtils, isFavorite = false)

        assertNotNull(mapped)
        assertEquals(10, mapped?.id?.id)
        assertEquals("naruto", mapped?.code?.code)
        assertEquals("https://aniliberty.top/optimized.jpg", mapped?.poster)
        assertEquals("3 из 12", mapped?.series)
        assertEquals(listOf("1"), mapped?.days)
        assertEquals(false, mapped?.favoriteInfo?.isAdded)
    }
}
