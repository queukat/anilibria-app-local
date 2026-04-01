package ru.radiationx.anilibria.screen.main

import com.github.terrakok.cicerone.Router
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
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
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.types.YoutubeId
import ru.radiationx.data.entity.domain.youtube.YoutubeItem
import ru.radiationx.data.interactors.tv.MainSchedulePayload
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.WeekSchedulePayload
import ru.radiationx.data.repository.YoutubeRepository

@OptIn(ExperimentalCoroutinesApi::class)
class MainTvViewModelsTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    @OptIn(ExperimentalCoroutinesApi::class)
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mainFeedViewModel_requestsFeedFromUseCase() = runBlocking {
        val fakeUseCase = FakeTvContentUseCase().apply {
            mainFeedResult = emptyList()
        }
        val viewModel = MainFeedViewModel(
            tvContentUseCase = fakeUseCase,
            converter = mockk<CardsDataConverter>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.onRefreshClick()
        waitUntil { fakeUseCase.mainFeedCalls.isNotEmpty() }

        assertEquals(listOf(1 to 20), fakeUseCase.mainFeedCalls)
    }

    @Test
    fun mainScheduleViewModel_setsRowTitleFromUseCase() = runBlocking {
        val fakeUseCase = FakeTvContentUseCase().apply {
            mainScheduleResult = MainSchedulePayload(
                title = "Ожидается сегодня",
                releases = emptyList(),
            )
        }
        val viewModel = MainScheduleViewModel(
            tvContentUseCase = fakeUseCase,
            converter = mockk<CardsDataConverter>(relaxed = true),
            router = mockk<Router>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.onRefreshClick()
        waitUntil { fakeUseCase.mainScheduleCalls > 0 }

        assertEquals("Ожидается сегодня", viewModel.rowTitle.value)
        assertTrue(viewModel.cardsData.value.isNotEmpty())
    }

    @Test
    fun mainScheduleViewModel_emptyStateCardOpensFullSchedule() = runBlocking {
        val fakeUseCase = FakeTvContentUseCase().apply {
            mainScheduleResult = MainSchedulePayload(
                title = "Ожидается сегодня",
                releases = emptyList(),
            )
        }
        val router = mockk<Router>(relaxed = true)
        val viewModel = MainScheduleViewModel(
            tvContentUseCase = fakeUseCase,
            converter = mockk<CardsDataConverter>(relaxed = true),
            router = router,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.onRefreshClick()
        waitUntil { viewModel.cardsData.value.size == 1 }

        viewModel.onLoadingCardClick()

        verify(exactly = 1) { router.navigateTo(any()) }
    }

    @Test
    fun mainYouTubeViewModel_deduplicatesPagedCardsAndHidesLoadMoreOnLastPage() = runBlocking {
        val repository = mockk<YoutubeRepository>()
        val converter = mockk<CardsDataConverter>()
        val itemA = youtubeItem(id = 1, vid = "alpha")
        val itemB = youtubeItem(id = 2, vid = "beta")
        val itemC = youtubeItem(id = 3, vid = "gamma")
        val cardA = youtubeCard(title = "A", vid = "alpha")
        val cardB = youtubeCard(title = "B", vid = "beta")
        val cardC = youtubeCard(title = "C", vid = "gamma")

        coEvery { repository.getYoutubeList(1) } returns Paginated(
            data = listOf(itemA, itemB),
            page = 1,
            allPages = 2,
            perPage = 2,
            allItems = 3,
        )
        coEvery { repository.getYoutubeList(2) } returns Paginated(
            data = listOf(itemB, itemC),
            page = 2,
            allPages = 2,
            perPage = 2,
            allItems = 3,
        )
        every { converter.toCard(itemA) } returns cardA
        every { converter.toCard(itemB) } returns cardB
        every { converter.toCard(itemC) } returns cardC

        val viewModel = MainYouTubeViewModel(
            youtubeRepository = repository,
            converter = converter,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        viewModel.setLoaderDispatcherForTests(testDispatcher)

        viewModel.onRefreshClick()
        waitUntil {
            viewModel.cardsData.value.filterIsInstance<LibriaCard>().map { it.title } == listOf("A", "B")
        }
        assertTrue(viewModel.cardsData.value.lastOrNull() is LinkCard)

        viewModel.onLinkCardClick()
        waitUntil {
            viewModel.cardsData.value.filterIsInstance<LibriaCard>().map { it.title } == listOf("A", "B", "C")
        }

        val loadedCards = viewModel.cardsData.value.filterIsInstance<LibriaCard>()
        assertEquals(listOf("A", "B", "C"), loadedCards.map { it.title })
        assertEquals(3, loadedCards.map { it.itemId }.distinct().size)
        assertTrue(viewModel.cardsData.value.none { it is LinkCard })
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(20)
        }
        error("Condition was not met in time")
    }

    private fun youtubeItem(
        id: Int,
        vid: String,
    ) = YoutubeItem(
        id = YoutubeId(id),
        title = "Video $id",
        image = "image-$id",
        vid = vid,
        views = 0,
        comments = 0,
        timestamp = id,
    )

    private fun youtubeCard(
        title: String,
        vid: String,
    ) = LibriaCard(
        title = title,
        description = "",
        image = "",
        type = LibriaCard.Type.Youtube("https://www.youtube.com/watch?v=$vid"),
    )
}

private class FakeTvContentUseCase : TvContentUseCase {
    var mainFeedResult: List<Release> = emptyList()
    var mainScheduleResult: MainSchedulePayload = MainSchedulePayload("", emptyList())
    val mainFeedCalls = mutableListOf<Pair<Int, Int>>()
    var mainScheduleCalls: Int = 0

    override suspend fun loadMainFeed(requestPage: Int, pageLimit: Int): List<Release> {
        mainFeedCalls += requestPage to pageLimit
        return mainFeedResult
    }

    override suspend fun loadMainSchedule(currentTimeMs: Long): MainSchedulePayload {
        mainScheduleCalls += 1
        return mainScheduleResult
    }

    override suspend fun loadWeekSchedule(): List<WeekSchedulePayload> = emptyList()

    override suspend fun loadFavoriteState(releaseId: ReleaseId): Boolean? = null

    override suspend fun loadRecommendations(seedReleaseId: Int?, limit: Int): List<Release> = emptyList()
}
