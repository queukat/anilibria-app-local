package ru.radiationx.anilibria.presentation.pagination

import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard

class LoadMoreCardsComposer(
    private val loadMoreCard: LinkCard = LinkCard("Load more"),
    private val loadingCard: LoadingCard = LoadingCard(title = "Loading data"),
    private val errorCardFactory: (Throwable) -> LoadingCard = { error ->
        LoadingCard(
            title = "Retry loading",
            description = error.message.orEmpty(),
            isError = true,
        )
    },
    private val emptyCardFactory: (() -> CardItem?)? = null,
) {

    fun compose(state: PaginatorState<LibriaCard>): List<CardItem> {
        if (state.items.isEmpty()) {
            if (state.isLoading) {
                return listOf(loadingCard)
            }
            if (state.error != null) {
                return listOf(errorCardFactory(state.error))
            }
            val emptyCard = emptyCardFactory?.invoke()
            if (emptyCard != null) {
                return listOf(emptyCard)
            }
            return emptyList()
        }

        val result = mutableListOf<CardItem>()
        result.addAll(state.items)

        when {
            state.isLoading -> result += loadingCard
            state.error != null -> result += errorCardFactory(state.error)
            state.canLoadMore -> result += loadMoreCard
        }

        return result
    }
}
