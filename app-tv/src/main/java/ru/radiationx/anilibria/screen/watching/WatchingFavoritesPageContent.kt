package ru.radiationx.anilibria.screen.watching

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.TvCollectionFilterChipState
import ru.radiationx.anilibria.common.TvCollectionFilterLabels
import ru.radiationx.anilibria.common.TvCollectionFilterPickerKind
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.common.shouldRequestTvCollectionPickerFocus
import ru.radiationx.anilibria.common.tvCollectionFilterIndex
import ru.radiationx.anilibria.screen.mainpages.MainShellCallbacks
import ru.radiationx.anilibria.screen.mainpages.MainShellPageContent
import ru.radiationx.anilibria.screen.mainpages.collectStarted
import ru.radiationx.quill.getViewModel

internal class WatchingFavoritesPageContent(
    private val fragment: Fragment,
    private val backgroundManager: GradientBackgroundManager,
) : MainShellPageContent {

    private val viewModel = fragment.getViewModel(WatchingFavoritesViewModel::class)

    private var cardsState by mutableStateOf<List<CardItem>>(emptyList())
    private var filtersState by mutableStateOf(
        TvCollectionFiltersUiState(
            year = TvCollectionFilterChipState(TvCollectionFilterLabels.ALL_YEARS, emphasized = false),
            season = TvCollectionFilterChipState(TvCollectionFilterLabels.ALL_SEASONS, emphasized = false),
            genre = TvCollectionFilterChipState(TvCollectionFilterLabels.ALL_GENRES, emphasized = false),
            sort = TvCollectionFilterChipState(TvCollectionFilterLabels.SORT_DATE, emphasized = false),
            onlyCompleted = TvCollectionFilterChipState(TvCollectionFilterLabels.ALL, emphasized = false),
        )
    )
    private var pickerState by mutableStateOf<TvCollectionFilterPickerState?>(null)
    private var focusRequestToken by mutableIntStateOf(0)
    private var visibilityRestoreToken by mutableIntStateOf(0)
    private var pickerFocusRequestToken by mutableIntStateOf(0)
    private var restoreFilterIndex by mutableIntStateOf(0)
    private var restoreFilterToken by mutableIntStateOf(0)

    override fun bind(owner: LifecycleOwner) {
        backgroundManager.clearGradient()

        owner.lifecycle.addObserver(viewModel)
        owner.collectStarted(viewModel.filtersUiState) { filtersState = it }
        owner.collectStarted(viewModel.cardsData) { cardsState = it }
        owner.collectStarted(viewModel.filterPicker) { picker ->
            val previous = pickerState
            pickerState = picker
            if (picker != null && shouldRequestTvCollectionPickerFocus(previous, picker)) {
                pickerFocusRequestToken++
            } else if (previous != null) {
                restoreFilterIndex = tvCollectionFilterIndex(previous.kind)
                restoreFilterToken++
            }
        }
    }

    override fun onSelected() {
        visibilityRestoreToken++
        backgroundManager.clearGradient()
    }

    override fun onBackPressed(): Boolean {
        if (pickerState == null) {
            return false
        }
        viewModel.dismissFilterPicker()
        return true
    }

    override fun requestContentFocus(): Boolean {
        if (pickerState != null) {
            pickerFocusRequestToken++
            return true
        }
        focusRequestToken++
        return true
    }

    @Composable
    override fun Render(callbacks: MainShellCallbacks) {
        WatchingFavoritesScreen(
            cards = cardsState,
            filters = filtersState,
            contentInteractionsEnabled = callbacks.contentInteractionsEnabled,
            focusRequestToken = focusRequestToken,
            visibilityRestoreToken = visibilityRestoreToken,
            restoreFilterIndex = restoreFilterIndex,
            restoreFilterToken = restoreFilterToken,
            pickerState = pickerState,
            pickerFocusRequestToken = pickerFocusRequestToken,
            onYearClick = viewModel::onYearClick,
            onSeasonClick = viewModel::onSeasonClick,
            onGenreClick = viewModel::onGenreClick,
            onSortClick = viewModel::onSortClick,
            onOnlyCompletedClick = viewModel::onOnlyCompletedClick,
            onPickerToggleOption = viewModel::togglePickerSelection,
            onPickerSingleSelect = viewModel::selectSinglePicker,
            onPickerApply = viewModel::applyFilterPicker,
            onPickerReset = viewModel::resetFilterPicker,
            onPickerDismiss = viewModel::dismissFilterPicker,
            onItemClick = ::handleItemClick,
            onRequestRailFocus = callbacks.onRequestRailFocus,
            onRequestHeaderFocus = callbacks.onRequestHeaderFocus,
            onContentMovedDown = callbacks.onContentMovedDown,
            onContentMovedUp = callbacks.onContentMovedUp,
        )
    }

    private fun handleItemClick(item: CardItem) {
        when (item) {
            is LibriaCard -> viewModel.onLibriaCardClick(item)
            is LinkCard -> viewModel.onLinkCardClick()
            is LoadingCard -> viewModel.onLoadingCardClick()
            is InfoCard -> Unit
        }
    }

}
