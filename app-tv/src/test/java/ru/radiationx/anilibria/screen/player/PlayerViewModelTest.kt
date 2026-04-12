package ru.radiationx.anilibria.screen.player

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.contracts.tv.TvPlayerFacade
import ru.radiationx.data.datasource.holders.AppPreference
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.QualityInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
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
    fun onReplayEpisodeClick_startsCurrentEpisodeFromBeginning_evenWhenRemoteSeekStaysAtEnd() =
        runTest {
            val releaseId = ReleaseId(77)
            val episode = createEpisode("1", releaseId)
            val release = createRelease(releaseId, listOf(episode))
            val tvPlayerFacade = mockk<TvPlayerFacade>()

            var localSeek = 95_000L
            val staleRemoteSeek = 120_000L

            every { tvPlayerFacade.observeAuthState() } returns MutableStateFlow(AuthState.AUTH)
            coEvery { tvPlayerFacade.getAuthState() } returns AuthState.AUTH
            coEvery { tvPlayerFacade.loadWithFranchises(releaseId) } returns listOf(release)
            coEvery { tvPlayerFacade.getLocalEpisodeSeek(episode.id) } answers { localSeek }
            coEvery { tvPlayerFacade.saveLocalEpisodeSeek(episode.id, any()) } answers {
                localSeek = secondArg()
                Unit
            }
            coEvery { tvPlayerFacade.getRemoteEpisodeSeek(episode.id) } returns staleRemoteSeek
            coEvery { tvPlayerFacade.saveRemoteEpisodeProgress(episode.id, any(), any()) } returns Unit

            val viewModel =
                PlayerViewModel(
                    argExtra =
                        PlayerExtra(
                            releaseId = releaseId,
                            episodeId = episode.id,
                        ),
                    tvPlayerFacade = tvPlayerFacade,
                    preferencesHolder = createPreferencesHolder(),
                )

            waitUntil { viewModel.videoData.value != null }
            assertEquals(staleRemoteSeek, viewModel.videoData.value?.seek)

            viewModel.onReplayEpisodeClick()

            waitUntil { viewModel.videoData.value?.seek == 0L }

            assertEquals(0L, viewModel.videoData.value?.seek)
            coVerify(atLeast = 1) { tvPlayerFacade.saveLocalEpisodeSeek(episode.id, 0L) }
            coVerify(exactly = 1) {
                tvPlayerFacade.saveRemoteEpisodeProgress(
                    episode.id,
                    positionMs = 0L,
                    isWatched = false,
                )
            }
        }

    @Test
    fun onComplete_showsEpisodeCompleteOverlay_whenNextEpisodeExistsAndAutoplayDisabled() =
        runTest {
            val releaseId = ReleaseId(88)
            val firstEpisode = createEpisode("1", releaseId)
            val secondEpisode = createEpisode("2", releaseId)
            val release = createRelease(releaseId, listOf(firstEpisode, secondEpisode))
            val tvPlayerFacade = mockk<TvPlayerFacade>()

            every { tvPlayerFacade.observeAuthState() } returns MutableStateFlow(AuthState.NO_AUTH)
            coEvery { tvPlayerFacade.getAuthState() } returns AuthState.NO_AUTH
            coEvery { tvPlayerFacade.loadWithFranchises(releaseId) } returns listOf(release)
            coEvery { tvPlayerFacade.getLocalEpisodeSeek(any()) } returns 0L
            coEvery { tvPlayerFacade.saveLocalEpisodeSeek(any(), any()) } returns Unit
            coEvery { tvPlayerFacade.getRemoteEpisodeSeek(any()) } returns 0L

            val viewModel =
                PlayerViewModel(
                    argExtra =
                        PlayerExtra(
                            releaseId = releaseId,
                            episodeId = firstEpisode.id,
                        ),
                    tvPlayerFacade = tvPlayerFacade,
                    preferencesHolder = createPreferencesHolder(autoplay = false),
                )

            waitUntil { viewModel.videoData.value != null }

            viewModel.onComplete(position = 123_000L)

            waitUntil { viewModel.completionOverlay.value != null }

            assertEquals(PlayerCompletionOverlay.EpisodeComplete, viewModel.completionOverlay.value)
            assertNotNull(viewModel.videoData.value)
        }

    @Test
    fun init_withoutEpisodeId_startsFromFirstEpisodeOfCurrentRelease_notFirstEpisodeOfFranchise() =
        runTest {
            val season1Id = ReleaseId(8498)
            val season4Id = ReleaseId(10161)
            val season1Episode = createEpisode("1", season1Id)
            val season4Episode = createEpisode("1", season4Id)
            val season1Release = createRelease(season1Id, listOf(season1Episode))
            val season4Release = createRelease(season4Id, listOf(season4Episode))
            val tvPlayerFacade = mockk<TvPlayerFacade>()

            every { tvPlayerFacade.observeAuthState() } returns MutableStateFlow(AuthState.NO_AUTH)
            coEvery { tvPlayerFacade.getAuthState() } returns AuthState.NO_AUTH
            coEvery { tvPlayerFacade.loadWithFranchises(season4Id) } returns listOf(season1Release, season4Release)
            coEvery { tvPlayerFacade.getLocalContinueEpisodeId(season4Id) } returns null
            coEvery { tvPlayerFacade.getLocalEpisodeSeek(any()) } returns 0L
            coEvery { tvPlayerFacade.saveLocalEpisodeSeek(any(), any()) } returns Unit
            coEvery { tvPlayerFacade.getRemoteEpisodeSeek(any()) } returns 0L

            val viewModel =
                PlayerViewModel(
                    argExtra =
                        PlayerExtra(
                            releaseId = season4Id,
                            episodeId = null,
                        ),
                    tvPlayerFacade = tvPlayerFacade,
                    preferencesHolder = createPreferencesHolder(),
                )

            waitUntil { viewModel.selectedEpisodeId.value != null }

            assertEquals(season4Episode.id, viewModel.selectedEpisodeId.value)
        }

    @Test
    fun onReplaySeasonClick_restartsFromFirstEpisodeOfCurrentRelease_notFirstEpisodeOfFranchise() =
        runTest {
            val season1Id = ReleaseId(8498)
            val season4Id = ReleaseId(10161)
            val season1Episode = createEpisode("1", season1Id)
            val season4Episode = createEpisode("1", season4Id)
            val season1Release = createRelease(season1Id, listOf(season1Episode))
            val season4Release = createRelease(season4Id, listOf(season4Episode))
            val tvPlayerFacade = mockk<TvPlayerFacade>()

            every { tvPlayerFacade.observeAuthState() } returns MutableStateFlow(AuthState.NO_AUTH)
            coEvery { tvPlayerFacade.getAuthState() } returns AuthState.NO_AUTH
            coEvery { tvPlayerFacade.loadWithFranchises(season4Id) } returns listOf(season1Release, season4Release)
            coEvery { tvPlayerFacade.getLocalEpisodeSeek(any()) } returns 0L
            coEvery { tvPlayerFacade.saveLocalEpisodeSeek(any(), any()) } returns Unit
            coEvery { tvPlayerFacade.getRemoteEpisodeSeek(any()) } returns 0L

            val viewModel =
                PlayerViewModel(
                    argExtra =
                        PlayerExtra(
                            releaseId = season4Id,
                            episodeId = season4Episode.id,
                        ),
                    tvPlayerFacade = tvPlayerFacade,
                    preferencesHolder = createPreferencesHolder(),
                )

            waitUntil { viewModel.selectedEpisodeId.value != null }

            viewModel.onReplaySeasonClick()

            waitUntil { viewModel.videoData.value?.seek == 0L }

            assertEquals(season4Episode.id, viewModel.selectedEpisodeId.value)
            coVerify(atLeast = 1) { tvPlayerFacade.saveLocalEpisodeSeek(season4Episode.id, 0L) }
            coVerify(exactly = 0) { tvPlayerFacade.saveLocalEpisodeSeek(season1Episode.id, 0L) }
        }

    @Test
    fun onPrepare_emitsAutoPlayWithoutRevealingControls() =
        runTest {
            val releaseId = ReleaseId(99)
            val episode = createEpisode("1", releaseId)
            val release = createRelease(releaseId, listOf(episode))
            val tvPlayerFacade = mockk<TvPlayerFacade>()

            every { tvPlayerFacade.observeAuthState() } returns MutableStateFlow(AuthState.NO_AUTH)
            coEvery { tvPlayerFacade.getAuthState() } returns AuthState.NO_AUTH
            coEvery { tvPlayerFacade.loadWithFranchises(releaseId) } returns listOf(release)
            coEvery { tvPlayerFacade.getLocalEpisodeSeek(any()) } returns 0L
            coEvery { tvPlayerFacade.saveLocalEpisodeSeek(any(), any()) } returns Unit
            coEvery { tvPlayerFacade.getRemoteEpisodeSeek(any()) } returns 0L

            val viewModel =
                PlayerViewModel(
                    argExtra =
                        PlayerExtra(
                            releaseId = releaseId,
                            episodeId = episode.id,
                        ),
                    tvPlayerFacade = tvPlayerFacade,
                    preferencesHolder = createPreferencesHolder(),
                )
            val commands = mutableListOf<PlayerCommand>()
            val collectJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    viewModel.commands.collect { commands += it }
                }

            try {
                waitUntil { viewModel.videoData.value != null }

                viewModel.onPrepare(duration = 120_000L)

                waitUntil { commands.any { it is PlayerCommand.Play } }

                assertEquals(
                    PlayerCommand.Play(revealControls = false),
                    commands.filterIsInstance<PlayerCommand.Play>().last(),
                )
            } finally {
                collectJob.cancel()
            }
        }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(80) {
            if (predicate()) {
                return
            }
            delay(25)
        }
        error("Condition was not met in time")
    }

    private fun createEpisode(
        ordinal: String,
        releaseId: ReleaseId,
    ): Episode {
        return Episode(
            id = EpisodeId(ordinal, releaseId),
            title = "Episode $ordinal",
            qualityInfo =
                QualityInfo(
                    urlSd = null,
                    urlHd = "https://example.com/${releaseId.id}/$ordinal.m3u8",
                    urlFullHd = null,
                ),
            updatedAt = null,
            skips = null,
        )
    }

    private fun createRelease(
        releaseId: ReleaseId,
        episodes: List<Episode>,
    ): Release {
        val release = mockk<Release>(relaxed = true)
        every { release.id } returns releaseId
        every { release.title } returns "Release ${releaseId.id}"
        every { release.episodes } returns episodes
        return release
    }

    private fun createPreferencesHolder(autoplay: Boolean = false): PreferencesHolder {
        val qualityPreference = statePreference(PlayerQuality.HD)
        val speedPreference = statePreference(1f)
        val autoplayPreference = statePreference(autoplay)
        val availableSpeeds = MutableStateFlow(listOf(0.75f, 1f, 1.25f))

        val preferencesHolder = mockk<PreferencesHolder>(relaxed = true)
        every { preferencesHolder.playerQuality } returns qualityPreference
        every { preferencesHolder.playSpeed } returns speedPreference
        every { preferencesHolder.playerAutoplay } returns autoplayPreference
        every { preferencesHolder.availableSpeeds } returns availableSpeeds
        return preferencesHolder
    }

    private fun <T> statePreference(initialValue: T): AppPreference<T> {
        val state = MutableStateFlow(initialValue)
        val preference = mockk<AppPreference<T>>(relaxed = true)
        every { preference.value } answers { state.value }
        every { preference.value = any() } answers {
            @Suppress("UNCHECKED_CAST")
            state.value = firstArg<Any?>() as T
        }
        every { preference.replayCache } answers { state.replayCache }
        coEvery { preference.collect(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val collector = firstArg<FlowCollector<*>>() as FlowCollector<T>
            state.collect(collector)
        }
        return preference
    }
}
