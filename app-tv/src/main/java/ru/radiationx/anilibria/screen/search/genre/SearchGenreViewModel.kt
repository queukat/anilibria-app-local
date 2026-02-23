package ru.radiationx.anilibria.screen.search.genre

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.search.BaseSearchValuesViewModel
import ru.radiationx.anilibria.screen.search.SearchController
import ru.radiationx.anilibria.screen.search.SearchValuesExtra
import ru.radiationx.data.entity.domain.release.GenreItem
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class SearchGenreViewModel @Inject constructor(
    argExtra: SearchValuesExtra,
    private val tvSearchUseCase: TvSearchUseCase,
    private val searchController: SearchController,
    private val guidedRouter: GuidedRouter,
) : BaseSearchValuesViewModel(argExtra) {

    private val currentGenres = mutableListOf<GenreItem>()

    init {
        tvSearchUseCase
            .observeGenres()
            .onEach { genres ->
                currentGenres.clear()
                currentGenres.addAll(genres)
                currentValues.clear()
                currentValues.addAll(genres.map { it.value })
                valuesData.value = genres.map { it.title }
                progressState.value = false
                updateChecked()
                updateSelected()
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            progressState.value = true
            coRunCatching {
                tvSearchUseCase.loadGenres()
            }.onFailure {
                Timber.e(it)
            }
            progressState.value = false
        }
    }

    override fun applyValues() {
        guidedRouter.close()
        val newGenres = currentGenres.filter { item ->
            checkedValues.contains(item.value)
        }.toSet()
        searchController.genresEvent.emit(newGenres)
    }
}
