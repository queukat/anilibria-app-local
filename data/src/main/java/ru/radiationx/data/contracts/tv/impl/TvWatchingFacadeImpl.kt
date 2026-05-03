package ru.radiationx.data.contracts.tv.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.radiationx.data.contracts.tv.TvWatchingFacade
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.interactors.UserViewsSyncInteractor
import ru.radiationx.data.repository.HistoryRepository
import javax.inject.Inject

class TvWatchingFacadeImpl
    @Inject
    constructor(
        private val historyRepository: HistoryRepository,
        private val episodesCheckerHolder: EpisodesCheckerHolder,
        private val userViewsSyncInteractor: UserViewsSyncInteractor,
    ) : TvWatchingFacade {
        override fun observeLocalContinueAvailable(): Flow<Boolean> {
            return episodesCheckerHolder.observeEpisodes().map { it.isNotEmpty() }
        }

        override fun observeLocalHistoryAvailable(): Flow<Boolean> {
            return historyRepository.observeReleases().map { it.items.isNotEmpty() }
        }

        override fun requestBackgroundSync() {
            // Watching screen is a good wake-up point on TVs where the process often sleeps
            // instead of being fully restarted. The sync remains fully async and non-blocking.
            userViewsSyncInteractor.scheduleSyncIfNeeded(reason = "watching_page_selected")
        }
    }
