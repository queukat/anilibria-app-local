package ru.radiationx.anilibria.screen.auth.otp

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.radiationx.anilibria.common.fragment.ComposeGuidedFragment
import ru.radiationx.anilibria.screen.auth.AuthOtpOverlay
import ru.radiationx.data.entity.domain.auth.OtpInfo
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class AuthOtpGuidedFragment : ComposeGuidedFragment() {

    private val viewModel by viewModel<AuthOtpViewModel>()

    private var otpInfoState by mutableStateOf<OtpInfo?>(null)
    private var stateState by mutableStateOf(AuthOtpViewModel.State())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.otpInfoData) {
            otpInfoState = it
        }
        subscribeTo(viewModel.state) {
            stateState = it
        }
    }

    @Composable
    override fun RenderContent() {
        AuthOtpOverlay(
            otpInfo = otpInfoState,
            state = stateState,
            onPrimaryClick = {
                when (stateState.buttonState) {
                    AuthOtpViewModel.ButtonState.COMPLETE -> viewModel.onCompleteClick()
                    AuthOtpViewModel.ButtonState.EXPIRED -> viewModel.onExpiredClick()
                    AuthOtpViewModel.ButtonState.REPEAT -> viewModel.onRepeatClick()
                }
            },
        )
    }
}
