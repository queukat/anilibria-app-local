package ru.radiationx.anilibria.screen.launcher

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.AuthGuidedScreen
import ru.radiationx.anilibria.screen.ConfigScreen
import ru.radiationx.anilibria.screen.DetailsScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.MainPagesScreen
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.system.AndroidTestMode
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class AppLauncherViewModel @Inject constructor(
    private val apiConfig: ApiConfig,
    private val router: Router,
    private val authRepository: AuthRepository,
) : LifecycleViewModel() {

    sealed interface AppLauncherCommand {
        data object AppReady : AppLauncherCommand
    }

    private var firstLaunch = true

    private val _commands = MutableSharedFlow<AppLauncherCommand>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val commands = _commands.asSharedFlow()

    fun openRelease(id: ReleaseId) {
        router.navigateTo(DetailsScreen(id))
    }

    fun coldLaunch() {
        initWithConfig()
        //initMain()
    }

    private fun initWithConfig() {
        apiConfig
            .observeNeedConfig()
            .distinctUntilChanged()
            .onEach {
                if (it) {
                    router.newRootScreen(ConfigScreen())
                } else {
                    if (firstLaunch) {
                        initMain()
                    }
                }
            }
            .launchIn(viewModelScope)

        if (apiConfig.needConfig) {
            router.newRootScreen(ConfigScreen())
        } else {
            initMain()
        }
    }

    private fun initMain() {
        firstLaunch = false

        viewModelScope.launch {
            router.newRootScreen(MainPagesScreen())
            if (!AndroidTestMode.enabled && authRepository.getAuthState() == AuthState.NO_AUTH) {
                router.navigateTo(AuthGuidedScreen())
            }
            _commands.tryEmit(AppLauncherCommand.AppReady)
        }
        viewModelScope.launch {
            coRunCatching {
                authRepository.loadUser()
            }.onFailure {
                Timber.e(it)
            }
        }
    }

}
