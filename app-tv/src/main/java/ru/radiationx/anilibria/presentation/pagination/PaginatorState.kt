package ru.radiationx.anilibria.presentation.pagination

data class PaginatorState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: Throwable? = null,
    val currentPage: Int? = null,
    val failedPage: Int? = null,
)
