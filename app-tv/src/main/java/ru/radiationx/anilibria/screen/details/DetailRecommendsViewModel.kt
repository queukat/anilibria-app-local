package ru.radiationx.anilibria.screen.details

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.interactors.tv.TvContentUseCase
import javax.inject.Inject

class DetailRecommendsViewModel @Inject constructor(
    private val tvContentUseCase: TvContentUseCase,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
    private val extra: DetailExtra,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Похожие тайтлы"

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean = false

    override fun getEmptyStateCard() = InfoCard(
        title = "Рекомендации пока недоступны",
        subtitle = "Для этого релиза сервис не вернул похожие тайтлы.",
    )

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        val cards = if (requestPage == firstPage) {
            val seededCards = loadRecommendationCards(seedReleaseId = extra.id.id)
            if (seededCards.isNotEmpty()) {
                seededCards
            } else {
                loadRecommendationCards(seedReleaseId = null)
            }
        } else {
            emptyList()
        }
        return cards
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private suspend fun loadRecommendationCards(seedReleaseId: Int?): List<LibriaCard> {
        return runCatching {
            tvContentUseCase.loadRecommendations(seedReleaseId = seedReleaseId, limit = RECOMMEND_LIMIT)
        }.getOrElse {
            emptyList()
        }
            .asSequence()
            .filterNot { it.id == extra.id }
            .distinctBy { it.id }
            .map(converter::toCard)
            .filterNot { card ->
                (card.type as? LibriaCard.Type.Release)?.releaseId == extra.id
            }
            .distinctBy { card ->
                (card.type as? LibriaCard.Type.Release)?.releaseId?.id ?: card.hashCode()
            }
            .toList()
    }

    private companion object {
        const val RECOMMEND_LIMIT = 14
    }
}
