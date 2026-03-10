package ru.radiationx.anilibria.screen.suggestions

import androidx.lifecycle.LifecycleOwner
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModelsTest {

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
    fun rowsViewModel_keepsResultRowVisibleForEmptyValidQuery() = runBlocking {
        val controller = SuggestionsController()
        val viewModel = SuggestionsRowsViewModel(controller)
        viewModel.onCreate(mockk<LifecycleOwner>(relaxed = true))

        assertEquals(
            listOf(SuggestionsRowsViewModel.RECOMMENDS_ROW_ID),
            viewModel.rowListData.value,
        )

        controller.resultEvent.emit(
            SuggestionsController.SearchResult(
                items = emptyList(),
                query = "naruto",
                validQuery = true,
            )
        )

        assertEquals(
            listOf(SuggestionsRowsViewModel.RESULT_ROW_ID),
            viewModel.rowListData.value,
        )
        assertTrue(viewModel.emptyResultState.value)
    }
}
