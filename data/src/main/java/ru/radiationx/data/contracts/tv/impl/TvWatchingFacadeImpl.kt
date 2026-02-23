package ru.radiationx.data.contracts.tv.impl

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.radiationx.data.contracts.tv.TvWatchingFacade
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

class TvWatchingFacadeImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val historyRepository: HistoryRepository,
    private val episodesCheckerHolder: EpisodesCheckerHolder,
    private val userViewsRepository: UserViewsRepository,
) : TvWatchingFacade {

    override fun observeAuthState(): Flow<AuthState> = authRepository.observeAuthState()

    override fun observeLocalContinueAvailable(): Flow<Boolean> {
        return episodesCheckerHolder.observeEpisodes().map { it.isNotEmpty() }
    }

    override fun observeLocalHistoryAvailable(): Flow<Boolean> {
        return historyRepository.observeReleases().map { it.items.isNotEmpty() }
    }

    override suspend fun probeRemoteAvailability(limit: Int): TvWatchingFacade.RemoteAvailability {
        val response = runCatching {
            userViewsRepository.getViewsHistory(page = 1, limit = limit)
        }.getOrNull() ?: return TvWatchingFacade.RemoteAvailability(
            hasHistory = false,
            hasContinue = false,
        )

        return TvWatchingFacade.RemoteAvailability(
            hasHistory = response.data.isNotEmpty(),
            hasContinue = response.data.any { !it.isWatched },
        )
    }
}
