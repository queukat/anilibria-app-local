package ru.radiationx.anilibria.screen.profile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.AuthGuidedScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val guidedRouter: GuidedRouter
) : LifecycleViewModel() {

    private val _profileData = MutableStateFlow<ProfileItem?>(null)
    val profileData: StateFlow<ProfileItem?> = _profileData.asStateFlow()

    init {
        authRepository
            .observeUser()
            .onEach { _profileData.value = it }
            .launchIn(viewModelScope)
    }

    fun onSignInClick() {
        guidedRouter.open(AuthGuidedScreen())
    }

    fun onSignOutClick() {
        viewModelScope.launch {
            coRunCatching {
                authRepository.signOut()
            }.onFailure {
                Timber.e(it)
            }
        }
    }
}
