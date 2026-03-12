package ru.radiationx.anilibria.screen.watching

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.mainpages.MainShellContentFragment
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel

class WatchingFavoritesGridFragment : Fragment(), MainShellContentFragment {

    private val viewModel by quillParentViewModel<WatchingFavoritesViewModel>()
    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

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
    private var focusRequestToken by mutableIntStateOf(1)
    private var pickerFocusRequestToken by mutableIntStateOf(0)
    private var restoreFilterIndex by mutableIntStateOf(0)
    private var restoreFilterToken by mutableIntStateOf(0)
    override var onRequestRailFocus: (() -> Boolean)? = null
    override var onContentMovedDown: (() -> Unit)? = null
    override var onContentMovedUp: (() -> Unit)? = null
    override var onRequestHeaderFocus: (() -> Boolean)? = null

    private var pickerBackCallback: OnBackPressedCallback? = null

    override fun requestContentFocus(): Boolean {
        val rootView = view ?: return false
        focusRequestToken++
        rootView.requestFocus()
        return true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focusRequestToken++
                }
            }
            setContent {
                WatchingFavoritesScreen(
                    cards = cardsState,
                    filters = filtersState,
                    focusRequestToken = focusRequestToken,
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
                    onRequestRailFocus = { onRequestRailFocus?.invoke() == true },
                    onRequestHeaderFocus = { onRequestHeaderFocus?.invoke() == true },
                    onContentMovedDown = { onContentMovedDown?.invoke() },
                    onContentMovedUp = { onContentMovedUp?.invoke() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        focusRequestToken++
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundManager.clearGradient()

        pickerBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                viewModel.dismissFilterPicker()
            }
        }.also {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
        }

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.filtersUiState) { filtersState = it }
        subscribeTo(viewModel.cardsData) { list ->
            cardsState = list
        }
        subscribeTo(viewModel.filterPicker) { picker ->
            val previous = pickerState
            pickerState = picker
            pickerBackCallback?.isEnabled = picker != null
            if (picker != null) {
                pickerFocusRequestToken++
            } else if (previous != null) {
                restoreFilterIndex = filterIndexFor(previous.kind)
                restoreFilterToken++
            }
        }
    }

    override fun onDestroyView() {
        pickerBackCallback?.remove()
        pickerBackCallback = null
        super.onDestroyView()
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
