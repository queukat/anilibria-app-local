package ru.radiationx.anilibria.screen.player

import com.github.terrakok.cicerone.Screen
import com.github.terrakok.cicerone.Router
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.PlayerScreen
import ru.radiationx.anilibria.screen.player.episodes.PlayerEpisodesViewModel
import ru.radiationx.anilibria.screen.player.episodes.formatEpisodeAccessDescription
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.TvReleaseUseCase
import ru.radiationx.shared.ktx.EventFlow

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerEpisodesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val createdViewModels = mutableListOf<PlayerEpisodesViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        createdViewModels.forEach(::clearViewModel)
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun formatEpisodeAccessDescription_showsStoppedTextWhenSeekPositive() {
        val access = EpisodeAccess(EpisodeId("1", ReleaseId(10)), 12_000L, true, 1L)
        val description = formatEpisodeAccessDescription(access)
        assertNotNull(description)
        assertTrue(description!!.startsWith("Остановлена на "))
    }

    @Test
    fun formatEpisodeAccessDescription_showsViewedWhenSeekZero() {
        val access = EpisodeAccess(EpisodeId("1", ReleaseId(10)), 0L, true, 1L)
        assertEquals("Просмотрено", formatEpisodeAccessDescription(access))
    }

    @Test
    fun formatEpisodeAccessDescription_returnsNullWhenNotViewed() {
        val access = EpisodeAccess(EpisodeId("1", ReleaseId(10)), 0L, false, 1L)
        assertEquals(null, formatEpisodeAccessDescription(access))
    }

    @Test
    fun applyEpisode_closesGuidedScreenBeforeNavigatingToPlayer_whenPlayerNotActive() = runBlocking {
        val fixture = createFixture(playerActive = false)
        val actionId = waitForFirstActionId(fixture.viewModel)

        fixture.viewModel.applyEpisode(actionId)

        verifyOrder {
            fixture.guidedRouter.close()
            fixture.router.navigateTo(any())
        }
        verify(exactly = 0) { fixture.playerRelay.emit(any()) }
    }

    @Test
    fun applyEpisode_emitsEpisodeSelectionAndClosesGuidedScreen_whenPlayerActive() = runBlocking {
        val fixture = createFixture(playerActive = true)
        val actionId = waitForFirstActionId(fixture.viewModel)

        fixture.viewModel.applyEpisode(actionId)

        verifyOrder {
            fixture.playerRelay.emit(fixture.episodeId)
            fixture.guidedRouter.close()
        }
        verify(exactly = 0) { fixture.router.navigateTo(any()) }
    }

    @Test
    fun applyEpisode_passesSelectedEpisodeIdToPlayer_whenAnotherEpisodeChosen() = runBlocking {
        val fixture = createFixture(
            playerActive = false,
            episodeIds = listOf(
                EpisodeId("1", ReleaseId(10)),
                EpisodeId("2", ReleaseId(10)),
            ),
        )
        val actionId = waitForActionId(fixture.viewModel, index = 1)
        val screenSlot = slot<Screen>()

        fixture.viewModel.applyEpisode(actionId)

        verify { fixture.router.navigateTo(capture(screenSlot)) }
        val playerScreen = screenSlot.captured as PlayerScreen
        assertEquals(fixture.episodeIds[1], playerScreen.readEpisodeId())
    }

    private suspend fun waitForFirstActionId(viewModel: PlayerEpisodesViewModel): Long {
        return waitForActionId(viewModel, index = 0)
    }

    private suspend fun waitForActionId(
        viewModel: PlayerEpisodesViewModel,
        index: Int,
    ): Long {
        repeat(50) {
            viewModel.episodesData.value.firstOrNull()?.actions?.getOrNull(index)?.id?.also { return it }
            delay(20L)
        }
        error("Episode actions were not loaded in time")
    }

    private fun createFixture(
        playerActive: Boolean,
        episodeIds: List<EpisodeId> = listOf(EpisodeId("1", ReleaseId(10))),
    ): Fixture {
        val releaseId = episodeIds.first().releaseId
        val episodeId = episodeIds.first()
        val release = mockk<Release> {
            every { id } returns releaseId
            every { title } returns "Release"
            every { episodes } returns episodeIds.mapIndexed { index, currentEpisodeId ->
                mockk {
                    every { id } returns currentEpisodeId
                    every { title } returns "Episode ${index + 1}"
                }
            }
        }
        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(releaseId) } returns emptyList()

        val tvReleaseUseCase = mockk<TvReleaseUseCase>()
        every { tvReleaseUseCase.observeRelease(releaseId) } returns flowOf(release)

        val guidedRouter = mockk<GuidedRouter>(relaxed = true)
        val router = mockk<Router>(relaxed = true)
        val playerRelay = mockk<EventFlow<EpisodeId>>(relaxed = true)
        val playerController = mockk<PlayerController> {
            every { isPlayerActive } returns playerActive
            every { data } returns MutableStateFlow(if (playerActive) listOf(release) else null)
            every { selectEpisodeRelay } returns playerRelay
        }

        val viewModel = PlayerEpisodesViewModel(
            argExtra = PlayerExtra(releaseId = releaseId, episodeId = episodeId),
            releaseInteractor = releaseInteractor,
            tvReleaseUseCase = tvReleaseUseCase,
            guidedRouter = guidedRouter,
            playerController = playerController,
            router = router,
        )
        createdViewModels += viewModel

        return Fixture(
            viewModel = viewModel,
            router = router,
            guidedRouter = guidedRouter,
            playerRelay = playerRelay,
            episodeId = episodeId,
            episodeIds = episodeIds,
        )
    }

    private fun clearViewModel(viewModel: PlayerEpisodesViewModel) {
        val clearMethod = androidx.lifecycle.ViewModel::class.java
            .getMethod("clear\$lifecycle_viewmodel_release")
        clearMethod.invoke(viewModel)
    }

    private data class Fixture(
        val viewModel: PlayerEpisodesViewModel,
        val router: Router,
        val guidedRouter: GuidedRouter,
        val playerRelay: EventFlow<EpisodeId>,
        val episodeId: EpisodeId,
        val episodeIds: List<EpisodeId>,
    )

    private fun PlayerScreen.readEpisodeId(): EpisodeId? {
        val field = PlayerScreen::class.java.getDeclaredField("episodeId")
        field.isAccessible = true
        return field.get(this) as EpisodeId?
    }
}
