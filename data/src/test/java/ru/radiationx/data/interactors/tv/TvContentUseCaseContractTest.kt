package ru.radiationx.data.interactors.tv

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogRequest
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseAlias
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.system.ApiUtils

class TvContentUseCaseContractTest {
    private val aniLibertyApi = mockk<AniLibertyApi>()
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val apiUtils = mockk<ApiUtils>()

    private lateinit var useCase: TvContentUseCaseImpl

    @Before
    fun setUp() {
        every { apiUtils.escapeHtml(any()) } answers { firstArg<String?>() }
        useCase =
            TvContentUseCaseImpl(
                aniLibertyApi = aniLibertyApi,
                authRepository = authRepository,
                apiUtils = apiUtils,
            )
    }

    @Test
    fun loadMainFeed_usesLatestWithoutFields_andFallsBackToCatalogWhenLatestIsEmpty() =
        runTest {
            val catalogRequestSlot = slot<AniLibertyCatalogRequest>()
            coEvery { aniLibertyApi.getLatestReleases(limit = 14, fields = null) } returns emptyList()
            coEvery { aniLibertyApi.getCatalogReleases(capture(catalogRequestSlot)) } returns
                PaginatedResponse(
                    data = listOf(release(id = 10096, titleRu = "Hell Mode")),
                    meta =
                        PaginatedResponse.PaginationResponse(
                            page = 1,
                            allPages = 1,
                            perPage = 14,
                            allItems = 1,
                        ),
                )

            val result = useCase.loadMainFeed(requestPage = 1, pageLimit = 14)

            assertEquals(1, result.size)
            assertEquals(10096, result.first().id.id)
            assertNull(catalogRequestSlot.captured.fields)
            coVerify(exactly = 1) { aniLibertyApi.getLatestReleases(limit = 14, fields = null) }
            coVerify(exactly = 1) { aniLibertyApi.getCatalogReleases(any()) }
        }

    @Test
    fun loadRecommendations_usesOnlyLimitAndReleaseIdWithoutFields() =
        runTest {
            coEvery {
                aniLibertyApi.getRecommendedReleases(
                    limit = 14,
                    releaseId = AniLibertyReleaseId(10096),
                    fields = null,
                )
            } returns listOf(release(id = 10097, titleRu = "Solo Leveling"))

            val result = useCase.loadRecommendations(seedReleaseId = 10096, limit = 14)

            assertEquals(1, result.size)
            assertEquals(10097, result.first().id.id)
            coVerify(exactly = 1) {
                aniLibertyApi.getRecommendedReleases(
                    limit = 14,
                    releaseId = AniLibertyReleaseId(10096),
                    fields = null,
                )
            }
        }

    private fun release(
        id: Int,
        titleRu: String,
    ): AniLibertyRelease {
        return AniLibertyRelease(
            id = AniLibertyReleaseId(id),
            alias = AniLibertyReleaseAlias("release-$id"),
            name =
                AniLibertyRelease.Name(
                    main = titleRu,
                    english = null,
                    alternative = null,
                ),
            type = null,
            year = 2026,
            season = null,
            poster = null,
            freshAt = null,
            createdAt = null,
            updatedAt = null,
            isOngoing = true,
            ageRating = null,
            publishDay = null,
            description = null,
            notification = null,
            episodesTotal = null,
            externalPlayer = null,
            isInProduction = true,
            isBlockedByGeo = false,
            isBlockedByCopyrights = false,
            addedInUsersFavorites = 0,
            averageDurationOfEpisode = null,
            plannedCount = 0,
            watchedCount = 0,
            watchingCount = 0,
            postponedCount = 0,
            abandonedCount = 0,
            genres = emptyList(),
            members = emptyList(),
            episodes = emptyList(),
            torrents = emptyList(),
            sponsor = null,
            latestEpisode = null,
        )
    }
}
