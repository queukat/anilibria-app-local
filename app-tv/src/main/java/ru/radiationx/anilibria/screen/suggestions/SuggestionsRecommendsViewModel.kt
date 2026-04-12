package ru.radiationx.anilibria.screen.suggestions

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.repository.HistoryRepository
import javax.inject.Inject

class SuggestionsRecommendsViewModel
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
                subtitle = "Попробуйте позже, когда сервис соберёт больше персональных данных.",
            )

        override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
            if (requestPage != firstPage) return emptyList()

            val seedId = resolveSeedReleaseId()
            return runCatching {
                tvContentUseCase.loadRecommendations(seedId, RECOMMEND_LIMIT)
            }.getOrElse {
                emptyList()
            }
                .asSequence()
                .map { converter.toCard(it) }
                .filterNot { card ->
                    (card.type as? LibriaCard.Type.Release)?.releaseId?.id == null
                }
                .distinctBy { (it.type as? LibriaCard.Type.Release)?.releaseId?.id }
                .toList()
        }

        override fun onLibriaCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        private suspend fun resolveSeedReleaseId(): Int? {
            val favSeed =
                runCatching {
                    tvFavoritesUseCase
                        .loadFavorites(page = 1)
                        .data
                        .firstOrNull()
                        ?.id
                        ?.id
                }.getOrNull()

            if (favSeed != null && favSeed > 0) return favSeed

            val historySeed =
                runCatching {
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
