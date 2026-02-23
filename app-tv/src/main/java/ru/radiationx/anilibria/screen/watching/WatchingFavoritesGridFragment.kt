package ru.radiationx.anilibria.screen.watching

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResultListener
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
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

    private var needAuthToastShown: Boolean = false

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

        needAuthToastShown = false

        // ВАЖНО: после возврата из карточки BrowseSupportFragment иногда прячет title view,
        // поэтому явно возвращаем шапку (фильтры должны быть "прибиты" сверху).
        showTitleView(true)

        (titleView as? SearchTitleView)?.resetFiltersScroll()
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ВАЖНО: привязываем mainFragmentAdapter к host (BrowseSupportFragment),
        // чтобы showTitleView(...) работал стабильно.
        runCatching {
            mainFragmentAdapter.fragmentHost.notifyViewCreated(mainFragmentAdapter)
        }

        // И сразу фиксируем видимость шапки при первом показе.
        showTitleView(true)

        val fm = hostFm()

        fm.setFragmentResultListener(REQ_YEAR, viewLifecycleOwner) { _, b ->
            viewModel.onYearSelected(b.getInt(SingleChoiceGuidedStepFragment.RESULT_INDEX))
        }
        fm.setFragmentResultListener(REQ_SEASON, viewLifecycleOwner) { _, b ->
            viewModel.onSeasonSelected(b.getInt(SingleChoiceGuidedStepFragment.RESULT_INDEX))
        }
        fm.setFragmentResultListener(REQ_GENRE, viewLifecycleOwner) { _, b ->
            viewModel.onGenreSelected(b.getInt(SingleChoiceGuidedStepFragment.RESULT_INDEX))
        }

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        val tv = (titleView as? SearchTitleView)
        tv?.apply {
            setMode(SearchTitleView.Mode.FAVORITES)

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
        }

        subscribeTo(viewModel.cardsData) { list ->
            // UX: show a visible warning once when user opens Favorites without auth.
            val first = list.firstOrNull() as? LoadingCard
            val needAuth = first?.isError == true && first.title == "Нужно войти"

            if (needAuth && !needAuthToastShown) {
                needAuthToastShown = true
                Toast.makeText(
                    requireContext(),
                    "Избранное доступно после авторизации",
                    Toast.LENGTH_LONG
                ).show()
            }

            cardsAdapter.setItems(list, CardDiffCallback)
            setDescriptionVisible(list.any { it is LibriaCard })
        }

        // Диалоги выбора фильтров
        subscribeTo(viewModel.dialogRequests) { req ->
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

        setOnItemViewClickedListener(OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is LibriaCard -> viewModel.onLibriaCardClick(item)
                is LinkCard -> viewModel.onLinkCardClick()
                is LoadingCard -> viewModel.onLoadingCardClick()
            }
        })

        setOnItemViewSelectedListener { _, item, _, _ ->
            // Доп. страховка: при восстановлении selection после back удерживаем title view.
            showTitleView(true)

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
