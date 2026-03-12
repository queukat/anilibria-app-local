package ru.radiationx.anilibria.screen.search

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
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.quill.viewModel

class SearchFragment : Fragment() {

    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }
    private val cardsViewModel by viewModel<SearchViewModel>()
    private val formViewModel by viewModel<SearchFormViewModel>()

    private var cardsState by mutableStateOf<List<CardItem>>(emptyList())
    private var filtersState by mutableStateOf(
        SearchFormViewModel.FiltersUiState(
            year = SearchFormViewModel.FilterChipState("Все годы", emphasized = false),
            season = SearchFormViewModel.FilterChipState("Все сезоны", emphasized = false),
            genre = SearchFormViewModel.FilterChipState("Все жанры", emphasized = false),
            sort = SearchFormViewModel.FilterChipState("По популярности", emphasized = false),
            onlyCompleted = SearchFormViewModel.FilterChipState("Все", emphasized = false),
        )
    )
    private var progressState by mutableStateOf(false)
    private var pickerState by mutableStateOf<SearchFormViewModel.FilterPickerState?>(null)
    private var focusRequestToken by mutableIntStateOf(1)
    private var pickerFocusRequestToken by mutableIntStateOf(0)
    private var restoreFilterIndex by mutableIntStateOf(0)
    private var restoreFilterToken by mutableIntStateOf(0)

    private var pickerBackCallback: OnBackPressedCallback? = null

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
                CatalogScreen(
                    cards = cardsState.ifEmpty {
                        if (progressState) {
                            emptyList()
                        } else {
                            listOf(
                                InfoCard(
                                    title = "Ничего не найдено",
                                    subtitle = "По данным параметрам ничего не найдено",
                                )
                            )
                        }
                    },
                    filters = filtersState,
                    progressVisible = progressState,
                    pickerState = pickerState,
                    focusRequestToken = focusRequestToken,
                    pickerFocusRequestToken = pickerFocusRequestToken,
                    restoreFilterIndex = restoreFilterIndex,
                    restoreFilterToken = restoreFilterToken,
                    onSearchClick = cardsViewModel::onSearchClick,
                    onYearClick = formViewModel::onYearClick,
                    onSeasonClick = formViewModel::onSeasonClick,
                    onGenreClick = formViewModel::onGenreClick,
                    onSortClick = formViewModel::onSortClick,
                    onOnlyCompletedClick = formViewModel::onOnlyCompletedClick,
                    onPickerToggleOption = formViewModel::togglePickerSelection,
                    onPickerSingleSelect = formViewModel::selectSinglePicker,
                    onPickerApply = formViewModel::applyFilterPicker,
                    onPickerReset = formViewModel::resetFilterPicker,
                    onPickerDismiss = formViewModel::dismissFilterPicker,
                    onItemClick = ::handleItemClick,
                    onItemFocused = { item ->
                        backgroundManager.applyCard(item)
                    },
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
                formViewModel.dismissFilterPicker()
            }
        }.also {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
        }

        viewLifecycleOwner.lifecycle.addObserver(cardsViewModel)
        viewLifecycleOwner.lifecycle.addObserver(formViewModel)

        subscribeTo(cardsViewModel.progressState) {
            progressState = it
        }

        subscribeTo(cardsViewModel.cardsData) {
            cardsState = it
        }

        subscribeTo(formViewModel.filtersUiState) {
            filtersState = it
        }

        subscribeTo(formViewModel.filterPicker) { picker ->
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
        backgroundManager.clearGradient()
        super.onDestroyView()
    }

    private fun handleItemClick(item: CardItem) {
        when (item) {
            is LibriaCard -> cardsViewModel.onLibriaCardClick(item)
            is LinkCard -> cardsViewModel.onLinkCardClick()
            is LoadingCard -> cardsViewModel.onLoadingCardClick()
            is InfoCard -> Unit
        }
    }

    private fun filterIndexFor(kind: SearchFormViewModel.FilterPickerKind): Int {
        return when (kind) {
            SearchFormViewModel.FilterPickerKind.YEAR -> 0
            SearchFormViewModel.FilterPickerKind.SEASON -> 1
            SearchFormViewModel.FilterPickerKind.GENRE -> 2
            SearchFormViewModel.FilterPickerKind.SORT -> 3
            SearchFormViewModel.FilterPickerKind.COMPLETED -> 4
        }
    }
}
