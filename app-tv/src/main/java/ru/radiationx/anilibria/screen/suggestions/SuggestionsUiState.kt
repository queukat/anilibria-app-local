package ru.radiationx.anilibria.screen.suggestions

import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.data.entity.domain.search.SuggestionItem

internal object SuggestionsRows {
    const val RESULT_ROW_ID = 1L
    const val RECOMMENDS_ROW_ID = 2L

    fun visibleRowIds(
        showResultRow: Boolean,
        showRecommendsRow: Boolean,
    ): List<Long> =
        buildList {
            if (showResultRow) {
                add(RESULT_ROW_ID)
            }
            if (showRecommendsRow) {
                add(RECOMMENDS_ROW_ID)
            }
        }
}

internal data class SuggestionsSearchResult(
    val items: List<SuggestionItem> = emptyList(),
    val query: String = "",
    val validQuery: Boolean = false,
)

internal data class SuggestionsResultUiState(
    val progressVisible: Boolean = false,
    val resultCards: List<CardItem> = emptyList(),
    val showResultRow: Boolean = false,
    val showRecommendsRow: Boolean = true,
)

internal fun SuggestionsSearchResult.toUiState(progressVisible: Boolean): SuggestionsResultUiState {
    val resultCards =
        if (validQuery && items.isEmpty()) {
            listOf(
                InfoCard(
                    title = "Ничего не найдено",
                    subtitle = "Попробуйте изменить запрос: \"$query\"",
                ),
            )
        } else {
            items.map {
                LibriaCard(
                    it.names.getOrNull(0).orEmpty(),
                    it.names.getOrNull(1).orEmpty(),
                    it.poster.orEmpty(),
                    LibriaCard.Type.Release(it.id),
                )
            }
        }
    return SuggestionsResultUiState(
        progressVisible = progressVisible,
        resultCards = resultCards,
        showResultRow = validQuery,
        showRecommendsRow = !validQuery && resultCards.isEmpty(),
    )
}
