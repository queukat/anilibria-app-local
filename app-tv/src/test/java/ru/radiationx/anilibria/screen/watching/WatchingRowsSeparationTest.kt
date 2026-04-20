package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.ViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.contracts.tv.TvWatchingFacade
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.HistoryReleases
import ru.radiationx.data.entity.domain.release.BlockedInfo
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.repository.HistoryRepository

@OptIn(ExperimentalCoroutinesApi::class)
class WatchingRowsSeparationTest {
    private val createdViewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { viewModel ->
            val clearMethod = ViewModel::class.java.getMethod("clear\$lifecycle_viewmodel_release")
            clearMethod.invoke(viewModel)
        }
        createdViewModels.clear()
    }

    @Test
    fun continueUsesOnlyLocalProgressAndHistoryProjection() = runTest {
        val release = release(id = 42)
        val localAccess = EpisodeAccess(
            id = EpisodeId("3", release.id),
            seek = 65_000L,
            isViewed = true,
            lastAccess = 2_000L,
        )
        val episodesHolder = FakeEpisodesCheckerHolder(listOf(localAccess))
        val historyFlow = MutableStateFlow(HistoryReleases(listOf(release), 1))
        val historyRepository = mockHistoryRepository(historyFlow)
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(release) } returns libriaCard(release)

        val viewModel = track(
            WatchingContinueViewModel(
                converter = converter,
                historyRepository = historyRepository,
                episodesCheckerHolder = episodesHolder,
                cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            ),
        )
        viewModel.setLoaderDispatcherForTests(StandardTestDispatcher(testScheduler))

        viewModel.onRefreshClick()
        advanceUntilIdle()

        val firstCard = viewModel.cardsData.value.first() as LibriaCard
        assertTrue(firstCard.description.contains("серии 3"))
        assertTrue(firstCard.description.contains("1:05"))
    }

    @Test
    fun continueClearsWhenLocalProgressClearsEvenIfHistoryRemains() = runTest {
        val release = release(id = 51)
        val localAccess = EpisodeAccess(
            id = EpisodeId("1", release.id),
            seek = 15_000L,
            isViewed = true,
            lastAccess = 1_000L,
        )
        val episodesHolder = FakeEpisodesCheckerHolder(listOf(localAccess))
        val historyFlow = MutableStateFlow(HistoryReleases(listOf(release), 1))
        val historyRepository = mockHistoryRepository(historyFlow)
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(release) } returns libriaCard(release)

        val viewModel = track(
            WatchingContinueViewModel(
                converter = converter,
                historyRepository = historyRepository,
                episodesCheckerHolder = episodesHolder,
                cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            ),
        )
        viewModel.setLoaderDispatcherForTests(StandardTestDispatcher(testScheduler))

        viewModel.onRefreshClick()
        advanceUntilIdle()
        assertTrue(viewModel.cardsData.value.any { it is LibriaCard })

        episodesHolder.setEpisodes(emptyList())
        viewModel.onRefreshClick()
        advanceUntilIdle()

        assertFalse(viewModel.cardsData.value.any { it is LibriaCard })
    }

    @Test
    fun continueDoesNotExposeUuidAsEpisodeNumber() = runTest {
        val release = release(id = 77)
        val localAccess = EpisodeAccess(
            id = EpisodeId("9fa62e2e-f1aa-43f0-a001", release.id),
            seek = 12_000L,
            isViewed = true,
            lastAccess = 1_500L,
        )
        val episodesHolder = FakeEpisodesCheckerHolder(listOf(localAccess))
        val historyFlow = MutableStateFlow(HistoryReleases(listOf(release), 1))
        val historyRepository = mockHistoryRepository(historyFlow)
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(release) } returns libriaCard(release)

        val viewModel = track(
            WatchingContinueViewModel(
                converter = converter,
                historyRepository = historyRepository,
                episodesCheckerHolder = episodesHolder,
                cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            ),
        )
        viewModel.setLoaderDispatcherForTests(StandardTestDispatcher(testScheduler))

        viewModel.onRefreshClick()
        advanceUntilIdle()

        val firstCard = viewModel.cardsData.value.first() as LibriaCard
        assertFalse(firstCard.description.contains("9fa62e2e-f1aa"))
        assertTrue(firstCard.description.contains("0:12"))
    }

    @Test
    fun historyUsesOnlyLocalHistoryProjection() = runTest {
        val release = release(id = 88)
        val historyFlow = MutableStateFlow(HistoryReleases(listOf(release), 1))
        val historyRepository = mockHistoryRepository(historyFlow)
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(release) } returns libriaCard(release)

        val viewModel = track(
            WatchingHistoryViewModel(
                converter = converter,
                historyRepository = historyRepository,
                cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            ),
        )
        viewModel.setLoaderDispatcherForTests(StandardTestDispatcher(testScheduler))

        viewModel.onRefreshClick()
        advanceUntilIdle()

        assertTrue(viewModel.cardsData.value.any { it is LibriaCard })
    }

    @Test
    fun historyClearsWhenLocalHistoryClears() = runTest {
        val release = release(id = 99)
        val historyFlow = MutableStateFlow(HistoryReleases(listOf(release), 1))
        val historyRepository = mockHistoryRepository(historyFlow)
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(release) } returns libriaCard(release)

        val viewModel = track(
            WatchingHistoryViewModel(
                converter = converter,
                historyRepository = historyRepository,
                cardRouter = mockk<LibriaCardRouter>(relaxed = true),
            ),
        )
        viewModel.setLoaderDispatcherForTests(StandardTestDispatcher(testScheduler))

        viewModel.onRefreshClick()
        advanceUntilIdle()
        assertTrue(viewModel.cardsData.value.any { it is LibriaCard })

        historyFlow.value = HistoryReleases(emptyList(), 0)
        advanceTimeBy(300)
        advanceUntilIdle()

        assertFalse(viewModel.cardsData.value.any { it is LibriaCard })
    }

    @Test
    fun watchingPageSelectionRequestsBackgroundSync() {
        val tvWatchingFacade = mockk<TvWatchingFacade>(relaxed = true)
        every { tvWatchingFacade.observeLocalContinueAvailable() } returns MutableStateFlow(false)
        every { tvWatchingFacade.observeLocalHistoryAvailable() } returns MutableStateFlow(false)

        val viewModel = track(
            WatchingViewModel(
                tvWatchingFacade = tvWatchingFacade,
            ),
        )

        viewModel.onPageSelected()

        verify(exactly = 1) {
            tvWatchingFacade.requestBackgroundSync()
        }
    }

    private fun mockHistoryRepository(
        historyFlow: MutableStateFlow<HistoryReleases>,
    ): HistoryRepository {
        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } answers { historyFlow.value }
        every { historyRepository.observeReleases(any()) } returns historyFlow
        return historyRepository
    }

    private fun libriaCard(release: Release): LibriaCard {
        return LibriaCard(
            title = release.names.first(),
            description = "",
            image = "",
            type = LibriaCard.Type.Release(release.id),
        )
    }

    private fun release(id: Int): Release {
        return Release(
            id = ReleaseId(id),
            code = ReleaseCode("code_$id"),
            names = listOf("Release $id"),
            series = null,
            poster = null,
            torrentUpdate = 0,
            status = null,
            statusCode = null,
            types = emptyList(),
            genres = emptyList(),
            voices = emptyList(),
            members = null,
            year = null,
            season = null,
            days = emptyList(),
            description = null,
            announce = null,
            favoriteInfo = FavoriteInfo(rating = 0, isAdded = false),
            link = null,
            franchises = emptyList(),
            showDonateDialog = false,
            blockedInfo = BlockedInfo(isBlocked = false, reason = null),
            moonwalkLink = null,
            episodes = emptyList(),
            sourceEpisodes = emptyList(),
            externalPlaylists = emptyList(),
            rutubePlaylist = emptyList(),
            torrents = emptyList(),
        )
    }

    private fun <T : ViewModel> track(viewModel: T): T {
        createdViewModels += viewModel
        return viewModel
    }
}

private class FakeEpisodesCheckerHolder(
    initial: List<EpisodeAccess>,
) : EpisodesCheckerHolder {
    private val state = MutableStateFlow(initial)

    override fun observeEpisodes() = state

    override suspend fun getEpisodes(): List<EpisodeAccess> = state.value

    override suspend fun putEpisode(episode: EpisodeAccess) {
        state.value = state.value.filterNot { it.id == episode.id } + episode
    }

    override suspend fun putAllEpisode(episodes: List<EpisodeAccess>) {
        val map = state.value.associateBy { it.id }.toMutableMap()
        episodes.forEach { map[it.id] = it }
        state.value = map.values.toList()
    }

    override suspend fun getEpisodes(releaseId: ReleaseId): List<EpisodeAccess> = state.value.filter { it.id.releaseId == releaseId }

    override suspend fun getEpisode(episodeId: EpisodeId): EpisodeAccess? = state.value.firstOrNull { it.id == episodeId }

    override suspend fun remove(releaseId: ReleaseId) {
        state.value = state.value.filterNot { it.id.releaseId == releaseId }
    }

    fun setEpisodes(episodes: List<EpisodeAccess>) {
        state.value = episodes
    }
}
