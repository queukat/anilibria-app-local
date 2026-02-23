package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.BaseRowsViewModel
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.contracts.tv.TvWatchingFacade
import javax.inject.Inject

class WatchingViewModel @Inject constructor(
    private val tvWatchingFacade: TvWatchingFacade,
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
        tvWatchingFacade
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
            tvWatchingFacade.observeLocalContinueAvailable(),
            tvWatchingFacade.observeLocalHistoryAvailable(),
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
            val availability = tvWatchingFacade.probeRemoteAvailability(limit = REMOTE_PROBE_LIMIT)
            remoteHistoryAvailable.value = availability.hasHistory
            remoteContinueAvailable.value = availability.hasContinue
        }
    }
}
