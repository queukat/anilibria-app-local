package ru.radiationx.anilibria.domain.pagination

class PageSizeHasMoreItemsPolicy<T>(
    private val minPageSize: Int = 10,
) : HasMoreItemsPolicy<T> {
    override fun hasMore(newItems: List<T>, allItems: List<T>): Boolean {
        return newItems.size >= minPageSize
    }
}
