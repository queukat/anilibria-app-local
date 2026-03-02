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
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.BlockedInfo
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
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

    @Test
    fun byDate_preservesServerOrderAcrossPages() = runBlocking {
        val authStateFlow = MutableStateFlow(AuthState.AUTH)
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeAuthState() } returns authStateFlow

        val requests = mutableListOf<Int>()
        val favoriteRepository = mockk<FavoriteRepository>()
        coEvery { favoriteRepository.getFavorites(any()) } answers {
            val page = firstArg<Int>()
            requests += page
            when (page) {
                1 -> response(page, listOf(release(10, "A", "2020"), release(20, "B", "2019")))
                2 -> response(page, listOf(release(30, "C", "2026")))
                else -> emptyResponse(page)
            }
        }

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

        val viewModel = WatchingFavoritesViewModel(
            favoriteRepository = favoriteRepository,
            authRepository = authRepository,
            converter = converter,
            cardRouter = mockk<LibriaCardRouter>(relaxed = true),
        )

        waitUntil {
            extractIds(viewModel.cardsData.value) == listOf(10, 20, 30)
        }

        assertEquals("Expected pages to be requested in sequence", listOf(1, 2, 3), requests)
        assertEquals(
            "Date mode must keep backend order to avoid jumping cards",
            listOf(10, 20, 30),
            extractIds(viewModel.cardsData.value),
        )
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

    private fun response(page: Int, data: List<Release>): Paginated<Release> = Paginated(
        data = data,
        page = page,
        allPages = 3,
        perPage = 25,
        allItems = data.size,
    )

    private fun extractIds(items: List<*>): List<Int> {
        return items
            .filterIsInstance<LibriaCard>()
            .mapNotNull { (it.type as? LibriaCard.Type.Release)?.releaseId?.id }
    }

    private fun release(id: Int, title: String, year: String): Release {
        return Release(
            id = ReleaseId(id),
            code = ReleaseCode("code_$id"),
            names = listOf(title),
            series = null,
            poster = null,
            torrentUpdate = 0,
            status = null,
            statusCode = null,
            types = emptyList(),
            genres = emptyList(),
            voices = emptyList(),
            members = null,
            year = year,
            season = null,
            days = emptyList(),
            description = null,
            announce = null,
            favoriteInfo = FavoriteInfo(rating = 0, isAdded = true),
            link = null,
            franchises = emptyList(),
            showDonateDialog = false,
            blockedInfo = BlockedInfo(isBlocked = false, reason = null),
            moonwalkLink = null,
            episodes = emptyList(),
            sourceEpisodes = emptyList(),
            externalPlaylists = emptyList(),
            rutubePlaylist = emptyList(),
            torrents = emptyList(),
        )
    }
}
