package ru.radiationx.anilibria.screen.suggestions

import androidx.lifecycle.LifecycleOwner
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
import ru.radiationx.anilibria.common.InfoCard

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
    fun validEmptyQuery_keepsResultRowVisibleViaUiState() = runBlocking {
        val uiState = SuggestionsSearchResult(
            items = emptyList(),
            query = "naruto",
            validQuery = true,
        ).toUiState(progressVisible = false)

        assertEquals(
            listOf(SuggestionsRows.RESULT_ROW_ID),
            SuggestionsRows.visibleRowIds(
                showResultRow = uiState.showResultRow,
                showRecommendsRow = uiState.showRecommendsRow,
            ),
        )
        assertEquals(1, uiState.resultCards.size)
        assertTrue(uiState.resultCards.first() is InfoCard)
    }
}
