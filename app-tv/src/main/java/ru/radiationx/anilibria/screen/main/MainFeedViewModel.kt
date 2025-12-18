package ru.radiationx.anilibria.screen.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.FeedRepository
import javax.inject.Inject

class MainFeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val releaseInteractor: ReleaseInteractor,
    private val converter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
) : BaseCardsViewModel() {

    override val defaultTitle: String = "Самое актуальное"

    // 1) Не показываем лоадер при refresh (особенно для фонового обновления)
    override val preventClearOnRefresh: Boolean = true
    override val progressOnRefresh: Boolean = false

    private var autoRefreshJob: Job? = null

    override fun onResume() {
        super.onResume()
        onRefreshClick() // первый refresh

        // 2) Запускаем тихий авто-рефреш раз в минуту, пока экран активен
        if (autoRefreshJob == null) {
            autoRefreshJob = viewModelScope.launch {
                while (isActive) {
                    delay(600_000) // можно 120_000 если сеть чувствительна
                    onRefreshClick()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override suspend fun getLoader(requestPage: Int): List<LibriaCard> = feedRepository
        .getFeed(requestPage)
        .also { items ->
            releaseInteractor.updateItemsCache(
                items.filter { it.release != null }.map { it.release!! }
            )
        }
        .map { converter.toCard(it) }

    override fun onLibriaCardClick(card: LibriaCard) {
        super.onLibriaCardClick(card)
        cardRouter.navigate(card)
    }
}
