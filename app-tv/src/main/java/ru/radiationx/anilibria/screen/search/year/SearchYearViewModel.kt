package ru.radiationx.anilibria.screen.search.year

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.search.BaseSearchValuesViewModel
import ru.radiationx.anilibria.screen.search.SearchController
import ru.radiationx.anilibria.screen.search.SearchValuesExtra
import ru.radiationx.data.entity.domain.release.YearItem
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class SearchYearViewModel @Inject constructor(
    argExtra: SearchValuesExtra,
    private val tvSearchUseCase: TvSearchUseCase,
    private val searchController: SearchController,
    private val guidedRouter: GuidedRouter,
) : BaseSearchValuesViewModel(argExtra) {

    private val currentYears = mutableListOf<YearItem>()

    init {
        tvSearchUseCase
            .observeYears()
            .onEach { years ->
                currentYears.clear()
                currentYears.addAll(years)
                currentValues.clear()
                currentValues.addAll(years.map { it.value })
                _valuesData.value = years.map { it.title }
                _progressState.value = false
                updateChecked()
                updateSelected()
            }
            .launchIn(viewModelScope)


        viewModelScope.launch {
            _progressState.value = true
            coRunCatching {
                tvSearchUseCase.loadYears()
            }.onFailure {
                Timber.e(it)
            }
            _progressState.value = false
        }
    }

    override fun applyValues() {
        guidedRouter.close()
        val newYears = currentYears.filter { item ->
            checkedValues.contains(item.value)
        }.toSet()
        searchController.yearsEvent.emit(newYears)
    }
}
