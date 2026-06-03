package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterController
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterOption
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterOptions
import ru.radiationx.anilibria.presentation.filters.TvCollectionFilterState
import ru.radiationx.anilibria.presentation.filters.TvCollectionSort
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.shared.ktx.coroutines.AppDispatchers
import javax.inject.Inject

class WatchingFavoritesViewModel
    @Inject
    constructor(
        private val tvSearchUseCase: TvSearchUseCase,
        authRepository: AuthRepository,
        tvFavoritesUseCase: TvFavoritesUseCase,
        converter: CardsDataConverter,
        private val cardRouter: LibriaCardRouter,
    ) : ViewModel(), DefaultLifecycleObserver {
        val defaultTitle: String = "Избранное"

        private val _cardsData = MutableStateFlow<List<CardItem>>(listOf(LoadingCard()))
        val cardsData: StateFlow<List<CardItem>> = _cardsData.asStateFlow()

        private val filterController =
            TvCollectionFilterController(
                defaultSort = TvCollectionSort.DATE,
                initialState = TvCollectionFilterState(sort = TvCollectionSort.DATE),
            )
        internal val filterPicker: StateFlow<TvCollectionFilterPickerState?> = filterController.pickerState
        internal val filtersUiState: StateFlow<TvCollectionFiltersUiState> = filterController.uiState

        private var rawAvailableYears: List<String> = emptyList()
        private var rawAvailableSeasons: List<String> = emptyList()
        private var rawAvailableGenres: List<String> = emptyList()
        private var availableYears: List<String> = emptyList()
        private var availableSeasons: List<String> = emptyList()
        private var availableGenres: List<String> = emptyList()
        private var catalogYearOptions: List<String> = emptyList()
        private var catalogSeasonOptions: List<String> = emptyList()
        private var catalogGenreOptions: List<String> = emptyList()

        private var loadJob: Job? = null
        private var rebuildJob: Job? = null
        private var releasesCache: List<Release> = emptyList()

        private var currentAuthState: AuthState? = null
        private var lastSuccessfulSyncMs: Long = 0L

        private val syncController = FavoritesSyncController(tvFavoritesUseCase)
        private val cardsPresenter = FavoritesCardsPresenter(converter)
        private val currentSort: SearchForm.Sort
            get() = filterController.state.value.sort.toSearchSort()
        private val onlyCompletedFilter: Boolean
            get() = filterController.state.value.onlyCompleted
        private val yearFilters: Set<String>
            get() = filterController.state.value.years
        private val seasonFilters: Set<String>
            get() = filterController.state.value.seasons
        private val genreFilters: Set<String>
            get() = filterController.state.value.genres

        init {
            viewModelScope.launch {
                runCatching { tvSearchUseCase.loadYears().map { it.title } }
                    .onSuccess { years ->
                        catalogYearOptions = years
                        syncAvailableFilterOptions()
                    }
                runCatching { tvSearchUseCase.loadSeasons().map { it.title } }
                    .onSuccess { seasons ->
                        catalogSeasonOptions = seasons
                        syncAvailableFilterOptions()
                    }
                runCatching { tvSearchUseCase.loadGenres().map { it.title } }
                    .onSuccess { genres ->
                        catalogGenreOptions = genres
                        syncAvailableFilterOptions()
                    }
            }
            authRepository
                .observeAuthState()
                .distinctUntilChanged()
                .onEach { state ->
                    currentAuthState = state
                    if (state == AuthState.AUTH) {
                        if (releasesCache.isNotEmpty()) {
                            rebuildFromCache()
                        }
                        reloadFromNetwork(showLoading = releasesCache.isEmpty())
                    } else {
                        showNeedAuth()
                    }
                }
                .launchIn(viewModelScope)
        }

        override fun onResume(owner: LifecycleOwner) {
            if (currentAuthState == AuthState.AUTH) {
                if (releasesCache.isNotEmpty()) {
                    rebuildFromCache()
                }

                if (shouldRefreshNow()) {
                    reloadFromNetwork(showLoading = releasesCache.isEmpty())
                }
            } else {
                showNeedAuth()
            }
        }

        override fun onPause(owner: LifecycleOwner) {
            loadJob?.cancel()
            rebuildJob?.cancel()
        }

        fun onLibriaCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        fun onLinkCardClick() {
            if (currentAuthState == AuthState.AUTH) {
                reloadFromNetwork(showLoading = releasesCache.isEmpty(), force = true)
            } else {
                showNeedAuth()
            }
        }

        fun onLoadingCardClick() {
            // No-op for now.
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
                rebuildFromCache()
            }
        }

        fun applyFilterPicker() {
            if (filterController.applyFilterPicker()) {
                rebuildFromCache()
            }
        }

        fun resetFilterPicker() {
            filterController.resetFilterPicker()
        }

        fun dismissFilterPicker() {
            if (filterController.dismissFilterPicker()) {
                rebuildFromCache()
            }
        }

        private fun shouldRefreshNow(): Boolean {
            return syncController.shouldRefresh(lastSuccessfulSyncMs)
        }

        private fun reloadFromNetwork(
            showLoading: Boolean,
            force: Boolean = false,
        ) {
            if (!force && !showLoading && !shouldRefreshNow()) {
                return
            }

            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    if (showLoading) {
                        _cardsData.value = listOf(LoadingCard(title = "Загрузка..."))
                    }

                    try {
                        val all =
                            syncController.loadAllFavoritesIncremental { partial ->
                                releasesCache = partial
                                val filters =
                                    withContext(AppDispatchers.default) {
                                        cardsPresenter.computeAvailableFilters(partial)
                                    }
                                updateAvailableFilters(filters)
                                rebuildFromCache()
                            }

                        releasesCache = all
                        lastSuccessfulSyncMs = System.currentTimeMillis()

                        val filters =
                            withContext(AppDispatchers.default) {
                                cardsPresenter.computeAvailableFilters(all)
                            }
                        updateAvailableFilters(filters)
                        rebuildFromCache()
                    } catch (e: Throwable) {
                        if (e is CancellationException) {
                            throw e
                        }
                        val is401 = e is ru.radiationx.data.system.HttpException && e.code == 401

                        if (is401) {
                            showNeedAuth()
                        } else {
                            if (releasesCache.isEmpty()) {
                                _cardsData.value = cardsPresenter.loadingErrorCards(e.message ?: "")
                            } else {
                                _cardsData.value = cardsPresenter.appendRetryCards(_cardsData.value)
                            }
                        }
                    }
                }
        }

        private fun updateAvailableFilters(filters: FavoritesAvailableFilters) {
            rawAvailableYears = filters.years
            rawAvailableSeasons = filters.seasons
            rawAvailableGenres = filters.genres
            syncAvailableFilterOptions()
        }

        private fun syncAvailableFilterOptions() {
            availableYears = rawAvailableYears.ifEmpty { catalogYearOptions }
            availableSeasons = rawAvailableSeasons.ifEmpty { catalogSeasonOptions }
            availableGenres = rawAvailableGenres.ifEmpty { catalogGenreOptions }
            val stateChanged =
                filterController.updateOptions(
                    TvCollectionFilterOptions(
                        years = availableYears.map(::asFilterOption),
                        seasons = availableSeasons.map(::asFilterOption),
                        genres = availableGenres.map(::asFilterOption),
                    ),
                )
            if (stateChanged) {
                rebuildFromCache()
            }
        }

        private fun showNeedAuth() {
            _cardsData.value = cardsPresenter.needAuthCards()
        }

        private fun rebuildFromCache() {
            rebuildJob?.cancel()
            rebuildJob =
                viewModelScope.launch {
                    val src = releasesCache
                    val cards =
                        withContext(AppDispatchers.default) {
                            cardsPresenter.present(
                                releases = src,
                                filterState =
                                    FavoritesCardsFilterState(
                                        sort = currentSort,
                                        onlyCompleted = onlyCompletedFilter,
                                        years = yearFilters,
                                        seasons = seasonFilters,
                                        genres = genreFilters.map(String::lowercase).toSet(),
                                    ),
                                isAuthenticated = currentAuthState == AuthState.AUTH,
                            )
                        }
                    _cardsData.value = cards
                }
        }

        private fun asFilterOption(value: String): TvCollectionFilterOption {
            return TvCollectionFilterOption(value = value)
        }
    }

private fun TvCollectionSort.toSearchSort(): SearchForm.Sort {
    return when (this) {
        TvCollectionSort.POPULARITY -> SearchForm.Sort.RATING
        TvCollectionSort.DATE -> SearchForm.Sort.DATE
    }
}
