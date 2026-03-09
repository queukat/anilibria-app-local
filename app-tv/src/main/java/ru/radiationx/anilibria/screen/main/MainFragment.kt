package ru.radiationx.anilibria.screen.main

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.handleTvCardClick
import ru.radiationx.anilibria.common.getOrPutRow
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.RowDiffCallback
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.extension.createCardsRowBy
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel

class MainFragment : RowsSupportFragment() {

    companion object {
        private const val DESCRIPTION_TICK_MS = 60_000L
    }

    private val rowsPresenter by lazy { CustomListRowPresenter() }
    private val rowsAdapter by lazy { ArrayObjectAdapter(rowsPresenter) }

    // РАНЬШЕ БЫЛО: private val backgroundManager by inject<GradientBackgroundManager>()
    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

    private val mainViewModel by quillParentViewModel<MainViewModel>()
    private val feedViewModel by quillParentViewModel<MainFeedViewModel>()
    private val scheduleViewModel by quillParentViewModel<MainScheduleViewModel>()
    private val favoritesViewModel by quillParentViewModel<MainFavoritesViewModel>()
    private val youtubeViewModel by quillParentViewModel<MainYouTubeViewModel>()

    private var selectedItem: Any? = null
    private var selectedRowViewHolder: CustomListRowViewHolder? = null
    private var descriptionTickerJob: Job? = null

    private fun getViewModel(rowId: Long): BaseCardsViewModel? = when (rowId) {
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

        adapter = rowsAdapter
        onItemViewSelectedListener = ItemViewSelectedListener()

        setOnItemViewClickedListener { _, item, _, row ->
            getViewModel((row as ListRow).id).handleTvCardClick(item)
        }

        val rowMap = mutableMapOf<Long, ListRow>()
        subscribeTo(mainViewModel.rowListData) { rowList ->
            val rows = rowList.map { rowId ->
                rowMap.getOrPutRow(rowId) {
                    createCardsRowBy(it, rowsAdapter, getViewModel(it)!!)
                }
            }
            rowsAdapter.setItems(rows, RowDiffCallback)
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
        val description = selectedItem.toTvCardDescription {
            it.resolveDescription(requireContext())
        }
        rowViewHolder.setDescription(description.title, description.subtitle)
    }

    private fun notifyReady() {
        mainFragmentAdapter.fragmentHost.notifyDataReady(mainFragmentAdapter)
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row,
        ) {
            if (rowViewHolder is CustomListRowViewHolder) {
                backgroundManager.applyCard(item)
                selectedItem = item
                selectedRowViewHolder = rowViewHolder
                applySelectedDescription()
            }
        }
    }
}
