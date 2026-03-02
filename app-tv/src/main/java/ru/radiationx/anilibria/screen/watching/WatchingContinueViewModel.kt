package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.viewModelScope
import ru.radiationx.anilibria.common.AniLibertyViewHistoryCardMapper
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.UserViewsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WatchingContinueViewModel @Inject constructor(
    private val converter: CardsDataConverter,
    private val releaseInteractor: ReleaseInteractor,
    private val historyRepository: HistoryRepository,
    private val episodesCheckerHolder: EpisodesCheckerHolder,
    private val userViewsRepository: UserViewsRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Продолжить просмотр"

    private var remoteMode: Boolean = true
    private var pagingState = PagingState(page = firstPage - 1)
    private val localProgressReleaseIds = MutableStateFlow<Set<Int>>(emptySet())

    init {
        episodesCheckerHolder.observeEpisodes()
            .map(::toLocalProgressState)
            .distinctUntilChanged()
            .onEach { state ->
                val hadLocalProgress = localProgressReleaseIds.value.isNotEmpty()
                localProgressReleaseIds.value = state.releaseIds
                if (hadLocalProgress && state.releaseIds.isEmpty()) {
                    _cardsData.value = emptyList()
                }
                onRefreshClick()
            }
            .launchIn(viewModelScope)
    }

    override fun onRefreshClick() {
        if (pagingState.isLoading) return
        // если был фолбек на local — при refresh попробуем remote снова
        remoteMode = true
        pagingState = pagingState.copy(
            page = firstPage - 1,
            isLoading = true,
            hasMore = true,
            error = null,
        )
        super.onRefreshClick()
    }

    override fun onLinkCardClick() {
        val state = pagingState
        if (state.isLoading || !state.hasMore) return
        pagingState = state.copy(
            isLoading = true,
            error = null,
        )
        super.onLinkCardClick()
    }

    override fun onLoadingCardClick() {
        if (pagingState.isLoading) return
        pagingState = pagingState.copy(
            isLoading = true,
            error = null,
        )
        super.onLoadingCardClick()
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        return try {
            val localState = toLocalProgressState(episodesCheckerHolder.getEpisodes())

            if (remoteMode) {
                val response = userViewsRepository.getViewsHistory(
                    page = requestPage,
                    limit = REMOTE_PAGE_LIMIT,
                )
                val remoteCards = mapRemoteContinue(
                    response = response,
                    localState = localState,
                )

                // remote пустой — на первой странице попробуем local (на сервере может не быть данных)
                if (remoteCards.isEmpty() && requestPage == firstPage) {
                    remoteMode = false
                    val localCards = loadLocalContinue()
                    pagingState = pagingState.copy(
                        items = localCards,
                        page = firstPage,
                        hasMore = false,
                        error = null,
                    )
                    return localCards
                }

                val allItems = if (requestPage == firstPage) remoteCards else pagingState.items + remoteCards
                pagingState = pagingState.copy(
                    items = allItems,
                    page = requestPage,
                    hasMore = hasMoreResponse(response),
                    error = null,
                )
                return remoteCards
            }

            // Local-режим без пагинации: отдаём данные только на первой странице.
            if (requestPage == firstPage) {
                val localCards = loadLocalContinue()
                pagingState = pagingState.copy(
                    items = localCards,
                    page = firstPage,
                    hasMore = false,
                    error = null,
                )
                localCards
            } else {
                pagingState = pagingState.copy(
                    hasMore = false,
                    error = null,
                )
                emptyList()
            }
        } catch (error: Throwable) {
            if (remoteMode) {
                // если упали/401 — переключаемся в local только на первой странице
                remoteMode = false
                pagingState = pagingState.copy(hasMore = false)
                if (requestPage == firstPage) {
                    return runCatching { loadLocalContinue() }
                        .onSuccess { localCards ->
                            pagingState = pagingState.copy(
                                items = localCards,
                                page = firstPage,
                                hasMore = false,
                                error = null,
                            )
                        }
                        .getOrElse { localError ->
                            pagingState = pagingState.copy(error = localError)
                            throw localError
                        }
                }
            }
            pagingState = pagingState.copy(error = error)
            throw error
        } finally {
            pagingState = pagingState.copy(isLoading = false)
        }
    }

    override fun hasMoreCards(
        newCards: List<LibriaCard>,
        allCards: List<LibriaCard>,
    ): Boolean {
        pagingState = pagingState.copy(items = allCards)
        return remoteMode && pagingState.hasMore
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private suspend fun mapRemoteContinue(
        response: PaginatedResponse<UserViewHistoryItem>,
        localState: LocalProgressState,
    ): List<LibriaCard> {
        val usedReleaseIds = mutableSetOf<Int>()
        val result = mutableListOf<LibriaCard>()

        response.data.forEach { item ->
            if (item.isWatched) return@forEach

            val card = AniLibertyViewHistoryCardMapper.toContinueCardOrNull(item) ?: return@forEach
            val releaseId = (card.type as? LibriaCard.Type.Release)?.releaseId?.id
            if (releaseId == null || !usedReleaseIds.add(releaseId) || !localState.releaseIds.contains(releaseId)) {
                return@forEach
            }

            val localAccess = localState.latestByRelease[releaseId]
            if (localAccess == null) {
                result += card
            } else {
                result += card.copy(description = buildLocalContinueDescription(localAccess))
            }
        }

        return result
    }

    private fun toLocalProgressState(
        episodes: List<EpisodeAccess>,
    ): LocalProgressState {
        val releaseIds = episodes.asSequence()
            .map { it.id.releaseId.id }
            .toSet()

        val latestByRelease = episodes
            .groupBy { it.id.releaseId.id }
            .mapValues { (_, accesses) ->
                accesses.maxByOrNull { it.lastAccessRaw } ?: accesses.first()
            }

        return LocalProgressState(
            releaseIds = releaseIds,
            latestByRelease = latestByRelease,
        )
    }

    private suspend fun buildLocalContinueDescription(access: EpisodeAccess): String {
        val episodeOrdinal = resolveLocalEpisodeOrdinal(access.id)
        val positionText = access.seek
            .takeIf { it > 0L }
            ?.let(::formatPosition)

        return when {
            episodeOrdinal != null && positionText != null ->
                "Вы остановились на серии $episodeOrdinal • $positionText"

            episodeOrdinal != null ->
                "Вы остановились на серии $episodeOrdinal"

            positionText != null ->
                "Вы остановились • $positionText"

            else ->
                "Вы остановились"
        }
    }

    private suspend fun resolveLocalEpisodeOrdinal(episodeId: EpisodeId): String? {
        val raw = episodeId.id.trim()
        if (raw.isEmpty()) return null

        normalizeOrdinalOrNull(raw)?.let { return it }

        return runCatching {
            userViewsRepository.resolveEpisodeOrdinal(episodeId)
        }.getOrNull()
    }

    private fun normalizeOrdinalOrNull(value: String): String? {
        return runCatching {
            BigDecimal(value).stripTrailingZeros().toPlainString()
        }.getOrNull()
    }

    private fun formatPosition(positionMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(positionMs).coerceAtLeast(0L)
        val hours = TimeUnit.SECONDS.toHours(totalSeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private suspend fun loadLocalContinue(): List<LibriaCard> {
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

        val pairs = releases.map { release ->
            val lastEpisode = releaseInteractor
                .getAccesses(release.id)
                .maxByOrNull { it.lastAccessRaw }
            release to lastEpisode
        }

        return pairs
            .sortedByDescending { it.second?.lastAccessRaw ?: 0L }
            .map { (release, lastEpisode) ->
                converter.toCard(release).copy(
                    description = lastEpisode?.let { buildLocalContinueDescription(it) }.orEmpty()
                )
            }
    }

    private fun hasMoreResponse(response: PaginatedResponse<*>): Boolean {
        val limit = response.meta.perPage?.takeIf { it > 0 } ?: REMOTE_PAGE_LIMIT
        if (response.data.isEmpty()) return false
        if (response.data.size < limit) return false

        val page = response.meta.page
        val allPages = response.meta.allPages
        if (page != null && allPages != null) {
            return page < allPages
        }
        return true
    }

    companion object {
        private const val REMOTE_PAGE_LIMIT = 50
    }

    private data class PagingState(
        val items: List<LibriaCard> = emptyList(),
        val page: Int,
        val isLoading: Boolean = false,
        val hasMore: Boolean = true,
        val error: Throwable? = null,
    )

    private data class LocalProgressState(
        val releaseIds: Set<Int>,
        val latestByRelease: Map<Int, EpisodeAccess>,
    )
}
