package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.FavoriteRepository
import javax.inject.Inject

class WatchingFavoritesViewModel @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    authRepository: AuthRepository,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
) : ViewModel(), DefaultLifecycleObserver {

    enum class SortMode { BY_DATE, BY_TITLE }

    sealed interface DialogRequest {
        data class ChooseYear(val options: List<String>, val selectedIndex: Int) : DialogRequest
        data class ChooseSeason(val options: List<String>, val selectedIndex: Int) : DialogRequest
        data class ChooseGenre(val options: List<String>, val selectedIndex: Int) : DialogRequest
    }

    val defaultTitle: String = "Избранное"

    val cardsData = MutableStateFlow<List<CardItem>>(listOf(LoadingCard()))

    val dialogRequests = MutableSharedFlow<DialogRequest>(extraBufferCapacity = 1)

    val yearLabel = MutableStateFlow("Год: любой")
    val seasonLabel = MutableStateFlow("Сезон: любой")
    val genreLabel = MutableStateFlow("Жанр: любой")
    val sortLabel = MutableStateFlow("По дате выхода")
    val onlyCompletedLabel = MutableStateFlow("Все")

    private var currentSort: SortMode = SortMode.BY_DATE
    private var onlyCompletedFilter: Boolean = false
    private var yearFilter: String? = null
    private var seasonFilter: String? = null
    private var genreFilter: String? = null

    private var availableYears: List<String> = emptyList()
    private var availableSeasons: List<String> = emptyList()
    private var availableGenres: List<String> = emptyList()

    private var loadJob: Job? = null
    private var rebuildJob: Job? = null
    private var releasesCache: List<Release> = emptyList()

    private var currentAuthState: AuthState? = null

    private var lastSuccessfulSyncMs: Long = 0L

    private val minRefreshIntervalMs: Long = 2L * 60L * 1000L

    init {
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

    fun onLinkCardBind() {
        // No-op for now.
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

    fun onSortClick() {
        currentSort = when (currentSort) {
            SortMode.BY_DATE -> SortMode.BY_TITLE
            SortMode.BY_TITLE -> SortMode.BY_DATE
        }
        updateLabels()
        rebuildFromCache()
    }

    fun onOnlyCompletedClick() {
        onlyCompletedFilter = !onlyCompletedFilter
        updateLabels()
        rebuildFromCache()
    }

    fun onYearClick() {
        val options = buildList {
            add("Любой")
            addAll(availableYears)
        }
        if (options.size <= 1) return
        dialogRequests.tryEmit(
            DialogRequest.ChooseYear(options, selectedIndex = selectedIndex(options, yearFilter))
        )
    }

    fun onSeasonClick() {
        val options = buildList {
            add("Любой")
            addAll(availableSeasons)
        }
        if (options.size <= 1) return
        dialogRequests.tryEmit(
            DialogRequest.ChooseSeason(options, selectedIndex = selectedIndex(options, seasonFilter))
        )
    }

    fun onGenreClick() {
        val options = buildList {
            add("Любой")
            addAll(availableGenres)
        }
        if (options.size <= 1) return
        dialogRequests.tryEmit(
            DialogRequest.ChooseGenre(options, selectedIndex = selectedIndex(options, genreFilter))
        )
    }

    fun onYearSelected(index: Int) {
        yearFilter = if (index <= 0) null else availableYears.getOrNull(index - 1)
        updateLabels()
        rebuildFromCache()
    }

    fun onSeasonSelected(index: Int) {
        seasonFilter = if (index <= 0) null else availableSeasons.getOrNull(index - 1)
        updateLabels()
        rebuildFromCache()
    }

    fun onGenreSelected(index: Int) {
        genreFilter = if (index <= 0) null else availableGenres.getOrNull(index - 1)
        updateLabels()
        rebuildFromCache()
    }

    private fun shouldRefreshNow(): Boolean {
        val now = System.currentTimeMillis()
        if (lastSuccessfulSyncMs == 0L) return true
        return (now - lastSuccessfulSyncMs) >= minRefreshIntervalMs
    }

    private fun reloadFromNetwork(showLoading: Boolean, force: Boolean = false) {
        if (!force && !showLoading && !shouldRefreshNow()) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showLoading) {
                cardsData.value = listOf(LoadingCard(title = "Загрузка…"))
            }

            try {
                val all = withContext(Dispatchers.IO) {
                    loadAllFavoritesSafe()
                }

                releasesCache = all
                lastSuccessfulSyncMs = System.currentTimeMillis()

                val filters = withContext(Dispatchers.Default) {
                    computeAvailableFilters(all)
                }
                availableYears = filters.years
                availableSeasons = filters.seasons
                availableGenres = filters.genres

                rebuildFromCache()
            } catch (e: Throwable) {
                val is401 = (e is ru.radiationx.data.system.HttpException && e.code == 401)

                if (is401) {
                    showNeedAuth()
                } else {
                    if (showLoading) {
                        cardsData.value = listOf(
                            LoadingCard(
                                title = "Ошибка загрузки",
                                description = e.message ?: "",
                                isError = true
                            ),
                            LinkCard("Повторить")
                        )
                    }
                    // If showLoading is false, keep cached UI as is.
                }
            }
        }
    }

    private fun showNeedAuth() {
        cardsData.value = listOf(
            LoadingCard(
                title = "Нужно войти",
                description = "Избранное доступно после авторизации",
                isError = true
            ),
            LinkCard("Открой профиль и войди")
        )
    }

    private suspend fun loadAllFavoritesSafe(): List<Release> {
        val result = LinkedHashMap<Int, Release>()
        var page = 1
        var unchangedPages = 0

        while (page <= 200) {
            val response = favoriteRepository.getFavorites(page)
            val data = response.data
            if (data.isEmpty()) break

            val before = result.size
            data.forEach { r -> result[r.id.id] = r }
            val after = result.size

            if (after == before) {
                unchangedPages += 1
                if (unchangedPages >= 2) break
            } else {
                unchangedPages = 0
            }

            page += 1
        }

        return result.values.toList()
    }

    private fun rebuildFromCache() {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            val src = releasesCache
            if (src.isEmpty()) {
                cardsData.value = emptyList()
                return@launch
            }

            val sortMode = currentSort
            val onlyCompleted = onlyCompletedFilter
            val year = yearFilter
            val season = seasonFilter
            val genre = genreFilter

            val sorted = withContext(Dispatchers.Default) {
                val filtered = src.asSequence()
                    .filter { r ->
                        if (!onlyCompleted) true else r.statusCode == Release.STATUS_CODE_COMPLETE
                    }
                    .filter { r -> year?.let { r.year == it } ?: true }
                    .filter { r -> season?.let { r.season == it } ?: true }
                    .filter { r ->
                        genre?.let { g -> r.genres.any { it.equals(g, ignoreCase = true) } } ?: true
                    }
                    .toList()

                when (sortMode) {
                    SortMode.BY_DATE -> filtered.sortedWith(
                        compareByDescending<Release> { parseYear(it.year) }
                            .thenByDescending { seasonRank(it.season) }
                            .thenByDescending { it.torrentUpdate }
                    )
                    SortMode.BY_TITLE -> filtered.sortedBy { it.title }
                }
            }

            cardsData.value = sorted.map { converter.toCard(it) }
                .ifEmpty { listOf(LinkCard("Ничего не найдено")) }
        }
    }

    private data class Filters(
        val years: List<String>,
        val seasons: List<String>,
        val genres: List<String>
    )

    private fun computeAvailableFilters(releases: List<Release>): Filters {
        val years = releases
            .mapNotNull { it.year }
            .distinct()
            .sortedWith(compareByDescending { parseYear(it) })

        val seasons = releases
            .mapNotNull { it.season }
            .distinct()
            .sortedWith(compareBy { seasonRank(it) })

        val genres = releases
            .flatMap { it.genres }
            .distinct()
            .sorted()

        return Filters(
            years = years,
            seasons = seasons,
            genres = genres
        )
    }

    private fun updateLabels() {
        yearLabel.value = yearFilter?.let { "Год: $it" } ?: "Год: любой"
        seasonLabel.value = seasonFilter?.let { "Сезон: $it" } ?: "Сезон: любой"
        genreLabel.value = genreFilter?.let { "Жанр: $it" } ?: "Жанр: любой"

        sortLabel.value = when (currentSort) {
            SortMode.BY_DATE -> "По дате выхода"
            SortMode.BY_TITLE -> "По названию"
        }
        onlyCompletedLabel.value = if (onlyCompletedFilter) "Только завершённые" else "Все"
    }

    private fun selectedIndex(options: List<String>, value: String?): Int {
        if (value == null) return 0
        val idx = options.indexOf(value)
        return if (idx >= 0) idx else 0
    }

    private fun parseYear(value: String?): Int {
        if (value.isNullOrBlank()) return Int.MIN_VALUE
        val digits = value.filter { it.isDigit() }
        return digits.toIntOrNull() ?: Int.MIN_VALUE
    }

    private fun seasonRank(value: String?): Int {
        val s = value?.lowercase().orEmpty()
        return when {
            "зим" in s || "win" in s -> 1
            "весн" in s || "spr" in s -> 2
            "лет" in s || "sum" in s -> 3
            "осен" in s || "aut" in s || "fall" in s -> 4
            else -> Int.MIN_VALUE
        }
    }
}
