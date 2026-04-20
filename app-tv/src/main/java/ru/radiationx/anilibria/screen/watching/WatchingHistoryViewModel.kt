package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.repository.HistoryRepository
import javax.inject.Inject

@OptIn(FlowPreview::class)
class WatchingHistoryViewModel
    @Inject
    constructor(
        private val converter: CardsDataConverter,
        private val historyRepository: HistoryRepository,
        private val cardRouter: LibriaCardRouter,
    ) : BaseCardsViewModel() {
        override val defaultTitle: String = "История просмотров"
        override val progressOnRefresh: Boolean = false

        private val autoRefreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        init {
            autoRefreshSignals
                .debounce(AUTO_REFRESH_DEBOUNCE_MS)
                .onEach { onRefreshClick() }
                .launchIn(viewModelScope)

            historyRepository
                .observeReleases()
                .map { history -> history.items.map { release -> release.id } }
                .distinctUntilChanged()
                .onEach { requestAutoRefresh() }
                .launchIn(viewModelScope)
        }

        override fun onLibriaCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
            if (requestPage != firstPage) {
                return emptyList()
            }
            return loadLocalHistory()
        }

        override fun hasMoreCards(
            newCards: List<LibriaCard>,
            allCards: List<LibriaCard>,
        ): Boolean = false

        private fun requestAutoRefresh() {
            autoRefreshSignals.tryEmit(Unit)
        }

        private suspend fun loadLocalHistory(): List<LibriaCard> {
            return historyRepository.getReleases().items.map { release ->
                converter.toCard(release)
            }
        }

        private companion object {
            private const val AUTO_REFRESH_DEBOUNCE_MS = 250L
        }
    }
