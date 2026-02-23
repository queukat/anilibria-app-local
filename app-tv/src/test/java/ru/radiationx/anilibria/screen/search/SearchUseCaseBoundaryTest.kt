package ru.radiationx.anilibria.screen.search

import com.github.terrakok.cicerone.Router
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.search.completed.SearchCompletedExtra
import ru.radiationx.anilibria.screen.search.completed.SearchCompletedViewModel
import ru.radiationx.anilibria.screen.search.sort.SearchSortExtra
import ru.radiationx.anilibria.screen.search.sort.SearchSortViewModel
import ru.radiationx.anilibria.screen.search.year.SearchYearViewModel
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.tv.TvSearchUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class SearchUseCaseBoundaryTest {

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
    fun searchViewModel_usesTvSearchUseCaseForLoading() = runBlocking {
        val fakeUseCase = FakeTvSearchUseCase().apply {
            searchResult = listOf(mockRelease(77))
        }

        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(any<Release>()) } answers {
            val release = firstArg<Release>()
            LibriaCard(
                title = "release-${release.id.id}",
                description = "",
                image = "",
                type = LibriaCard.Type.Release(release.id),
            )
        }

        val viewModel = SearchViewModel(
            tvSearchUseCase = fakeUseCase,
            converter = converter,
            router = mockk<Router>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            searchController = SearchController(),
        )

        viewModel.onRefreshClick()
        waitUntil { fakeUseCase.searchCalls.isNotEmpty() }

        assertEquals(1, fakeUseCase.searchCalls.first().second)
    }

    @Test
    fun searchYearViewModel_readsFromTvSearchUseCase() = runBlocking {
        val fakeUseCase = FakeTvSearchUseCase().apply {
            yearsState.value = listOf(YearItem("2024", "2024"), YearItem("2025", "2025"))
        }

        val viewModel = SearchYearViewModel(
            argExtra = SearchValuesExtra(values = listOf("2025")),
            tvSearchUseCase = fakeUseCase,
            searchController = mockk(relaxed = true),
            guidedRouter = mockk<GuidedRouter>(relaxed = true),
        )

        waitUntil { viewModel.valuesData.value.isNotEmpty() }

        assertTrue(fakeUseCase.loadYearsCalls > 0)
        assertEquals(listOf("2024", "2025"), viewModel.valuesData.value)
        assertEquals(1, viewModel.selectedIndex.value)
    }

    @Test
    fun sortAndCompletedSelections_areNonNullableState() {
        val sortVm = SearchSortViewModel(
            argExtra = SearchSortExtra(SearchForm.Sort.DATE),
            searchController = mockk(relaxed = true),
            guidedRouter = mockk(relaxed = true),
        )
        val completedVm = SearchCompletedViewModel(
            argExtra = SearchCompletedExtra(isCompleted = true),
            searchController = mockk(relaxed = true),
            guidedRouter = mockk(relaxed = true),
        )

        assertEquals(1, sortVm.selectedIndex.value)
        assertEquals(1, completedVm.selectedIndex.value)
    }

    private fun mockRelease(id: Int): Release {
        val release = mockk<Release>()
        every { release.id } returns ReleaseId(id)
        return release
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(20)
        }
        error("Condition was not met in time")
    }
}

private class FakeTvSearchUseCase : TvSearchUseCase {
    val genresState = MutableStateFlow<List<GenreItem>>(emptyList())
    val yearsState = MutableStateFlow<List<YearItem>>(emptyList())
    var seasonsResult: List<SeasonItem> = emptyList()
    var searchResult: List<Release> = emptyList()
    val searchCalls = mutableListOf<Pair<SearchForm, Int>>()
    var loadYearsCalls: Int = 0

    override fun observeGenres(): Flow<List<GenreItem>> = genresState

    override fun observeYears(): Flow<List<YearItem>> = yearsState

    override suspend fun loadGenres(): List<GenreItem> = genresState.value

    override suspend fun loadYears(): List<YearItem> {
        loadYearsCalls += 1
        return yearsState.value
    }

    override suspend fun loadSeasons(): List<SeasonItem> = seasonsResult

    override suspend fun searchReleases(form: SearchForm, page: Int): List<Release> {
        searchCalls += form to page
        return searchResult
    }
}
