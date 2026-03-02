package ru.radiationx.anilibria.screen.details

import com.github.terrakok.cicerone.Router
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.DetailHeaderRemoteData
import ru.radiationx.data.interactors.tv.MainSchedulePayload
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.WeekSchedulePayload
import ru.radiationx.shared_app.common.SystemUtils

@OptIn(ExperimentalCoroutinesApi::class)
class DetailRecommendsViewModelTest {

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
    fun refresh_fallsBackToGlobalRecommendationsWhenSeededIsEmpty() = runBlocking {
        val fakeUseCase = FakeTvContentUseCaseForDetails().apply {
            seededRecommendations = emptyList()
            globalRecommendations = listOf(mockRelease(9839))
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

        val viewModel = DetailRecommendsViewModel(
            tvContentUseCase = fakeUseCase,
            releaseInteractor = mockk<ReleaseInteractor>(relaxed = true),
            converter = converter,
            cardRouter = LibriaCardRouter(
                router = mockk<Router>(relaxed = true),
                systemUtils = mockk<SystemUtils>(relaxed = true),
            ),
            extra = DetailExtra(id = ReleaseId(9600)),
        )

        viewModel.onRefreshClick()
        waitUntil {
            fakeUseCase.v1Calls.any { it == 9600 } && fakeUseCase.v1Calls.any { it == null }
        }

        val seededIndex = fakeUseCase.v1Calls.indexOf(9600)
        val globalIndex = fakeUseCase.v1Calls.indexOf(null)
        assertTrue("Expected seeded recommendations call", seededIndex >= 0)
        assertTrue("Expected global fallback recommendations call", globalIndex >= 0)
        assertTrue("Global fallback should happen after seeded call", globalIndex > seededIndex)
        assertTrue(fakeUseCase.legacyCalls == 0)
        waitUntil { viewModel.cardsData.value.any { it is LibriaCard } }
        assertTrue(viewModel.cardsData.value.any { it is LibriaCard })
    }

    private fun mockRelease(id: Int): Release {
        val release = mockk<Release>()
        every { release.id } returns ReleaseId(id)
        return release
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) return
            delay(25)
        }
        error("Condition was not met in time")
    }
}

private class FakeTvContentUseCaseForDetails : TvContentUseCase {
    var seededRecommendations: List<Release> = emptyList()
    var globalRecommendations: List<Release> = emptyList()
    val v1Calls = mutableListOf<Int?>()
    var legacyCalls: Int = 0

    override suspend fun loadMainFeed(requestPage: Int, pageLimit: Int): List<Release> = emptyList()

    override suspend fun loadMainSchedule(currentTimeMs: Long): MainSchedulePayload =
        MainSchedulePayload(title = "", releases = emptyList())

    override suspend fun loadWeekSchedule(): List<WeekSchedulePayload> = emptyList()

    override suspend fun loadDetailHeaderRemote(releaseId: ReleaseId): DetailHeaderRemoteData? = null

    override suspend fun loadDetailFavoriteState(releaseId: ReleaseId): Boolean? = null

    override suspend fun loadV1Recommendations(seedReleaseId: Int?, limit: Int): List<Release> {
        v1Calls += seedReleaseId
        return if (seedReleaseId == null) globalRecommendations else seededRecommendations
    }

    override suspend fun loadLegacyRecommendations(releaseId: ReleaseId, requestPage: Int): List<Release> {
        legacyCalls += 1
        return emptyList()
    }
}
