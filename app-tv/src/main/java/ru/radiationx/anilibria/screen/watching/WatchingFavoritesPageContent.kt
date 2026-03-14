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
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
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
        WatchingFavoritesViewModel.FiltersUiState(
            year = WatchingFavoritesViewModel.FilterChipState("Год: любой", emphasized = false),
            season = WatchingFavoritesViewModel.FilterChipState("Сезон: любой", emphasized = false),
            genre = WatchingFavoritesViewModel.FilterChipState("Жанр: любой", emphasized = false),
            sort = WatchingFavoritesViewModel.FilterChipState("По дате выхода", emphasized = false),
            onlyCompleted = WatchingFavoritesViewModel.FilterChipState("Все", emphasized = false),
        )
    )
    private var pickerState by mutableStateOf<WatchingFavoritesViewModel.FilterPickerState?>(null)
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
            if (picker != null) {
                pickerFocusRequestToken++
            } else if (previous != null) {
                restoreFilterIndex = filterIndexFor(previous.kind)
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
            focusRequestToken = focusRequestToken,
            visibilityRestoreToken = visibilityRestoreToken,
            restoreFilterIndex = restoreFilterIndex,
            restoreFilterToken = restoreFilterToken,
            pickerState = pickerState?.let {
                WatchingChoiceDialogUiState(
                    title = it.title,
                    options = it.options,
                    selectedIndex = it.selectedIndex,
                )
            },
            pickerFocusRequestToken = pickerFocusRequestToken,
            onYearClick = viewModel::onYearClick,
            onSeasonClick = viewModel::onSeasonClick,
            onGenreClick = viewModel::onGenreClick,
            onSortClick = viewModel::onSortClick,
            onOnlyCompletedClick = viewModel::onOnlyCompletedClick,
            onPickerDismiss = viewModel::dismissFilterPicker,
            onPickerOptionClick = ::handlePickerOptionClick,
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
        }
    }

    private fun handlePickerOptionClick(index: Int) {
        when (pickerState?.kind) {
            WatchingFavoritesViewModel.FilterPickerKind.YEAR -> viewModel.onYearSelected(index)
            WatchingFavoritesViewModel.FilterPickerKind.SEASON -> viewModel.onSeasonSelected(index)
            WatchingFavoritesViewModel.FilterPickerKind.GENRE -> viewModel.onGenreSelected(index)
            null -> Unit
        }
    }

    private fun filterIndexFor(kind: WatchingFavoritesViewModel.FilterPickerKind): Int {
        return when (kind) {
            WatchingFavoritesViewModel.FilterPickerKind.YEAR -> 0
            WatchingFavoritesViewModel.FilterPickerKind.SEASON -> 1
            WatchingFavoritesViewModel.FilterPickerKind.GENRE -> 2
        }
    }
}
