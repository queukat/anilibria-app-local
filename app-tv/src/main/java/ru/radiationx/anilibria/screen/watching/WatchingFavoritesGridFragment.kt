// File: WatchingFavoritesGridFragment.kt
package ru.radiationx.anilibria.screen.watching

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.OnChildLaidOutListener
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.fragment.BaseVerticalGridFragment
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.anilibria.ui.widget.SearchTitleView
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel
import timber.log.Timber
import kotlin.math.roundToInt

class WatchingFavoritesGridFragment :
    BaseVerticalGridFragment(),
    BrowseSupportFragment.MainFragmentAdapterProvider {

    private val viewModel by quillParentViewModel<WatchingFavoritesViewModel>()

    private val cardsAdapter = ArrayObjectAdapter(
        CardPresenterSelector { viewModel.onLinkCardBind() }
    )

    private val mainFragmentAdapter =
        object : BrowseSupportFragment.MainFragmentAdapter<WatchingFavoritesGridFragment>(this) {}

    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> =
        mainFragmentAdapter

    private fun hostFm() = requireActivity().supportFragmentManager

    override fun onInflateTitleView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.lb_search_titleview, parent, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = viewModel.defaultTitle

        setGridPresenter(
            VerticalGridPresenter().apply { numberOfColumns = computeColumns() }
        )

        adapter = cardsAdapter
    }

    override fun onResume() {
        super.onResume()
        (titleView as? SearchTitleView)?.resetFiltersScroll()
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        runCatching {
            mainFragmentAdapter.fragmentHost.notifyViewCreated(mainFragmentAdapter)
        }

        val grid = browseGridView
        setupFocusBridge(grid)

        grid?.setOnChildLaidOutListener(OnChildLaidOutListener { _, _, position, _ ->
            if (position == 0) {
                updateTitleVisibility()
            }
        })

        hostFm().setFragmentResultListener(REQ_YEAR, viewLifecycleOwner) { _, b ->
            viewModel.onYearSelected(b.getInt(SingleChoiceGuidedStepFragment.RESULT_INDEX))
        }
        hostFm().setFragmentResultListener(REQ_SEASON, viewLifecycleOwner) { _, b ->
            viewModel.onSeasonSelected(b.getInt(SingleChoiceGuidedStepFragment.RESULT_INDEX))
        }
        hostFm().setFragmentResultListener(REQ_GENRE, viewLifecycleOwner) { _, b ->
            viewModel.onGenreSelected(b.getInt(SingleChoiceGuidedStepFragment.RESULT_INDEX))
        }

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        (titleView as? SearchTitleView)?.apply {
            setMode(SearchTitleView.Mode.FAVORITES)
//            (titleViewAdapter as? ru.radiationx.anilibria.ui.widget.BrowseTitleView.Adapter)?.apply {
//                setOther("Каталог")
//                setOnOtherClickedListener { viewModel.onLinkCardClick() }
//            }


            setYearClickListener { viewModel.onYearClick() }
            setSeasonClickListener { viewModel.onSeasonClick() }
            setGenreClickListener { viewModel.onGenreClick() }

            setSortClickListener { viewModel.onSortClick() }
            setOnlyCompletedClickListener { viewModel.onOnlyCompletedClick() }

            subscribeTo(viewModel.yearLabel) { year = it }
            subscribeTo(viewModel.seasonLabel) { season = it }
            subscribeTo(viewModel.genreLabel) { genre = it }
            subscribeTo(viewModel.sortLabel) { sort = it }
            subscribeTo(viewModel.onlyCompletedLabel) { onlyCompleted = it }

            setupTitleControlsNextFocusDown()
        }

        subscribeTo(viewModel.cardsData) { list ->
            cardsAdapter.setItems(list, CardDiffCallback)
            setDescriptionVisible(list.any { it is LibriaCard })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dialogRequests.collect { req ->
                when (req) {
                    is WatchingFavoritesViewModel.DialogRequest.ChooseYear -> {
                        showChoiceDialog(
                            requestKey = REQ_YEAR,
                            title = "Год",
                            options = req.options,
                            selectedIndex = req.selectedIndex
                        )
                    }

                    is WatchingFavoritesViewModel.DialogRequest.ChooseSeason -> {
                        showChoiceDialog(
                            requestKey = REQ_SEASON,
                            title = "Сезон",
                            options = req.options,
                            selectedIndex = req.selectedIndex
                        )
                    }

                    is WatchingFavoritesViewModel.DialogRequest.ChooseGenre -> {
                        showChoiceDialog(
                            requestKey = REQ_GENRE,
                            title = "Жанр",
                            options = req.options,
                            selectedIndex = req.selectedIndex
                        )
                    }
                }
            }
        }

        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is LibriaCard -> viewModel.onLibriaCardClick(item)
                is LinkCard -> viewModel.onLinkCardClick()
                is LoadingCard -> viewModel.onLoadingCardClick()
            }
        })

        setOnItemViewSelectedListener { _, item, _, _ ->
            updateTitleVisibility()

            Timber.d("selected = $item")
            when (item) {
                is LibriaCard -> {
                    setDescriptionVisible(true)
                    setDescription(item.title, item.description)
                }

                null -> {
                    // Keep the last description state.
                }

                else -> {
                    setDescriptionVisible(false)
                }
            }
        }
    }

    private fun setupFocusBridge(grid: BaseGridView?) {
        grid ?: return

        grid.nextFocusUpId = R.id.searchTitleYear



        grid.setOnKeyInterceptListener(object : BaseGridView.OnKeyInterceptListener {
            override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
                if (event.action != KeyEvent.ACTION_DOWN) return false
                if (event.keyCode != KeyEvent.KEYCODE_DPAD_UP) return false

                val pos = grid.selectedPosition
                if (pos < 0) return false

                val columns = computeColumns()
                val isTopRow = pos in 0 until columns
                if (!isTopRow) return false

                showTitleView(true)

                // не перехватываем, пусть система сделает фокус по nextFocusUpId
                return false
            }
        })

    }

    private fun focusTitleControls(): Boolean {
        val root = titleView ?: return false

        val ids = listOf(
            R.id.title_other,
            R.id.searchTitleYear,
            R.id.searchTitleSeason,
            R.id.searchTitleGenre,
            R.id.searchTitleSort,
            R.id.searchTitleComplete
        )

        for (id in ids) {
            val v = root.findViewById<View>(id)
            if (v != null && v.isShown && v.isFocusable) {
                if (v.requestFocus()) return true
            }
        }

        return root.requestFocus()
    }

    private fun setupTitleControlsNextFocusDown() {
        val root = titleView ?: return
        val downId = androidx.leanback.R.id.browse_grid

        val ids = listOf(
            R.id.title_other,
            R.id.searchTitleYear,
            R.id.searchTitleSeason,
            R.id.searchTitleGenre,
            R.id.searchTitleSort,
            R.id.searchTitleComplete
        )

        ids.forEach { id ->
            root.findViewById<View>(id)?.nextFocusDownId = downId
        }
    }

    private fun updateTitleVisibility() {
        val grid = browseGridView ?: return
        val pos = grid.selectedPosition
        if (pos < 0) return

        val show = !grid.hasPreviousViewInSameRow(pos)
        showTitleView(show)
    }

    private fun showTitleView(show: Boolean) {
        runCatching {
            mainFragmentAdapter.fragmentHost.showTitleView(show)
        }
    }

    private fun showChoiceDialog(
        requestKey: String,
        title: String,
        options: List<String>,
        selectedIndex: Int,
    ) {
        val fragment = SingleChoiceGuidedStepFragment.newInstance(
            title = title,
            requestKey = requestKey,
            options = options,
            selectedIndex = selectedIndex
        )
        GuidedStepSupportFragment.add(hostFm(), fragment)
    }

    private fun computeColumns(): Int {
        val metrics = requireContext().resources.displayMetrics
        val cardWidthPx = (180f * metrics.density).toInt()
        val raw = (metrics.widthPixels / cardWidthPx.toFloat()).roundToInt()
        return raw.coerceAtLeast(6)
    }

    private companion object {
        const val REQ_YEAR = "favorites_year"
        const val REQ_SEASON = "favorites_season"
        const val REQ_GENRE = "favorites_genre"
    }
}
