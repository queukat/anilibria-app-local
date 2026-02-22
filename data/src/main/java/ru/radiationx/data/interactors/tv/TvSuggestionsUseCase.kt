package ru.radiationx.data.interactors.tv

import ru.radiationx.data.entity.domain.search.SuggestionItem
import ru.radiationx.data.repository.SearchRepository
import javax.inject.Inject

interface TvSuggestionsUseCase {
    suspend fun loadSuggestions(query: String): List<SuggestionItem>
}

class TvSuggestionsUseCaseImpl @Inject constructor(
    private val searchRepository: SearchRepository,
) : TvSuggestionsUseCase {
    override suspend fun loadSuggestions(query: String): List<SuggestionItem> {
        return searchRepository.fastSearch(query).items
    }
}
