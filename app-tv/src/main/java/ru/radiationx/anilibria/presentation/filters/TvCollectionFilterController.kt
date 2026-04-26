package ru.radiationx.anilibria.presentation.filters

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.radiationx.anilibria.common.TvCollectionFilterLabels
import ru.radiationx.anilibria.common.TvCollectionFilterPickerKind
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.common.buildTvCollectionFiltersUiState
import ru.radiationx.anilibria.common.buildTvCollectionListLabel
import ru.radiationx.anilibria.common.selectedIndices

internal class TvCollectionFilterController(
    private val defaultSort: TvCollectionSort,
    initialState: TvCollectionFilterState = TvCollectionFilterState(sort = defaultSort),
    initialOptions: TvCollectionFilterOptions = TvCollectionFilterOptions(),
) {
    private val _state = MutableStateFlow(normalizeState(initialState, initialOptions))
    val state: StateFlow<TvCollectionFilterState> = _state.asStateFlow()

    private val _options = MutableStateFlow(initialOptions)
    val options: StateFlow<TvCollectionFilterOptions> = _options.asStateFlow()

    private val _pickerState = MutableStateFlow<TvCollectionFilterPickerState?>(null)
    val pickerState: StateFlow<TvCollectionFilterPickerState?> = _pickerState.asStateFlow()

    private val _uiState = MutableStateFlow(buildUiState(_state.value, initialOptions))
    val uiState: StateFlow<TvCollectionFiltersUiState> = _uiState.asStateFlow()

    fun updateOptions(nextOptions: TvCollectionFilterOptions): Boolean {
        _options.value = nextOptions
        val normalizedState = normalizeState(_state.value, nextOptions)
        val stateChanged = normalizedState != _state.value
        _state.value = normalizedState
        syncUi()
        syncPicker()
        return stateChanged
    }

    fun updateState(nextState: TvCollectionFilterState): Boolean {
        val normalizedState = normalizeState(nextState, _options.value)
        if (normalizedState == _state.value) {
            syncPicker()
            return false
        }
        _state.value = normalizedState
        syncUi()
        syncPicker()
        return true
    }

    fun openYearPicker() {
        val options = _options.value.years
        if (options.isEmpty()) return
        _pickerState.value =
            TvCollectionFilterPickerState(
                kind = TvCollectionFilterPickerKind.YEAR,
                title = TvCollectionFilterLabels.YEARS_TITLE,
                options = options.map(TvCollectionFilterOption::label),
                selectedIndices = selectedIndices(options.map(TvCollectionFilterOption::value), _state.value.years),
                multiSelect = true,
            )
    }

    fun openSeasonPicker() {
        val options = _options.value.seasons
        if (options.isEmpty()) return
        _pickerState.value =
            TvCollectionFilterPickerState(
                kind = TvCollectionFilterPickerKind.SEASON,
                title = TvCollectionFilterLabels.SEASONS_TITLE,
                options = options.map(TvCollectionFilterOption::label),
                selectedIndices = selectedIndices(options.map(TvCollectionFilterOption::value), _state.value.seasons),
                multiSelect = true,
            )
    }

    fun openGenrePicker() {
        val options = _options.value.genres
        if (options.isEmpty()) return
        _pickerState.value =
            TvCollectionFilterPickerState(
                kind = TvCollectionFilterPickerKind.GENRE,
                title = TvCollectionFilterLabels.GENRES_TITLE,
                options = options.map(TvCollectionFilterOption::label),
                selectedIndices = selectedIndices(options.map(TvCollectionFilterOption::value), _state.value.genres),
                multiSelect = true,
            )
    }

    fun openSortPicker() {
        _pickerState.value =
            TvCollectionFilterPickerState(
                kind = TvCollectionFilterPickerKind.SORT,
                title = TvCollectionFilterLabels.SORT_TITLE,
                options =
                    listOf(
                        TvCollectionFilterLabels.SORT_POPULARITY,
                        TvCollectionFilterLabels.SORT_DATE,
                    ),
                selectedIndices = setOf(if (_state.value.sort == TvCollectionSort.POPULARITY) 0 else 1),
                multiSelect = false,
            )
    }

    fun openCompletedPicker() {
        _pickerState.value =
            TvCollectionFilterPickerState(
                kind = TvCollectionFilterPickerKind.COMPLETED,
                title = TvCollectionFilterLabels.STATUS_TITLE,
                options =
                    listOf(
                        TvCollectionFilterLabels.ALL,
                        TvCollectionFilterLabels.ONLY_COMPLETED,
                    ),
                selectedIndices = setOf(if (_state.value.onlyCompleted) 1 else 0),
                multiSelect = false,
            )
    }

    fun togglePickerSelection(index: Int) {
        val current = _pickerState.value ?: return
        if (!current.multiSelect || index !in current.options.indices) {
            return
        }
        val nextSelection =
            current.selectedIndices.toMutableSet().apply {
                if (!add(index)) {
                    remove(index)
                }
            }
        _pickerState.value = current.copy(selectedIndices = nextSelection)
    }

    fun selectSinglePicker(index: Int): Boolean {
        val current = _pickerState.value ?: return false
        if (current.multiSelect || index !in current.options.indices) {
            return false
        }
        val nextState =
            when (current.kind) {
                TvCollectionFilterPickerKind.SORT -> {
                    _state.value.copy(
                        sort =
                            when (index) {
                                0 -> TvCollectionSort.POPULARITY
                                1 -> TvCollectionSort.DATE
                                else -> return false
                            },
                    )
                }

                TvCollectionFilterPickerKind.COMPLETED -> {
                    _state.value.copy(
                        onlyCompleted =
                            when (index) {
                                0 -> false
                                1 -> true
                                else -> return false
                            },
                    )
                }

                else -> return false
            }
        _pickerState.value = null
        return updateState(nextState)
    }

    fun applyFilterPicker(): Boolean {
        val current = _pickerState.value ?: return false
        val wasApplied = if (current.multiSelect) applyMultiSelectPickerSelection(current) else false
        _pickerState.value = null
        return wasApplied
    }

    fun resetFilterPicker() {
        val current = _pickerState.value ?: return
        if (!current.multiSelect) {
            return
        }
        _pickerState.value = current.copy(selectedIndices = emptySet())
    }

    fun dismissFilterPicker(): Boolean {
        val current = _pickerState.value
        val wasApplied = if (current?.multiSelect == true) applyMultiSelectPickerSelection(current) else false
        _pickerState.value = null
        return wasApplied
    }

    private fun applyMultiSelectPickerSelection(picker: TvCollectionFilterPickerState): Boolean {
        val nextState =
            when (picker.kind) {
                TvCollectionFilterPickerKind.YEAR -> {
                    _state.value.copy(
                        years =
                            picker.selectedIndices
                                .mapNotNull { _options.value.years.getOrNull(it)?.value }
                                .toSet(),
                    )
                }

                TvCollectionFilterPickerKind.SEASON -> {
                    _state.value.copy(
                        seasons =
                            picker.selectedIndices
                                .mapNotNull { _options.value.seasons.getOrNull(it)?.value }
                                .toSet(),
                    )
                }

                TvCollectionFilterPickerKind.GENRE -> {
                    _state.value.copy(
                        genres =
                            picker.selectedIndices
                                .mapNotNull { _options.value.genres.getOrNull(it)?.value }
                                .toSet(),
                    )
                }

                TvCollectionFilterPickerKind.SORT,
                TvCollectionFilterPickerKind.COMPLETED,
                -> return false
            }
        return updateState(nextState)
    }

    private fun syncUi() {
        _uiState.value = buildUiState(_state.value, _options.value)
    }

    private fun syncPicker() {
        val current = _pickerState.value ?: return
        val nextPicker =
            when (current.kind) {
                TvCollectionFilterPickerKind.YEAR ->
                    current.copy(
                        options = _options.value.years.map(TvCollectionFilterOption::label),
                        selectedIndices =
                            selectedIndices(
                                _options.value.years.map(TvCollectionFilterOption::value),
                                _state.value.years,
                            ),
                    )

                TvCollectionFilterPickerKind.SEASON ->
                    current.copy(
                        options = _options.value.seasons.map(TvCollectionFilterOption::label),
                        selectedIndices =
                            selectedIndices(
                                _options.value.seasons.map(TvCollectionFilterOption::value),
                                _state.value.seasons,
                            ),
                    )

                TvCollectionFilterPickerKind.GENRE ->
                    current.copy(
                        options = _options.value.genres.map(TvCollectionFilterOption::label),
                        selectedIndices =
                            selectedIndices(
                                _options.value.genres.map(TvCollectionFilterOption::value),
                                _state.value.genres,
                            ),
                    )

                TvCollectionFilterPickerKind.SORT ->
                    current.copy(
                        selectedIndices = setOf(if (_state.value.sort == TvCollectionSort.POPULARITY) 0 else 1),
                    )

                TvCollectionFilterPickerKind.COMPLETED ->
                    current.copy(
                        selectedIndices = setOf(if (_state.value.onlyCompleted) 1 else 0),
                    )
            }
        _pickerState.value = nextPicker
    }

    private fun normalizeState(
        state: TvCollectionFilterState,
        options: TvCollectionFilterOptions,
    ): TvCollectionFilterState {
        val availableYears = options.years.map(TvCollectionFilterOption::value).toSet()
        val availableSeasons = options.seasons.map(TvCollectionFilterOption::value).toSet()
        val availableGenres = options.genres.map(TvCollectionFilterOption::value).toSet()
        return state.copy(
            years = state.years.intersect(availableYears.ifEmpty { state.years }),
            seasons = state.seasons.intersect(availableSeasons.ifEmpty { state.seasons }),
            genres = state.genres.intersect(availableGenres.ifEmpty { state.genres }),
        )
    }

    private fun buildUiState(
        state: TvCollectionFilterState,
        options: TvCollectionFilterOptions,
    ): TvCollectionFiltersUiState {
        val orderedYears =
            options.years
                .filter { it.value in state.years }
                .map(TvCollectionFilterOption::label)
                .ifEmpty { state.years.sortedDescending() }
        val orderedSeasons =
            options.seasons
                .filter { it.value in state.seasons }
                .map(TvCollectionFilterOption::label)
                .ifEmpty { state.seasons.sorted() }
        val orderedGenres =
            options.genres
                .filter { it.value in state.genres }
                .map(TvCollectionFilterOption::label)
                .ifEmpty { state.genres.sorted() }
        return buildTvCollectionFiltersUiState(
            yearLabel = buildTvCollectionListLabel(orderedYears, TvCollectionFilterLabels.ALL_YEARS),
            yearEmphasized = state.years.isNotEmpty(),
            seasonLabel = buildTvCollectionListLabel(orderedSeasons, TvCollectionFilterLabels.ALL_SEASONS),
            seasonEmphasized = state.seasons.isNotEmpty(),
            genreLabel = buildTvCollectionListLabel(orderedGenres, TvCollectionFilterLabels.ALL_GENRES),
            genreEmphasized = state.genres.isNotEmpty(),
            sortLabel = state.sort.toLabel(),
            sortEmphasized = state.sort != defaultSort,
            onlyCompletedLabel =
                if (state.onlyCompleted) {
                    TvCollectionFilterLabels.ONLY_COMPLETED
                } else {
                    TvCollectionFilterLabels.ALL
                },
            onlyCompletedEmphasized = state.onlyCompleted,
        )
    }

    private fun TvCollectionSort.toLabel(): String {
        return when (this) {
            TvCollectionSort.POPULARITY -> TvCollectionFilterLabels.SORT_POPULARITY
            TvCollectionSort.DATE -> TvCollectionFilterLabels.SORT_DATE
        }
    }
}
