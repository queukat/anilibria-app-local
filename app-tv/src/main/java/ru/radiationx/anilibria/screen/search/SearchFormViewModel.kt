package ru.radiationx.anilibria.screen.search

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.SearchCompletedGuidedScreen
import ru.radiationx.anilibria.screen.SearchGenreGuidedScreen
import ru.radiationx.anilibria.screen.SearchSeasonGuidedScreen
import ru.radiationx.anilibria.screen.SearchSortGuidedScreen
import ru.radiationx.anilibria.screen.SearchYearGuidedScreen
import ru.radiationx.data.entity.domain.search.SearchForm
import javax.inject.Inject

class SearchFormViewModel @Inject constructor(
    private val searchController: SearchController,
    private val guidedRouter: GuidedRouter,
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

    private var searchForm = SearchForm()

    init {
        updateDataByForm()

        searchController.yearsEvent.onEach {
            searchForm = searchForm.copy(years = it)
            updateDataByForm()
        }.launchIn(viewModelScope)

        searchController.seasonsEvent.onEach {
            searchForm = searchForm.copy(seasons = it)
            updateDataByForm()
        }.launchIn(viewModelScope)

        searchController.genresEvent.onEach {
            searchForm = searchForm.copy(genres = it)
            updateDataByForm()
        }.launchIn(viewModelScope)

        searchController.sortEvent.onEach {
            searchForm = searchForm.copy(sort = it)
            updateDataByForm()
        }.launchIn(viewModelScope)

        searchController.completedEvent.onEach {
            searchForm = searchForm.copy(onlyCompleted = it)
            updateDataByForm()
        }.launchIn(viewModelScope)
    }

    fun onYearClick() {
        guidedRouter.open(SearchYearGuidedScreen(searchForm.years.map { it.value }))
    }

    fun onSeasonClick() {
        guidedRouter.open(SearchSeasonGuidedScreen(searchForm.seasons.map { it.value }))
    }

    fun onGenreClick() {
        guidedRouter.open(SearchGenreGuidedScreen(searchForm.genres.map { it.value }))
    }

    fun onSortClick() {
        guidedRouter.open(SearchSortGuidedScreen(searchForm.sort))
    }

    fun onOnlyCompletedClick() {
        guidedRouter.open(SearchCompletedGuidedScreen(searchForm.onlyCompleted))
    }

    private fun updateDataByForm() {
        _yearData.value = searchForm.years.map { it.title }.generateListTitle("Все годы")
        _seasonData.value = searchForm.seasons.map { it.title }.generateListTitle("Все сезоны")
        _genreData.value = searchForm.genres.map { it.title }.generateListTitle("Все жанры")
        _sortData.value = when (searchForm.sort) {
            SearchForm.Sort.RATING -> "По популярности"
            SearchForm.Sort.DATE -> "По новизне"
        }
        _onlyCompletedData.value = if (searchForm.onlyCompleted) {
            "Только завершенные"
        } else {
            "Все"
        }

        searchController.applyFormEvent.emit(searchForm)
    }

    private fun List<String>?.generateListTitle(fallback: String, take: Int = 2): String {
        if (isNullOrEmpty()) {
            return fallback
        }
        var result = take(take).joinToString()
        if (size > take) {
            result += "… +${size - take}"
        }
        return result
    }
}
