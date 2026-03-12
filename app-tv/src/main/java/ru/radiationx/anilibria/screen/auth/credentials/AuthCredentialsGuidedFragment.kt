package ru.radiationx.anilibria.screen.auth.credentials

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.radiationx.anilibria.common.fragment.ComposeGuidedFragment
import ru.radiationx.anilibria.screen.auth.AuthCredentialsOverlay
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class AuthCredentialsGuidedFragment : ComposeGuidedFragment() {

    private val viewModel by viewModel<AuthCredentialsViewModel>()

    private var isLoadingState by mutableStateOf(false)
    private var errorState by mutableStateOf("")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.progressState) {
            isLoadingState = it
        }
        subscribeTo(viewModel.error) {
            errorState = it
        }
    }

    @Composable
    override fun RenderContent() {
        AuthCredentialsOverlay(
            isLoading = isLoadingState,
            errorText = errorState,
            onSubmit = viewModel::onLoginClicked,
        )
    }
}
