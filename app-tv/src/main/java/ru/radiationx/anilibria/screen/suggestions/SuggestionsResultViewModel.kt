package ru.radiationx.anilibria.screen.suggestions

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.search.SuggestionItem
import ru.radiationx.data.interactors.tv.TvSuggestionsUseCase
import ru.radiationx.shared_app.controllers.loadersearch.SearchLoader
import ru.radiationx.shared_app.controllers.loadersearch.SearchQuery
import ru.radiationx.shared_app.controllers.loadersingle.mapData
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SuggestionsResultViewModel @Inject constructor(
    private val tvSuggestionsUseCase: TvSuggestionsUseCase,
    private val cardRouter: LibriaCardRouter,
) : LifecycleViewModel() {

    private val searchLoader = SearchLoader<Query, List<SuggestionItem>>(viewModelScope) {
        tvSuggestionsUseCase.loadSuggestions(it.query)
    }

    private val _uiState = MutableStateFlow(SuggestionsResultUiState())
    internal val uiState: StateFlow<SuggestionsResultUiState> = _uiState.asStateFlow()

    init {
        searchLoader
            .observeState()
            .mapData { items ->
                val currentQuery = searchLoader.getQuery()?.query.orEmpty()
                SuggestionsSearchResult(
                    items = items,
                    query = currentQuery,
                    validQuery = currentQuery.length >= 3,
                )
            }
            .onEach {
                val result = it.data ?: SuggestionsSearchResult()
                _uiState.value = result.toUiState(progressVisible = it.loading)
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        searchLoader.onNewQuery(Query(query))
    }

    fun onCardClick(item: LibriaCard) {
        cardRouter.navigate(item)
    }

    private data class Query(val query: String) : SearchQuery {
        override fun isEmpty(): Boolean {
            return query.length < 3
        }
    }
}
