package ru.radiationx.anilibria.screen.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel

class ProfileFragment : Fragment() {

    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

    private val viewModel by quillParentViewModel<ProfileViewModel>()

    private var profileState by mutableStateOf<ProfileItem?>(null)
    private var focusRequestToken by mutableIntStateOf(1)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focusRequestToken++
                }
            }
            setContent {
                ProfileScreen(
                    profile = profileState,
                    focusRequestToken = focusRequestToken,
                    onSignInClick = viewModel::onSignInClick,
                    onSignOutClick = viewModel::onSignOutClick,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundManager.clearGradient()

        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        subscribeTo(viewModel.profileData) { profile ->
            profileState = profile
            focusRequestToken++
        }
    }
}
