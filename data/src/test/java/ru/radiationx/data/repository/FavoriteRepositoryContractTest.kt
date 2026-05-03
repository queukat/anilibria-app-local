package ru.radiationx.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyEpisode
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFavoriteSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPublishDay
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseAlias
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.api.FavoriteApi
import ru.radiationx.data.entity.domain.release.Release
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
    private val authTokenHolder = mockk<AuthTokenHolder>()
    private val cookieHolder = mockk<CookieHolder>()

    private lateinit var repository: FavoriteRepository

    @Before
    fun setUp() {
        every { apiUtils.escapeHtml(any()) } answers { firstArg<String?>() }
        coEvery { authTokenHolder.getToken() } returns "token"
        coEvery { cookieHolder.getCookies() } returns emptyMap()
        repository =
            FavoriteRepository(
                aniLibertyApi = aniLibertyApi,
                favoriteApi = favoriteApi,
                updateMiddleware = updateMiddleware,
                apiUtils = apiUtils,
                apiConfig = apiConfig,
                authTokenHolder = authTokenHolder,
                cookieHolder = cookieHolder,
            )
    }

    @Test
    fun getFavorites_requestsAniLibertyWithSortingAndSlimFields() =
        runTest {
            coEvery {
                aniLibertyApi.getUserFavoriteReleasesFiltered(
                    page = 1,
                    limit = 25,
                    years = null,
                    types = null,
                    genres = null,
                    search = null,
                    sorting = AniLibertyFavoriteSorting.YearDesc,
                    ageRatings = null,
                    fields = AniLibertyReleaseFields.FavoritesList,
                )
            } returns
                PaginatedResponse(
                    data = listOf(release(id = 10096, titleRu = "Hell Mode")),
                    meta =
                        PaginatedResponse.PaginationResponse(
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
                    sorting = AniLibertyFavoriteSorting.YearDesc,
                    ageRatings = null,
                    fields = AniLibertyReleaseFields.FavoritesList,
                )
            }
        }

    @Test
    fun addFavorite_legacyFallbackRequestsReleaseWithoutExcludeFields() =
        runTest {
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
    fun getFavorites_whenCancelled_doesNotFallbackToLegacy() =
        runTest {
            coEvery {
                aniLibertyApi.getUserFavoriteReleasesFiltered(
                    page = 1,
                    limit = 25,
                    years = null,
                    types = null,
                    genres = null,
                    search = null,
                    sorting = AniLibertyFavoriteSorting.YearDesc,
                    ageRatings = null,
                    fields = AniLibertyReleaseFields.FavoritesList,
                )
            } throws CancellationException("cancelled")

            val error = runCatching { repository.getFavorites(page = 1) }.exceptionOrNull()

            assertTrue(error is CancellationException)
            coVerify(exactly = 0) { favoriteApi.getFavorites(any()) }
        }

    @Test
    fun getFavorites_resolvesAmbiguousStoppedReleaseStatusFromFullRelease() =
        runTest {
            coEvery {
                aniLibertyApi.getUserFavoriteReleasesFiltered(
                    page = 1,
                    limit = 25,
                    years = null,
                    types = null,
                    genres = null,
                    search = null,
                    sorting = AniLibertyFavoriteSorting.YearDesc,
                    ageRatings = null,
                    fields = AniLibertyReleaseFields.FavoritesList,
                )
            } returns
                PaginatedResponse(
                    data =
                        listOf(
                            release(
                                id = 10096,
                                titleRu = "Hell Mode",
                                isOngoing = false,
                                isInProduction = false,
                                episodesTotal = null,
                                publishDayValue = AniLibertyPublishDay.Monday,
                                episodes = emptyList(),
                            ),
                        ),
                    meta =
                        PaginatedResponse.PaginationResponse(
                            page = 1,
                            allPages = 1,
                            perPage = 25,
                            allItems = 1,
                        ),
                )
            coEvery {
                aniLibertyApi.getReleasesList(
                    ids = listOf(AniLibertyReleaseId(10096)),
                    aliases = null,
                    page = 1,
                    limit = 1,
                    fields = null,
                )
            } returns
                PaginatedResponse(
                    data =
                        listOf(
                            release(
                                id = 10096,
                                titleRu = "Hell Mode",
                                isOngoing = false,
                                isInProduction = false,
                                episodesTotal = null,
                                publishDayValue = AniLibertyPublishDay.Monday,
                                episodes =
                                    listOf(
                                        AniLibertyEpisode(
                                            id = AniLibertyReleaseEpisodeId("episode-1"),
                                            name = "Episode 1",
                                            ordinal = 1.0,
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
                                            sortOrder = 1.0,
                                            releaseId = AniLibertyReleaseId(10096),
                                            nameEnglish = null,
                                        ),
                                    ),
                            ),
                        ),
                    meta =
                        PaginatedResponse.PaginationResponse(
                            page = 1,
                            allPages = 1,
                            perPage = 1,
                            allItems = 1,
                        ),
                )

            val result = repository.getFavorites(page = 1)

            assertEquals(1, result.data.size)
            assertEquals(Release.STATUS_CODE_COMPLETE, result.data.first().statusCode)
            coVerify(exactly = 1) {
                aniLibertyApi.getReleasesList(
                    ids = listOf(AniLibertyReleaseId(10096)),
                    aliases = null,
                    page = 1,
                    limit = 1,
                    fields = null,
                )
            }
        }

    private fun release(
        id: Int,
        titleRu: String,
        isOngoing: Boolean? = true,
        isInProduction: Boolean? = true,
        episodesTotal: Int? = null,
        publishDayValue: AniLibertyPublishDay? = null,
        episodes: List<AniLibertyEpisode> = emptyList(),
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
            isOngoing = isOngoing,
            ageRating = null,
            publishDay =
                publishDayValue?.let {
                    AniLibertyRelease.PublishDay(
                        value = it,
                        description = it.value.toString(),
                    )
                },
            description = null,
            notification = null,
            episodesTotal = episodesTotal,
            externalPlayer = null,
            isInProduction = isInProduction,
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
            episodes = episodes,
            torrents = emptyList(),
            sponsor = null,
            latestEpisode = null,
        )
    }
}
