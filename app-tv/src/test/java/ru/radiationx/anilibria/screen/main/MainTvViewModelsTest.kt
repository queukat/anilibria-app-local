package ru.radiationx.anilibria.screen.main

import com.github.terrakok.cicerone.Router
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
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.tv.DetailHeaderRemoteData
import ru.radiationx.data.interactors.tv.MainSchedulePayload
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.WeekSchedulePayload

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

        viewModel.onRefreshClick()
        waitUntil { fakeUseCase.mainScheduleCalls > 0 }

        assertEquals("Ожидается сегодня", viewModel.rowTitle.value)
        assertTrue(viewModel.cardsData.value.isNotEmpty())
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(50) {
            if (predicate()) return
            delay(20)
        }
        error("Condition was not met in time")
    }
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

    override suspend fun loadDetailHeaderRemote(releaseId: ReleaseId): DetailHeaderRemoteData? = null

    override suspend fun loadDetailFavoriteState(releaseId: ReleaseId): Boolean? = null

    override suspend fun loadV1Recommendations(seedReleaseId: Int?, limit: Int): List<Release> = emptyList()

    override suspend fun loadLegacyRecommendations(releaseId: ReleaseId, requestPage: Int): List<Release> = emptyList()
}
