package ru.radiationx.anilibria.screen.schedule

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.shared.ktx.asDayName
import java.util.Calendar
import javax.inject.Inject

class ScheduleViewModel @Inject constructor(
    private val tvContentUseCase: TvContentUseCase,
    private val dataConverter: CardsDataConverter,
    private val cardRouter: LibriaCardRouter,
) : LifecycleViewModel() {

    private val _scheduleRows = MutableStateFlow<List<Pair<String, List<LibriaCard>>>>(emptyList())
    val scheduleRows: StateFlow<List<Pair<String, List<LibriaCard>>>> = _scheduleRows.asStateFlow()

    override fun onColdCreate() {
        super.onColdCreate()
        loadSchedule()
    }

    private fun loadSchedule() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                tvContentUseCase.loadWeekSchedule()
            }.onSuccess { grouped ->

                val orderedDays = listOf(
                    Calendar.MONDAY,
                    Calendar.TUESDAY,
                    Calendar.WEDNESDAY,
                    Calendar.THURSDAY,
                    Calendar.FRIDAY,
                    Calendar.SATURDAY,
                    Calendar.SUNDAY,
                )

                val rows = orderedDays.mapNotNull { day ->
                    val dayReleases = grouped.firstOrNull { it.calendarDay == day }?.releases.orEmpty()
                    val cards = dayReleases.map { dataConverter.toCard(it) }
                    if (cards.isEmpty()) return@mapNotNull null
                    day.asDayName() to cards
                }

                val unknownCards = grouped.firstOrNull { it.calendarDay == null }?.releases
                    .orEmpty()
                    .map { dataConverter.toCard(it) }

                _scheduleRows.value = if (unknownCards.isNotEmpty()) {
                    rows + ("Другое" to unknownCards)
                } else {
                    rows
                }
            }
        }
    }

    fun onCardClick(card: LibriaCard) {
        cardRouter.navigate(card)
    }
}
