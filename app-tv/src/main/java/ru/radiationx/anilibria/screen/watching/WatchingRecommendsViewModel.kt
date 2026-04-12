package ru.radiationx.anilibria.screen.watching

import kotlinx.coroutines.CancellationException
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.repository.HistoryRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Рекомендации на экране "Я смотрю":
 * - если есть локальная история, берём самый свежий тайтл как seed,
 * - если истории нет, пробуем избранное,
 * - если персональный seed недоступен, используем глобальные рекомендации.
 */
class WatchingRecommendsViewModel
    @Inject
    constructor(
        private val tvContentUseCase: TvContentUseCase,
        private val converter: CardsDataConverter,
        private val cardRouter: LibriaCardRouter,
        private val tvFavoritesUseCase: TvFavoritesUseCase,
        private val historyRepository: HistoryRepository,
    ) : BaseCardsViewModel() {
        override val defaultTitle: String = "Рекомендации"

        override fun hasMoreCards(
            newCards: List<LibriaCard>,
            allCards: List<LibriaCard>,
        ): Boolean = false

        override fun getEmptyStateCard() =
            InfoCard(
                title = "Рекомендации пока недоступны",
                subtitle = "Сервис не вернул релевантные тайтлы для этой страницы.",
            )

        override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
            if (requestPage != firstPage) return emptyList()

            val seedReleaseId = resolveSeedReleaseId()
            val releases =
                try {
                    tvContentUseCase.loadRecommendations(seedReleaseId = seedReleaseId, limit = RECOMMEND_LIMIT)
                } catch (error: Throwable) {
                    if (error is CancellationException) {
                        throw error
                    }
                    Timber.w(error, "Failed to load TV Watching recommendations")
                    emptyList()
                }

            return releases
                .asSequence()
                .map(converter::toCard)
                .filterNot { card ->
                    (card.type as? LibriaCard.Type.Release)?.releaseId?.id == null
                }
                .distinctBy { card ->
                    (card.type as? LibriaCard.Type.Release)?.releaseId?.id
                }
                .toList()
        }

        override fun onLibriaCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        private suspend fun resolveSeedReleaseId(): Int? {
            val historySeed =
                runCatching {
                    historyRepository
                        .getReleases(count = 1)
                        .items
                        .firstOrNull()
                        ?.id
                        ?.id
                }.getOrNull()

            if (historySeed != null && historySeed > 0) {
                return historySeed
            }

            val favoriteSeed =
                runCatching {
                    tvFavoritesUseCase
                        .loadFavorites(page = 1)
                        .data
                        .firstOrNull()
                        ?.id
                        ?.id
                }.getOrNull()

            return favoriteSeed?.takeIf { it > 0 }
        }

        private companion object {
            const val RECOMMEND_LIMIT = 14
        }
    }
