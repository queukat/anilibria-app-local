package ru.radiationx.anilibria.screen.auth.main

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import ru.radiationx.anilibria.common.fragment.ComposeGuidedFragment
import ru.radiationx.anilibria.screen.auth.AuthMenuOverlay
import ru.radiationx.quill.viewModel

class AuthGuidedFragment : ComposeGuidedFragment() {

    private val viewModel by viewModel<AuthViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)
    }

    @Composable
    override fun RenderContent() {
        AuthMenuOverlay(
            onCodeClick = viewModel::onCodeClick,
            onClassicClick = viewModel::onClassicClick,
            onSkipClick = viewModel::onSkipClick,
        )
    }
}
