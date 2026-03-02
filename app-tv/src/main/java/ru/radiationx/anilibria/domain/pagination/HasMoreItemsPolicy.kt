package ru.radiationx.anilibria.domain.pagination

fun interface HasMoreItemsPolicy<T> {
    fun hasMore(newItems: List<T>, allItems: List<T>): Boolean
}
