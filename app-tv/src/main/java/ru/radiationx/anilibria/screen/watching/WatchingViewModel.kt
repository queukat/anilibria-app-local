package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import ru.radiationx.anilibria.common.BaseRowsViewModel
import ru.radiationx.data.contracts.tv.TvWatchingFacade
import javax.inject.Inject

class WatchingViewModel
    @Inject
    constructor(
        private val tvWatchingFacade: TvWatchingFacade,
    ) : BaseRowsViewModel() {
        companion object {
            const val HISTORY_ROW_ID = 1L
            const val CONTINUE_ROW_ID = 2L
            const val RECOMMENDS_ROW_ID = 4L
        }

        override val rowIds: List<Long> =
            listOf(
                CONTINUE_ROW_ID,
                HISTORY_ROW_ID,
                RECOMMENDS_ROW_ID,
            )

        override val availableRows: MutableSet<Long> =
            mutableSetOf(CONTINUE_ROW_ID, HISTORY_ROW_ID, RECOMMENDS_ROW_ID)

        init {
            combine(
                tvWatchingFacade.observeLocalContinueAvailable(),
                tvWatchingFacade.observeLocalHistoryAvailable(),
            ) { hasLocalContinue, hasLocalHistory ->
                updateAvailableRow(CONTINUE_ROW_ID, hasLocalContinue)
                updateAvailableRow(HISTORY_ROW_ID, hasLocalHistory)
            }.launchIn(viewModelScope)
        }

        fun onPageSelected() {
            tvWatchingFacade.requestBackgroundSync()
        }
    }
