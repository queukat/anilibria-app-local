package ru.radiationx.anilibria.screen.suggestions

import androidx.lifecycle.LifecycleOwner
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModelsTest {

    @Test
    fun rowsViewModel_keepsResultRowVisibleForEmptyValidQuery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val controller = SuggestionsController()
        val viewModel = SuggestionsRowsViewModel(controller)
        viewModel.onCreate(mockk<LifecycleOwner>(relaxed = true))
        advanceUntilIdle()

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
        advanceUntilIdle()

        assertEquals(
            listOf(SuggestionsRowsViewModel.RESULT_ROW_ID),
            viewModel.rowListData.value,
        )
        assertTrue(viewModel.emptyResultState.value)
    }
}
