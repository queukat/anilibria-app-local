package ru.radiationx.anilibria.screen.main

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.presentation.pagination.TvPagingLoadResult
import ru.radiationx.anilibria.presentation.pagination.TvPagingState
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.repository.YoutubeRepository
import javax.inject.Inject

class MainYouTubeViewModel
    @Inject
    constructor(
        private val youtubeRepository: YoutubeRepository,
        private val converter: CardsDataConverter,
        private val cardRouter: LibriaCardRouter,
    ) : BaseCardsViewModel() {
        override val defaultTitle: String = MainSectionTitles.YOUTUBE

        override val preventClearOnRefresh: Boolean = true
        override val progressOnAppend: Boolean = false

        override fun onResume() {
            super.onResume()
            onRefreshClick()
        }

        override suspend fun loadPagingResult(
            requestPage: Int,
            currentState: TvPagingState<LibriaCard>,
        ): TvPagingLoadResult<LibriaCard> {
            val response = youtubeRepository.getYoutubeList(requestPage)
            val mapped = response.data.map { converter.toCard(it) }
            val isFirstPage = requestPage == firstPage
            val freshCards =
                if (isFirstPage) {
                    mapped
                } else {
                    mapped.filter { candidate ->
                        currentState.items.none { it.stableKey == candidate.stableKey }
                    }
                }
            val allowModify =
                if (isFirstPage) {
                    needsModify(freshCards, currentState.items)
                } else {
                    true
                }
            val mergedItems =
                if (isFirstPage) {
                    if (allowModify) {
                        freshCards
                    } else {
                        currentState.items
                    }
                } else {
                    currentState.items + freshCards
                }

            return TvPagingLoadResult(
                pageItems = freshCards,
                mergedItems = mergedItems,
                canLoadMore = hasMoreResponse(response, freshCards),
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

        private fun hasMoreResponse(
            response: Paginated<*>,
            freshCards: List<LibriaCard>,
        ): Boolean {
            val page = response.page
            val allPages = response.allPages
            if (page != null && allPages != null) {
                return page < allPages
            }

            val limit = response.perPage?.takeIf { it > 0 } ?: response.data.size
            return response.data.isNotEmpty() &&
                response.data.size >= limit &&
                freshCards.isNotEmpty()
        }
    }
