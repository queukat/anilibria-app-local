package ru.radiationx.anilibria.screen.schedule

import android.os.Bundle
import android.view.View
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.RowDiffCallback
import ru.radiationx.anilibria.common.fragment.BaseTvBrowseRowsFragment
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class ScheduleFragment : BaseTvBrowseRowsFragment() {

    private val viewModel by viewModel<ScheduleViewModel>()
    private val rowHolders = linkedMapOf<String, ScheduleRowHolder>()
    private var nextRowId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        title = "Расписание"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.scheduleRows) {
            val activeTitles = it.mapTo(mutableSetOf()) { day -> day.first }
            rowHolders.keys.retainAll(activeTitles)

            val rows = it.map { day ->
                val rowHolder = rowHolders.getOrPut(day.first) {
                    createScheduleRow(day.first)
                }
                rowHolder.cardsAdapter.setItems(day.second, CardDiffCallback)
                rowHolder.row
            }
            rowsAdapter.setItems(rows, RowDiffCallback)
        }
    }

    override fun onDestroyView() {
        rowHolders.clear()
        super.onDestroyView()
    }

    override fun onRowItemClicked(item: Any?) {
        when (item) {
            is LibriaCard -> viewModel.onCardClick(item)
            is LinkCard -> viewModel.onRetryClick()
            is LoadingCard -> if (item.isError) {
                viewModel.onRetryClick()
            }
        }
    }

    private fun createScheduleRow(title: String): ScheduleRowHolder {
        val cardsAdapter = ArrayObjectAdapter(CardPresenterSelector(null))
        val row = ListRow(nextRowId++, HeaderItem(title), cardsAdapter)
        return ScheduleRowHolder(row = row, cardsAdapter = cardsAdapter)
    }

    private data class ScheduleRowHolder(
        val row: ListRow,
        val cardsAdapter: ArrayObjectAdapter,
    )
}
