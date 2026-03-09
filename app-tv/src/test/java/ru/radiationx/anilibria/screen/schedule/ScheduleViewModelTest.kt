package ru.radiationx.anilibria.screen.schedule

import io.mockk.every
import io.mockk.mockk
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.tv.DetailHeaderRemoteData
import ru.radiationx.data.interactors.tv.MainSchedulePayload
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.WeekSchedulePayload
import java.util.ArrayDeque
import java.util.Calendar

class ScheduleViewModelTest {

    @Test
    fun onColdCreate_exposesErrorAndRetryCards_whenLoadFails() = runBlocking {
        val fakeUseCase = FakeTvContentUseCase().apply {
            weekScheduleResults += Result.failure<List<WeekSchedulePayload>>(IllegalStateException("offline"))
        }
        val viewModel = ScheduleViewModel(
            tvContentUseCase = fakeUseCase,
            dataConverter = mockk<CardsDataConverter>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        viewModel.onCreate(mockk<LifecycleOwner>(relaxed = true))
        waitUntil { viewModel.scheduleRows.value.isNotEmpty() }

        assertEquals(
            listOf(
                "Ошибка загрузки" to listOf(
                    LoadingCard(
                        title = "Не удалось загрузить расписание",
                        description = "Проверьте подключение и попробуйте снова",
                        isError = true,
                    ),
                    LinkCard("Повторить"),
                )
            ),
            viewModel.scheduleRows.value,
        )
    }

    @Test
    fun onRetryClick_reloadsSchedule_afterFailure() = runBlocking {
        val mondayRelease = mockk<Release>()
        val mondayCard = LibriaCard(
            title = "Наруто",
            description = "Описание",
            image = "poster.jpg",
            type = LibriaCard.Type.Release(ReleaseId(7)),
        )
        val fakeUseCase = FakeTvContentUseCase().apply {
            weekScheduleResults += Result.failure<List<WeekSchedulePayload>>(IllegalStateException("offline"))
            weekScheduleResults += Result.success(
                listOf(
                    WeekSchedulePayload(
                        calendarDay = Calendar.MONDAY,
                        releases = listOf(mondayRelease),
                    )
                )
            )
        }
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(mondayRelease) } returns mondayCard
        val viewModel = ScheduleViewModel(
            tvContentUseCase = fakeUseCase,
            dataConverter = converter,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        viewModel.onCreate(mockk<LifecycleOwner>(relaxed = true))
        waitUntil { viewModel.scheduleRows.value.isNotEmpty() }

        viewModel.onRetryClick()
        waitUntil { fakeUseCase.loadWeekScheduleCalls == 2 }
        waitUntil {
            viewModel.scheduleRows.value == listOf("Понедельник" to listOf(mondayCard))
        }

        assertEquals(
            listOf("Понедельник" to listOf(mondayCard)),
            viewModel.scheduleRows.value,
        )
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(20L)
        }
        error("Condition was not met in time")
    }
}

private class FakeTvContentUseCase : TvContentUseCase {
    val weekScheduleResults = ArrayDeque<Result<List<WeekSchedulePayload>>>()
    var loadWeekScheduleCalls: Int = 0

    override suspend fun loadMainFeed(requestPage: Int, pageLimit: Int): List<Release> = emptyList()

    override suspend fun loadMainSchedule(currentTimeMs: Long): MainSchedulePayload {
        return MainSchedulePayload("", emptyList())
    }

    override suspend fun loadWeekSchedule(): List<WeekSchedulePayload> {
        loadWeekScheduleCalls += 1
        val result = if (weekScheduleResults.isEmpty()) {
            Result.success(emptyList())
        } else {
            weekScheduleResults.removeFirst()
        }
        return result.getOrThrow()
    }

    override suspend fun loadDetailHeaderRemote(releaseId: ReleaseId): DetailHeaderRemoteData? = null

    override suspend fun loadDetailFavoriteState(releaseId: ReleaseId): Boolean? = null

    override suspend fun loadV1Recommendations(seedReleaseId: Int?, limit: Int): List<Release> = emptyList()

    override suspend fun loadLegacyRecommendations(
        releaseId: ReleaseId,
        requestPage: Int,
    ): List<Release> = emptyList()
}
