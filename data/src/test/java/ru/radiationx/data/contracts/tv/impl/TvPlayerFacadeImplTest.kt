package ru.radiationx.data.contracts.tv.impl

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.UserViewsRepository

class TvPlayerFacadeImplTest {

    @Test
    fun getLocalContinueEpisodeId_picksMostRecentAccess() = runBlocking {
        val releaseId = ReleaseId(42)
        val first = EpisodeId("1", releaseId)
        val second = EpisodeId("2", releaseId)
        val releaseInteractor = mockk<ReleaseInteractor>()
        coEvery { releaseInteractor.getAccesses(releaseId) } returns listOf(
            EpisodeAccess(id = first, seek = 1_000L, isViewed = false, lastAccess = 100L),
            EpisodeAccess(id = second, seek = 2_000L, isViewed = false, lastAccess = 200L),
        )

        val facade = TvPlayerFacadeImpl(
            releaseInteractor = releaseInteractor,
            userViewsRepository = mockk<UserViewsRepository>(relaxed = true),
            authRepository = mockk<AuthRepository>(relaxed = true),
        )

        val result = facade.getLocalContinueEpisodeId(releaseId)

        assertEquals(second, result)
    }

    @Test
    fun getRemoteEpisodeSeek_returnsZero_whenNoRemoteTimecode() = runBlocking {
        val episodeId = EpisodeId("1", ReleaseId(24))
        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getEpisodeTimecode(episodeId) } returns null

        val facade = TvPlayerFacadeImpl(
            releaseInteractor = mockk<ReleaseInteractor>(relaxed = true),
            userViewsRepository = userViewsRepository,
            authRepository = mockk<AuthRepository>(relaxed = true),
        )

        val result = facade.getRemoteEpisodeSeek(episodeId)

        assertEquals(0L, result)
    }
}
