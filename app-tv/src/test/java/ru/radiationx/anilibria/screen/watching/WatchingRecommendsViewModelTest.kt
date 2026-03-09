package ru.radiationx.anilibria.screen.watching

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.data.repository.SearchRepository

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
    fun refresh_fallsBackToTopRated_whenFavoritesUnavailable() = runBlocking {
        val releases = listOf(fakeRelease(101, genres = listOf("action")), fakeRelease(102, genres = listOf("drama")))
        val favoriteRepository = mockk<FavoriteRepository>()
        coEvery { favoriteRepository.getFavorites(any()) } throws IllegalStateException("guest mode")

        val searchRepository = mockk<SearchRepository>()
        coEvery { searchRepository.searchReleases(any(), any()) } answers {
            Paginated(
                data = releases,
                page = secondArg(),
                allPages = 1,
                perPage = releases.size,
                allItems = releases.size,
            )
        }

        val viewModel = WatchingRecommendsViewModel(
            searchRepository = searchRepository,
            releaseInteractor = mockk<ReleaseInteractor>(relaxed = true),
            converter = converter(),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            favoriteRepository = favoriteRepository,
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.onRefreshClick()
        waitUntil { viewModel.cardsData.value.any { it is LibriaCard } }

        val cards = viewModel.cardsData.value.filterIsInstance<LibriaCard>()
        assertEquals(listOf("title-101", "title-102"), cards.map { it.title })
        assertTrue(viewModel.cardsData.value.none { it is ru.radiationx.anilibria.common.LoadingCard && it.isError })
        coVerify(exactly = 1) { favoriteRepository.getFavorites(1) }
        coVerify(exactly = 1) {
            searchRepository.searchReleases(
                SearchForm(sort = SearchForm.Sort.RATING),
                1,
            )
        }
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(80) {
            if (predicate()) return
            delay(25)
        }
        error("Condition was not met in time")
    }

    private fun fakeRelease(id: Int, genres: List<String>): Release {
        val release = mockk<Release>(relaxed = true)
        every { release.id } returns ReleaseId(id)
        every { release.title } returns "title-$id"
        every { release.genres } returns genres
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
