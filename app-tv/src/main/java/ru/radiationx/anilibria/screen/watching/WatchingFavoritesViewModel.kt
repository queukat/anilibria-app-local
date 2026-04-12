package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.TvCollectionFilterLabels
import ru.radiationx.anilibria.common.TvCollectionFilterPickerKind
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.common.buildTvCollectionFiltersUiState
import ru.radiationx.anilibria.common.buildTvCollectionListLabel
import ru.radiationx.anilibria.common.isCompletedForTvCollectionFilters
import ru.radiationx.anilibria.common.selectedIndices
import ru.radiationx.anilibria.common.toTvCollectionCompletedLabel
import ru.radiationx.anilibria.common.toTvCollectionSortLabel
import ru.radiationx.anilibria.common.tvCollectionRecencyComparator
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.data.repository.AuthRepository
import javax.inject.Inject

class WatchingFavoritesViewModel
    @Inject
    constructor(
        private val tvFavoritesUseCase: TvFavoritesUseCase,
        private val tvSearchUseCase: TvSearchUseCase,
        authRepository: AuthRepository,
        private val converter: CardsDataConverter,
        private val cardRouter: LibriaCardRouter,
    ) : ViewModel(), DefaultLifecycleObserver {
        val defaultTitle: String = "Избранное"

        private val _cardsData = MutableStateFlow<List<CardItem>>(listOf(LoadingCard()))
        val cardsData: StateFlow<List<CardItem>> = _cardsData.asStateFlow()

        private val _filterPicker = MutableStateFlow<TvCollectionFilterPickerState?>(null)
        internal val filterPicker: StateFlow<TvCollectionFilterPickerState?> = _filterPicker.asStateFlow()

        private val _filtersUiState =
            MutableStateFlow(
                buildTvCollectionFiltersUiState(
                    yearLabel = TvCollectionFilterLabels.ALL_YEARS,
                    yearEmphasized = false,
                    seasonLabel = TvCollectionFilterLabels.ALL_SEASONS,
                    seasonEmphasized = false,
                    genreLabel = TvCollectionFilterLabels.ALL_GENRES,
                    genreEmphasized = false,
                    sortLabel = SearchForm.Sort.DATE.toTvCollectionSortLabel(),
                    sortEmphasized = false,
                    onlyCompletedLabel = false.toTvCollectionCompletedLabel(),
                    onlyCompletedEmphasized = false,
                ),
            )
        internal val filtersUiState: StateFlow<TvCollectionFiltersUiState> = _filtersUiState.asStateFlow()

        private var currentSort: SearchForm.Sort = SearchForm.Sort.DATE
        private var onlyCompletedFilter: Boolean = false
        private var yearFilters: Set<String> = emptySet()
        private var seasonFilters: Set<String> = emptySet()
        private var genreFilters: Set<String> = emptySet()

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

        private val minRefreshIntervalMs: Long = 2L * 60L * 1000L

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
            updateLabels()
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
            if (availableYears.isEmpty()) return
            _filterPicker.value =
                TvCollectionFilterPickerState(
                    kind = TvCollectionFilterPickerKind.YEAR,
                    title = TvCollectionFilterLabels.YEARS_TITLE,
                    options = availableYears,
                    selectedIndices = selectedIndices(availableYears, yearFilters),
                    multiSelect = true,
                )
        }

        fun onSeasonClick() {
            if (availableSeasons.isEmpty()) return
            _filterPicker.value =
                TvCollectionFilterPickerState(
                    kind = TvCollectionFilterPickerKind.SEASON,
                    title = TvCollectionFilterLabels.SEASONS_TITLE,
                    options = availableSeasons,
                    selectedIndices = selectedIndices(availableSeasons, seasonFilters),
                    multiSelect = true,
                )
        }

        fun onGenreClick() {
            if (availableGenres.isEmpty()) return
            _filterPicker.value =
                TvCollectionFilterPickerState(
                    kind = TvCollectionFilterPickerKind.GENRE,
                    title = TvCollectionFilterLabels.GENRES_TITLE,
                    options = availableGenres,
                    selectedIndices = selectedIndices(availableGenres, genreFilters),
                    multiSelect = true,
                )
        }

        fun onSortClick() {
            _filterPicker.value =
                TvCollectionFilterPickerState(
                    kind = TvCollectionFilterPickerKind.SORT,
                    title = TvCollectionFilterLabels.SORT_TITLE,
                    options =
                        listOf(
                            TvCollectionFilterLabels.SORT_POPULARITY,
                            TvCollectionFilterLabels.SORT_DATE,
                        ),
                    selectedIndices =
                        setOf(
                            when (currentSort) {
                                SearchForm.Sort.RATING -> 0
                                SearchForm.Sort.DATE -> 1
                            },
                        ),
                    multiSelect = false,
                )
        }

        fun onOnlyCompletedClick() {
            _filterPicker.value =
                TvCollectionFilterPickerState(
                    kind = TvCollectionFilterPickerKind.COMPLETED,
                    title = TvCollectionFilterLabels.STATUS_TITLE,
                    options =
                        listOf(
                            TvCollectionFilterLabels.ALL,
                            TvCollectionFilterLabels.ONLY_COMPLETED,
                        ),
                    selectedIndices = setOf(if (onlyCompletedFilter) 1 else 0),
                    multiSelect = false,
                )
        }

        fun togglePickerSelection(index: Int) {
            val current = _filterPicker.value ?: return
            if (!current.multiSelect || index !in current.options.indices) {
                return
            }
            val nextSelection =
                current.selectedIndices.toMutableSet().apply {
                    if (!add(index)) {
                        remove(index)
                    }
                }
            _filterPicker.value = current.copy(selectedIndices = nextSelection)
        }

        fun selectSinglePicker(index: Int) {
            val current = _filterPicker.value ?: return
            if (current.multiSelect || index !in current.options.indices) {
                return
            }
            val applied =
                when (current.kind) {
                    TvCollectionFilterPickerKind.SORT -> {
                        resolveSortOption(index)?.let { sort ->
                            currentSort = sort
                            true
                        } ?: false
                    }

                    TvCollectionFilterPickerKind.COMPLETED -> {
                        resolveCompletedOption(index)?.let { onlyCompleted ->
                            onlyCompletedFilter = onlyCompleted
                            true
                        } ?: false
                    }

                    else -> false
                }
            if (applied) {
                updateLabels()
                dismissFilterPicker()
                rebuildFromCache()
            }
        }

        fun applyFilterPicker() {
            val current = _filterPicker.value
            val wasApplied = current?.takeIf { it.multiSelect }?.let(::applyMultiSelectPickerSelection) == true
            if (current != null) {
                _filterPicker.value = null
            }
            if (wasApplied) {
                updateLabels()
                rebuildFromCache()
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
            val current = _filterPicker.value
            val wasApplied = current?.takeIf { it.multiSelect }?.let(::applyMultiSelectPickerSelection) == true
            _filterPicker.value = null
            if (wasApplied) {
                updateLabels()
                rebuildFromCache()
            }
        }

        private fun shouldRefreshNow(): Boolean {
            val now = System.currentTimeMillis()
            if (lastSuccessfulSyncMs == 0L) return true
            return (now - lastSuccessfulSyncMs) >= minRefreshIntervalMs
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
                            loadAllFavoritesIncremental { partial ->
                                releasesCache = partial
                                val filters =
                                    withContext(Dispatchers.Default) {
                                        computeAvailableFilters(partial)
                                    }
                                updateAvailableFilters(filters)
                                rebuildFromCache()
                            }

                        releasesCache = all
                        lastSuccessfulSyncMs = System.currentTimeMillis()

                        val filters =
                            withContext(Dispatchers.Default) {
                                computeAvailableFilters(all)
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
                                _cardsData.value =
                                    listOf(
                                        LoadingCard(
                                            title = "Ошибка загрузки",
                                            description = e.message ?: "",
                                            isError = true,
                                        ),
                                        LinkCard("Повторить"),
                                    )
                            } else {
                                _cardsData.value = _cardsData.value
                                    .filterNot { it is LinkCard && it.title == "Повторить" } + LinkCard("Повторить")
                            }
                        }
                    }
                }
        }

        private fun updateAvailableFilters(filters: Filters) {
            rawAvailableYears = filters.years
            rawAvailableSeasons = filters.seasons
            rawAvailableGenres = filters.genres
            syncAvailableFilterOptions()
        }

        private fun syncAvailableFilterOptions() {
            availableYears = rawAvailableYears.ifEmpty { catalogYearOptions }
            availableSeasons = rawAvailableSeasons.ifEmpty { catalogSeasonOptions }
            availableGenres = rawAvailableGenres.ifEmpty { catalogGenreOptions }
            pruneUnavailableFilters()
            updateLabels()
            syncFilterPicker()
        }

        private fun showNeedAuth() {
            _cardsData.value =
                listOf(
                    InfoCard(
                        title = "Нужно войти",
                        subtitle = "Откройте профиль и авторизуйтесь, чтобы видеть избранное на этом устройстве",
                    ),
                )
        }

        private fun showAuthenticatedEmptyState() {
            _cardsData.value =
                listOf(
                    InfoCard(
                        title = "Избранное пока пусто",
                        subtitle = "Добавьте тайтлы в избранное, чтобы они появились здесь",
                    ),
                )
        }

        private suspend fun loadAllFavoritesIncremental(onPartialLoaded: suspend (List<Release>) -> Unit): List<Release> {
            val result = LinkedHashMap<Int, Release>()
            var page = 1
            var unchangedPages = 0
            var shouldContinue = true

            while (shouldContinue && page <= MAX_FAVORITES_SYNC_PAGES && currentCoroutineContext().isActive) {
                val response = tvFavoritesUseCase.loadFavorites(page)
                val data = response.data
                if (data.isEmpty()) break

                val before = result.size
                data.forEach { release ->
                    result[release.id.id] = release
                }
                val after = result.size

                if (after > before) {
                    onPartialLoaded(result.values.toList())
                }

                val responsePage = response.page
                val responseAllPages = response.allPages
                val reachedLastPage =
                    responsePage != null &&
                        responseAllPages != null &&
                        responsePage >= responseAllPages
                val hitUnchangedPagesLimit =
                    if (after == before) {
                        unchangedPages += 1
                        unchangedPages >= MAX_UNCHANGED_PAGES
                    } else {
                        unchangedPages = 0
                        false
                    }

                shouldContinue = !reachedLastPage && !hitUnchangedPagesLimit
                if (shouldContinue) {
                    page += 1
                }
            }

            return result.values.toList()
        }

        private fun rebuildFromCache() {
            rebuildJob?.cancel()
            rebuildJob =
                viewModelScope.launch {
                    val src = releasesCache
                    if (src.isEmpty()) {
                        if (currentAuthState == AuthState.AUTH) {
                            showAuthenticatedEmptyState()
                        } else {
                            _cardsData.value = emptyList()
                        }
                        return@launch
                    }

                    val sortMode = currentSort
                    val onlyCompleted = onlyCompletedFilter
                    val years = yearFilters
                    val seasons = seasonFilters
                    val genres = genreFilters.map(String::lowercase).toSet()

                    val sorted =
                        withContext(Dispatchers.Default) {
                            val filtered =
                                src.asSequence()
                                    .filter { release ->
                                        if (!onlyCompleted) true else release.isCompletedForTvCollectionFilters()
                                    }
                                    .filter { release -> years.isEmpty() || release.year in years }
                                    .filter { release -> seasons.isEmpty() || release.season in seasons }
                                    .filter { release ->
                                        genres.isEmpty() ||
                                            release.genres.any { genre ->
                                                genre.lowercase() in genres
                                            }
                                    }
                                    .toList()

                            when (sortMode) {
                                SearchForm.Sort.RATING -> {
                                    filtered.sortedWith(
                                        compareByDescending<Release> { it.favoriteInfo.rating }
                                            .thenBy { it.title.orEmpty() },
                                    )
                                }

                                SearchForm.Sort.DATE -> {
                                    filtered.sortedWith(tvCollectionRecencyComparator())
                                }
                            }
                        }

                    _cardsData.value =
                        sorted.map { converter.toCard(it) }
                            .ifEmpty {
                                listOf(
                                    InfoCard(
                                        title = "Ничего не найдено",
                                        subtitle = "Попробуйте изменить фильтры или сбросить часть условий",
                                    ),
                                )
                            }
                }
        }

        private data class Filters(
            val years: List<String>,
            val seasons: List<String>,
            val genres: List<String>,
        )

        private fun computeAvailableFilters(releases: List<Release>): Filters {
            val years =
                releases
                    .mapNotNull { it.year }
                    .distinct()
                    .sortedByDescending(::parseYear)

            val seasons =
                releases
                    .mapNotNull { it.season }
                    .distinct()
                    .sortedByDescending(::seasonRank)

            val genres =
                releases
                    .flatMap { it.genres }
                    .distinct()
                    .sorted()

            return Filters(
                years = years,
                seasons = seasons,
                genres = genres,
            )
        }

        private fun updateLabels() {
            val yearLabel =
                buildTvCollectionListLabel(
                    values = orderedSelectedValues(availableYears, yearFilters),
                    fallback = TvCollectionFilterLabels.ALL_YEARS,
                )
            val seasonLabel =
                buildTvCollectionListLabel(
                    values = orderedSelectedValues(availableSeasons, seasonFilters),
                    fallback = TvCollectionFilterLabels.ALL_SEASONS,
                )
            val genreLabel =
                buildTvCollectionListLabel(
                    values = orderedSelectedValues(availableGenres, genreFilters),
                    fallback = TvCollectionFilterLabels.ALL_GENRES,
                )
            val sortLabel = currentSort.toTvCollectionSortLabel()
            val onlyCompletedLabel = onlyCompletedFilter.toTvCollectionCompletedLabel()

            _filtersUiState.value =
                buildTvCollectionFiltersUiState(
                    yearLabel = yearLabel,
                    yearEmphasized = yearFilters.isNotEmpty(),
                    seasonLabel = seasonLabel,
                    seasonEmphasized = seasonFilters.isNotEmpty(),
                    genreLabel = genreLabel,
                    genreEmphasized = genreFilters.isNotEmpty(),
                    sortLabel = sortLabel,
                    sortEmphasized = currentSort != SearchForm.Sort.DATE,
                    onlyCompletedLabel = onlyCompletedLabel,
                    onlyCompletedEmphasized = onlyCompletedFilter,
                )
        }

        private fun syncFilterPicker() {
            val current = _filterPicker.value ?: return
            _filterPicker.value =
                when (current.kind) {
                    TvCollectionFilterPickerKind.YEAR ->
                        current.copy(
                            options = availableYears,
                            selectedIndices = selectedIndices(availableYears, yearFilters),
                        )

                    TvCollectionFilterPickerKind.SEASON ->
                        current.copy(
                            options = availableSeasons,
                            selectedIndices = selectedIndices(availableSeasons, seasonFilters),
                        )

                    TvCollectionFilterPickerKind.GENRE ->
                        current.copy(
                            options = availableGenres,
                            selectedIndices = selectedIndices(availableGenres, genreFilters),
                        )

                    TvCollectionFilterPickerKind.SORT ->
                        current.copy(
                            selectedIndices =
                                setOf(
                                    when (currentSort) {
                                        SearchForm.Sort.RATING -> 0
                                        SearchForm.Sort.DATE -> 1
                                    },
                                ),
                        )

                    TvCollectionFilterPickerKind.COMPLETED ->
                        current.copy(
                            selectedIndices = setOf(if (onlyCompletedFilter) 1 else 0),
                        )
                }
        }

        private fun orderedSelectedValues(
            availableValues: List<String>,
            selectedValues: Set<String>,
        ): List<String> {
            if (selectedValues.isEmpty()) {
                return emptyList()
            }
            val availableOrder = availableValues.filter { value -> value in selectedValues }
            return if (availableOrder.isNotEmpty()) {
                availableOrder
            } else {
                selectedValues.sorted()
            }
        }

        private fun pruneUnavailableFilters() {
            yearFilters = yearFilters.intersect(availableYears.toSet())
            seasonFilters = seasonFilters.intersect(availableSeasons.toSet())
            genreFilters = genreFilters.intersect(availableGenres.toSet())
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

        private fun applyMultiSelectPickerSelection(picker: TvCollectionFilterPickerState): Boolean {
            return when (picker.kind) {
                TvCollectionFilterPickerKind.YEAR -> {
                    val nextFilters =
                        picker.selectedIndices
                            .mapNotNull { availableYears.getOrNull(it) }
                            .toSet()
                    if (nextFilters == yearFilters) {
                        false
                    } else {
                        yearFilters = nextFilters
                        true
                    }
                }

                TvCollectionFilterPickerKind.SEASON -> {
                    val nextFilters =
                        picker.selectedIndices
                            .mapNotNull { availableSeasons.getOrNull(it) }
                            .toSet()
                    if (nextFilters == seasonFilters) {
                        false
                    } else {
                        seasonFilters = nextFilters
                        true
                    }
                }

                TvCollectionFilterPickerKind.GENRE -> {
                    val nextFilters =
                        picker.selectedIndices
                            .mapNotNull { availableGenres.getOrNull(it) }
                            .toSet()
                    if (nextFilters == genreFilters) {
                        false
                    } else {
                        genreFilters = nextFilters
                        true
                    }
                }

                TvCollectionFilterPickerKind.SORT,
                TvCollectionFilterPickerKind.COMPLETED,
                -> false
            }
        }

        private fun parseYear(value: String): Int {
            return value.filter(Char::isDigit).toIntOrNull() ?: Int.MIN_VALUE
        }

        private fun seasonRank(value: String): Int {
            val normalized = value.lowercase()
            return when {
                "осен" in normalized || "aut" in normalized || "fall" in normalized -> 4
                "лет" in normalized || "sum" in normalized -> 3
                "весн" in normalized || "spr" in normalized -> 2
                "зим" in normalized || "win" in normalized -> 1
                else -> Int.MIN_VALUE
            }
        }

        private companion object {
            private const val MAX_FAVORITES_SYNC_PAGES = 50
            private const val MAX_UNCHANGED_PAGES = 2
        }
    }
