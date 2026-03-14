package ru.radiationx.anilibria.screen.search

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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

    enum class FilterPickerKind {
        YEAR,
        SEASON,
        GENRE,
        SORT,
        COMPLETED,
    }

    data class FilterPickerState(
        val kind: FilterPickerKind,
        val title: String,
        val options: List<String>,
        val selectedIndices: Set<Int>,
        val multiSelect: Boolean,
    )

    data class FilterChipState(
        val label: String,
        val emphasized: Boolean,
    )

    data class FiltersUiState(
        val year: FilterChipState,
        val season: FilterChipState,
        val genre: FilterChipState,
        val sort: FilterChipState,
        val onlyCompleted: FilterChipState,
    )

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
        FiltersUiState(
            year = FilterChipState("Все годы", emphasized = false),
            season = FilterChipState("Все сезоны", emphasized = false),
            genre = FilterChipState("Все жанры", emphasized = false),
            sort = FilterChipState("По популярности", emphasized = false),
            onlyCompleted = FilterChipState("Все", emphasized = false),
        )
    )
    val filtersUiState: StateFlow<FiltersUiState> = _filtersUiState.asStateFlow()

    private val _filterPicker = MutableStateFlow<FilterPickerState?>(null)
    val filterPicker: StateFlow<FilterPickerState?> = _filterPicker.asStateFlow()

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
        _filterPicker.value = FilterPickerState(
            kind = FilterPickerKind.YEAR,
            title = "Годы",
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
        _filterPicker.value = FilterPickerState(
            kind = FilterPickerKind.SEASON,
            title = "Сезоны",
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
        _filterPicker.value = FilterPickerState(
            kind = FilterPickerKind.GENRE,
            title = "Жанры",
            options = options,
            selectedIndices = selectedIndices(
                availableGenres.map(GenreItem::value),
                searchForm.genres.map(GenreItem::value).toSet(),
            ),
            multiSelect = true,
        )
    }

    fun onSortClick() {
        _filterPicker.value = FilterPickerState(
            kind = FilterPickerKind.SORT,
            title = "Сортировка",
            options = listOf("По популярности", "По новизне"),
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
        _filterPicker.value = FilterPickerState(
            kind = FilterPickerKind.COMPLETED,
            title = "Статус",
            options = listOf("Все", "Только завершенные"),
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
                FilterPickerKind.SORT -> {
                    resolveSortOption(index)?.let { sort ->
                        searchController.sortEvent.emit(sort)
                        true
                    } ?: false
                }

                FilterPickerKind.COMPLETED -> {
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
        val wasApplied = if (current != null && current.multiSelect) {
            when (current.kind) {
                FilterPickerKind.YEAR -> {
                    searchController.yearsEvent.emit(
                        current.selectedIndices
                            .mapNotNull { availableYears.getOrNull(it) }
                            .toSet()
                    )
                    true
                }

                FilterPickerKind.SEASON -> {
                    searchController.seasonsEvent.emit(
                        current.selectedIndices
                            .mapNotNull { availableSeasons.getOrNull(it) }
                            .toSet()
                    )
                    true
                }

                FilterPickerKind.GENRE -> {
                    searchController.genresEvent.emit(
                        current.selectedIndices
                            .mapNotNull { availableGenres.getOrNull(it) }
                            .toSet()
                    )
                    true
                }

                FilterPickerKind.SORT,
                FilterPickerKind.COMPLETED,
                -> false
            }
        } else {
            false
        }
        if (wasApplied) {
            dismissFilterPicker()
        }
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
        _filterPicker.value = null
    }

    private fun syncFilterPicker() {
        val current = _filterPicker.value ?: return
        _filterPicker.value = when (current.kind) {
            FilterPickerKind.YEAR -> current.copy(
                options = availableYears.map(YearItem::title),
                selectedIndices = selectedIndices(
                    availableYears.map(YearItem::value),
                    searchForm.years.map(YearItem::value).toSet(),
                ),
            )

            FilterPickerKind.SEASON -> current.copy(
                options = availableSeasons.map(SeasonItem::title),
                selectedIndices = selectedIndices(
                    availableSeasons.map(SeasonItem::value),
                    searchForm.seasons.map(SeasonItem::value).toSet(),
                ),
            )

            FilterPickerKind.GENRE -> current.copy(
                options = availableGenres.map(GenreItem::title),
                selectedIndices = selectedIndices(
                    availableGenres.map(GenreItem::value),
                    searchForm.genres.map(GenreItem::value).toSet(),
                ),
            )

            FilterPickerKind.SORT -> current.copy(
                selectedIndices = setOf(
                    when (searchForm.sort) {
                        SearchForm.Sort.RATING -> 0
                        SearchForm.Sort.DATE -> 1
                    }
                ),
            )

            FilterPickerKind.COMPLETED -> current.copy(
                selectedIndices = setOf(if (searchForm.onlyCompleted) 1 else 0),
            )
        }
    }

    private fun updateDataByForm(emitApply: Boolean = true) {
        val yearLabel = searchForm.years
            .map(YearItem::title)
            .sortedDescending()
            .generateListTitle("Все годы")
        val seasonLabel = searchForm.seasons
            .map(SeasonItem::title)
            .generateListTitle("Все сезоны")
        val genreLabel = searchForm.genres
            .map(GenreItem::title)
            .generateListTitle("Все жанры")
        val sortLabel = when (searchForm.sort) {
            SearchForm.Sort.RATING -> "По популярности"
            SearchForm.Sort.DATE -> "По новизне"
        }
        val onlyCompletedLabel = if (searchForm.onlyCompleted) {
            "Только завершенные"
        } else {
            "Все"
        }

        _yearData.value = yearLabel
        _seasonData.value = seasonLabel
        _genreData.value = genreLabel
        _sortData.value = sortLabel
        _onlyCompletedData.value = onlyCompletedLabel
        _filtersUiState.value = FiltersUiState(
            year = FilterChipState(yearLabel, emphasized = searchForm.years.isNotEmpty()),
            season = FilterChipState(seasonLabel, emphasized = searchForm.seasons.isNotEmpty()),
            genre = FilterChipState(genreLabel, emphasized = searchForm.genres.isNotEmpty()),
            sort = FilterChipState(
                sortLabel,
                emphasized = searchForm.sort != SearchForm.Sort.RATING,
            ),
            onlyCompleted = FilterChipState(
                onlyCompletedLabel,
                emphasized = searchForm.onlyCompleted,
            ),
        )

        if (emitApply) {
            searchController.applyFormEvent.emit(searchForm)
        }
    }

    private fun selectedIndices(
        allValues: List<String>,
        selectedValues: Set<String>,
    ): Set<Int> {
        return allValues.mapIndexedNotNull { index, value ->
            index.takeIf { value in selectedValues }
        }.toSet()
    }

    private fun List<String>.generateListTitle(fallback: String, take: Int = 2): String {
        if (isEmpty()) {
            return fallback
        }
        var result = take(take).joinToString()
        if (size > take) {
            result += "… +${size - take}"
        }
        return result
    }
}
