package ru.radiationx.anilibria.screen.main

import com.github.terrakok.cicerone.Router
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.ScheduleScreen
import ru.radiationx.data.interactors.tv.TvContentUseCase
import javax.inject.Inject

class MainScheduleViewModel
    @Inject
    constructor(
        private val tvContentUseCase: TvContentUseCase,
        private val converter: CardsDataConverter,
        private val router: Router,
        private val cardRouter: LibriaCardRouter,
    ) : BaseCardsViewModel() {
        override val defaultTitle: String = MainSectionTitles.SCHEDULE

        override val loadMoreCard: LinkCard = LinkCard("Открыть полное расписание")

        override val preventClearOnRefresh: Boolean = true

        override fun getEmptyStateCard(): CardItem = emptyStateCard

        override fun onResume() {
            super.onResume()
            onRefreshClick()
        }

        override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
            val payload = tvContentUseCase.loadMainSchedule(currentTimeMs = System.currentTimeMillis())
            rowTitleMutable.value = payload.title
            return payload.releases.map { converter.toCard(it) }
        }

        override fun hasMoreCards(
            newCards: List<LibriaCard>,
            allCards: List<LibriaCard>,
        ): Boolean {
            // Здесь LinkCard используется как "Открыть полное расписание", а не пагинация.
            return true
        }

        override fun onLinkCardClick() {
            router.navigateTo(ScheduleScreen())
        }

        override fun onLoadingCardClick() {
            if (cardsData.value.singleOrNull() == emptyStateCard) {
                onLinkCardClick()
                return
            }
            super.onLoadingCardClick()
        }

        override fun onLibriaCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        private companion object {
            val emptyStateCard =
                LoadingCard(
                    title = "На сегодня релизов нет",
                    description = "Откройте полное расписание",
                    isError = false,
                )
        }
    }
