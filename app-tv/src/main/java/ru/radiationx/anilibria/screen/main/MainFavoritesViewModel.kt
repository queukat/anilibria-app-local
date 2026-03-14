package ru.radiationx.anilibria.screen.main

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.repository.AuthRepository
import javax.inject.Inject

class MainFavoritesViewModel @Inject constructor(
    private val tvFavoritesUseCase: TvFavoritesUseCase,
    authRepository: AuthRepository,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    private val cacheTtlMs: Long = 2 * 60 * 1000L
    private var lastLoadAtMs: Long = 0L
    private var pagingState = PagingState(page = firstPage - 1)

    override val defaultTitle: String = "Обновления в избранном"

    override val loadOnCreate: Boolean = false

    override val preventClearOnRefresh: Boolean = true

    init {
        authRepository
            .observeAuthState()
            .drop(1)
            .filter { it == AuthState.AUTH }
            .distinctUntilChanged()
            .onEach { onRefreshClick() }
            .launchIn(viewModelScope)
    }

    override fun onResume() {
        super.onResume()

        val now = SystemClock.elapsedRealtime()
        val isExpired = lastLoadAtMs != 0L && (now - lastLoadAtMs) > cacheTtlMs

        if (cardsData.value.isEmpty() || isExpired) {
            onRefreshClick()
        }
    }

    override fun onRefreshClick() {
        if (pagingState.isLoading) return
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
            val response = tvFavoritesUseCase.loadFavorites(requestPage)
            lastLoadAtMs = SystemClock.elapsedRealtime()

            val mapped = response.data
                .sortedByDescending { it.torrentUpdate }
                .map { converter.toCard(it) }
            val allItems = if (requestPage == firstPage) mapped else pagingState.items + mapped

            pagingState = pagingState.copy(
                items = allItems,
                page = requestPage,
                hasMore = hasMoreResponse(response),
                error = null,
            )

            mapped
        } catch (error: Throwable) {
            pagingState = pagingState.copy(error = error)
            throw error
        } finally {
            pagingState = pagingState.copy(isLoading = false)
        }
    }

    override fun hasMoreCards(newCards: List<LibriaCard>, allCards: List<LibriaCard>): Boolean {
        pagingState = pagingState.copy(items = allCards)
        return pagingState.hasMore
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private fun hasMoreResponse(response: Paginated<*>): Boolean {
        val limit = response.perPage?.takeIf { it > 0 } ?: FAVORITES_PAGE_LIMIT
        val pageHasEnoughItems = response.data.isNotEmpty() && response.data.size >= limit
        val page = response.page
        val allPages = response.allPages
        val hasNextPageByMeta = if (page != null && allPages != null) {
            page < allPages
        } else {
            true
        }
        return pageHasEnoughItems && hasNextPageByMeta
    }

    private data class PagingState(
        val items: List<LibriaCard> = emptyList(),
        val page: Int,
        val isLoading: Boolean = false,
        val hasMore: Boolean = true,
        val error: Throwable? = null,
    )

    private companion object {
        private const val FAVORITES_PAGE_LIMIT = 25
    }
}
