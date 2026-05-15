package ru.radiationx.anilibria.screen.watching

import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.isCompletedForTvCollectionFilters
import ru.radiationx.anilibria.common.tvCollectionRecencyComparator
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.search.SearchForm

internal data class FavoritesCardsFilterState(
    val sort: SearchForm.Sort,
    val onlyCompleted: Boolean,
    val years: Set<String>,
    val seasons: Set<String>,
    val genres: Set<String>,
)

internal data class FavoritesAvailableFilters(
    val years: List<String>,
    val seasons: List<String>,
    val genres: List<String>,
)

private const val RETRY_TITLE = "Повторить"

internal class FavoritesCardsPresenter(
    private val converter: CardsDataConverter,
) {
    fun needAuthCards(): List<CardItem> {
        return listOf(
            InfoCard(
                title = "Нужно войти",
                subtitle = "Откройте профиль и авторизуйтесь, чтобы видеть избранное на этом устройстве",
            ),
        )
    }

    fun emptyFavoritesCards(): List<CardItem> {
        return listOf(
            InfoCard(
                title = "Избранное пока пусто",
                subtitle = "Добавьте тайтлы в избранное, чтобы они появились здесь",
            ),
        )
    }

    fun loadingErrorCards(message: String): List<CardItem> {
        return listOf(
            LoadingCard(
                title = "Ошибка загрузки",
                description = message,
                isError = true,
            ),
            LinkCard(RETRY_TITLE),
        )
    }

    fun appendRetryCards(currentCards: List<CardItem>): List<CardItem> {
        return currentCards.filterNot { it is LinkCard && it.title == RETRY_TITLE } + LinkCard(RETRY_TITLE)
    }

    fun present(
        releases: List<Release>,
        filterState: FavoritesCardsFilterState,
        isAuthenticated: Boolean,
    ): List<CardItem> {
        if (releases.isEmpty()) {
            return if (isAuthenticated) {
                emptyFavoritesCards()
            } else {
                emptyList()
            }
        }

        val filtered =
            releases.asSequence()
                .filter { release ->
                    if (!filterState.onlyCompleted) {
                        true
                    } else {
                        release.isCompletedForTvCollectionFilters()
                    }
                }
                .filter { release -> filterState.years.isEmpty() || release.year in filterState.years }
                .filter { release -> filterState.seasons.isEmpty() || release.season in filterState.seasons }
                .filter { release ->
                    filterState.genres.isEmpty() ||
                        release.genres.any { genre ->
                            genre.lowercase() in filterState.genres
                        }
                }
                .toList()

        val sorted =
            when (filterState.sort) {
                SearchForm.Sort.RATING -> {
                    filtered.sortedWith(
                        compareByDescending<Release> { it.favoriteInfo.rating }
                            .thenBy { it.title.orEmpty() },
                    )
                }

                SearchForm.Sort.DATE -> {
                    filtered.sortedWith(tvCollectionRecencyComparator())
                }
            }

        return sorted.map { converter.toCard(it) }
            .ifEmpty {
                listOf(
                    InfoCard(
                        title = "Ничего не найдено",
                        subtitle = "Попробуйте изменить фильтры или сбросить часть условий",
                    ),
                )
            }
    }

    fun computeAvailableFilters(releases: List<Release>): FavoritesAvailableFilters {
        val years =
            releases
                .mapNotNull { it.year }
                .distinct()
                .sortedByDescending(::parseYear)

        val seasons =
            releases
                .mapNotNull { it.season }
                .distinct()
                .sortedByDescending(::seasonRank)

        val genres =
            releases
                .flatMap { it.genres }
                .distinct()
                .sorted()

        return FavoritesAvailableFilters(
            years = years,
            seasons = seasons,
            genres = genres,
        )
    }

    private fun parseYear(value: String): Int {
        return value.filter(Char::isDigit).toIntOrNull() ?: Int.MIN_VALUE
    }

    private fun seasonRank(value: String): Int {
        val normalized = value.lowercase()
        return when {
            "осен" in normalized || "aut" in normalized || "fall" in normalized -> 4
            "лет" in normalized || "sum" in normalized -> 3
            "весн" in normalized || "spr" in normalized -> 2
            "зим" in normalized || "win" in normalized -> 1
            else -> Int.MIN_VALUE
        }
    }
}
