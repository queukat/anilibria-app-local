package ru.radiationx.anilibria.screen.search

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.toTvCollectionSortLabel
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.tv.TvSearchUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class SearchFormViewModelTest {
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
    fun selectSinglePicker_updatesSearchFormStateWithoutController() =
        runTest(testDispatcher) {
            val tvSearchUseCase = mockk<TvSearchUseCase>()
            every { tvSearchUseCase.observeYears() } returns emptyFlow()
            every { tvSearchUseCase.observeGenres() } returns emptyFlow()
            coEvery { tvSearchUseCase.loadYears() } returns emptyList()
            coEvery { tvSearchUseCase.loadGenres() } returns emptyList()
            coEvery { tvSearchUseCase.loadSeasons() } returns emptyList()

            val viewModel = SearchFormViewModel(tvSearchUseCase)
            runCurrent()

            viewModel.onSortClick()
            viewModel.selectSinglePicker(1)

            assertEquals(SearchForm.Sort.DATE, viewModel.searchFormData.value.sort)
            assertEquals(SearchForm.Sort.DATE.toTvCollectionSortLabel(), viewModel.filtersUiState.value.sort.label)
            assertNull(viewModel.filterPicker.value)
        }
}
