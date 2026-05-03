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
import ru.radiationx.anilibria.presentation.pagination.TvPagingLoadResult
import ru.radiationx.anilibria.presentation.pagination.TvPagingState
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.repository.AuthRepository
import javax.inject.Inject

class MainFavoritesViewModel
    @Inject
    constructor(
        private val tvFavoritesUseCase: TvFavoritesUseCase,
        authRepository: AuthRepository,
        private val converter: CardsDataConverter,
        private val cardRouter: LibriaCardRouter,
    ) : BaseCardsViewModel() {
        private val cacheTtlMs: Long = 2 * 60 * 1000L
        private var lastLoadAtMs: Long = 0L

        override val defaultTitle: String = MainSectionTitles.FAVORITES

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

        override suspend fun loadPagingResult(
            requestPage: Int,
            currentState: TvPagingState<LibriaCard>,
        ): TvPagingLoadResult<LibriaCard> {
            val response = tvFavoritesUseCase.loadFavorites(requestPage)
            lastLoadAtMs = SystemClock.elapsedRealtime()

            val mapped =
                response.data
                    .sortedByDescending { it.torrentUpdate }
                    .map { converter.toCard(it) }
            val isFirstPage = requestPage == firstPage
            val allowModify =
                if (isFirstPage) {
                    needsModify(mapped, currentState.items)
                } else {
                    true
                }
            val mergedItems =
                if (isFirstPage) {
                    if (allowModify) {
                        mapped
                    } else {
                        currentState.items
                    }
                } else {
                    currentState.items + mapped
                }

            return TvPagingLoadResult(
                pageItems = mapped,
                mergedItems = mergedItems,
                canLoadMore = hasMoreResponse(response),
                appliedPage =
                    if (isFirstPage && !allowModify) {
                        currentState.currentPage
                    } else {
                        requestPage
                    },
            )
        }

        override fun onLibriaCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        private fun hasMoreResponse(response: Paginated<*>): Boolean {
            val limit = response.perPage?.takeIf { it > 0 } ?: FAVORITES_PAGE_LIMIT
            val pageHasEnoughItems = response.data.isNotEmpty() && response.data.size >= limit
            val page = response.page
            val allPages = response.allPages
            val hasNextPageByMeta =
                if (page != null && allPages != null) {
                    page < allPages
                } else {
                    true
                }
            return pageHasEnoughItems && hasNextPageByMeta
        }

        private companion object {
            private const val FAVORITES_PAGE_LIMIT = 25
        }
    }
