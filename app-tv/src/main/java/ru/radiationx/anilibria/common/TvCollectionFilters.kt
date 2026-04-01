package ru.radiationx.anilibria.common

import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.search.SearchForm

internal enum class TvCollectionFilterPickerKind {
    YEAR,
    SEASON,
    GENRE,
    SORT,
    COMPLETED,
}

internal data class TvCollectionFilterPickerState(
    val kind: TvCollectionFilterPickerKind,
    val title: String,
    val options: List<String>,
    val selectedIndices: Set<Int>,
    val multiSelect: Boolean,
)

internal data class TvCollectionFilterChipState(
    val label: String,
    val emphasized: Boolean,
)

internal data class TvCollectionFiltersUiState(
    val year: TvCollectionFilterChipState,
    val season: TvCollectionFilterChipState,
    val genre: TvCollectionFilterChipState,
    val sort: TvCollectionFilterChipState,
    val onlyCompleted: TvCollectionFilterChipState,
)

internal object TvCollectionFilterLabels {
    const val ALL_YEARS = "Все годы"
    const val ALL_SEASONS = "Все сезоны"
    const val ALL_GENRES = "Все жанры"
    const val ALL = "Все"
    const val ONLY_COMPLETED = "Только завершенные"

    const val YEARS_TITLE = "Годы"
    const val SEASONS_TITLE = "Сезоны"
    const val GENRES_TITLE = "Жанры"
    const val SORT_TITLE = "Сортировка"
    const val STATUS_TITLE = "Статус"

    const val SORT_POPULARITY = "По популярности"
    const val SORT_DATE = "По новизне"
}

internal fun buildTvCollectionFiltersUiState(
    yearLabel: String,
    yearEmphasized: Boolean,
    seasonLabel: String,
    seasonEmphasized: Boolean,
    genreLabel: String,
    genreEmphasized: Boolean,
    sortLabel: String,
    sortEmphasized: Boolean,
    onlyCompletedLabel: String,
    onlyCompletedEmphasized: Boolean,
): TvCollectionFiltersUiState {
    return TvCollectionFiltersUiState(
        year = TvCollectionFilterChipState(yearLabel, yearEmphasized),
        season = TvCollectionFilterChipState(seasonLabel, seasonEmphasized),
        genre = TvCollectionFilterChipState(genreLabel, genreEmphasized),
        sort = TvCollectionFilterChipState(sortLabel, sortEmphasized),
        onlyCompleted = TvCollectionFilterChipState(onlyCompletedLabel, onlyCompletedEmphasized),
    )
}

internal fun buildTvCollectionListLabel(
    values: List<String>,
    fallback: String,
    take: Int = 2,
): String {
    if (values.isEmpty()) {
        return fallback
    }
    var result = values.take(take).joinToString()
    if (values.size > take) {
        result += "… +${values.size - take}"
    }
    return result
}

internal fun selectedIndices(
    allValues: List<String>,
    selectedValues: Set<String>,
): Set<Int> {
    return allValues.mapIndexedNotNull { index, value ->
        index.takeIf { value in selectedValues }
    }.toSet()
}

internal fun tvCollectionFilterIndex(kind: TvCollectionFilterPickerKind): Int {
    return when (kind) {
        TvCollectionFilterPickerKind.YEAR -> 0
        TvCollectionFilterPickerKind.SEASON -> 1
        TvCollectionFilterPickerKind.GENRE -> 2
        TvCollectionFilterPickerKind.SORT -> 3
        TvCollectionFilterPickerKind.COMPLETED -> 4
    }
}

internal fun shouldRequestTvCollectionPickerFocus(
    previous: TvCollectionFilterPickerState?,
    next: TvCollectionFilterPickerState,
): Boolean {
    return previous == null ||
        previous.kind != next.kind ||
        previous.options != next.options ||
        previous.multiSelect != next.multiSelect
}

internal fun SearchForm.Sort.toTvCollectionSortLabel(): String {
    return when (this) {
        SearchForm.Sort.RATING -> TvCollectionFilterLabels.SORT_POPULARITY
        SearchForm.Sort.DATE -> TvCollectionFilterLabels.SORT_DATE
    }
}

internal fun Boolean.toTvCollectionCompletedLabel(): String {
    return if (this) {
        TvCollectionFilterLabels.ONLY_COMPLETED
    } else {
        TvCollectionFilterLabels.ALL
    }
}

internal fun Release.isCompletedForTvCollectionFilters(): Boolean {
    return when (statusCode) {
        Release.STATUS_CODE_COMPLETE -> true
        Release.STATUS_CODE_PROGRESS,
        Release.STATUS_CODE_NOT_ONGOING,
        Release.STATUS_CODE_HIDDEN,
        -> false

        else -> status?.contains("заверш", ignoreCase = true) == true
    }
}

internal fun tvCollectionRecencyComparator(): Comparator<Release> {
    return compareByDescending<Release> { release ->
        release.tvCollectionYearSortValue()
    }.thenByDescending { release ->
        release.tvCollectionSeasonSortValue()
    }.thenByDescending { release ->
        release.id.id
    }.thenBy { release ->
        release.title.orEmpty()
    }
}

private object TvCollectionSeasonRank {
    const val AUTUMN = 4
    const val SUMMER = 3
    const val SPRING = 2
    const val WINTER = 1
}

private fun Release.tvCollectionYearSortValue(): Int {
    val digits = year
        ?.filter(Char::isDigit)
        .orEmpty()
    return digits.toIntOrNull() ?: Int.MIN_VALUE
}

private fun Release.tvCollectionSeasonSortValue(): Int {
    val normalized = season?.lowercase().orEmpty()
    return when {
        "осен" in normalized || "aut" in normalized || "fall" in normalized -> TvCollectionSeasonRank.AUTUMN
        "лет" in normalized || "sum" in normalized -> TvCollectionSeasonRank.SUMMER
        "весн" in normalized || "spr" in normalized -> TvCollectionSeasonRank.SPRING
        "зим" in normalized || "win" in normalized -> TvCollectionSeasonRank.WINTER
        else -> Int.MIN_VALUE
    }
}
