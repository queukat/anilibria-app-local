package ru.radiationx.anilibria.screen.details.other

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.details.DetailExtra
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

class DetailOtherViewModel
    @Inject
    constructor(
        private val argExtra: DetailExtra,
        private val releaseInteractor: ReleaseInteractor,
        private val userViewsRepository: UserViewsRepository,
    ) : LifecycleViewModel() {
        private val _dismissEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val dismissEvents: SharedFlow<Unit> = _dismissEvents.asSharedFlow()

        fun onClearClick() {
            viewModelScope.launch {
                releaseInteractor.resetAccessHistory(argExtra.id)
                userViewsRepository.deleteAllTimecodesForRelease(argExtra.id)
                _dismissEvents.emit(Unit)
            }
        }

        fun onMarkClick() {
            viewModelScope.launch {
                releaseInteractor.markAllViewed(argExtra.id)
                userViewsRepository.markAllWatchedForRelease(argExtra.id)
                _dismissEvents.emit(Unit)
            }
        }
    }
