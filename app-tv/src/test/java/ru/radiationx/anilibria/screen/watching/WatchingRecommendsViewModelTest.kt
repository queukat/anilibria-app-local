package ru.radiationx.anilibria.screen.watching

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import ru.radiationx.data.entity.domain.HistoryReleases
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.repository.HistoryRepository

@OptIn(ExperimentalCoroutinesApi::class)
class WatchingRecommendsViewModelTest {
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
    fun refresh_usesHistorySeedBeforeFavorites() =
        runTest {
            val releases = listOf(fakeRelease(101), fakeRelease(102))
            val tvContentUseCase = mockk<TvContentUseCase>()
            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            val historyRepository = mockk<HistoryRepository>()

            coEvery { historyRepository.getReleases(1) } returns
                HistoryReleases(
                    items = listOf(fakeRelease(777)),
                    total = 1,
                )
            coEvery { tvContentUseCase.loadRecommendations(777, 14) } returns releases

            val viewModel =
                WatchingRecommendsViewModel(
                    tvContentUseCase = tvContentUseCase,
                    converter = converter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    historyRepository = historyRepository,
                )
            viewModel.setLoaderDispatcherForTests(testDispatcher)

            viewModel.onRefreshClick()
            waitUntil { viewModel.cardsData.value.any { it is LibriaCard } }

            val cards = viewModel.cardsData.value.filterIsInstance<LibriaCard>()
            assertEquals(listOf("title-101", "title-102"), cards.map { it.title })
            assertTrue(viewModel.cardsData.value.none { it is ru.radiationx.anilibria.common.LoadingCard && it.isError })
            coVerify(exactly = 1) { historyRepository.getReleases(1) }
            coVerify(exactly = 1) { tvContentUseCase.loadRecommendations(777, 14) }
            coVerify(exactly = 0) { tvFavoritesUseCase.loadFavorites(any()) }
        }

    @Test
    fun refresh_fallsBackToGlobalRecommendations_whenNoSeedAvailable() =
        runTest {
            val releases = listOf(fakeRelease(201), fakeRelease(202))
            val tvContentUseCase = mockk<TvContentUseCase>()
            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            val historyRepository = mockk<HistoryRepository>()

            coEvery { historyRepository.getReleases(1) } returns HistoryReleases(emptyList(), 0)
            coEvery { tvFavoritesUseCase.loadFavorites(1) } throws IllegalStateException("guest mode")
            coEvery { tvContentUseCase.loadRecommendations(null, 14) } returns releases

            val viewModel =
                WatchingRecommendsViewModel(
                    tvContentUseCase = tvContentUseCase,
                    converter = converter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    historyRepository = historyRepository,
                )
            viewModel.setLoaderDispatcherForTests(testDispatcher)

            viewModel.onRefreshClick()
            waitUntil { viewModel.cardsData.value.any { it is LibriaCard } }

            val cards = viewModel.cardsData.value.filterIsInstance<LibriaCard>()
            assertEquals(listOf("title-201", "title-202"), cards.map { it.title })
            coVerify(exactly = 1) { historyRepository.getReleases(1) }
            coVerify(exactly = 1) { tvFavoritesUseCase.loadFavorites(1) }
            coVerify(exactly = 1) { tvContentUseCase.loadRecommendations(null, 14) }
        }

    @Test
    fun refresh_showsEmptyState_whenRecommendationsUnavailable() =
        runTest {
            val tvContentUseCase = mockk<TvContentUseCase>()
            val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
            val historyRepository = mockk<HistoryRepository>()

            coEvery { historyRepository.getReleases(1) } returns HistoryReleases(emptyList(), 0)
            coEvery { tvFavoritesUseCase.loadFavorites(1) } returns
                ru.radiationx.data.entity.domain.Paginated(
                    data = emptyList(),
                    page = 1,
                    allPages = 1,
                    perPage = 0,
                    allItems = 0,
                )
            coEvery { tvContentUseCase.loadRecommendations(null, 14) } returns emptyList()

            val viewModel =
                WatchingRecommendsViewModel(
                    tvContentUseCase = tvContentUseCase,
                    converter = converter(),
                    cardRouter = mockk<LibriaCardRouter>(relaxed = true),
                    tvFavoritesUseCase = tvFavoritesUseCase,
                    historyRepository = historyRepository,
                )
            viewModel.setLoaderDispatcherForTests(testDispatcher)

            viewModel.onRefreshClick()
            waitUntil { viewModel.cardsData.value.any { it is InfoCard } }

            val card = viewModel.cardsData.value.single() as InfoCard
            assertEquals("Рекомендации пока недоступны", card.title)
            assertEquals(
                "Сервис не вернул релевантные тайтлы для этой страницы.",
                card.subtitle,
            )
        }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(80) {
            if (predicate()) return
            delay(25)
        }
        error("Condition was not met in time")
    }

    private fun fakeRelease(id: Int): Release {
        val release = mockk<Release>(relaxed = true)
        every { release.id } returns ReleaseId(id)
        every { release.title } returns "title-$id"
        return release
    }

    private fun converter(): CardsDataConverter {
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
