package ru.radiationx.anilibria.screen.config

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.entity.common.ConfigScreenState
import ru.radiationx.data.interactors.ConfiguringInteractor
import ru.radiationx.shared.ktx.EventFlow
import javax.inject.Inject

class ConfiguringViewModel
    @Inject
    constructor(
        private val apiConfig: ApiConfig,
        private val configuringInteractor: ConfiguringInteractor,
    ) : LifecycleViewModel() {
        private var configuringStarted = false
        private val _screenStateData = MutableStateFlow<ConfigScreenState?>(null)
        val screenStateData: StateFlow<ConfigScreenState?> = _screenStateData.asStateFlow()
        val completeEvent = EventFlow<Unit>()

        fun startConfiguring() {
            if (configuringStarted) {
                return
            }
            configuringStarted = true
            apiConfig
                .observeNeedConfig()
                .onEach {
                    if (!it) {
                        completeEvent.emit(Unit)
                    }
                }
                .launchIn(viewModelScope)

            configuringInteractor
                .observeScreenState()
                .onEach {
                    _screenStateData.value = it
                }
                .launchIn(viewModelScope)

            configuringInteractor.initCheck()
        }

        fun repeatCheck() = configuringInteractor.repeatCheck()

        fun nextCheck() = configuringInteractor.nextCheck()

        fun skipCheck() = configuringInteractor.skipCheck()

        override fun onCleared() {
            super.onCleared()
            configuringInteractor.finishCheck()
        }
    }
