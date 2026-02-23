package ru.radiationx.anilibria.screen.auth.credentials

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.auth.mapAuthErrorMessage
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class AuthCredentialsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val guidedRouter: GuidedRouter,
) : LifecycleViewModel() {

    private val _progressState = MutableStateFlow(false)
    val progressState: StateFlow<Boolean> = _progressState.asStateFlow()
    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    fun onLoginClicked(login: String, password: String, code: String) {
        viewModelScope.launch {
            _progressState.value = true
            _error.value = ""
            coRunCatching {
                authRepository.signIn(login, password, code)
            }.onSuccess {
                guidedRouter.finishGuidedChain()
                _error.value = ""
            }.onFailure {
                Timber.e(it)
                _error.value = mapAuthErrorMessage(it)
            }
            _progressState.value = false
        }
    }
}
