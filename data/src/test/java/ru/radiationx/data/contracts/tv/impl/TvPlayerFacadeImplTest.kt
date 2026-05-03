package ru.radiationx.data.contracts.tv.impl

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.TvReleaseUseCase
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.UserViewsRepository

class TvPlayerFacadeImplTest {
    @Test
    fun getLocalContinueEpisodeId_picksMostRecentAccess() =
        runTest {
            val releaseId = ReleaseId(42)
            val first = EpisodeId("1", releaseId)
            val second = EpisodeId("2", releaseId)
            val releaseInteractor = mockk<ReleaseInteractor>()
            coEvery { releaseInteractor.getAccesses(releaseId) } returns
                listOf(
                    EpisodeAccess(id = first, seek = 1_000L, isViewed = false, lastAccess = 100L),
                    EpisodeAccess(id = second, seek = 2_000L, isViewed = false, lastAccess = 200L),
                )

            val facade =
                TvPlayerFacadeImpl(
                    releaseInteractor = releaseInteractor,
                    tvReleaseUseCase = mockk<TvReleaseUseCase>(relaxed = true),
                    userViewsRepository = mockk<UserViewsRepository>(relaxed = true),
                    authRepository = mockk<AuthRepository>(relaxed = true),
                )

            val result = facade.getLocalContinueEpisodeId(releaseId)

            assertEquals(second, result)
        }

    @Test
    fun saveRemoteEpisodeProgress_delegatesToRepositoryQueue() =
        runTest {
            val episodeId = EpisodeId("1", ReleaseId(24))
            val userViewsRepository = mockk<UserViewsRepository>(relaxed = true)

            val facade =
                TvPlayerFacadeImpl(
                    releaseInteractor = mockk<ReleaseInteractor>(relaxed = true),
                    tvReleaseUseCase = mockk<TvReleaseUseCase>(relaxed = true),
                    userViewsRepository = userViewsRepository,
                    authRepository = mockk<AuthRepository>(relaxed = true),
                )

            facade.saveRemoteEpisodeProgress(
                episodeId = episodeId,
                positionMs = 12_000L,
                isWatched = false,
            )

            coVerify(exactly = 1) {
                userViewsRepository.upsertEpisodeTimecode(
                    episodeId = episodeId,
                    positionMs = 12_000L,
                    isWatched = false,
                )
            }
        }

    @Test
    fun loadWithFranchises_usesCachedReleasePath_whenFullReleaseAlreadyCached() =
        runTest {
            val releaseId = ReleaseId(99)
            val cachedRelease = mockk<Release>()
            val releaseInteractor = mockk<ReleaseInteractor>()
            val tvReleaseUseCase = mockk<TvReleaseUseCase>()
            every { releaseInteractor.getCachedFull(releaseId = releaseId) } returns cachedRelease
            coEvery { releaseInteractor.loadWithFranchises(releaseId) } returns listOf(cachedRelease)

            val facade =
                TvPlayerFacadeImpl(
                    releaseInteractor = releaseInteractor,
                    tvReleaseUseCase = tvReleaseUseCase,
                    userViewsRepository = mockk<UserViewsRepository>(relaxed = true),
                    authRepository = mockk<AuthRepository>(relaxed = true),
                )

            val result = facade.loadWithFranchises(releaseId)

            assertEquals(listOf(cachedRelease), result)
            coVerify(exactly = 1) { releaseInteractor.loadWithFranchises(releaseId) }
            coVerify(exactly = 0) { tvReleaseUseCase.loadWithFranchises(any()) }
        }

    @Test
    fun loadWithFranchises_usesFreshLoad_whenNoCachedReleaseExists() =
        runTest {
            val releaseId = ReleaseId(77)
            val loadedRelease = mockk<Release>()
            val releaseInteractor = mockk<ReleaseInteractor>()
            val tvReleaseUseCase = mockk<TvReleaseUseCase>()
            every { releaseInteractor.getCachedFull(releaseId = releaseId) } returns null
            coEvery { tvReleaseUseCase.loadWithFranchises(releaseId) } returns listOf(loadedRelease)

            val facade =
                TvPlayerFacadeImpl(
                    releaseInteractor = releaseInteractor,
                    tvReleaseUseCase = tvReleaseUseCase,
                    userViewsRepository = mockk<UserViewsRepository>(relaxed = true),
                    authRepository = mockk<AuthRepository>(relaxed = true),
                )

            val result = facade.loadWithFranchises(releaseId)

            assertEquals(listOf(loadedRelease), result)
            coVerify(exactly = 1) { tvReleaseUseCase.loadWithFranchises(releaseId) }
            coVerify(exactly = 0) { releaseInteractor.loadWithFranchises(any()) }
        }
}
