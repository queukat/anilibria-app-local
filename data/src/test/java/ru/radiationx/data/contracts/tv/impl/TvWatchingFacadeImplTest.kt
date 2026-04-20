package ru.radiationx.data.contracts.tv.impl

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.HistoryReleases
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.UserViewsSyncInteractor
import ru.radiationx.data.repository.HistoryRepository

class TvWatchingFacadeImplTest {

    @Test
    fun observeLocalContinueAvailable_reflectsEpisodePresence() = runBlocking {
        val episodesFlow = MutableStateFlow(listOf(
            EpisodeAccess(
                id = EpisodeId("1", ReleaseId(1)),
                seek = 10_000L,
                isViewed = true,
                lastAccess = 100L,
            ),
        ))
        val episodesCheckerHolder = mockk<EpisodesCheckerHolder>()
        every { episodesCheckerHolder.observeEpisodes() } returns episodesFlow

        val facade = TvWatchingFacadeImpl(
            historyRepository = mockk<HistoryRepository>(relaxed = true),
            episodesCheckerHolder = episodesCheckerHolder,
            userViewsSyncInteractor = mockk<UserViewsSyncInteractor>(relaxed = true),
        )

        assertTrue(facade.observeLocalContinueAvailable().first())
    }

    @Test
    fun observeLocalHistoryAvailable_reflectsHistoryPresence() = runBlocking {
        val historyFlow = MutableStateFlow(HistoryReleases(emptyList(), 0))
        val historyRepository = mockk<HistoryRepository>()
        every { historyRepository.observeReleases() } returns historyFlow

        val facade = TvWatchingFacadeImpl(
            historyRepository = historyRepository,
            episodesCheckerHolder = mockk<EpisodesCheckerHolder>(relaxed = true),
            userViewsSyncInteractor = mockk<UserViewsSyncInteractor>(relaxed = true),
        )

        assertFalse(facade.observeLocalHistoryAvailable().first())
    }

    @Test
    fun requestBackgroundSync_delegatesToInteractor() {
        val interactor = mockk<UserViewsSyncInteractor>(relaxed = true)

        val facade = TvWatchingFacadeImpl(
            historyRepository = mockk<HistoryRepository>(relaxed = true),
            episodesCheckerHolder = mockk<EpisodesCheckerHolder>(relaxed = true),
            userViewsSyncInteractor = interactor,
        )

        facade.requestBackgroundSync()

        verify(exactly = 1) {
            interactor.scheduleSyncIfNeeded(reason = "watching_page_selected")
        }
    }
}
