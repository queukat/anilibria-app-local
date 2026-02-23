package ru.radiationx.anilibria.screen.launcher

import com.github.terrakok.cicerone.Router
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.data.interactors.UserViewsSyncInteractor
import ru.radiationx.data.repository.AuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AppLauncherViewModelCommandsTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val router = mockk<Router>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        clearMocks(router)
        Dispatchers.resetMain()
    }

    @Test
    fun coldLaunch_emitsAppReadyOnceForActiveCollector() = runBlocking {
        val viewModel = createViewModel()
        val firstCommandDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            viewModel.commands.first()
        }

        viewModel.coldLaunch()

        val firstCommand = withTimeoutOrNull(1_000) {
            firstCommandDeferred.await()
        }
        assertEquals(AppLauncherViewModel.AppLauncherCommand.AppReady, firstCommand)
    }

    @Test
    fun coldLaunch_doesNotReplayAppReadyToLateCollector() = runBlocking {
        val viewModel = createViewModel()
        viewModel.coldLaunch()
        delay(150)

        val replayed = withTimeoutOrNull(150) {
            viewModel.commands.first()
        }

        assertNull(replayed)
    }

    private fun createViewModel(): AppLauncherViewModel {
        val apiConfig = mockk<ApiConfig>()
        val needConfigEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1).apply {
            tryEmit(false)
        }
        every { apiConfig.observeNeedConfig() } returns needConfigEvents
        every { apiConfig.needConfig } returns false

        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.getAuthState() } returns AuthState.NO_AUTH
        coEvery { authRepository.loadUser() } returns mockk<ProfileItem>(relaxed = true)
        every { authRepository.observeAuthState() } returns flowOf(AuthState.NO_AUTH)

        return AppLauncherViewModel(
            apiConfig = apiConfig,
            router = router,
            authRepository = authRepository,
            userViewsSyncInteractor = mockk<UserViewsSyncInteractor>(relaxed = true),
        )
    }
}
