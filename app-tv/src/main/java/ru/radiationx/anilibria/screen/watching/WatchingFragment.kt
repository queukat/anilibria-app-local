package ru.radiationx.anilibria.screen.watching

import android.os.Bundle
import android.view.View
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.fragment.BaseTvRowsSupportFragment
import ru.radiationx.anilibria.extension.createCardsRowBy
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel

class WatchingFragment : BaseTvRowsSupportFragment() {

    private val watchingViewModel by quillParentViewModel<WatchingViewModel>()

    private val historyViewModel by quillParentViewModel<WatchingHistoryViewModel>()
    private val continueViewModel by quillParentViewModel<WatchingContinueViewModel>()
    private val favoritesViewModel by quillParentViewModel<WatchingFavoritesViewModel>()
    private val recommendsViewModel by quillParentViewModel<WatchingRecommendsViewModel>()

    override fun getBaseCardsViewModel(rowId: Long): BaseCardsViewModel? = when (rowId) {
        WatchingViewModel.HISTORY_ROW_ID -> historyViewModel
        WatchingViewModel.CONTINUE_ROW_ID -> continueViewModel
//        WatchingViewModel.FAVORITES_ROW_ID -> favoritesViewModel
        WatchingViewModel.RECOMMENDS_ROW_ID -> recommendsViewModel
        else -> null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(watchingViewModel)
        viewLifecycleOwner.lifecycle.addObserver(historyViewModel)
        viewLifecycleOwner.lifecycle.addObserver(continueViewModel)
        viewLifecycleOwner.lifecycle.addObserver(favoritesViewModel)
        viewLifecycleOwner.lifecycle.addObserver(recommendsViewModel)
        subscribeTo(watchingViewModel.rowListData) { rowList ->
            submitRows(rowList) { rowId ->
                createCardsRowBy(rowId, rowsAdapter, getBaseCardsViewModel(rowId)!!)
            }
        }
    }
}
