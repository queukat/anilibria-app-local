package ru.radiationx.anilibria.screen.watching

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.FavoriteRepository

@OptIn(ExperimentalCoroutinesApi::class)
class WatchingFavoritesViewModelStream2Test {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bind_doesNotTriggerRequest() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val requests = mutableListOf<Int>()
        val favoriteRepository = mockk<FavoriteRepository>()
        coEvery { favoriteRepository.getFavorites(any()) } answers {
            val page = firstArg<Int>()
            requests += page
            emptyResponse(page)
        }

        val viewModel = WatchingFavoritesViewModel(
            favoriteRepository = favoriteRepository,
            authRepository = authRepository,
            converter = mockk<CardsDataConverter>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        waitUntil { requests.size == 1 }
        viewModel.onLinkCardBind()
        delay(100)

        assertEquals("Bind should not trigger a network request", 1, requests.size)
    }

    @Test
    fun loadMore_triggersOneRequestPerExplicitEvent() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val requests = mutableListOf<Int>()
        val favoriteRepository = mockk<FavoriteRepository>()
        coEvery { favoriteRepository.getFavorites(any()) } answers {
            val page = firstArg<Int>()
            requests += page
            emptyResponse(page)
        }

        val viewModel = WatchingFavoritesViewModel(
            favoriteRepository = favoriteRepository,
            authRepository = authRepository,
            converter = mockk<CardsDataConverter>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        waitUntil { requests.size == 1 } // initial auth-triggered load

        viewModel.onLinkCardClick()
        waitUntil { requests.size == 2 }

        viewModel.onLinkCardClick()
        waitUntil { requests.size == 3 }

        assertEquals("Expected one request for each explicit loadMore click", 3, requests.size)
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(25)
        }
        error("Condition was not met in time")
    }

    private fun emptyResponse(page: Int): Paginated<Release> = Paginated(
        data = emptyList(),
        page = page,
        allPages = 1,
        perPage = 25,
        allItems = 0,
    )
}
