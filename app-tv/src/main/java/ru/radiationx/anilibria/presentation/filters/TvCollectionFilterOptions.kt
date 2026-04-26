package ru.radiationx.anilibria.presentation.filters

internal data class TvCollectionFilterOption(
    val value: String,
    val label: String = value,
)

internal data class TvCollectionFilterOptions(
    val years: List<TvCollectionFilterOption> = emptyList(),
    val seasons: List<TvCollectionFilterOption> = emptyList(),
    val genres: List<TvCollectionFilterOption> = emptyList(),
)
