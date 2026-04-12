package ru.radiationx.anilibria.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.entity.domain.types.ReleaseId

@OptIn(ExperimentalCoroutinesApi::class)
class BaseCardsViewModelTest {
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
    fun refreshEmitsUpdatedCardsWhenRefreshHasNoProgressCard() =
        runBlocking {
            val viewModel =
                TestCardsViewModel(
                    loaderResults =
                        ArrayDeque(
                            listOf(
                                listOf(card(title = "A")),
                                listOf(card(title = "B")),
                            ),
                        ),
                )

            val snapshots = mutableListOf<List<String>>()
            val collectJob =
                launch {
                    viewModel.cardsData.collect { items ->
                        snapshots +=
                            items
                                .filterIsInstance<LibriaCard>()
                                .map { it.title }
                    }
                }

            viewModel.onRefreshClick()
            waitUntil { snapshots.any { it == listOf("A") } }

            viewModel.onRefreshClick()
            waitUntil { snapshots.any { it == listOf("B") } }

            assertTrue("First refresh must emit the first snapshot", snapshots.any { it == listOf("A") })
            assertTrue("Second refresh must emit updated snapshot", snapshots.any { it == listOf("B") })

            collectJob.cancel()
        }

    @Test
    fun loadMoreKeepsActionCardWhileAppendIsLoadingWithoutProgressState() =
        runBlocking {
            val secondPageGate = CompletableDeferred<Unit>()
            val viewModel = AppendWithoutProgressCardsViewModel(secondPageGate)

            viewModel.onRefreshClick()
            waitUntil {
                viewModel.cardsData.value.filterIsInstance<LibriaCard>().map { it.title } == listOf("A")
            }
            assertTrue(viewModel.cardsData.value.lastOrNull() is LinkCard)

            viewModel.onLinkCardClick()
            repeat(5) { delay(20) }

            assertTrue(viewModel.cardsData.value.lastOrNull() is LinkCard)
            assertTrue(viewModel.cardsData.value.none { it is LoadingCard && !it.isError })

            secondPageGate.complete(Unit)
            waitUntil {
                viewModel.cardsData.value.filterIsInstance<LibriaCard>().map { it.title } == listOf("A", "B")
            }
        }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(20)
        }
        error("Condition was not met in time")
    }

    private fun card(title: String): LibriaCard = testCard(title)
}

private fun testCard(title: String): LibriaCard =
    LibriaCard(
        title = title,
        description = "",
        image = "",
        type = LibriaCard.Type.Release(ReleaseId(1)),
    )

private class TestCardsViewModel(
    private val loaderResults: ArrayDeque<List<LibriaCard>>,
) : BaseCardsViewModel() {
    override val preventClearOnRefresh: Boolean = true
    override val progressOnRefresh: Boolean = false

    override fun hasMoreCards(
        newCards: List<LibriaCard>,
        allCards: List<LibriaCard>,
    ): Boolean = false

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        return loaderResults.removeFirst()
    }
}

private class AppendWithoutProgressCardsViewModel(
    private val secondPageGate: CompletableDeferred<Unit>,
) : BaseCardsViewModel() {
    override val progressOnAppend: Boolean = false

    override fun hasMoreCards(
        newCards: List<LibriaCard>,
        allCards: List<LibriaCard>,
    ): Boolean {
        return allCards.size < 2
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        return when (requestPage) {
            1 -> listOf(testCard(title = "A"))
            2 -> {
                secondPageGate.await()
                listOf(testCard(title = "B"))
            }
            else -> emptyList()
        }
    }
}
