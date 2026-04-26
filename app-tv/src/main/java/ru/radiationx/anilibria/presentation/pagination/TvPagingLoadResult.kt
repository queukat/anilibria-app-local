package ru.radiationx.anilibria.presentation.pagination

data class TvPagingLoadResult<T>(
    val pageItems: List<T>,
    val canLoadMore: Boolean,
    val mergedItems: List<T>? = null,
    val appliedPage: Int? = null,
)
