package ru.radiationx.anilibria.screen.watching

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.repository.AuthRepository

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
    fun idle_doesNotTriggerAdditionalRequest() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val requests = mutableListOf<Int>()
        val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
        coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
            val page = firstArg<Int>()
            requests += page
            emptyResponse(page)
        }

        val viewModel = WatchingFavoritesViewModel(
            tvFavoritesUseCase = tvFavoritesUseCase,
            authRepository = authRepository,
            converter = mockk<CardsDataConverter>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        waitUntil { requests.size == 1 }
        delay(100)

        assertEquals("Idle state should not trigger a network request", 1, requests.size)
    }

    @Test
    fun loadMore_triggersOneRequestPerExplicitEvent() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val requests = mutableListOf<Int>()
        val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
        coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
            val page = firstArg<Int>()
            requests += page
            emptyResponse(page)
        }

        val viewModel = WatchingFavoritesViewModel(
            tvFavoritesUseCase = tvFavoritesUseCase,
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

    @Test
    fun initialSync_doesNotRequestMoreThanFiftyPages() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val requests = mutableListOf<Int>()
        val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
        coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
            val page = firstArg<Int>()
            requests += page
            singleItemResponse(page)
        }

        WatchingFavoritesViewModel(
            tvFavoritesUseCase = tvFavoritesUseCase,
            authRepository = authRepository,
            converter = mockk<CardsDataConverter>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        waitUntil { requests.size >= 50 }
        delay(100)

        assertEquals("Expected sync to stop at 50 pages max", 50, requests.size)
    }

    @Test
    fun emptyFavorites_showsExplicitEmptyState() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
        coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
            emptyResponse(firstArg<Int>())
        }

        val viewModel = WatchingFavoritesViewModel(
            tvFavoritesUseCase = tvFavoritesUseCase,
            authRepository = authRepository,
            converter = favoriteConverter(),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        waitUntil {
            (viewModel.cardsData.value.firstOrNull() as? LoadingCard)?.title == "Избранное пока пусто"
        }

        val first = viewModel.cardsData.value.single() as LoadingCard
        assertEquals("Избранное пока пусто", first.title)
        assertEquals(
            "Добавьте тайтлы в избранное, чтобы они появились здесь",
            first.description
        )
        assertFalse(first.isError)
    }

    @Test
    fun explicitReloadCancellation_keepsCurrentCardsWithoutRetryUi() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val release = fakeRelease(7)
        var requestCount = 0
        val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
        coEvery { tvFavoritesUseCase.loadFavorites(any()) } answers {
            requestCount += 1
            if (requestCount == 1) {
                Paginated(
                    data = listOf(release),
                    page = 1,
                    allPages = 1,
                    perPage = 25,
                    allItems = 1,
                )
            } else {
                throw CancellationException("favorites reload cancelled")
            }
        }

        val viewModel = WatchingFavoritesViewModel(
            tvFavoritesUseCase = tvFavoritesUseCase,
            authRepository = authRepository,
            converter = favoriteConverter(),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        waitUntil { viewModel.cardsData.value.any { it is LibriaCard } }

        viewModel.onLinkCardClick()
        waitUntil { requestCount == 2 }
        delay(100)

        assertTrue(viewModel.cardsData.value.any { it is LibriaCard })
        assertTrue(viewModel.cardsData.value.none { it is LinkCard && it.title == "Повторить" })
        assertTrue(viewModel.cardsData.value.none { it is LoadingCard && it.isError })
    }

    @Test
    fun initialCancellation_doesNotShowFalseError() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val tvFavoritesUseCase = mockk<TvFavoritesUseCase>()
        coEvery { tvFavoritesUseCase.loadFavorites(any()) } throws
            CancellationException("favorites load cancelled")

        val viewModel = WatchingFavoritesViewModel(
            tvFavoritesUseCase = tvFavoritesUseCase,
            authRepository = authRepository,
            converter = mockk<CardsDataConverter>(relaxed = true),
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        delay(100)

        assertTrue(viewModel.cardsData.value.none { it is LinkCard && it.title == "Повторить" })
        assertTrue(viewModel.cardsData.value.none { it is LoadingCard && it.isError })
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

    private fun singleItemResponse(page: Int): Paginated<Release> = Paginated(
        data = listOf(fakeRelease(page)),
        page = page,
        allPages = null,
        perPage = 25,
        allItems = null,
    )

    private fun fakeRelease(id: Int): Release {
        val release = mockk<Release>(relaxed = true)
        every { release.id } returns ReleaseId(id)
        every { release.title } returns "title-$id"
        every { release.year } returns "2026"
        every { release.season } returns "spring"
        every { release.genres } returns emptyList()
        every { release.statusCode } returns Release.STATUS_CODE_COMPLETE
        every { release.torrentUpdate } returns id
        return release
    }

    private fun favoriteConverter(): CardsDataConverter {
        val converter = mockk<CardsDataConverter>()
        every { converter.toCard(any<Release>()) } answers {
            val release = firstArg<Release>()
            LibriaCard(
                title = release.title.orEmpty(),
                description = "",
                image = "",
                type = LibriaCard.Type.Release(release.id),
            )
        }
        return converter
    }
}
