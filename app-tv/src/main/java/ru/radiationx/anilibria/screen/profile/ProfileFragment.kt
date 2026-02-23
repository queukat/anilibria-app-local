package ru.radiationx.anilibria.screen.profile

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.leanback.app.BrowseSupportFragment
import dev.androidbroadcast.vbpd.viewBinding
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.databinding.FragmentProfileBinding
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel
import ru.radiationx.shared_app.imageloader.showImageUrl

class ProfileFragment : Fragment(R.layout.fragment_profile),
    BrowseSupportFragment.MainFragmentAdapterProvider {

    private val binding by viewBinding<FragmentProfileBinding>()

    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

    private val viewModel by quillParentViewModel<ProfileViewModel>()

    private val selfMainFragmentAdapter by lazy { BrowseSupportFragment.MainFragmentAdapter(this) }

    override fun getMainFragmentAdapter(): BrowseSupportFragment.MainFragmentAdapter<*> {
        return selfMainFragmentAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundManager.clearGradient()

        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        subscribeTo(viewModel.profileData) { profile ->
            val hasAuth = (profile != null)

            if (hasAuth) {
                val avatarUrl = profile?.avatarUrl
                if (avatarUrl.isNullOrBlank()) {
                    // Сбрасываем прошлую загрузку/кэш-тег и ставим стабильный плейсхолдер
                    binding.profileAvatar.showImageUrl(null)
                    binding.profileAvatar.setImageResource(R.drawable.ic_anilibria_splash)
                } else {
                    binding.profileAvatar.showImageUrl(avatarUrl)
                }
            } else {
                // Важно очистить прошлый state, чтобы не было "фантомных" картинок при logout/login
                binding.profileAvatar.showImageUrl(null)
                binding.profileAvatar.setImageDrawable(null)
            }

            binding.profileNick.text = profile?.nick

            binding.profileAvatar.isVisible = hasAuth
            binding.profileNick.isVisible = hasAuth
            binding.profileSignIn.isGone = hasAuth
            binding.profileSignOut.isVisible = hasAuth
        }

        binding.profileSignIn.setOnClickListener { viewModel.onSignInClick() }
        binding.profileSignOut.setOnClickListener { viewModel.onSignOutClick() }

        mainFragmentAdapter.fragmentHost.notifyViewCreated(selfMainFragmentAdapter)
        mainFragmentAdapter.fragmentHost.notifyDataReady(selfMainFragmentAdapter)
    }
}
