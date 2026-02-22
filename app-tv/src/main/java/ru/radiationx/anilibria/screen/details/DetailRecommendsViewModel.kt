package ru.radiationx.anilibria.screen.details

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.TvContentUseCase
import javax.inject.Inject

/**
 * Рекомендации на экране деталей:
 *
 * Приоритет:
 * 1) AniLiberty API v1: /anime/releases/recommended?release_id=...
 * 2) Legacy fallback: «похожие» через старый каталог по year/season/genres
 */
class DetailRecommendsViewModel @Inject constructor(
    private val tvContentUseCase: TvContentUseCase,
    private val releaseInteractor: ReleaseInteractor,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
    private val extra: DetailExtra,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Похожие тайтлы"

    /**
     * Источник рекомендаций:
     *  - null: ещё не выбрали (попробуем v1 на первой загрузке)
     *  - true: AniLiberty v1 recommendations
     *  - false: legacy search fallback
     */
    private var v1Mode: Boolean? = null

    override fun onRefreshClick() {
        v1Mode = null
        super.onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        return when (v1Mode) {
            true -> {
                // В v1 режиме не пагинируем — рекомендации «одним набором».
                if (requestPage == firstPage) loadV1Recommended() else emptyList()
            }

            false -> loadLegacySimilar(requestPage)

            null -> {
                // Пытаемся один раз перейти на v1; если не получилось — используем legacy.
                val v1 = if (requestPage == firstPage) loadV1Recommended() else emptyList()
                if (v1.isNotEmpty()) {
                    v1Mode = true
                    v1
                } else {
                    v1Mode = false
                    loadLegacySimilar(requestPage)
                }
            }
        }
    }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean {
        // В v1 режиме «Load more» не нужен.
        return v1Mode != true && super.hasMoreCards(newCards, allCards)
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private suspend fun loadV1Recommended(): List<LibriaCard> {
        val seeded = tvContentUseCase
            .loadV1Recommendations(seedReleaseId = extra.id.id, limit = RECOMMEND_LIMIT)
            .filterNot { it.id == extra.id }

        val releases = if (seeded.isNotEmpty()) {
            seeded
        } else {
            tvContentUseCase
                .loadV1Recommendations(seedReleaseId = null, limit = RECOMMEND_LIMIT)
                .filterNot { it.id == extra.id }
        }

        return releases
            .distinctBy { it.id }
            .map { converter.toCard(it) }
    }

    private suspend fun loadLegacySimilar(requestPage: Int): List<LibriaCard> {
        val filtered = withContext(Dispatchers.IO) {
            tvContentUseCase.loadLegacyRecommendations(releaseId = extra.id, requestPage = requestPage)
        }
        releaseInteractor.updateItemsCache(filtered)

        // 3) Подмешиваем немного «случайного» для разнообразия, но без дублей по id
        val randomPick = filtered.shuffled().take(2)
        val finalList = (filtered + randomPick).distinctBy { it.id }

        return finalList.map { converter.toCard(it) }
    }

    private companion object {
        const val RECOMMEND_LIMIT = 14
    }
}
