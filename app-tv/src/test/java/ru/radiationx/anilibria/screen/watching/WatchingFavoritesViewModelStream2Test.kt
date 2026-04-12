package ru.radiationx.anilibria.screen.watching

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.data.repository.AuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class WatchingFavoritesViewModelStream2Test {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun idle_doesNotTriggerAdditionalRequest() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val requests = mutableListOf<Int>()
            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                val page = firstArg<Int>()
                requests += page
                emptyResponse(page)
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(),
                    authRepository = authRepository,
                    converter = mockk<CardsDataConverter>(relaxed = true),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil { requests.size == 1 }
            delay(100)

            assertEquals("Idle state should not trigger a network request", 1, requests.size)
            viewModel.dispose()
        }

    @Test
    fun loadMore_triggersOneRequestPerExplicitEvent() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val requests = mutableListOf<Int>()
            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                val page = firstArg<Int>()
                requests += page
                emptyResponse(page)
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(),
                    authRepository = authRepository,
                    converter = mockk<CardsDataConverter>(relaxed = true),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil { requests.size == 1 } // initial auth-triggered load

            viewModel.onLinkCardClick()
            waitUntil { requests.size == 2 }

            viewModel.onLinkCardClick()
            waitUntil { requests.size == 3 }

            assertEquals("Expected one request for each explicit loadMore click", 3, requests.size)
            viewModel.dispose()
        }

    @Test
    fun initialSync_doesNotRequestMoreThanFiftyPages() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val requests = mutableListOf<Int>()
            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                val page = firstArg<Int>()
                requests += page
                singleItemResponse(page)
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(),
                    authRepository = authRepository,
                    converter = mockk<CardsDataConverter>(relaxed = true),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil { requests.size >= 50 }
            delay(100)

            assertEquals("Expected sync to stop at 50 pages max", 50, requests.size)
            viewModel.dispose()
        }

    @Test
    fun emptyFavorites_showsExplicitEmptyState() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                emptyResponse(firstArg<Int>())
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(),
                    authRepository = authRepository,
                    converter = favoriteConverter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil {
                (viewModel.cardsData.value.firstOrNull() as? InfoCard)?.title == "Избранное пока пусто"
            }

            val first = viewModel.cardsData.value.single() as InfoCard
            assertEquals("Избранное пока пусто", first.title)
            assertEquals(
                "Добавьте тайтлы в избранное, чтобы они появились здесь",
                first.subtitle,
            )
            viewModel.dispose()
        }

    @Test
    fun explicitReloadCancellation_keepsCurrentCardsWithoutRetryUi() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val release = fakeRelease(7)
            var requestCount = 0
            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                requestCount += 1
                if (requestCount == 1) {
                    Paginated(
                        data = listOf(release),
                        page = 1,
                        allPages = 1,
                        perPage = 25,
                        allItems = 1,
                    )
                } else {
                    throw CancellationException("favorites reload cancelled")
                }
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(),
                    authRepository = authRepository,
                    converter = favoriteConverter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil { viewModel.cardsData.value.any { it is LibriaCard } }

            viewModel.onLinkCardClick()
            waitUntil { requestCount == 2 }
            delay(100)

            assertTrue(viewModel.cardsData.value.any { it is LibriaCard })
            assertTrue(viewModel.cardsData.value.none { it is LinkCard && it.title == "Повторить" })
            assertTrue(viewModel.cardsData.value.none { it is LoadingCard && it.isError })
            viewModel.dispose()
        }

    @Test
    fun initialCancellation_doesNotShowFalseError() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } throws
                CancellationException("favorites load cancelled")

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(),
                    authRepository = authRepository,
                    converter = mockk<CardsDataConverter>(relaxed = true),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            delay(100)

            assertTrue(viewModel.cardsData.value.none { it is LinkCard && it.title == "Повторить" })
            assertTrue(viewModel.cardsData.value.none { it is LoadingCard && it.isError })
            viewModel.dispose()
        }

    @Test
    fun onlyCompletedFilter_keepsOnlyCompletedFavorites() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                favoritesResponse(
                    page = firstArg(),
                    data =
                        listOf(
                            fakeRelease(
                                id = 1,
                                title = "completed-explicit",
                                statusCode = Release.STATUS_CODE_COMPLETE,
                            ),
                            fakeRelease(
                                id = 2,
                                title = "announced",
                                statusCode = Release.STATUS_CODE_NOT_ONGOING,
                                status = "Анонс",
                            ),
                            fakeRelease(
                                id = 3,
                                title = "completed-fallback",
                                statusCode = null,
                                status = "Релиз завершен",
                            ),
                        ),
                )
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(),
                    authRepository = authRepository,
                    converter = favoriteConverter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 3 }

            viewModel.onOnlyCompletedClick()
            viewModel.selectSinglePicker(1)

            waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 2 }

            val titles =
                viewModel.cardsData.value
                    .filterIsInstance<LibriaCard>()
                    .map(LibriaCard::title)
                    .toSet()
            assertEquals(
                setOf("completed-explicit", "completed-fallback"),
                titles,
            )
            assertEquals(
                "Только завершенные",
                viewModel.filtersUiState.value.onlyCompleted.label,
            )
            assertTrue(viewModel.filtersUiState.value.onlyCompleted.emphasized)
            viewModel.dispose()
        }

    @Test
    fun yearFilter_supportsMultiSelectAndMatchesCatalogPattern() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                favoritesResponse(
                    page = firstArg(),
                    data =
                        listOf(
                            fakeRelease(id = 1, title = "year-2026", year = "2026"),
                            fakeRelease(id = 2, title = "year-2025", year = "2025"),
                            fakeRelease(id = 3, title = "year-2024", year = "2024"),
                        ),
                )
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(years = listOf("2026", "2025", "2024")),
                    authRepository = authRepository,
                    converter = favoriteConverter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 3 }

            viewModel.onYearClick()
            assertTrue(viewModel.filterPicker.value?.multiSelect == true)
            viewModel.togglePickerSelection(0)
            viewModel.togglePickerSelection(2)
            viewModel.applyFilterPicker()

            waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 2 }

            val titles =
                viewModel.cardsData.value
                    .filterIsInstance<LibriaCard>()
                    .map(LibriaCard::title)
                    .toSet()
            assertEquals(setOf("year-2026", "year-2024"), titles)
            assertEquals("2026, 2024", viewModel.filtersUiState.value.year.label)
            assertTrue(viewModel.filtersUiState.value.year.emphasized)
            viewModel.dispose()
        }

    @Test
    fun defaultDateSort_prioritizesReleaseYearOverTorrentFreshness() =
        runBlocking {
            val authStateFlow = MutableStateFlow(AuthState.AUTH)
            val authRepository = mockk<AuthRepository>()
            every { authRepository.observeAuthState() } returns authStateFlow

            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
                favoritesResponse(
                    page = firstArg(),
                    data =
                        listOf(
                            fakeRelease(
                                id = 1,
                                title = "older-but-fresh",
                                year = "2025",
                                season = "Осень",
                                favoriteRating = 1,
                                torrentUpdate = 10_000,
                            ),
                            fakeRelease(
                                id = 2,
                                title = "newer-but-stale",
                                year = "2026",
                                season = "Зима",
                                favoriteRating = 2,
                                torrentUpdate = 10,
                            ),
                        ),
                )
            }

            val viewModel =
                WatchingFavoritesViewModel(
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    tvSearchUseCase = catalogFilterUseCase(seasons = listOf("Зима", "Осень")),
                    authRepository = authRepository,
                    converter = favoriteConverter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                )

            waitUntil {
                viewModel.cardsData.value.filterIsInstance<LibriaCard>().map(LibriaCard::title) ==
                    listOf("newer-but-stale", "older-but-fresh")
            }

            val titles =
                viewModel.cardsData.value
                    .filterIsInstance<LibriaCard>()
                    .map(LibriaCard::title)
            assertEquals(listOf("newer-but-stale", "older-but-fresh"), titles)
            assertEquals("По новизне", viewModel.filtersUiState.value.sort.label)
            assertTrue(!viewModel.filtersUiState.value.sort.emphasized)
            viewModel.dispose()
        }

    private fun catalogFilterUseCase(
        years: List<String> = emptyList(),
        seasons: List<String> = emptyList(),
        genres: List<String> = emptyList(),
    ): TvSearchUseCase {
        val useCase = mockk<TvSearchUseCase>()
        coEvery { useCase.loadYears() } returns years.map { YearItem(title = it, value = it) }
        coEvery { useCase.loadSeasons() } returns
            seasons.map {
                SeasonItem(title = it, value = it.lowercase())
            }
        coEvery { useCase.loadGenres() } returns
            genres.map {
                GenreItem(title = it, value = it.lowercase())
            }
        return useCase
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(25)
        }
        error("Condition was not met in time")
    }

    private suspend fun WatchingFavoritesViewModel.dispose() {
        onPause(mockk(relaxed = true))
        delay(20)
    }

    private fun emptyResponse(page: Int): Paginated<Release> =
        Paginated(
            data = emptyList(),
            page = page,
            allPages = 1,
            perPage = 25,
            allItems = 0,
        )

    private fun favoritesResponse(
        page: Int,
        data: List<Release>,
    ): Paginated<Release> =
        Paginated(
            data = data,
            page = page,
            allPages = 1,
            perPage = 25,
            allItems = data.size,
        )

    private fun singleItemResponse(page: Int): Paginated<Release> =
        Paginated(
            data = listOf(fakeRelease(page)),
            page = page,
            allPages = null,
            perPage = 25,
            allItems = null,
        )

    private fun fakeRelease(
        id: Int,
        title: String = "title-$id",
        year: String = "2026",
        season: String = "spring",
        genres: List<String> = emptyList(),
        statusCode: String? = Release.STATUS_CODE_COMPLETE,
        status: String? = null,
        favoriteRating: Int = id,
        torrentUpdate: Int = id,
    ): Release {
        val favoriteInfo = mockk<FavoriteInfo>()
        every { favoriteInfo.rating } returns favoriteRating
        every { favoriteInfo.isAdded } returns true

        val release = mockk<Release>(relaxed = true)
        every { release.id } returns ReleaseId(id)
        every { release.title } returns title
        every { release.year } returns year
        every { release.season } returns season
        every { release.genres } returns genres
        every { release.favoriteInfo } returns favoriteInfo
        every { release.statusCode } returns statusCode
        every { release.status } returns status
        every { release.torrentUpdate } returns torrentUpdate
        return release
    }

    private fun favoriteConverter(): CardsDataConverter {
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(any<Release>()) } answers {
            val release = firstArg<Release>()
            LibriaCard(
                title = release.title.orEmpty(),
                description = "",
                image = "",
                type = LibriaCard.Type.Release(release.id),
            )
        }
        return converter
    }
}
