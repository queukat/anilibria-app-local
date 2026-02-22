package ru.radiationx.data.entity.mapper

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    private val apiUtils = mockk<ApiUtils>().apply {
        every { escapeHtml(any()) } answers { firstArg<String?>() }
    }

    @Test
    fun toLegacyReleaseOrNull_mapsKeyFields() {
        val release = buildRelease(
            isOngoing = true,
            episodesTotal = 12,
            latestEpisodeOrdinal = 3.0,
        )

        val mapped = release.toLegacyReleaseOrNull(apiUtils = apiUtils, isFavorite = false)

        assertNotNull(mapped)
        assertEquals(10, mapped?.id?.id)
        assertEquals("naruto", mapped?.code?.code)
        assertEquals("https://aniliberty.top/optimized.jpg", mapped?.poster)
        assertEquals("3 из 12", mapped?.series)
        assertEquals(listOf("1"), mapped?.days)
        assertEquals(false, mapped?.favoriteInfo?.isAdded)
    }

    @Test
    fun toLegacyReleaseOrNull_series_usesTotalForFinishedRelease() {
        val release = buildRelease(
            isOngoing = false,
            episodesTotal = 12,
            latestEpisodeOrdinal = 7.0,
        )

        val mapped = release.toLegacyReleaseOrNull(apiUtils = apiUtils, isFavorite = false)

        assertEquals("12", mapped?.series)
    }

    @Test
    fun toLegacyReleaseOrNull_series_usesLatestWhenTotalUnknown() {
        val release = buildRelease(
            isOngoing = true,
            episodesTotal = null,
            latestEpisodeOrdinal = 5.0,
        )

        val mapped = release.toLegacyReleaseOrNull(apiUtils = apiUtils, isFavorite = false)

        assertEquals("5", mapped?.series)
    }

    @Test
    fun toLegacyReleaseOrNull_series_usesEpisodesAsLatestFallbackForOngoing() {
        val release = buildRelease(
            isOngoing = true,
            episodesTotal = 12,
            latestEpisodeOrdinal = null,
            episodeOrdinals = listOf(1.0, 2.0, 4.0),
        )

        val mapped = release.toLegacyReleaseOrNull(apiUtils = apiUtils, isFavorite = false)

        assertEquals("4 из 12", mapped?.series)
    }

    @Test
    fun toLegacyReleaseOrNull_series_staysNullWhenNoCountsAvailable() {
        val release = buildRelease(
            isOngoing = true,
            episodesTotal = null,
            latestEpisodeOrdinal = null,
            episodeOrdinals = emptyList(),
        )

        val mapped = release.toLegacyReleaseOrNull(apiUtils = apiUtils, isFavorite = false)

        assertNull(mapped?.series)
    }

    private fun buildRelease(
        isOngoing: Boolean?,
        episodesTotal: Int?,
        latestEpisodeOrdinal: Double?,
        episodeOrdinals: List<Double> = emptyList(),
    ): AniLibertyRelease {
        return AniLibertyRelease(
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
            isOngoing = isOngoing,
            ageRating = null,
            publishDay = AniLibertyRelease.PublishDay(
                value = AniLibertyPublishDay.Monday,
                description = "Понедельник",
            ),
            description = "desc",
            notification = null,
            episodesTotal = episodesTotal,
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
            episodes = episodeOrdinals.map { ordinal ->
                AniLibertyEpisode(
                    id = null,
                    name = null,
                    ordinal = ordinal,
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
                )
            },
            torrents = emptyList(),
            sponsor = null,
            latestEpisode = latestEpisodeOrdinal?.let { ordinal ->
                AniLibertyEpisode(
                    id = null,
                    name = null,
                    ordinal = ordinal,
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
                )
            },
        )
    }
}
