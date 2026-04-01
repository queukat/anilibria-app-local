package ru.radiationx.anilibria.screen.search

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.TvCollectionFilterLabels
import ru.radiationx.anilibria.common.TvCollectionFilterPickerKind
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.common.buildTvCollectionFiltersUiState
import ru.radiationx.anilibria.common.buildTvCollectionListLabel
import ru.radiationx.anilibria.common.selectedIndices
import ru.radiationx.anilibria.common.toTvCollectionCompletedLabel
import ru.radiationx.anilibria.common.toTvCollectionSortLabel
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class SearchFormViewModel @Inject constructor(
    private val searchController: SearchController,
    private val tvSearchUseCase: TvSearchUseCase,
) : LifecycleViewModel() {

    private val _yearData = MutableStateFlow<String?>(null)
    val yearData: StateFlow<String?> = _yearData.asStateFlow()
    private val _seasonData = MutableStateFlow<String?>(null)
    val seasonData: StateFlow<String?> = _seasonData.asStateFlow()
    private val _genreData = MutableStateFlow<String?>(null)
    val genreData: StateFlow<String?> = _genreData.asStateFlow()
    private val _sortData = MutableStateFlow<String?>(null)
    val sortData: StateFlow<String?> = _sortData.asStateFlow()
    private val _onlyCompletedData = MutableStateFlow<String?>(null)
    val onlyCompletedData: StateFlow<String?> = _onlyCompletedData.asStateFlow()

    private val _filtersUiState = MutableStateFlow(
        buildTvCollectionFiltersUiState(
            yearLabel = TvCollectionFilterLabels.ALL_YEARS,
            yearEmphasized = false,
            seasonLabel = TvCollectionFilterLabels.ALL_SEASONS,
            seasonEmphasized = false,
            genreLabel = TvCollectionFilterLabels.ALL_GENRES,
            genreEmphasized = false,
            sortLabel = SearchForm.Sort.RATING.toTvCollectionSortLabel(),
            sortEmphasized = false,
            onlyCompletedLabel = false.toTvCollectionCompletedLabel(),
            onlyCompletedEmphasized = false,
        )
    )
    internal val filtersUiState: StateFlow<TvCollectionFiltersUiState> = _filtersUiState.asStateFlow()

    private val _filterPicker = MutableStateFlow<TvCollectionFilterPickerState?>(null)
    internal val filterPicker: StateFlow<TvCollectionFilterPickerState?> = _filterPicker.asStateFlow()

    private var searchForm = SearchForm()
    private var availableYears: List<YearItem> = emptyList()
    private var availableSeasons: List<SeasonItem> = emptyList()
    private var availableGenres: List<GenreItem> = emptyList()

    init {
        updateDataByForm(emitApply = true)

        searchController.yearsEvent.onEach {
            searchForm = searchForm.copy(years = it)
            updateDataByForm()
            syncFilterPicker()
        }.launchIn(viewModelScope)

        searchController.seasonsEvent.onEach {
            searchForm = searchForm.copy(seasons = it)
            updateDataByForm()
            syncFilterPicker()
        }.launchIn(viewModelScope)

        searchController.genresEvent.onEach {
            searchForm = searchForm.copy(genres = it)
            updateDataByForm()
            syncFilterPicker()
        }.launchIn(viewModelScope)

        searchController.sortEvent.onEach {
            searchForm = searchForm.copy(sort = it)
            updateDataByForm()
            syncFilterPicker()
        }.launchIn(viewModelScope)

        searchController.completedEvent.onEach {
            searchForm = searchForm.copy(onlyCompleted = it)
            updateDataByForm()
            syncFilterPicker()
        }.launchIn(viewModelScope)

        tvSearchUseCase.observeYears().onEach { years ->
            availableYears = years
            syncFilterPicker()
        }.launchIn(viewModelScope)

        tvSearchUseCase.observeGenres().onEach { genres ->
            availableGenres = genres
            syncFilterPicker()
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
                syncFilterPicker()
            }.onFailure {
                Timber.e(it)
            }
        }
    }

    fun onYearClick() {
        val options = availableYears.map(YearItem::title)
        if (options.isEmpty()) return
        _filterPicker.value = TvCollectionFilterPickerState(
            kind = TvCollectionFilterPickerKind.YEAR,
            title = TvCollectionFilterLabels.YEARS_TITLE,
            options = options,
            selectedIndices = selectedIndices(
                availableYears.map(YearItem::value),
                searchForm.years.map(YearItem::value).toSet(),
            ),
            multiSelect = true,
        )
    }

    fun onSeasonClick() {
        val options = availableSeasons.map(SeasonItem::title)
        if (options.isEmpty()) return
        _filterPicker.value = TvCollectionFilterPickerState(
            kind = TvCollectionFilterPickerKind.SEASON,
            title = TvCollectionFilterLabels.SEASONS_TITLE,
            options = options,
            selectedIndices = selectedIndices(
                availableSeasons.map(SeasonItem::value),
                searchForm.seasons.map(SeasonItem::value).toSet(),
            ),
            multiSelect = true,
        )
    }

    fun onGenreClick() {
        val options = availableGenres.map(GenreItem::title)
        if (options.isEmpty()) return
        _filterPicker.value = TvCollectionFilterPickerState(
            kind = TvCollectionFilterPickerKind.GENRE,
            title = TvCollectionFilterLabels.GENRES_TITLE,
            options = options,
            selectedIndices = selectedIndices(
                availableGenres.map(GenreItem::value),
                searchForm.genres.map(GenreItem::value).toSet(),
            ),
            multiSelect = true,
        )
    }

    fun onSortClick() {
        _filterPicker.value = TvCollectionFilterPickerState(
            kind = TvCollectionFilterPickerKind.SORT,
            title = TvCollectionFilterLabels.SORT_TITLE,
            options = listOf(
                TvCollectionFilterLabels.SORT_POPULARITY,
                TvCollectionFilterLabels.SORT_DATE,
            ),
            selectedIndices = setOf(
                when (searchForm.sort) {
                    SearchForm.Sort.RATING -> 0
                    SearchForm.Sort.DATE -> 1
                }
            ),
            multiSelect = false,
        )
    }

    fun onOnlyCompletedClick() {
        _filterPicker.value = TvCollectionFilterPickerState(
            kind = TvCollectionFilterPickerKind.COMPLETED,
            title = TvCollectionFilterLabels.STATUS_TITLE,
            options = listOf(
                TvCollectionFilterLabels.ALL,
                TvCollectionFilterLabels.ONLY_COMPLETED,
            ),
            selectedIndices = setOf(if (searchForm.onlyCompleted) 1 else 0),
            multiSelect = false,
        )
    }

    fun togglePickerSelection(index: Int) {
        val current = _filterPicker.value ?: return
        if (!current.multiSelect || index !in current.options.indices) {
            return
        }
        val nextSelection = current.selectedIndices.toMutableSet().apply {
            if (!add(index)) {
                remove(index)
            }
        }
        _filterPicker.value = current.copy(selectedIndices = nextSelection)
    }

    fun selectSinglePicker(index: Int) {
        val current = _filterPicker.value
        val wasApplied = if (current != null && !current.multiSelect && index in current.options.indices) {
            when (current.kind) {
                TvCollectionFilterPickerKind.SORT -> {
                    resolveSortOption(index)?.let { sort ->
                        searchController.sortEvent.emit(sort)
                        true
                    } ?: false
                }

                TvCollectionFilterPickerKind.COMPLETED -> {
                    resolveCompletedOption(index)?.let { onlyCompleted ->
                        searchController.completedEvent.emit(onlyCompleted)
                        true
                    } ?: false
                }

                else -> false
            }
        } else {
            false
        }
        if (wasApplied) {
            dismissFilterPicker()
        }
    }

    fun applyFilterPicker() {
        val current = _filterPicker.value
        val wasApplied = current?.takeIf { it.multiSelect }?.let(::applyMultiSelectPickerSelection) == true
        if (current != null) {
            _filterPicker.value = null
        }
        if (wasApplied) return
    }

    private fun resolveSortOption(index: Int): SearchForm.Sort? {
        return when (index) {
            0 -> SearchForm.Sort.RATING
            1 -> SearchForm.Sort.DATE
            else -> null
        }
    }

    private fun resolveCompletedOption(index: Int): Boolean? {
        return when (index) {
            0 -> false
            1 -> true
            else -> null
        }
    }

    fun resetFilterPicker() {
        val current = _filterPicker.value ?: return
        if (!current.multiSelect) {
            return
        }
        _filterPicker.value = current.copy(selectedIndices = emptySet())
    }

    fun dismissFilterPicker() {
        currentMultiSelectPicker()?.let(::applyMultiSelectPickerSelection)
        _filterPicker.value = null
    }

    private fun syncFilterPicker() {
        val current = _filterPicker.value ?: return
        _filterPicker.value = when (current.kind) {
            TvCollectionFilterPickerKind.YEAR -> current.copy(
                options = availableYears.map(YearItem::title),
                selectedIndices = selectedIndices(
                    availableYears.map(YearItem::value),
                    searchForm.years.map(YearItem::value).toSet(),
                ),
            )

            TvCollectionFilterPickerKind.SEASON -> current.copy(
                options = availableSeasons.map(SeasonItem::title),
                selectedIndices = selectedIndices(
                    availableSeasons.map(SeasonItem::value),
                    searchForm.seasons.map(SeasonItem::value).toSet(),
                ),
            )

            TvCollectionFilterPickerKind.GENRE -> current.copy(
                options = availableGenres.map(GenreItem::title),
                selectedIndices = selectedIndices(
                    availableGenres.map(GenreItem::value),
                    searchForm.genres.map(GenreItem::value).toSet(),
                ),
            )

            TvCollectionFilterPickerKind.SORT -> current.copy(
                selectedIndices = setOf(
                    when (searchForm.sort) {
                        SearchForm.Sort.RATING -> 0
                        SearchForm.Sort.DATE -> 1
                    }
                ),
            )

            TvCollectionFilterPickerKind.COMPLETED -> current.copy(
                selectedIndices = setOf(if (searchForm.onlyCompleted) 1 else 0),
            )
        }
    }

    private fun currentMultiSelectPicker(): TvCollectionFilterPickerState? {
        return _filterPicker.value?.takeIf { it.multiSelect }
    }

    private fun applyMultiSelectPickerSelection(
        picker: TvCollectionFilterPickerState,
    ): Boolean {
        return when (picker.kind) {
            TvCollectionFilterPickerKind.YEAR -> {
                val nextSelection = picker.selectedIndices
                    .mapNotNull { availableYears.getOrNull(it) }
                    .toSet()
                if (nextSelection == searchForm.years.toSet()) {
                    false
                } else {
                    searchController.yearsEvent.emit(nextSelection)
                    true
                }
            }

            TvCollectionFilterPickerKind.SEASON -> {
                val nextSelection = picker.selectedIndices
                    .mapNotNull { availableSeasons.getOrNull(it) }
                    .toSet()
                if (nextSelection == searchForm.seasons.toSet()) {
                    false
                } else {
                    searchController.seasonsEvent.emit(nextSelection)
                    true
                }
            }

            TvCollectionFilterPickerKind.GENRE -> {
                val nextSelection = picker.selectedIndices
                    .mapNotNull { availableGenres.getOrNull(it) }
                    .toSet()
                if (nextSelection == searchForm.genres.toSet()) {
                    false
                } else {
                    searchController.genresEvent.emit(nextSelection)
                    true
                }
            }

            TvCollectionFilterPickerKind.SORT,
            TvCollectionFilterPickerKind.COMPLETED,
            -> false
        }
    }

    private fun updateDataByForm(emitApply: Boolean = true) {
        val yearLabel = searchForm.years
            .map(YearItem::title)
            .sortedDescending()
            .let { buildTvCollectionListLabel(it, TvCollectionFilterLabels.ALL_YEARS) }
        val seasonLabel = searchForm.seasons
            .map(SeasonItem::title)
            .let { buildTvCollectionListLabel(it, TvCollectionFilterLabels.ALL_SEASONS) }
        val genreLabel = searchForm.genres
            .map(GenreItem::title)
            .let { buildTvCollectionListLabel(it, TvCollectionFilterLabels.ALL_GENRES) }
        val sortLabel = searchForm.sort.toTvCollectionSortLabel()
        val onlyCompletedLabel = searchForm.onlyCompleted.toTvCollectionCompletedLabel()

        _yearData.value = yearLabel
        _seasonData.value = seasonLabel
        _genreData.value = genreLabel
        _sortData.value = sortLabel
        _onlyCompletedData.value = onlyCompletedLabel
        _filtersUiState.value = buildTvCollectionFiltersUiState(
            yearLabel = yearLabel,
            yearEmphasized = searchForm.years.isNotEmpty(),
            seasonLabel = seasonLabel,
            seasonEmphasized = searchForm.seasons.isNotEmpty(),
            genreLabel = genreLabel,
            genreEmphasized = searchForm.genres.isNotEmpty(),
            sortLabel = sortLabel,
            sortEmphasized = searchForm.sort != SearchForm.Sort.RATING,
            onlyCompletedLabel = onlyCompletedLabel,
            onlyCompletedEmphasized = searchForm.onlyCompleted,
        )

        if (emitApply) {
            searchController.applyFormEvent.emit(searchForm)
        }
    }
}
