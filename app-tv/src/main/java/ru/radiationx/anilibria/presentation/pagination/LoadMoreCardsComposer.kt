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
            val emptyStateCard =
                when {
                    state.isLoading -> loadingCard
                    state.error != null -> errorCardFactory(state.error)
                    else -> emptyCardFactory?.invoke()
                }
            return emptyStateCard?.let(::listOf).orEmpty()
        }

        return buildList {
            addAll(state.items)
            when {
                state.isLoading -> add(loadingCard)
                state.error != null -> add(errorCardFactory(state.error))
                state.canLoadMore -> add(loadMoreCard)
            }
        }
    }
}
