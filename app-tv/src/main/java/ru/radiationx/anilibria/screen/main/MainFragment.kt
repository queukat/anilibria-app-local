package ru.radiationx.anilibria.screen.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.fragment.BaseTvRowsSupportFragment
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.extension.createCardsRowBy
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel

class MainFragment : BaseTvRowsSupportFragment() {

    companion object {
        private const val DESCRIPTION_TICK_MS = 60_000L
    }

    private val mainViewModel by quillParentViewModel<MainViewModel>()
    private val feedViewModel by quillParentViewModel<MainFeedViewModel>()
    private val scheduleViewModel by quillParentViewModel<MainScheduleViewModel>()
    private val favoritesViewModel by quillParentViewModel<MainFavoritesViewModel>()
    private val youtubeViewModel by quillParentViewModel<MainYouTubeViewModel>()

    private var selectedItem: Any? = null
    private var selectedRowViewHolder: CustomListRowViewHolder? = null
    private var descriptionTickerJob: Job? = null

    override fun getBaseCardsViewModel(rowId: Long): BaseCardsViewModel? = when (rowId) {
        MainViewModel.FEED_ROW_ID -> feedViewModel
        MainViewModel.SCHEDULE_ROW_ID -> scheduleViewModel
        MainViewModel.FAVORITE_ROW_ID -> favoritesViewModel
        MainViewModel.YOUTUBE_ROW_ID -> youtubeViewModel
        else -> null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(mainViewModel)
        viewLifecycleOwner.lifecycle.addObserver(feedViewModel)
        viewLifecycleOwner.lifecycle.addObserver(scheduleViewModel)
        viewLifecycleOwner.lifecycle.addObserver(favoritesViewModel)
        viewLifecycleOwner.lifecycle.addObserver(youtubeViewModel)
        subscribeTo(mainViewModel.rowListData) { rowList ->
            submitRows(rowList) { rowId ->
                createCardsRowBy(rowId, rowsAdapter, getBaseCardsViewModel(rowId)!!)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startDescriptionTicker()
        notifyReady()
    }

    override fun onPause() {
        descriptionTickerJob?.cancel()
        descriptionTickerJob = null
        super.onPause()
    }

    override fun onDestroyView() {
        selectedItem = null
        selectedRowViewHolder = null
        super.onDestroyView()
    }

    private fun startDescriptionTicker() {
        if (descriptionTickerJob?.isActive == true) return
        descriptionTickerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(DESCRIPTION_TICK_MS)
                applySelectedDescription()
            }
        }
    }

    private fun applySelectedDescription() {
        val rowViewHolder = selectedRowViewHolder ?: return
        val description = selectedItem.toTvCardDescription(::resolveLibriaSubtitle)
        rowViewHolder.setDescription(description.title, description.subtitle)
    }

    private fun notifyReady() {
        mainFragmentAdapter.fragmentHost.notifyDataReady(mainFragmentAdapter)
    }

    override fun resolveLibriaSubtitle(card: LibriaCard): CharSequence {
        return card.resolveDescription(requireContext())
    }

    override fun onCardDescriptionApplied(
        item: Any?,
        rowViewHolder: CustomListRowViewHolder,
        row: Row,
    ) {
        if (row is ListRow) {
            selectedItem = item
            selectedRowViewHolder = rowViewHolder
            applySelectedDescription()
        }
    }
}
