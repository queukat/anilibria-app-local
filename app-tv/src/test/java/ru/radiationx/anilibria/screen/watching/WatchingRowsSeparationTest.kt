package ru.radiationx.anilibria.screen.watching

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.HistoryReleases
import ru.radiationx.data.entity.domain.release.BlockedInfo
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.UserViewsRepository
import ru.radiationx.data.entity.response.PaginatedResponse

@OptIn(ExperimentalCoroutinesApi::class)
class WatchingRowsSeparationTest {

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
    fun clearWatchProgress_removesContinueButKeepsOpenedHistory() = runBlocking {
        val release = release(id = 42)
        val card = LibriaCard(
            title = "Release 42",
            description = "",
            image = "",
            type = LibriaCard.Type.Release(release.id),
        )

        val episodesHolder = FakeEpisodesCheckerHolder(
            listOf(
                EpisodeAccess(
                    id = EpisodeId("1", release.id),
                    seek = 15_000,
                    isViewed = true,
                    lastAccess = 1000L,
                )
            )
        )

        val converter = mockk<CardsDataConverter>(relaxed = true)
        every { converter.toCard(release) } returns card

        val historyFlow = MutableStateFlow(HistoryReleases(listOf(release), 1))
        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } answers { historyFlow.value }
        every { historyRepository.observeReleases(any()) } returns historyFlow

        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(release.id) } returns listOf(
            EpisodeAccess(
                id = EpisodeId("1", release.id),
                seek = 15_000,
                isViewed = true,
                lastAccess = 1000L,
            )
        )

        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getViewsHistory(any(), any()) } throws RuntimeException("offline")

        val continueVm = WatchingContinueViewModel(
            converter = converter,
            releaseInteractor = releaseInteractor,
            historyRepository = historyRepository,
            episodesCheckerHolder = episodesHolder,
            userViewsRepository = userViewsRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        val historyVm = WatchingHistoryViewModel(
            converter = converter,
            historyRepository = historyRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        continueVm.onRefreshClick()
        historyVm.onRefreshClick()
        waitUntil {
            continueVm.cardsData.value.isNotEmpty() &&
                historyVm.cardsData.value.isNotEmpty() &&
                continueVm.cardsData.value.none { it is LoadingCard } &&
                historyVm.cardsData.value.none { it is LoadingCard }
        }

        assertTrue(continueVm.cardsData.value.isNotEmpty())
        assertTrue(historyVm.cardsData.value.isNotEmpty())

        // Simulate "clear watch progress": continue source becomes empty.
        episodesHolder.setEpisodes(emptyList())
        coEvery { releaseInteractor.getAccesses(release.id) } returns emptyList()

        historyVm.onRefreshClick()
        waitUntil {
            continueVm.cardsData.value.isEmpty() &&
                historyVm.cardsData.value.isNotEmpty() &&
                historyVm.cardsData.value.none { it is LoadingCard }
        }

        assertTrue("Continue must be empty after clearing watch progress", continueVm.cardsData.value.isEmpty())
        assertTrue("Opened history must remain after clearing watch progress", historyVm.cardsData.value.isNotEmpty())
    }

    @Test
    fun clearWatchProgress_removesRemoteContinueItem() = runTest {
        val deterministicDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(deterministicDispatcher)
        val release = release(id = 51)
        val card = LibriaCard(
            title = "Release 51",
            description = "",
            image = "",
            type = LibriaCard.Type.Release(release.id),
        )

        val episodesHolder = FakeEpisodesCheckerHolder(
            listOf(
                EpisodeAccess(
                    id = EpisodeId("1", release.id),
                    seek = 5_000,
                    isViewed = true,
                    lastAccess = 1000L,
                )
            )
        )

        val converter = mockk<CardsDataConverter>(relaxed = true)
        every { converter.toCard(release) } returns card

        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } returns HistoryReleases(emptyList(), 0)
        every { historyRepository.observeReleases(any()) } returns MutableStateFlow(HistoryReleases(emptyList(), 0))

        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(release.id) } returns listOf(
            EpisodeAccess(
                id = EpisodeId("1", release.id),
                seek = 5_000,
                isViewed = true,
                lastAccess = 1000L,
            )
        )

        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getViewsHistory(any(), any()) } answers {
            PaginatedResponse(
                data = listOf(
                    UserViewHistoryItem(
                        releaseId = release.id,
                        titleMain = release.names.first(),
                        titleEnglish = null,
                        titleAlternative = null,
                        posterPreview = null,
                        posterThumbnail = null,
                        episodeOrdinal = 1.0,
                        timeSeconds = 12.0,
                        isWatched = false,
                    )
                ),
                meta = PaginatedResponse.PaginationResponse(1, 1, 1, 1),
            )
        }

        val continueVm = WatchingContinueViewModel(
            converter = converter,
            releaseInteractor = releaseInteractor,
            historyRepository = historyRepository,
            episodesCheckerHolder = episodesHolder,
            userViewsRepository = userViewsRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        continueVm.setLoaderDispatcherForTests(deterministicDispatcher)

        continueVm.onRefreshClick()
        advanceUntilIdle()
        assertTrue("Continue must contain remote item before clear", continueVm.cardsData.value.isNotEmpty())

        // Эмулируем очистку watch-progress: локальный список эпизодов пуст.
        episodesHolder.setEpisodes(emptyList())
        coEvery { releaseInteractor.getAccesses(release.id) } returns emptyList()
        continueVm.onRefreshClick()
        advanceUntilIdle()

        assertTrue("Continue should disappear when watch progress cleared even if remote still returns item", continueVm.cardsData.value.isEmpty())
    }

    @Test
    fun openedCardAppearsInHistoryAfterClear() = runBlocking {
        val release = release(id = 99)
        val continueCard = LibriaCard(
            title = "Release 99",
            description = "",
            image = "",
            type = LibriaCard.Type.Release(release.id),
        )

        val openedHistoryFlow = MutableStateFlow(HistoryReleases(emptyList(), 0))

        val converter = mockk<CardsDataConverter>(relaxed = true)
        every { converter.toCard(release) } returns continueCard

        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } answers { openedHistoryFlow.value }
        every { historyRepository.observeReleases(any()) } returns openedHistoryFlow

        val historyVm = WatchingHistoryViewModel(
            converter = converter,
            historyRepository = historyRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        historyVm.onRefreshClick()
        waitUntil {
            historyVm.cardsData.value.isEmpty()
        }

        // Simulate "open" action by writing card id into opened history storage.
        openedHistoryFlow.value = HistoryReleases(listOf(release), 1)

        waitUntil {
            historyVm.cardsData.value.isNotEmpty() &&
                historyVm.cardsData.value.none { it is LoadingCard }
        }

        assertTrue("Opened card must appear in history", historyVm.cardsData.value.isNotEmpty())
        assertTrue(
            "Opened card must come from local history flow",
            historyVm.cardsData.value.any { card -> card is LibriaCard && card.type is LibriaCard.Type.Release && card.type.releaseId == release.id }
        )
    }

    @Test
    fun remoteContinueItemDisappearsAfterWatchProgressClearAndVmRecreate() = runTest {
        val deterministicDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(deterministicDispatcher)
        val release = release(id = 77)
        val card = LibriaCard(
            title = "Release 77",
            description = "",
            image = "",
            type = LibriaCard.Type.Release(release.id),
        )

        val episodesHolder = FakeEpisodesCheckerHolder(
            listOf(
                EpisodeAccess(
                    id = EpisodeId("1", release.id),
                    seek = 7_000,
                    isViewed = true,
                    lastAccess = 1000L,
                )
            )
        )

        val converter = mockk<CardsDataConverter>(relaxed = true)
        every { converter.toCard(release) } returns card

        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } returns HistoryReleases(emptyList(), 0)
        every { historyRepository.observeReleases(any()) } returns MutableStateFlow(HistoryReleases(emptyList(), 0))

        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(release.id) } returns emptyList()

        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getViewsHistory(any(), any()) } answers {
            PaginatedResponse(
                data = listOf(
                    UserViewHistoryItem(
                        releaseId = release.id,
                        titleMain = release.names.first(),
                        titleEnglish = null,
                        titleAlternative = null,
                        posterPreview = null,
                        posterThumbnail = null,
                        episodeOrdinal = 1.0,
                        timeSeconds = 10.0,
                        isWatched = false,
                    )
                ),
                meta = PaginatedResponse.PaginationResponse(1, 1, 1, 1),
            )
        }

        val continueVmFirst = WatchingContinueViewModel(
            converter = converter,
            releaseInteractor = releaseInteractor,
            historyRepository = historyRepository,
            episodesCheckerHolder = episodesHolder,
            userViewsRepository = userViewsRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        continueVmFirst.setLoaderDispatcherForTests(deterministicDispatcher)

        continueVmFirst.onRefreshClick()
        advanceUntilIdle()
        assertTrue("Continue must contain remote item before clear", continueVmFirst.cardsData.value.isNotEmpty())

        episodesHolder.setEpisodes(emptyList())
        continueVmFirst.onRefreshClick()
        advanceUntilIdle()
        assertTrue("First VM should clear continue items after watch progress clear", continueVmFirst.cardsData.value.isEmpty())

        val continueVmSecond = WatchingContinueViewModel(
            converter = converter,
            releaseInteractor = releaseInteractor,
            historyRepository = historyRepository,
            episodesCheckerHolder = episodesHolder,
            userViewsRepository = userViewsRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )
        continueVmSecond.setLoaderDispatcherForTests(deterministicDispatcher)

        continueVmSecond.onRefreshClick()
        advanceUntilIdle()

        assertTrue("Continue should stay empty after watch progress clear even after VM recreate", continueVmSecond.cardsData.value.isEmpty())
    }

    @Test
    fun remoteContinueDescriptionUsesLocalEpisodeAndTimeWhenDifferent() = runBlocking {
        val release = release(id = 88)
        val localAccess = EpisodeAccess(
            id = EpisodeId("3", release.id),
            seek = 65_000,
            isViewed = true,
            lastAccess = 2_000L,
        )

        val episodesHolder = FakeEpisodesCheckerHolder(listOf(localAccess))

        val converter = mockk<CardsDataConverter>(relaxed = true)
        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } returns HistoryReleases(emptyList(), 0)
        every { historyRepository.observeReleases(any()) } returns MutableStateFlow(HistoryReleases(emptyList(), 0))

        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(release.id) } returns listOf(localAccess)

        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getViewsHistory(any(), any()) } answers {
            PaginatedResponse(
                data = listOf(
                    UserViewHistoryItem(
                        releaseId = release.id,
                        titleMain = release.names.first(),
                        titleEnglish = null,
                        titleAlternative = null,
                        posterPreview = null,
                        posterThumbnail = null,
                        episodeOrdinal = 1.0,
                        timeSeconds = 12.0,
                        isWatched = false,
                    )
                ),
                meta = PaginatedResponse.PaginationResponse(1, 1, 1, 1),
            )
        }

        val continueVm = WatchingContinueViewModel(
            converter = converter,
            releaseInteractor = releaseInteractor,
            historyRepository = historyRepository,
            episodesCheckerHolder = episodesHolder,
            userViewsRepository = userViewsRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        continueVm.onRefreshClick()
        waitUntil {
            continueVm.cardsData.value.isNotEmpty() &&
                continueVm.cardsData.value.none { it is LoadingCard }
        }

        val firstCard = continueVm.cardsData.value.first() as LibriaCard
        val description = firstCard.description

        assertTrue("Description should use local episode ordinal", description.contains("серии 3"))
        assertTrue("Description should use local playback time", description.contains("1:05"))
        assertFalse("Description should not use stale remote episode/time", description.contains("серии 1"))
    }

    @Test
    fun remoteContinueDescriptionResolvesUuidEpisodeIdToOrdinal() = runBlocking {
        val release = release(id = 89)
        val localAccess = EpisodeAccess(
            id = EpisodeId("9fa62e2e-f1aa-4e9d-a1f7-123456789abc", release.id),
            seek = 65_000,
            isViewed = true,
            lastAccess = 2_000L,
        )

        val episodesHolder = FakeEpisodesCheckerHolder(listOf(localAccess))

        val converter = mockk<CardsDataConverter>(relaxed = true)
        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } returns HistoryReleases(emptyList(), 0)
        every { historyRepository.observeReleases(any()) } returns MutableStateFlow(HistoryReleases(emptyList(), 0))

        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(release.id) } returns listOf(localAccess)

        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.resolveEpisodeOrdinal(localAccess.id) } returns "9"
        coEvery { userViewsRepository.getViewsHistory(any(), any()) } answers {
            PaginatedResponse(
                data = listOf(
                    UserViewHistoryItem(
                        releaseId = release.id,
                        titleMain = release.names.first(),
                        titleEnglish = null,
                        titleAlternative = null,
                        posterPreview = null,
                        posterThumbnail = null,
                        episodeOrdinal = 1.0,
                        timeSeconds = 12.0,
                        isWatched = false,
                    )
                ),
                meta = PaginatedResponse.PaginationResponse(1, 1, 1, 1),
            )
        }

        val continueVm = WatchingContinueViewModel(
            converter = converter,
            releaseInteractor = releaseInteractor,
            historyRepository = historyRepository,
            episodesCheckerHolder = episodesHolder,
            userViewsRepository = userViewsRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        continueVm.onRefreshClick()
        waitUntil {
            continueVm.cardsData.value.isNotEmpty() &&
                continueVm.cardsData.value.none { it is LoadingCard }
        }

        val firstCard = continueVm.cardsData.value.first() as LibriaCard
        val description = firstCard.description

        assertTrue("Description should use resolved ordinal from UUID", description.contains("серии 9"))
        assertTrue("Description should use local playback time", description.contains("1:05"))
        assertFalse("Description should not expose UUID as episode number", description.contains("9fa62e2e-f1aa"))
    }

    @Test
    fun localContinueDescriptionResolvesUuidEpisodeIdToOrdinal() = runBlocking {
        val release = release(id = 90)
        val localAccess = EpisodeAccess(
            id = EpisodeId("9fa62e2e-f1aa-4e9d-a1f7-123456789abc", release.id),
            seek = 65_000,
            isViewed = true,
            lastAccess = 2_000L,
        )

        val episodesHolder = FakeEpisodesCheckerHolder(listOf(localAccess))

        val converter = mockk<CardsDataConverter>(relaxed = true)
        every {
            converter.toCard(release)
        } returns LibriaCard(
            title = "Release 90",
            description = "",
            image = "",
            type = LibriaCard.Type.Release(release.id),
        )

        val historyRepository = mockk<HistoryRepository>()
        coEvery { historyRepository.getReleases(any()) } returns HistoryReleases(listOf(release), 1)
        every { historyRepository.observeReleases(any()) } returns MutableStateFlow(HistoryReleases(listOf(release), 1))

        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(release.id) } returns listOf(localAccess)

        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getViewsHistory(any(), any()) } throws RuntimeException("offline")
        coEvery { userViewsRepository.resolveEpisodeOrdinal(localAccess.id) } returns "9"

        val continueVm = WatchingContinueViewModel(
            converter = converter,
            releaseInteractor = releaseInteractor,
            historyRepository = historyRepository,
            episodesCheckerHolder = episodesHolder,
            userViewsRepository = userViewsRepository,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        continueVm.onRefreshClick()
        waitUntil {
            continueVm.cardsData.value.isNotEmpty() &&
                continueVm.cardsData.value.none { it is LoadingCard }
        }

        val firstCard = continueVm.cardsData.value.first() as LibriaCard
        val description = firstCard.description

        assertTrue("Local fallback should use resolved ordinal from UUID", description.contains("серии 9"))
        assertTrue("Local fallback should use playback time", description.contains("1:05"))
        assertFalse("Local fallback should not expose UUID as episode number", description.contains("9fa62e2e-f1aa"))
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) return
            delay(50)
        }
        error("Condition was not met in time")
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

    override suspend fun getEpisodes(releaseId: ReleaseId): List<EpisodeAccess> =
        state.value.filter { it.id.releaseId == releaseId }

    override suspend fun getEpisode(episodeId: EpisodeId): EpisodeAccess? =
        state.value.firstOrNull { it.id == episodeId }

    override suspend fun remove(releaseId: ReleaseId) {
        state.value = state.value.filterNot { it.id.releaseId == releaseId }
    }

    fun setEpisodes(episodes: List<EpisodeAccess>) {
        state.value = episodes
    }
}
