package ru.radiationx.anilibria.common.fragment

import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowPresenter
import ru.radiationx.anilibria.ui.presenter.cust.CustomListRowViewHolder

abstract class BaseTvBrowseRowsFragment : BrowseSupportFragment() {

    protected open fun createRowsAdapter(): ArrayObjectAdapter {
        return ArrayObjectAdapter(CustomListRowPresenter())
    }

    protected val rowsAdapter: ArrayObjectAdapter by lazy(LazyThreadSafetyMode.NONE) {
        createRowsAdapter()
    }

    protected val backgroundManager: GradientBackgroundManager by lazy(LazyThreadSafetyMode.NONE) {
        GradientBackgroundManager(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = rowsAdapter

        setOnItemViewSelectedListener { _, item, rowViewHolder, _ ->
            backgroundManager.applyCard(item)
            if (rowViewHolder is CustomListRowViewHolder) {
                val description = item.toTvCardDescription(::resolveLibriaSubtitle)
                rowViewHolder.setDescription(description.title, description.subtitle)
                onCardDescriptionApplied(item, rowViewHolder)
            }
        }

        setOnItemViewClickedListener { _, item, _, _ ->
            onRowItemClicked(item)
        }
    }

    protected open fun resolveLibriaSubtitle(card: LibriaCard): CharSequence = card.description

    protected open fun onCardDescriptionApplied(
        item: Any?,
        rowViewHolder: CustomListRowViewHolder,
    ) {
        // No-op by default.
    }

    protected open fun onRowItemClicked(item: Any?) {
        // No-op by default.
    }
}
