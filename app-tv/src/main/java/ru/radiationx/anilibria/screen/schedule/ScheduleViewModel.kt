package ru.radiationx.anilibria.screen.schedule

import androidx.lifecycle.viewModelScope
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.shared.ktx.asDayName
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class ScheduleViewModel
    @Inject
    constructor(
        private val tvContentUseCase: TvContentUseCase,
        private val dataConverter: CardsDataConverter,
        private val cardRouter: LibriaCardRouter,
    ) : LifecycleViewModel() {
        private val _scheduleRows = MutableStateFlow<List<Pair<String, List<CardItem>>>>(emptyList())
        val scheduleRows: StateFlow<List<Pair<String, List<CardItem>>>> = _scheduleRows.asStateFlow()
        private val _loadingState = MutableStateFlow(true)
        val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

        override fun onColdCreate() {
            super.onColdCreate()
            loadSchedule()
        }

        private fun loadSchedule() {
            _loadingState.value = true
            viewModelScope.launch(AppDispatchers.io) {
                runCatching {
                    tvContentUseCase.loadWeekSchedule()
                }.onSuccess { grouped ->

                    val orderedDays =
                        listOf(
                            Calendar.MONDAY,
                            Calendar.TUESDAY,
                            Calendar.WEDNESDAY,
                            Calendar.THURSDAY,
                            Calendar.FRIDAY,
                            Calendar.SATURDAY,
                            Calendar.SUNDAY,
                        )

                    val rows =
                        orderedDays.mapNotNull { day ->
                            val dayReleases = grouped.firstOrNull { it.calendarDay == day }?.releases.orEmpty()
                            val cards: List<CardItem> = dayReleases.map { dataConverter.toCard(it) }
                            if (cards.isEmpty()) return@mapNotNull null
                            day.asDayName() to cards
                        }

                    val unknownCards: List<CardItem> =
                        grouped.firstOrNull { it.calendarDay == null }?.releases
                            .orEmpty()
                            .map { dataConverter.toCard(it) }

                    _scheduleRows.value =
                        if (unknownCards.isNotEmpty()) {
                            rows + ("Другое" to unknownCards)
                        } else {
                            rows
                        }
                    _loadingState.value = false
                }.onFailure {
                    _scheduleRows.value = errorRows()
                    _loadingState.value = false
                }
            }
        }

        fun onCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        fun onRetryClick() {
            loadSchedule()
        }

        private fun errorRows(): List<Pair<String, List<CardItem>>> {
            return listOf(
                ERROR_ROW_TITLE to
                    listOf(
                        LoadingCard(
                            title = ERROR_CARD_TITLE,
                            description = ERROR_CARD_DESCRIPTION,
                            isError = true,
                        ),
                        LinkCard(RETRY_CARD_TITLE),
                    ),
            )
        }

        private companion object {
            const val ERROR_ROW_TITLE = "Ошибка загрузки"
            const val ERROR_CARD_TITLE = "Не удалось загрузить расписание"
            const val ERROR_CARD_DESCRIPTION = "Проверьте подключение и попробуйте снова"
            const val RETRY_CARD_TITLE = "Повторить"
        }
    }
