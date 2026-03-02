package ru.radiationx.anilibria.presentation.pagination

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.domain.pagination.HasMoreItemsPolicy
import ru.radiationx.anilibria.domain.pagination.LoadPageUseCase
import ru.radiationx.anilibria.domain.pagination.PageSizeHasMoreItemsPolicy

class CardsPaginatorFactory(
    private val firstPage: Int = 1,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultHasMorePolicy: HasMoreItemsPolicy<LibriaCard> = PageSizeHasMoreItemsPolicy(),
) {
    fun create(
        scope: CoroutineScope,
        loadPageUseCase: LoadPageUseCase<LibriaCard>,
        hasMoreItemsPolicy: HasMoreItemsPolicy<LibriaCard> = defaultHasMorePolicy,
    ): Paginator<LibriaCard> {
        return Paginator(
            scope = scope,
            loader = { page -> loadPageUseCase.execute(page) },
            hasMorePolicy = { newItems, allItems -> hasMoreItemsPolicy.hasMore(newItems, allItems) },
            firstPage = firstPage,
            dispatcher = dispatcher,
        )
    }
}
