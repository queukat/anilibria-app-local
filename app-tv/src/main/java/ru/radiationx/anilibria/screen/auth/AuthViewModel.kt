package ru.radiationx.anilibria.screen.auth

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.auth.OtpInfo
import ru.radiationx.data.entity.domain.auth.OtpNotAcceptedException
import ru.radiationx.data.entity.domain.auth.OtpNotFoundException
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

internal enum class AuthScreenMode {
    Menu,
    Credentials,
    Otp,
}

internal data class AuthCredentialsState(
    val isLoading: Boolean = false,
    val error: String = "",
)

internal data class AuthOtpState(
    val buttonState: AuthOtpButtonState = AuthOtpButtonState.COMPLETE,
    val progress: Boolean = false,
    val error: String = "",
)

internal enum class AuthOtpButtonState {
    COMPLETE,
    EXPIRED,
    REPEAT,
}

internal class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val router: Router,
    ) : LifecycleViewModel() {
        private val _screenMode = MutableStateFlow(AuthScreenMode.Menu)
        internal val screenMode: StateFlow<AuthScreenMode> = _screenMode.asStateFlow()

        private val _credentialsState = MutableStateFlow(AuthCredentialsState())
        internal val credentialsState: StateFlow<AuthCredentialsState> = _credentialsState.asStateFlow()

        private val _otpInfoData = MutableStateFlow<OtpInfo?>(null)
        internal val otpInfoData: StateFlow<OtpInfo?> = _otpInfoData.asStateFlow()

        private val _otpState = MutableStateFlow(AuthOtpState())
        internal val otpState: StateFlow<AuthOtpState> = _otpState.asStateFlow()

        private var otpTimerJob: Job? = null
        private var otpRequestJob: Job? = null
        private var credentialsJob: Job? = null

        fun onCodeClick() {
            _screenMode.value = AuthScreenMode.Otp
            ensureOtpInfo()
        }

        fun onClassicClick() {
            _screenMode.value = AuthScreenMode.Credentials
            _credentialsState.value =
                _credentialsState.value.copy(
                    isLoading = false,
                    error = "",
                )
        }

        fun onSkipClick() {
            viewModelScope.launch {
                authRepository.setAuthSkipped(true)
                router.exit()
            }
        }

        fun onCredentialsSubmit(
            login: String,
            password: String,
            code: String,
        ) {
            credentialsJob?.cancel()
            credentialsJob =
                viewModelScope.launch {
                    _credentialsState.value = AuthCredentialsState(isLoading = true)
                    coRunCatching {
                        authRepository.signIn(login, password, code)
                    }.onSuccess {
                        _credentialsState.value = AuthCredentialsState()
                        router.exit()
                    }.onFailure {
                        Timber.e(it)
                        _credentialsState.value =
                            AuthCredentialsState(
                                isLoading = false,
                                error = mapAuthErrorMessage(it),
                            )
                    }
                }
        }

        fun onOtpPrimaryClick() {
            when (_otpState.value.buttonState) {
                AuthOtpButtonState.COMPLETE -> signInOtp()
                AuthOtpButtonState.EXPIRED,
                AuthOtpButtonState.REPEAT,
                -> loadOtpInfo(force = true)
            }
        }

        fun onBackClick() {
            if (_screenMode.value == AuthScreenMode.Menu) {
                router.exit()
            } else {
                _screenMode.value = AuthScreenMode.Menu
            }
        }

        fun onBackPressed() {
            onBackClick()
        }

        private fun ensureOtpInfo() {
            val shouldReload =
                _otpInfoData.value == null ||
                    _otpState.value.buttonState != AuthOtpButtonState.COMPLETE ||
                    _otpState.value.error.isNotBlank()
            if (shouldReload) {
                loadOtpInfo(force = true)
            }
        }

        private fun signInOtp() {
            val code =
                _otpInfoData.value?.code ?: run {
                    loadOtpInfo(force = true)
                    return
                }
            otpRequestJob?.cancel()
            _otpState.value = _otpState.value.copy(progress = true, error = "")
            otpRequestJob =
                viewModelScope.launch {
                    coRunCatching {
                        authRepository.signInOtp(code)
                    }.onSuccess {
                        _otpState.value = AuthOtpState()
                        router.exit()
                    }.onFailure {
                        handleOtpError(it)
                    }
                }
        }

        private fun loadOtpInfo(force: Boolean) {
            if (!force && _otpInfoData.value != null) {
                return
            }
            otpRequestJob?.cancel()
            _otpInfoData.value = null
            _otpState.value = _otpState.value.copy(progress = true, error = "")
            otpRequestJob =
                viewModelScope.launch {
                    coRunCatching {
                        authRepository.getOtpInfo()
                    }.onSuccess {
                        _otpInfoData.value = it
                        startOtpTimer(it)
                        _otpState.value =
                            AuthOtpState(
                                buttonState = AuthOtpButtonState.COMPLETE,
                                progress = false,
                                error = "",
                            )
                    }.onFailure {
                        handleOtpError(it)
                    }
                }
        }

        private fun handleOtpError(error: Throwable) {
            Timber.e(error)
            val buttonState =
                when (error) {
                    is OtpNotFoundException -> AuthOtpButtonState.EXPIRED
                    is OtpNotAcceptedException -> AuthOtpButtonState.COMPLETE
                    else -> AuthOtpButtonState.REPEAT
                }
            _otpState.value =
                AuthOtpState(
                    buttonState = buttonState,
                    progress = false,
                    error = mapAuthErrorMessage(error),
                )
        }

        private fun startOtpTimer(otpInfo: OtpInfo) {
            otpTimerJob?.cancel()
            val remainingTimeMs = otpInfo.expiresAt.time - System.currentTimeMillis()
            if (remainingTimeMs <= 0L) {
                setOtpExpired()
                return
            }
            otpTimerJob =
                viewModelScope.launch {
                    delay(otpInfo.remainingTime)
                    setOtpExpired()
                }
        }

        private fun setOtpExpired() {
            otpRequestJob?.cancel()
            _otpInfoData.value = null
            _otpState.value =
                _otpState.value.copy(
                    buttonState = AuthOtpButtonState.EXPIRED,
                    progress = false,
                    error = "",
                )
        }

        override fun onCleared() {
            otpTimerJob?.cancel()
            otpRequestJob?.cancel()
            credentialsJob?.cancel()
            super.onCleared()
        }
    }
