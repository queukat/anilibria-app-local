package ru.radiationx.anilibria.screen.watching

import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.radiationx.data.repository.HistoryRepository
import javax.inject.Inject

class WatchingHistoryViewModel @Inject constructor(
    private val converter: CardsDataConverter,
    private val historyRepository: HistoryRepository,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "История просмотров"
    override val progressOnRefresh: Boolean = false

    init {
        historyRepository
            .observeReleases()
            .map { history -> history.items.map { it.id } }
            .distinctUntilChanged()
            .onEach { onRefreshClick() }
            .launchIn(viewModelScope)
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
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
        return false
    }

    override fun onLibriaCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }

    private suspend fun loadLocalHistory(): List<LibriaCard> {
        val releases = historyRepository.getReleases().items

        return releases.map { converter.toCard(it) }
    }
}
