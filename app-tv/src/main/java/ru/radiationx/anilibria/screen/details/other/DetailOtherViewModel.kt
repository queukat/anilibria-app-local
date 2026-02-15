package ru.radiationx.anilibria.screen.details.other

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.details.DetailExtra
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

class DetailOtherViewModel @Inject constructor(
    private val argExtra: DetailExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val userViewsRepository: UserViewsRepository,
    private val guidedRouter: GuidedRouter,
) : LifecycleViewModel() {


    fun onClearClick() {
        viewModelScope.launch {
            releaseInteractor.resetAccessHistory(argExtra.id)
            userViewsRepository.deleteAllTimecodesForRelease(argExtra.id)
            guidedRouter.close()
        }
    }

    fun onMarkClick() {
        viewModelScope.launch {
            releaseInteractor.markAllViewed(argExtra.id)
            userViewsRepository.markAllWatchedForRelease(argExtra.id)
            guidedRouter.close()
        }
    }
}
