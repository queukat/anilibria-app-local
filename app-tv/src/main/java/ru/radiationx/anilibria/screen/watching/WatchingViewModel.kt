package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.BaseRowsViewModel
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

class WatchingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val historyRepository: HistoryRepository,
    private val episodesCheckerHolder: EpisodesCheckerHolder,
    private val userViewsRepository: UserViewsRepository,
) : BaseRowsViewModel() {

    companion object {
        const val HISTORY_ROW_ID = 1L
        const val CONTINUE_ROW_ID = 2L

        //        const val FAVORITES_ROW_ID = 3L
        const val RECOMMENDS_ROW_ID = 4L

        private const val REMOTE_PROBE_LIMIT = 25
    }

    override val rowIds: List<Long> =
        listOf(
            CONTINUE_ROW_ID,
            HISTORY_ROW_ID,
//            FAVORITES_ROW_ID,
            RECOMMENDS_ROW_ID
        )

    override val availableRows: MutableSet<Long> =
        mutableSetOf(CONTINUE_ROW_ID, HISTORY_ROW_ID, RECOMMENDS_ROW_ID)

    private val remoteHistoryAvailable = MutableStateFlow(false)
    private val remoteContinueAvailable = MutableStateFlow(false)

    init {
        // При авторизации пробуем понять, есть ли remote-история/продолжение, чтобы не скрывать строки.
        authRepository
            .observeAuthState()
            .distinctUntilChanged()
            .onEach { state ->
                if (state == AuthState.AUTH) {
                    probeRemoteAvailability()
                } else {
                    remoteHistoryAvailable.value = false
                    remoteContinueAvailable.value = false
                }
            }
            .launchIn(viewModelScope)

        combine(
            episodesCheckerHolder.observeEpisodes().map { it.isNotEmpty() },
            historyRepository.observeReleases().map { it.items.isNotEmpty() },
            remoteContinueAvailable,
            remoteHistoryAvailable,
        ) { hasLocalContinue, hasLocalHistory, hasRemoteContinue, hasRemoteHistory ->
            updateAvailableRow(CONTINUE_ROW_ID, hasLocalContinue || hasRemoteContinue)
            updateAvailableRow(HISTORY_ROW_ID, hasLocalHistory || hasRemoteHistory)
//            updateAvailableRow(FAVORITES_ROW_ID, hasAuth)
        }.launchIn(viewModelScope)
    }

    private fun probeRemoteAvailability() {
        viewModelScope.launch {
            val response = runCatching {
                userViewsRepository.getViewsHistory(
                    page = 1,
                    limit = REMOTE_PROBE_LIMIT,
                )
            }.getOrNull()

            if (response == null) {
                remoteHistoryAvailable.value = false
                remoteContinueAvailable.value = false
                return@launch
            }

            // «История» — любая запись, где есть релиз.
            remoteHistoryAvailable.value = response.data.isNotEmpty()

            // «Продолжить» — не досмотрено до конца.
            remoteContinueAvailable.value = response.data.any { !it.isWatched }
        }
    }
}
