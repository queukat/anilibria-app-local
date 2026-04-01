package ru.radiationx.anilibria.screen.search

import com.github.terrakok.cicerone.Router
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
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.data.repository.SearchRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
    fun completedOnly_filtersPrimaryCatalogResultsByResolvedReleaseStatus() = runBlocking {
        val completedRelease = fakeRelease(
            id = 1,
            title = "completed-release",
            statusCode = Release.STATUS_CODE_COMPLETE,
        )
        val notOngoingRelease = fakeRelease(
            id = 2,
            title = "not-ongoing-release",
            statusCode = Release.STATUS_CODE_NOT_ONGOING,
            status = "Анонс",
        )
        val tvSearchUseCase = mockk<TvSearchUseCase>()
        coEvery { tvSearchUseCase.searchReleases(any(), any()) } returns listOf(
            completedRelease,
            notOngoingRelease,
        )
        val searchRepository = mockk<SearchRepository>()
        coEvery { searchRepository.searchReleases(any(), any()) } returns paginatedResponse(emptyList())
        val viewModel = SearchViewModel(
            tvSearchUseCase = tvSearchUseCase,
            converter = searchConverter(),
            router = mockk<Router>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            searchRepository = searchRepository,
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.submitSearchForm(SearchForm(onlyCompleted = true))
        waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 1 }

        val cards = viewModel.cardsData.value.filterIsInstance<LibriaCard>()
        assertEquals(listOf("completed-release"), cards.map(LibriaCard::title))
        coVerify(exactly = 1) { tvSearchUseCase.searchReleases(match { it.onlyCompleted }, 1) }
        coVerify(exactly = 0) { searchRepository.searchReleases(any(), any()) }
    }

    @Test
    fun completedOnly_filtersRepositoryFallbackResultsByResolvedReleaseStatus() = runBlocking {
        val tvSearchUseCase = mockk<TvSearchUseCase>()
        coEvery { tvSearchUseCase.searchReleases(any(), any()) } returns listOf(
            fakeRelease(
                id = 1,
                title = "not-ongoing-primary",
                statusCode = Release.STATUS_CODE_NOT_ONGOING,
                status = "Анонс",
            )
        )
        val completedFallback = fakeRelease(
            id = 2,
            title = "completed-fallback",
            statusCode = null,
            status = "Релиз завершен",
        )
        val searchRepository = mockk<SearchRepository>()
        coEvery { searchRepository.searchReleases(any(), any()) } returns paginatedResponse(
            listOf(
                completedFallback,
                fakeRelease(
                    id = 3,
                    title = "not-ongoing-fallback",
                    statusCode = Release.STATUS_CODE_NOT_ONGOING,
                    status = "Анонс",
                ),
            )
        )
        val viewModel = SearchViewModel(
            tvSearchUseCase = tvSearchUseCase,
            converter = searchConverter(),
            router = mockk<Router>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            searchRepository = searchRepository,
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.submitSearchForm(SearchForm(onlyCompleted = true))
        waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 1 }

        val cards = viewModel.cardsData.value.filterIsInstance<LibriaCard>()
        assertEquals(listOf("completed-fallback"), cards.map(LibriaCard::title))
        coVerify(exactly = 1) { tvSearchUseCase.searchReleases(match { it.onlyCompleted }, 1) }
        coVerify(exactly = 1) { searchRepository.searchReleases(match { it.onlyCompleted }, 1) }
    }

    @Test
    fun dateSort_prioritizesReleaseYearOverTorrentFreshness() = runBlocking {
        val tvSearchUseCase = mockk<TvSearchUseCase>()
        coEvery { tvSearchUseCase.searchReleases(any(), any()) } returns listOf(
            fakeRelease(
                id = 1,
                title = "older-but-fresh",
                year = "2025",
                season = "Осень",
                torrentUpdate = 10_000,
            ),
            fakeRelease(
                id = 2,
                title = "newer-but-stale",
                year = "2026",
                season = "Зима",
                torrentUpdate = 10,
            ),
        )
        val searchRepository = mockk<SearchRepository>()
        coEvery { searchRepository.searchReleases(any(), any()) } returns paginatedResponse(emptyList())
        val viewModel = SearchViewModel(
            tvSearchUseCase = tvSearchUseCase,
            converter = searchConverter(),
            router = mockk<Router>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            searchRepository = searchRepository,
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.submitSearchForm(SearchForm(sort = SearchForm.Sort.DATE))
        waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 2 }

        val cards = viewModel.cardsData.value.filterIsInstance<LibriaCard>()
        assertEquals(listOf("newer-but-stale", "older-but-fresh"), cards.map(LibriaCard::title))
    }

    @Test
    fun sameForm_isNotReloadedTwice() = runBlocking {
        val tvSearchUseCase = mockk<TvSearchUseCase>()
        coEvery { tvSearchUseCase.searchReleases(any(), any()) } returns listOf(
            fakeRelease(id = 1, title = "only-release"),
        )
        val searchRepository = mockk<SearchRepository>()
        coEvery { searchRepository.searchReleases(any(), any()) } returns paginatedResponse(emptyList())
        val viewModel = SearchViewModel(
            tvSearchUseCase = tvSearchUseCase,
            converter = searchConverter(),
            router = mockk<Router>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            searchRepository = searchRepository,
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        val form = SearchForm(sort = SearchForm.Sort.DATE)
        viewModel.submitSearchForm(form)
        waitUntil { viewModel.cardsData.value.filterIsInstance<LibriaCard>().size == 1 }

        viewModel.submitSearchForm(form)

        coVerify(exactly = 1) { tvSearchUseCase.searchReleases(match { it.sort == SearchForm.Sort.DATE }, 1) }
        coVerify(exactly = 0) { searchRepository.searchReleases(any(), any()) }
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(20)
        }
        error("Condition was not met in time")
    }

    private fun paginatedResponse(data: List<Release>): Paginated<Release> = Paginated(
        data = data,
        page = 1,
        allPages = 1,
        perPage = 20,
        allItems = data.size,
    )

    private fun fakeRelease(
        id: Int,
        title: String = "title-$id",
        statusCode: String? = Release.STATUS_CODE_COMPLETE,
        status: String? = null,
        year: String? = "2026",
        season: String? = "Весна",
        torrentUpdate: Int = id,
    ): Release {
        val favoriteInfo = mockk<FavoriteInfo>()
        every { favoriteInfo.rating } returns id
        every { favoriteInfo.isAdded } returns true

        val release = mockk<Release>(relaxed = true)
        every { release.id } returns ReleaseId(id)
        every { release.title } returns title
        every { release.favoriteInfo } returns favoriteInfo
        every { release.statusCode } returns statusCode
        every { release.status } returns status
        every { release.year } returns year
        every { release.season } returns season
        every { release.torrentUpdate } returns torrentUpdate
        return release
    }

    private fun searchConverter(): CardsDataConverter {
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
