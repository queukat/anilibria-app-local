package ru.radiationx.anilibria.screen.watching

import ru.radiationx.anilibria.common.AniLibertyViewHistoryCardMapper
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

class WatchingHistoryViewModel @Inject constructor(
    private val converter: CardsDataConverter,
    private val historyRepository: HistoryRepository,
    private val episodesCheckerHolder: EpisodesCheckerHolder,
    private val userViewsRepository: UserViewsRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "История просмотров"

    private var remoteMode: Boolean = true
    private var remoteHasMore: Boolean = true

    override fun onRefreshClick() {
        // если был фолбек на local — при refresh попробуем remote снова
        remoteMode = true
        remoteHasMore = true
        super.onRefreshClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        if (remoteMode) {
            val remoteCards = try {
                val response = userViewsRepository.getViewsHistory(
                    page = requestPage,
                    limit = REMOTE_PAGE_LIMIT,
                )
                remoteHasMore = isHasMore(response)
                mapRemoteHistory(response)
            } catch (error: Throwable) {
                // если упали/401 — переключаемся в local только на первой странице
                remoteMode = false
                remoteHasMore = false
                if (requestPage == firstPage) {
                    return loadLocalHistory()
                }
                throw error
            }

            // remote пустой — на первой странице пробуем local
            if (remoteCards.isEmpty() && requestPage == firstPage) {
                remoteMode = false
                remoteHasMore = false
                return loadLocalHistory()
            }

            return remoteCards
        }

        // Local-режим без пагинации: отдаём данные только на первой странице.
        return if (requestPage == firstPage) {
            loadLocalHistory()
        } else {
            emptyList()
        }
    }

    override fun hasMoreCards(
        newCards: List<LibriaCard>,
        allCards: List<LibriaCard>,
    ): Boolean {
        return remoteMode && remoteHasMore
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private fun mapRemoteHistory(
        response: PaginatedResponse<UserViewHistoryItem>,
    ): List<LibriaCard> {
        val usedReleaseIds = mutableSetOf<Int>()

        return response.data
            .asSequence()
            .mapNotNull { AniLibertyViewHistoryCardMapper.toHistoryCardOrNull(it) }
            .filter { card ->
                val id = (card.type as? LibriaCard.Type.Release)?.releaseId?.id
                id != null && usedReleaseIds.add(id)
            }
            .toList()
    }

    private suspend fun loadLocalHistory(): List<LibriaCard> {
        val releaseIds = episodesCheckerHolder
            .getEpisodes()
            .sortedByDescending { it.lastAccessRaw }
            .map { it.id.releaseId }
            .distinct()

        if (releaseIds.isEmpty()) return emptyList()

        val releases = historyRepository
            .getReleases()
            .items
            .filter { releaseIds.contains(it.id) }

        return releases.map { converter.toCard(it) }
    }

    private fun isHasMore(response: PaginatedResponse<*>): Boolean {
        val page = response.meta.page ?: return response.data.size >= REMOTE_PAGE_LIMIT
        val allPages = response.meta.allPages ?: return response.data.size >= REMOTE_PAGE_LIMIT
        return page < allPages
    }

    companion object {
        private const val REMOTE_PAGE_LIMIT = 50
    }
}
