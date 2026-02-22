package ru.radiationx.anilibria.screen.suggestions

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.SearchRepository
import javax.inject.Inject

/**
 * Рекомендации для экрана подсказок.
 *
 * Приоритет:
 * 1) AniLiberty API v1: /anime/releases/recommended (опционально с seed релизом)
 * 2) Legacy fallback: топ по рейтингу из старого каталога
 */
class SuggestionsRecommendsViewModel @Inject constructor(
    private val tvContentUseCase: TvContentUseCase,
    private val searchRepository: SearchRepository,
    private val releaseInteractor: ReleaseInteractor,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
    private val favoriteRepository: FavoriteRepository,
    private val historyRepository: HistoryRepository,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Рекомендации"

    // Здесь не нужна пагинация: лучше показать небольшой «снэк» релизов.
    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        if (requestPage != firstPage) return emptyList()

        // 1) Пробуем v1 рекомендации, используя seed по избранному/истории.
        val seedId = resolveSeedReleaseId()
        val v1Cards = runCatching { tvContentUseCase.loadV1Recommendations(seedId, RECOMMEND_LIMIT) }
            .getOrNull()
            ?.asSequence()
            ?.map { converter.toCard(it) }
            ?.filterNot { card ->
                // Сервис может вернуть дубликаты — подстрахуемся.
                (card.type as? LibriaCard.Type.Release)?.releaseId?.id == null
            }
            ?.distinctBy { (it.type as? LibriaCard.Type.Release)?.releaseId?.id }
            ?.toList()
            .orEmpty()

        if (v1Cards.isNotEmpty()) {
            return v1Cards
        }

        // 2) Fallback: старый каталог — топ по рейтингу.
        val topRated = searchRepository.searchReleases(
            form = SearchForm(sort = SearchForm.Sort.RATING),
            page = 1,
        )
        releaseInteractor.updateItemsCache(topRated.data)
        return topRated.data
            .take(RECOMMEND_LIMIT)
            .map { converter.toCard(it) }
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private suspend fun resolveSeedReleaseId(): Int? {
        // 1) Seed из избранного (если доступно)
        val favSeed = runCatching {
            favoriteRepository
                .getFavorites(page = 1)
                .data
                .firstOrNull()
                ?.id
                ?.id
        }.getOrNull()

        if (favSeed != null && favSeed > 0) return favSeed

        // 2) Seed из локальной истории (последний просмотренный)
        val historySeed = runCatching {
            historyRepository
                .getReleases(count = 1)
                .items
                .firstOrNull()
                ?.id
                ?.id
        }.getOrNull()

        return historySeed?.takeIf { it > 0 }
    }

    private companion object {
        const val RECOMMEND_LIMIT = 14
    }
}
