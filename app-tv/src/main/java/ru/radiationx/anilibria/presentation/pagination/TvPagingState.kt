package ru.radiationx.anilibria.presentation.pagination

data class TvPagingState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: TvPagingError? = null,
    val currentPage: Int? = null,
    val failedPage: Int? = null,
)
