package ru.radiationx.anilibria.presentation.pagination

sealed interface PaginatorEvent {
    object Refresh : PaginatorEvent
    object LoadNextPage : PaginatorEvent
    object Retry : PaginatorEvent
}
