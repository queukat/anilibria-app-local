package ru.radiationx.anilibria.presentation.pagination

data class TvPagingError(
    val page: Int,
    val cause: Throwable,
)
