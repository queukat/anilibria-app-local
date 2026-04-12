package ru.radiationx.anilibria.screen.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.data.entity.domain.auth.OtpInfo
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class AuthFragment : Fragment() {
    private val viewModel by viewModel<AuthViewModel>()

    private var screenModeState by mutableStateOf(AuthScreenMode.Menu)
    private var credentialsState by mutableStateOf(AuthCredentialsState())
    private var otpInfoState by mutableStateOf<OtpInfo?>(null)
    private var otpState by mutableStateOf(AuthOtpState())

    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                when (screenModeState) {
                    AuthScreenMode.Menu -> {
                        AuthMenuOverlay(
                            onCodeClick = viewModel::onCodeClick,
                            onClassicClick = viewModel::onClassicClick,
                            onSkipClick = viewModel::onSkipClick,
                        )
                    }

                    AuthScreenMode.Credentials -> {
                        AuthCredentialsOverlay(
                            isLoading = credentialsState.isLoading,
                            errorText = credentialsState.error,
                            onSubmit = viewModel::onCredentialsSubmit,
                            onBackClick = viewModel::onBackClick,
                        )
                    }

                    AuthScreenMode.Otp -> {
                        AuthOtpOverlay(
                            otpInfo = otpInfoState,
                            state = otpState,
                            onPrimaryClick = viewModel::onOtpPrimaryClick,
                            onBackClick = viewModel::onBackClick,
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        backPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.onBackPressed()
                }
            }.also {
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
            }

        subscribeTo(viewModel.screenMode) {
            screenModeState = it
        }
        subscribeTo(viewModel.credentialsState) {
            credentialsState = it
        }
        subscribeTo(viewModel.otpInfoData) {
            otpInfoState = it
        }
        subscribeTo(viewModel.otpState) {
            otpState = it
        }
    }

    override fun onDestroyView() {
        backPressedCallback?.remove()
        backPressedCallback = null
        super.onDestroyView()
    }
}
