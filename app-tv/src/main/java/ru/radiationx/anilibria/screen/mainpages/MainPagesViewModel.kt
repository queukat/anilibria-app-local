package ru.radiationx.anilibria.screen.mainpages

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.SearchScreen
import ru.radiationx.anilibria.screen.SuggestionsScreen
import ru.radiationx.anilibria.screen.UpdateScreen
import ru.radiationx.data.repository.CheckerRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class MainPagesViewModel @Inject constructor(
    private val checkerRepository: CheckerRepository,
    private val router: Router,
) : LifecycleViewModel() {

    private val _hasUpdatesData = MutableStateFlow(false)
    val hasUpdatesData: StateFlow<Boolean> = _hasUpdatesData.asStateFlow()

    init {
        viewModelScope.launch {
            coRunCatching {
                checkerRepository.checkUpdate(true)
            }.onSuccess {
                _hasUpdatesData.value = it.hasUpdate
            }.onFailure {
                Timber.e(it)
            }
        }
    }

    fun onAppUpdateClick() {
        router.navigateTo(UpdateScreen())
    }

    fun onCatalogClick() {
        router.navigateTo(SearchScreen())
    }

    fun onSearchClick() {
        router.navigateTo(SuggestionsScreen())
    }
}
