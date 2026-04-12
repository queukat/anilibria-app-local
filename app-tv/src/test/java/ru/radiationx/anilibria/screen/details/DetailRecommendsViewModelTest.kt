package ru.radiationx.anilibria.screen.details

import androidx.lifecycle.ViewModel
import com.github.terrakok.cicerone.Router
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import ru.radiationx.data.interactors.tv.MainSchedulePayload
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.WeekSchedulePayload
import ru.radiationx.shared_app.common.SystemUtils
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class DetailRecommendsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val createdViewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { viewModel ->
            clearViewModel(viewModel)
        }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun <T : ViewModel> track(viewModel: T): T {
        createdViewModels += viewModel
        return viewModel
    }

    private fun clearViewModel(viewModel: ViewModel) {
        val clearMethod = ViewModel::class.java.getMethod("clear\$lifecycle_viewmodel_release")
        clearMethod.invoke(viewModel)
    }

    @Test
    fun refresh_usesRecommendationsFromUseCase() =
        runTest {
            val fakeUseCase =
                FakeTvContentUseCaseForDetails().apply {
                    recommendationsBySeed[9600] = listOf(mockRelease(9839))
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

            val viewModel =
                track(
                    DetailRecommendsViewModel(
                        tvContentUseCase = fakeUseCase,
                        converter = converter,
                        cardRouter =
                            LibriaCardRouter(
                                router = mockk<Router>(relaxed = true),
                                systemUtils = mockk<SystemUtils>(relaxed = true),
                            ),
                        extra = DetailExtra(id = ReleaseId(9600)),
                    ),
                )
            viewModel.setLoaderDispatcherForTests(testDispatcher)

            viewModel.onRefreshClick()
            waitUntil { fakeUseCase.recommendationCalls == listOf(9600) }
            waitUntil { viewModel.cardsData.value.any { it is LibriaCard } }
            assertTrue(viewModel.cardsData.value.any { it is LibriaCard })
        }

    @Test
    fun refresh_fallsBackToGlobalRecommendations_whenSeededListContainsOnlyCurrentRelease() =
        runTest {
            val fakeUseCase =
                FakeTvContentUseCaseForDetails().apply {
                    recommendationsBySeed[9600] = listOf(mockRelease(9600))
                    recommendationsBySeed[null] = listOf(mockRelease(9839))
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

            val viewModel =
                track(
                    DetailRecommendsViewModel(
                        tvContentUseCase = fakeUseCase,
                        converter = converter,
                        cardRouter =
                            LibriaCardRouter(
                                router = mockk<Router>(relaxed = true),
                                systemUtils = mockk<SystemUtils>(relaxed = true),
                            ),
                        extra = DetailExtra(id = ReleaseId(9600)),
                    ),
                )
            viewModel.setLoaderDispatcherForTests(testDispatcher)

            viewModel.onRefreshClick()
            waitUntil { fakeUseCase.recommendationCalls == listOf(9600, null) }
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
    val recommendationsBySeed = mutableMapOf<Int?, List<Release>>()
    val recommendationCalls = CopyOnWriteArrayList<Int?>()

    override suspend fun loadMainFeed(
        requestPage: Int,
        pageLimit: Int,
    ): List<Release> = emptyList()

    override suspend fun loadMainSchedule(currentTimeMs: Long): MainSchedulePayload =
        MainSchedulePayload(title = "", releases = emptyList())

    override suspend fun loadWeekSchedule(): List<WeekSchedulePayload> = emptyList()

    override suspend fun loadFavoriteState(releaseId: ReleaseId): Boolean? = null

    override suspend fun loadRecommendations(
        seedReleaseId: Int?,
        limit: Int,
    ): List<Release> {
        recommendationCalls += seedReleaseId
        return recommendationsBySeed[seedReleaseId].orEmpty()
    }
}
