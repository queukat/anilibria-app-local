package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.common.AniLibertyViewHistoryCardMapper
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

@OptIn(FlowPreview::class)
class WatchingHistoryViewModel @Inject constructor(
    private val converter: CardsDataConverter,
    authRepository: AuthRepository,
    private val historyRepository: HistoryRepository,
    private val userViewsRepository: UserViewsRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "История просмотров"
    override val progressOnRefresh: Boolean = false

    private var remoteMode: Boolean = true
    private var pagingState = PagingState(page = firstPage - 1)
    private val autoRefreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        autoRefreshSignals
            .debounce(AUTO_REFRESH_DEBOUNCE_MS)
            .onEach { refreshFromAutoSignal() }
            .launchIn(viewModelScope)

        authRepository
            .observeAuthState()
            .distinctUntilChanged()
            .onEach { requestAutoRefresh() }
            .launchIn(viewModelScope)

        historyRepository
            .observeReleases()
            .map { history -> history.items.map { it.id } }
            .distinctUntilChanged()
            .onEach { requestAutoRefresh() }
            .launchIn(viewModelScope)
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        return try {
            if (remoteMode) {
                val response = userViewsRepository.getViewsHistory(
                    page = requestPage,
                    limit = REMOTE_PAGE_LIMIT,
                )
                val remoteCards = mapRemoteHistory(response, pagingState.items)

                if (remoteCards.isEmpty() && requestPage == firstPage) {
                    remoteMode = false
                    val localCards = loadLocalHistory()
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

            if (requestPage == firstPage) {
                val localCards = loadLocalHistory()
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
            if (error is CancellationException) {
                throw error
            }
            if (remoteMode) {
                remoteMode = false
                pagingState = pagingState.copy(hasMore = false)
                if (requestPage == firstPage) {
                    return try {
                        val localCards = loadLocalHistory()
                        pagingState = pagingState.copy(
                            items = localCards,
                            page = firstPage,
                            hasMore = false,
                            error = null,
                        )
                        localCards
                    } catch (localError: Throwable) {
                        if (localError is CancellationException) {
                            throw localError
                        }
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

    private fun requestAutoRefresh() {
        autoRefreshSignals.tryEmit(Unit)
    }

    private fun refreshFromAutoSignal() {
        onRefreshClick()
    }

    override fun onRefreshClick() {
        if (pagingState.isLoading) return
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

    private fun mapRemoteHistory(
        response: PaginatedResponse<UserViewHistoryItem>,
        existingItems: List<LibriaCard>,
    ): List<LibriaCard> {
        val usedReleaseIds = existingItems.mapNotNullTo(mutableSetOf()) { card ->
            (card.type as? LibriaCard.Type.Release)?.releaseId?.id
        }

        return response.data.mapNotNull { item ->
            val card = AniLibertyViewHistoryCardMapper.toHistoryCardOrNull(item) ?: return@mapNotNull null
            val releaseId = (card.type as? LibriaCard.Type.Release)?.releaseId?.id ?: return@mapNotNull null
            card.takeIf { usedReleaseIds.add(releaseId) }
        }
    }

    private suspend fun loadLocalHistory(): List<LibriaCard> {
        val releases = historyRepository.getReleases().items

        return releases.map { converter.toCard(it) }
    }

    private fun hasMoreResponse(response: PaginatedResponse<*>): Boolean {
        val page = response.meta.page
        val allPages = response.meta.allPages
        if (page != null && allPages != null) {
            return page < allPages
        }

        val limit = response.meta.perPage?.takeIf { it > 0 } ?: REMOTE_PAGE_LIMIT
        return response.data.isNotEmpty() && response.data.size >= limit
    }

    companion object {
        private const val REMOTE_PAGE_LIMIT = 50
        private const val AUTO_REFRESH_DEBOUNCE_MS = 250L
    }

    private data class PagingState(
        val items: List<LibriaCard> = emptyList(),
        val page: Int,
        val isLoading: Boolean = false,
        val hasMore: Boolean = true,
        val error: Throwable? = null,
    )
}
