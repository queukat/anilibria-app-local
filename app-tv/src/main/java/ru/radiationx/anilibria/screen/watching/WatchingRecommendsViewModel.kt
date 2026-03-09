package ru.radiationx.anilibria.screen.watching

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.data.repository.SearchRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * «Глобальные» рекомендации на экране "Я смотрю":
 * - Извлекаем "любимые жанры" из жанров избранных релизов пользователя,
 * - Подмешиваем немного случайных тайтлов для разнообразия.
 */
class WatchingRecommendsViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val releaseInteractor: ReleaseInteractor,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
    private val favoriteRepository: FavoriteRepository,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Рекомендации"

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val userFavGenres = loadUserFavoriteGenres()
        val topRated = loadTopRated(requestPage)
        // Обновляем кеш (если нужно)
        releaseInteractor.updateItemsCache(topRated.data)

        if (userFavGenres.isEmpty()) {
            return topRated.data.map { converter.toCard(it) }
        }

        // 4) Фильтруем часть релизов, у которых есть пересечение жанров c userFavGenres
        val matchedByGenres = topRated.data.filter { release ->
            release.genres.any { g -> userFavGenres.contains(g) }
        }

        // 5) Подмешиваем несколько случайных тайтлов
        val randomSubset = topRated.data.shuffled().take(3)

        // 6) Объединяем две выборки (union убирает дубли, если есть)
        val finalList = matchedByGenres.union(randomSubset).toList()

        // 7) Преобразуем в LibriaCard
        return finalList.map { converter.toCard(it) }
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        // Переход на детальный экран
        cardRouter.navigate(card)
    }

    private suspend fun loadUserFavoriteGenres(): Set<String> {
        return try {
            withContext(Dispatchers.IO) {
                favoriteRepository.getFavorites(page = 1).data
            }.flatMap { it.genres }.toSet()
        } catch (error: Throwable) {
            if (error is CancellationException) {
                throw error
            }
            Timber.w(error, "Failed to load favorite genres for TV recommendations, fallback to top rated")
            emptySet()
        }
    }

    private suspend fun loadTopRated(requestPage: Int) = withContext(Dispatchers.IO) {
        searchRepository.searchReleases(SearchForm(sort = SearchForm.Sort.RATING), requestPage)
    }
}
