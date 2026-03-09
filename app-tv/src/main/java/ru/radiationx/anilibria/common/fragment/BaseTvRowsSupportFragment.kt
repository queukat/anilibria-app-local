package ru.radiationx.anilibria.common.fragment

import android.os.Bundle
import android.view.View
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.RowDiffCallback
import ru.radiationx.anilibria.common.getOrPutRow
import ru.radiationx.anilibria.common.handleTvCardClick
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder

abstract class BaseTvRowsSupportFragment : RowsSupportFragment() {

    protected open fun createRowsAdapter(): ArrayObjectAdapter {
        return ArrayObjectAdapter(CustomListRowPresenter())
    }

    protected val rowsAdapter: ArrayObjectAdapter by lazy(LazyThreadSafetyMode.NONE) {
        createRowsAdapter()
    }

    protected val backgroundManager: GradientBackgroundManager by lazy(LazyThreadSafetyMode.NONE) {
        GradientBackgroundManager(requireActivity())
    }

    private val rowCache = mutableMapOf<Long, Row>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = rowsAdapter

        setOnItemViewClickedListener { _, item, _, row ->
            val selectedRow = row as? Row ?: return@setOnItemViewClickedListener
            onRowItemClicked(item, selectedRow)
        }

        setOnItemViewSelectedListener { _, item, rowViewHolder, row ->
            val selectedRow = row as? Row ?: return@setOnItemViewSelectedListener
            val selectedRowViewHolder = rowViewHolder ?: return@setOnItemViewSelectedListener
            onRowItemSelected(item, selectedRowViewHolder, selectedRow)
        }
    }

    override fun onDestroyView() {
        rowCache.clear()
        super.onDestroyView()
    }

    protected open fun getBaseCardsViewModel(rowId: Long): BaseCardsViewModel? = null

    protected open fun onRowItemClicked(item: Any?, row: Row) {
        getBaseCardsViewModel(row.id).handleTvCardClick(item)
    }

    protected open fun onRowItemSelected(
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder,
        row: Row,
    ) {
        if (row is ListRow) {
            backgroundManager.applyCard(item)
        } else {
            onNonListRowSelected(item, row)
        }

        if (rowViewHolder is CustomListRowViewHolder) {
            val description = item.toTvCardDescription(::resolveLibriaSubtitle)
            rowViewHolder.setDescription(description.title, description.subtitle)
            onCardDescriptionApplied(item, rowViewHolder, row)
        }
    }

    protected open fun onNonListRowSelected(item: Any?, row: Row) {
        // No-op by default.
    }

    protected open fun onCardDescriptionApplied(
        item: Any?,
        rowViewHolder: CustomListRowViewHolder,
        row: Row,
    ) {
        // No-op by default.
    }

    protected open fun resolveLibriaSubtitle(card: LibriaCard): CharSequence = card.description

    protected fun submitRows(
        rowIds: List<Long>,
        createRow: (Long) -> Row,
    ) {
        val rows = rowIds.map { rowId ->
            rowCache.getOrPutRow(rowId, createRow)
        }
        rowsAdapter.setItems(rows, RowDiffCallback)
    }
}
