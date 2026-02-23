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
    private val suggestionsController: SuggestionsController,
) : LifecycleViewModel() {

    private val searchLoader = SearchLoader<Query, List<SuggestionItem>>(viewModelScope) {
        tvSuggestionsUseCase.loadSuggestions(it.query)
    }

    private val _progressState = MutableStateFlow(false)
    val progressState: StateFlow<Boolean> = _progressState.asStateFlow()
    private val _resultData = MutableStateFlow<List<LibriaCard>>(emptyList())
    val resultData: StateFlow<List<LibriaCard>> = _resultData.asStateFlow()

    init {
        searchLoader
            .observeState()
            .mapData { items ->
                val currentQuery = searchLoader.getQuery()?.query.orEmpty()
                SuggestionsController.SearchResult(
                    items = items,
                    query = currentQuery,
                    validQuery = currentQuery.length >= 3,
                )
            }
            .onEach {
                _progressState.value = it.loading
                val result = it.data ?: SuggestionsController.SearchResult(emptyList(), "", false)
                showItems(result)
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        searchLoader.onNewQuery(Query(query))
    }

    fun onCardClick(item: LibriaCard) {
        cardRouter.navigate(item)
    }

    private fun showItems(result: SuggestionsController.SearchResult) {
        suggestionsController.resultEvent.emit(result)
        _resultData.value = result.items.map {
            LibriaCard(
                it.names.getOrNull(0).orEmpty(),
                it.names.getOrNull(1).orEmpty(),
                it.poster.orEmpty(),
                LibriaCard.Type.Release(it.id)
            )
        }
    }

    private data class Query(val query: String) : SearchQuery {
        override fun isEmpty(): Boolean {
            return query.length < 3
        }
    }
}
