package ru.radiationx.anilibria.presentation.filters

internal data class TvCollectionFilterState(
    val years: Set<String> = emptySet(),
    val seasons: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val sort: TvCollectionSort,
    val onlyCompleted: Boolean = false,
)
