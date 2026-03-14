package ru.radiationx.anilibria.screen.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.screen.mainpages.MainShellCallbacks
import ru.radiationx.anilibria.screen.mainpages.MainShellPageContent
import ru.radiationx.anilibria.screen.mainpages.collectStarted
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.quill.getViewModel

internal class ProfilePageContent(
    private val fragment: Fragment,
    private val backgroundManager: GradientBackgroundManager,
) : MainShellPageContent {

    private val viewModel = fragment.getViewModel(ProfileViewModel::class)

    private var profileState by mutableStateOf<ProfileItem?>(null)
    private var focusRequestToken by mutableIntStateOf(1)

    override fun bind(owner: LifecycleOwner) {
        backgroundManager.clearGradient()

        owner.lifecycle.addObserver(viewModel)
        owner.collectStarted(viewModel.profileData) { profile ->
            profileState = profile
            focusRequestToken++
        }
    }

    override fun onSelected() {
        backgroundManager.clearGradient()
    }

    override fun requestContentFocus(): Boolean {
        focusRequestToken++
        return true
    }

    @Composable
    override fun Render(callbacks: MainShellCallbacks) {
        ProfileScreen(
            profile = profileState,
            focusRequestToken = focusRequestToken,
            onSignInClick = viewModel::onSignInClick,
            onSignOutClick = viewModel::onSignOutClick,
            onRequestRailFocus = callbacks.onRequestRailFocus,
            onRequestHeaderFocus = callbacks.onRequestHeaderFocus,
        )
    }
}
