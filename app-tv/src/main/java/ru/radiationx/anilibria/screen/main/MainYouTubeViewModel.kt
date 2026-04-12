package ru.radiationx.anilibria.screen.main

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
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
        private var pagingState = PagingState(page = firstPage - 1)

        override val defaultTitle: String = MainSectionTitles.YOUTUBE

        override val preventClearOnRefresh: Boolean = true
        override val progressOnAppend: Boolean = false

        override fun onResume() {
            super.onResume()
            onRefreshClick()
        }

        override fun onRefreshClick() {
            if (pagingState.isLoading) return
            pagingState =
                PagingState(
                    page = firstPage - 1,
                    isLoading = true,
                    hasMore = true,
                )
            super.onRefreshClick()
        }

        override fun onLinkCardClick() {
            val state = pagingState
            if (state.isLoading || !state.hasMore) return
            pagingState =
                state.copy(
                    isLoading = true,
                    error = null,
                )
            super.onLinkCardClick()
        }

        override fun onLoadingCardClick() {
            if (pagingState.isLoading) return
            pagingState =
                pagingState.copy(
                    isLoading = true,
                    error = null,
                )
            super.onLoadingCardClick()
        }

        override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
            return try {
                val response = youtubeRepository.getYoutubeList(requestPage)
                val mapped = response.data.map { converter.toCard(it) }
                val freshCards =
                    mapped.filter { candidate ->
                        pagingState.items.none { it.itemId == candidate.itemId }
                    }
                val allItems =
                    if (requestPage == firstPage) {
                        freshCards
                    } else {
                        pagingState.items + freshCards
                    }

                pagingState =
                    pagingState.copy(
                        items = allItems,
                        page = requestPage,
                        hasMore = hasMoreResponse(response, freshCards),
                        error = null,
                    )

                freshCards
            } catch (error: Throwable) {
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
            return pagingState.hasMore
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

        private data class PagingState(
            val items: List<LibriaCard> = emptyList(),
            val page: Int,
            val isLoading: Boolean = false,
            val hasMore: Boolean = true,
            val error: Throwable? = null,
        )
    }
