package ru.radiationx.anilibria.screen.search

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterController
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterOption
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterOptions
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterState
import ru.radiationx.anilibria.presentation.filters.TvCollectionSort
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class SearchFormViewModel
    @Inject
    constructor(
        private val tvSearchUseCase: TvSearchUseCase,
    ) : LifecycleViewModel() {
        private val filterController =
            TvCollectionFilterController(
                defaultSort = TvCollectionSort.POPULARITY,
            )

        internal val filtersUiState: StateFlow<TvCollectionFiltersUiState> = filterController.uiState
        internal val filterPicker: StateFlow<TvCollectionFilterPickerState?> = filterController.pickerState

        private val _searchFormData = MutableStateFlow(SearchForm())
        internal val searchFormData: StateFlow<SearchForm> = _searchFormData.asStateFlow()

        private var searchForm = SearchForm()
        private var availableYears: List<YearItem> = emptyList()
        private var availableSeasons: List<SeasonItem> = emptyList()
        private var availableGenres: List<GenreItem> = emptyList()

        init {
            filterController.updateState(searchForm.toTvCollectionFilterState())
            syncSearchFormFromController()

            tvSearchUseCase.observeYears()
                .onEach { years ->
                    availableYears = years
                    updateControllerOptions()
                }.launchIn(viewModelScope)

            tvSearchUseCase.observeGenres()
                .onEach { genres ->
                    availableGenres = genres
                    updateControllerOptions()
                }.launchIn(viewModelScope)

            viewModelScope.launch {
                coRunCatching {
                    tvSearchUseCase.loadYears()
                }.onFailure {
                    Timber.e(it)
                }
            }

            viewModelScope.launch {
                coRunCatching {
                    tvSearchUseCase.loadGenres()
                }.onFailure {
                    Timber.e(it)
                }
            }

            viewModelScope.launch {
                coRunCatching {
                    tvSearchUseCase.loadSeasons()
                }.onSuccess { seasons ->
                    availableSeasons = seasons
                    updateControllerOptions()
                }.onFailure {
                    Timber.e(it)
                }
            }
        }

        fun onYearClick() {
            filterController.openYearPicker()
        }

        fun onSeasonClick() {
            filterController.openSeasonPicker()
        }

        fun onGenreClick() {
            filterController.openGenrePicker()
        }

        fun onSortClick() {
            filterController.openSortPicker()
        }

        fun onOnlyCompletedClick() {
            filterController.openCompletedPicker()
        }

        fun togglePickerSelection(index: Int) {
            filterController.togglePickerSelection(index)
        }

        fun selectSinglePicker(index: Int) {
            if (filterController.selectSinglePicker(index)) {
                syncSearchFormFromController()
            }
        }

        fun applyFilterPicker() {
            if (filterController.applyFilterPicker()) {
                syncSearchFormFromController()
            }
        }

        fun resetFilterPicker() {
            filterController.resetFilterPicker()
        }

        fun dismissFilterPicker() {
            if (filterController.dismissFilterPicker()) {
                syncSearchFormFromController()
            }
        }

        private fun updateControllerOptions() {
            filterController.updateOptions(
                TvCollectionFilterOptions(
                    years = availableYears.map { TvCollectionFilterOption(value = it.value, label = it.title) },
                    seasons = availableSeasons.map { TvCollectionFilterOption(value = it.value, label = it.title) },
                    genres = availableGenres.map { TvCollectionFilterOption(value = it.value, label = it.title) },
                ),
            )
            syncSearchFormFromController()
        }

        private fun syncSearchFormFromController() {
            val filterState = filterController.state.value
            searchForm =
                SearchForm(
                    years = availableYears.filter { it.value in filterState.years }.toSet(),
                    seasons = availableSeasons.filter { it.value in filterState.seasons }.toSet(),
                    genres = availableGenres.filter { it.value in filterState.genres }.toSet(),
                    sort = filterState.sort.toSearchSort(),
                    onlyCompleted = filterState.onlyCompleted,
                )
            _searchFormData.value = searchForm
        }
    }

private fun SearchForm.toTvCollectionFilterState(): TvCollectionFilterState {
    return TvCollectionFilterState(
        years = years.map(YearItem::value).toSet(),
        seasons = seasons.map(SeasonItem::value).toSet(),
        genres = genres.map(GenreItem::value).toSet(),
        sort = sort.toTvCollectionSort(),
        onlyCompleted = onlyCompleted,
    )
}

private fun SearchForm.Sort.toTvCollectionSort(): TvCollectionSort {
    return when (this) {
        SearchForm.Sort.RATING -> TvCollectionSort.POPULARITY
        SearchForm.Sort.DATE -> TvCollectionSort.DATE
    }
}

private fun TvCollectionSort.toSearchSort(): SearchForm.Sort {
    return when (this) {
        TvCollectionSort.POPULARITY -> SearchForm.Sort.RATING
        TvCollectionSort.DATE -> SearchForm.Sort.DATE
    }
}
