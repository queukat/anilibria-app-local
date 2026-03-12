package ru.radiationx.anilibria.screen.watching

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import timber.log.Timber
import javax.inject.Inject

/**
 * «Глобальные» рекомендации на экране "Я смотрю":
 * - Извлекаем "любимые жанры" из жанров избранных релизов пользователя,
 * - Подмешиваем немного случайных тайтлов для разнообразия.
 */
class WatchingRecommendsViewModel @Inject constructor(
    private val tvSearchUseCase: TvSearchUseCase,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
    private val tvFavoritesUseCase: TvFavoritesUseCase,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Рекомендации"

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val userFavGenres = loadUserFavoriteGenres()
        val topRated = loadTopRated(requestPage)

        if (userFavGenres.isEmpty()) {
            return topRated.map { converter.toCard(it) }
        }

        val matchedByGenres = topRated.filter { release ->
            release.genres.any { g -> userFavGenres.contains(g) }
        }

        val randomSubset = topRated.shuffled().take(3)

        val finalList = matchedByGenres.union(randomSubset).toList()

        return finalList.map { converter.toCard(it) }
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        // Переход на детальный экран
        cardRouter.navigate(card)
    }

    private suspend fun loadUserFavoriteGenres(): Set<String> {
        return try {
            withContext(Dispatchers.IO) {
                tvFavoritesUseCase.loadFavorites(page = 1).data
            }.flatMap { it.genres }.toSet()
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            Timber.w(error, "Failed to load favorite genres for TV recommendations, fallback to top rated")
            emptySet()
        }
    }

    private suspend fun loadTopRated(requestPage: Int): List<ru.radiationx.data.entity.domain.release.Release> {
        return withContext(Dispatchers.IO) {
            tvSearchUseCase.searchReleases(SearchForm(sort = SearchForm.Sort.RATING), requestPage)
        }
    }
}
