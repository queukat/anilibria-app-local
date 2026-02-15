package ru.radiationx.anilibria.screen.details

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.entity.domain.release.SeasonItem
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.entity.domain.search.SearchForm
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.ReleaseRepository
import ru.radiationx.data.repository.SearchRepository
import javax.inject.Inject

/**
 * Рекомендации на экране деталей:
 *
 * Приоритет:
 * 1) AniLiberty API v1: /anime/releases/recommended?release_id=...
 * 2) Legacy fallback: «похожие» через старый каталог по year/season/genres
 */
class DetailRecommendsViewModel @Inject constructor(
    private val aniLibertyApi: AniLibertyApi,
    private val releaseRepository: ReleaseRepository,
    private val searchRepository: SearchRepository,
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

    /** Кэш формы для legacy поиска, чтобы не дёргать релиз на каждую страницу. */
    private var legacyFormCache: SearchForm? = null

    override fun onRefreshClick() {
        v1Mode = null
        legacyFormCache = null
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
        val seedId = extra.id.id
        val releases = runCatching {
            aniLibertyApi.getRecommendedReleases(
                limit = RECOMMEND_LIMIT,
                releaseId = AniLibertyReleaseId(seedId),
                fields = AniLibertyReleaseFields.Suggestions,
            )
        }.getOrNull().orEmpty()

        return releases
            .asSequence()
            .mapNotNull { converter.toCardOrNull(it) }
            .filterNot { card ->
                (card.type as? LibriaCard.Type.Release)?.releaseId == extra.id
            }
            .distinctBy { (it.type as? LibriaCard.Type.Release)?.releaseId?.id }
            .toList()
    }

    private suspend fun loadLegacySimilar(requestPage: Int): List<LibriaCard> {
        // 1) Берём релиз (один раз), собираем форму (year/season/genres)
        val form = legacyFormCache ?: withContext(Dispatchers.IO) {
            releaseRepository.getRelease(extra.id)
        }.let { currentRelease ->
            SearchForm(
                years = currentRelease.year?.let { setOf(YearItem(it, it)) }.orEmpty(),
                seasons = currentRelease.season?.let { setOf(SeasonItem(it, it)) }.orEmpty(),
                genres = currentRelease.genres.map { g -> GenreItem(g, g) }.toSet(),
                sort = SearchForm.Sort.RATING,
                onlyCompleted = false,
            )
        }.also { legacyFormCache = it }

        // 2) Поиск + исключаем сам релиз
        val searchResult = withContext(Dispatchers.IO) {
            searchRepository.searchReleases(form, requestPage)
        }
        releaseInteractor.updateItemsCache(searchResult.data)

        val filtered = searchResult.data.filterNot { it.id == extra.id }

        // 3) Подмешиваем немного «случайного» для разнообразия, но без дублей по id
        val randomPick = filtered.shuffled().take(2)
        val finalList = (filtered + randomPick).distinctBy { it.id }

        return finalList.map { converter.toCard(it) }
    }

    private companion object {
        const val RECOMMEND_LIMIT = 20
    }
}
