package ru.radiationx.anilibria.common.fragment

import android.os.Bundle
import android.view.View
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Row
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.RowDiffCallback
import ru.radiationx.anilibria.common.getOrPutRow
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder

abstract class BaseTvSearchRowsFragment :
    SearchSupportFragment(),
    SearchSupportFragment.SearchResultProvider {

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

        setSearchResultProvider(this)

        setOnItemViewSelectedListener { _, item, rowViewHolder, _ ->
            backgroundManager.applyCard(item)
            if (rowViewHolder is CustomListRowViewHolder) {
                val description = item.toTvCardDescription(::resolveLibriaSubtitle)
                rowViewHolder.setDescription(description.title, description.subtitle)
                onCardDescriptionApplied(item, rowViewHolder)
            }
        }

        setOnItemViewClickedListener { _, item, _, row ->
            val selectedRow = row as? Row ?: return@setOnItemViewClickedListener
            onRowItemClicked(item, selectedRow)
        }
    }

    override fun onDestroyView() {
        rowCache.clear()
        super.onDestroyView()
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    protected open fun resolveLibriaSubtitle(card: LibriaCard): CharSequence = card.description

    protected open fun onCardDescriptionApplied(
        item: Any?,
        rowViewHolder: CustomListRowViewHolder,
    ) {
        // No-op by default.
    }

    protected open fun onRowItemClicked(item: Any?, row: Row) {
        // No-op by default.
    }

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
