package ru.radiationx.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFavoriteSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseAlias
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.api.FavoriteApi
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.interactors.ReleaseUpdateMiddleware
import ru.radiationx.data.system.ApiUtils

class FavoriteRepositoryContractTest {

    private val aniLibertyApi = mockk<AniLibertyApi>()
    private val favoriteApi = mockk<FavoriteApi>()
    private val updateMiddleware = mockk<ReleaseUpdateMiddleware>(relaxed = true)
    private val apiUtils = mockk<ApiUtils>()
    private val apiConfig = mockk<ApiConfig>(relaxed = true)

    private lateinit var repository: FavoriteRepository

    @Before
    fun setUp() {
        every { apiUtils.escapeHtml(any()) } answers { firstArg<String?>() }
        repository = FavoriteRepository(
            aniLibertyApi = aniLibertyApi,
            favoriteApi = favoriteApi,
            updateMiddleware = updateMiddleware,
            apiUtils = apiUtils,
            apiConfig = apiConfig,
        )
    }

    @Test
    fun getFavorites_requestsAniLibertyWithSortingAndSlimFields() = runBlocking {
        coEvery {
            aniLibertyApi.getUserFavoriteReleasesFiltered(
                page = 1,
                limit = 25,
                years = null,
                types = null,
                genres = null,
                search = null,
                sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                ageRatings = null,
                fields = AniLibertyReleaseFields.FavoritesList,
            )
        } returns PaginatedResponse(
            data = listOf(release(id = 10096, titleRu = "Hell Mode")),
            meta = PaginatedResponse.PaginationResponse(
                page = 1,
                allPages = 1,
                perPage = 25,
                allItems = 1,
            ),
        )

        val result = repository.getFavorites(page = 1)

        assertEquals(1, result.data.size)
        coVerify(exactly = 1) {
            aniLibertyApi.getUserFavoriteReleasesFiltered(
                page = 1,
                limit = 25,
                years = null,
                types = null,
                genres = null,
                search = null,
                sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                ageRatings = null,
                fields = AniLibertyReleaseFields.FavoritesList,
            )
        }
    }

    @Test
    fun addFavorite_legacyFallbackRequestsReleaseWithoutExcludeFields() = runBlocking {
        coEvery { aniLibertyApi.addToFavorites(any()) } returns listOf(AniLibertyReleaseId(10096))
        coEvery { favoriteApi.addFavorite(10096) } throws RuntimeException("Legacy API failed")
        coEvery {
            aniLibertyApi.getRelease(
                key = any(),
                fields = null,
            )
        } returns release(id = 10096, titleRu = "Hell Mode")

        val result = repository.addFavorite(ReleaseId(10096))

        assertEquals(10096, result.id.id)
        assertTrue(result.favoriteInfo.isAdded)
        coVerify(exactly = 1) {
            aniLibertyApi.getRelease(
                key = any(),
                fields = null,
            )
        }
    }

    @Test
    fun getFavorites_whenCancelled_doesNotFallbackToLegacy() = runBlocking {
        coEvery {
            aniLibertyApi.getUserFavoriteReleasesFiltered(
                page = 1,
                limit = 25,
                years = null,
                types = null,
                genres = null,
                search = null,
                sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                ageRatings = null,
                fields = AniLibertyReleaseFields.FavoritesList,
            )
        } throws CancellationException("cancelled")

        val error = runCatching { repository.getFavorites(page = 1) }.exceptionOrNull()

        assertTrue(error is CancellationException)
        coVerify(exactly = 0) { favoriteApi.getFavorites(any()) }
    }

    private fun release(id: Int, titleRu: String): AniLibertyRelease {
        return AniLibertyRelease(
            id = AniLibertyReleaseId(id),
            alias = AniLibertyReleaseAlias("release-$id"),
            name = AniLibertyRelease.Name(
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
