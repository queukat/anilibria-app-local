package ru.radiationx.anilibria.screen.search

import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.radiationx.anilibria.common.isCompletedForTvCollectionFilters
import ru.radiationx.anilibria.common.tvCollectionRecencyComparator
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.screen.SuggestionsScreen
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.data.repository.SearchRepository
import javax.inject.Inject

class SearchViewModel @Inject constructor(
    private val tvSearchUseCase: TvSearchUseCase,
    private val converter: CardsDataConverter,
    private val router: Router,
    private val cardRouter: LibriaCardRouter,
    private val searchRepository: SearchRepository,
) : BaseCardsViewModel() {

    private var searchForm = SearchForm()
    private var hasSubmittedSearchForm = false

    private val _progressState = MutableStateFlow(false)
    val progressState: StateFlow<Boolean> = _progressState.asStateFlow()

    override val loadOnCreate: Boolean = false

    override val progressOnRefresh: Boolean = false

    fun submitSearchForm(form: SearchForm) {
        if (hasSubmittedSearchForm && searchForm == form) {
            return
        }
        hasSubmittedSearchForm = true
        searchForm = form
        onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val needProgress = requestPage == firstPage
        if (needProgress) {
            _progressState.value = true
        }
        return try {
            val primaryResult = tvSearchUseCase
                .searchReleases(searchForm, requestPage)
                .sortByTvCollectionMode(searchForm)
                .filterByCompletedState(searchForm)
            val resolvedResult = if (primaryResult.isNotEmpty()) {
                primaryResult
            } else {
                searchRepository.searchReleases(searchForm, requestPage)
                    .data
                    .sortByTvCollectionMode(searchForm)
                    .filterByCompletedState(searchForm)
            }
            resolvedResult.map { converter.toCard(it) }
        } finally {
            if (needProgress) {
                _progressState.value = false
            }
        }
    }

    fun onSearchClick() {
        router.navigateTo(SuggestionsScreen())
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private fun List<Release>.filterByCompletedState(form: SearchForm): List<Release> {
        if (!form.onlyCompleted) {
            return this
        }
        return filter { release -> release.isCompletedForTvCollectionFilters() }
    }

    private fun List<Release>.sortByTvCollectionMode(form: SearchForm): List<Release> {
        return when (form.sort) {
            SearchForm.Sort.RATING -> this
            SearchForm.Sort.DATE -> sortedWith(tvCollectionRecencyComparator())
        }
    }
}
