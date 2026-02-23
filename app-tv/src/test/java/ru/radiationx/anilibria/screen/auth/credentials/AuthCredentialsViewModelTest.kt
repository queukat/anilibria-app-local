package ru.radiationx.anilibria.screen.auth.credentials

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.data.entity.domain.auth.CriticalSecureStorageUnavailableException
import ru.radiationx.data.repository.AuthRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCredentialsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onLoginClicked_showsTypedSecureStorageError_andDoesNotCrash() = runTest {
        val authRepository = mockk<AuthRepository>()
        val guidedRouter = mockk<GuidedRouter>(relaxed = true)
        coEvery {
            authRepository.signIn("user", "password", "")
        } throws CriticalSecureStorageUnavailableException()

        val viewModel = AuthCredentialsViewModel(
            authRepository = authRepository,
            guidedRouter = guidedRouter,
        )

        viewModel.onLoginClicked("user", "password", "")
        advanceUntilIdle()

        assertEquals(
            "Безопасное хранилище недоступно. Включите защиту экрана устройства и повторите вход.",
            viewModel.error.value
        )
        assertFalse(viewModel.progressState.value)
        verify(exactly = 0) { guidedRouter.finishGuidedChain() }
    }
}
