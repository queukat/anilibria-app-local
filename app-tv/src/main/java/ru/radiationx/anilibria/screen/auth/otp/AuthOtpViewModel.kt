package ru.radiationx.anilibria.screen.auth.otp

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.auth.mapAuthErrorMessage
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.auth.OtpInfo
import ru.radiationx.data.entity.domain.auth.OtpNotAcceptedException
import ru.radiationx.data.entity.domain.auth.OtpNotFoundException
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class AuthOtpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val guidedRouter: GuidedRouter,
) : LifecycleViewModel() {

    private val _otpInfoData = MutableStateFlow<OtpInfo?>(null)
    val otpInfoData: StateFlow<OtpInfo?> = _otpInfoData.asStateFlow()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var timerJob: Job? = null
    private var signInJob: Job? = null

    init {
        loadOtpInfo()
    }

    fun onCompleteClick() {
        updateState(progress = true, error = "")
        signIn()
    }

    fun onExpiredClick() {
        updateState(progress = true, error = "")
        loadOtpInfo()
    }

    fun onRepeatClick() {
        updateState(progress = true, error = "")
        loadOtpInfo()
    }

    fun onBackClick() {
        guidedRouter.close()
    }

    private fun signIn() {
        val code = _otpInfoData.value?.code ?: return
        signInJob?.cancel()
        signInJob = viewModelScope.launch {
            coRunCatching {
                authRepository.signInOtp(code)
            }.onSuccess {
                guidedRouter.finishGuidedChain()
            }.onFailure {
                handleError(it)
            }
        }
    }

    private fun loadOtpInfo() {
        signInJob?.cancel()
        signInJob = viewModelScope.launch {
            coRunCatching {
                authRepository.getOtpInfo()
            }.onSuccess {
                _otpInfoData.value = it
                startTimer(it)
                updateState(ButtonState.COMPLETE, false)
            }.onFailure {
                handleError(it)
            }
        }
    }

    private fun handleError(error: Throwable) {
        Timber.e(error)
        val buttonState = when (error) {
            is OtpNotFoundException -> ButtonState.EXPIRED
            is OtpNotAcceptedException -> ButtonState.COMPLETE
            else -> ButtonState.REPEAT
        }
        updateState(buttonState, false, mapAuthErrorMessage(error))
    }

    private fun startTimer(otpInfo: OtpInfo) {
        timerJob?.cancel()
        val time = otpInfo.expiresAt.time - System.currentTimeMillis()
        if (time < 0) {
            setExpired()
            return
        }
        timerJob = viewModelScope.launch {
            delay(otpInfo.remainingTime)
            setExpired()
        }
    }

    private fun setExpired() {
        signInJob?.cancel()
        updateState(ButtonState.EXPIRED, false, "")
    }

    private fun updateState(
        buttonState: ButtonState = _state.value.buttonState,
        progress: Boolean = _state.value.progress,
        error: String = _state.value.error,
    ) {
        _state.value = State(buttonState, progress, error)
    }

    data class State(
        val buttonState: ButtonState = ButtonState.COMPLETE,
        val progress: Boolean = false,
        val error: String = "",
    )

    enum class ButtonState {
        COMPLETE,
        EXPIRED,
        REPEAT
    }
}
