package ru.radiationx.data.interactors

import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.datasource.holders.HistoryHolder
import ru.radiationx.data.datasource.holders.UserViewsSyncHolder
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.repository.UserViewsRepository
import ru.radiationx.data.system.ApplicationCoroutineScope

class UserViewsSyncInteractorMergeGuardTest {

    @Test
    fun mergeEpisodeProgress_localLastAccessAfterSyncStart_localWinsEvenWithHigherRemoteSeek() {
        val interactor = createInteractor()
        val episodeId = EpisodeId(id = "1", releaseId = ReleaseId(1))
        val local = EpisodeAccess(
            id = episodeId,
            seek = 20_000L,
            isViewed = true,
            lastAccess = 50_000L,
        )

        val result = interactor.invokeMergeEpisodeProgress(
            episodeId = episodeId,
            local = local,
            localHasPendingUpload = false,
            remoteSeekMs = 95_000L,
            remoteIsWatched = true,
            durationMs = 100_000L,
            remoteLastAccessMs = 49_000L,
            remoteTimestampTrusted = true,
            syncSessionStartedAtMs = 40_000L,
        )

        assertSame(local, result)
    }

    @Test
    fun mergeEpisodeProgress_trustedRemoteTimestampTooOld_localWins() {
        val interactor = createInteractor()
        val episodeId = EpisodeId(id = "2", releaseId = ReleaseId(1))
        val local = EpisodeAccess(
            id = episodeId,
            seek = 25_000L,
            isViewed = true,
            lastAccess = 50_000L,
        )

        val result = interactor.invokeMergeEpisodeProgress(
            episodeId = episodeId,
            local = local,
            localHasPendingUpload = false,
            remoteSeekMs = 120_000L,
            remoteIsWatched = true,
            durationMs = 120_000L,
            remoteLastAccessMs = 30_000L,
            remoteTimestampTrusted = true,
            syncSessionStartedAtMs = 60_000L,
        )

        assertSame(local, result)
    }

    @Test
    fun mergeEpisodeProgress_pendingLocalUpload_localWins() {
        val interactor = createInteractor()
        val episodeId = EpisodeId(id = "3", releaseId = ReleaseId(1))
        val local = EpisodeAccess(
            id = episodeId,
            seek = 15_000L,
            isViewed = true,
            lastAccess = 10_000L,
        )

        val result = interactor.invokeMergeEpisodeProgress(
            episodeId = episodeId,
            local = local,
            localHasPendingUpload = true,
            remoteSeekMs = 120_000L,
            remoteIsWatched = true,
            durationMs = 120_000L,
            remoteLastAccessMs = 30_000L,
            remoteTimestampTrusted = true,
            syncSessionStartedAtMs = 60_000L,
        )

        assertSame(local, result)
    }

    private fun createInteractor(): UserViewsSyncInteractor {
        return UserViewsSyncInteractor(
            aniLibertyApi = mockk<AniLibertyApi>(relaxed = true),
            authTokenHolder = mockk<AuthTokenHolder>(relaxed = true),
            episodesCheckerHolder = mockk<EpisodesCheckerHolder>(relaxed = true),
            historyHolder = mockk<HistoryHolder>(relaxed = true),
            syncHolder = mockk<UserViewsSyncHolder>(relaxed = true),
            userViewsRepository = mockk<UserViewsRepository>(relaxed = true),
            applicationScope = ApplicationCoroutineScope(),
        )
    }

    private fun UserViewsSyncInteractor.invokeMergeEpisodeProgress(
        episodeId: EpisodeId,
        local: EpisodeAccess?,
        localHasPendingUpload: Boolean,
        remoteSeekMs: Long,
        remoteIsWatched: Boolean,
        durationMs: Long?,
        remoteLastAccessMs: Long?,
        remoteTimestampTrusted: Boolean,
        syncSessionStartedAtMs: Long,
    ): EpisodeAccess? {
        val method = UserViewsSyncInteractor::class.java.getDeclaredMethod(
            "mergeEpisodeProgress",
            EpisodeId::class.java,
            EpisodeAccess::class.java,
            Boolean::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            java.lang.Long::class.java,
            java.lang.Long::class.java,
            Boolean::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
        ).apply {
            isAccessible = true
        }

        return method.invoke(
            this,
            episodeId,
            local,
            localHasPendingUpload,
            remoteSeekMs,
            remoteIsWatched,
            durationMs,
            remoteLastAccessMs,
            remoteTimestampTrusted,
            syncSessionStartedAtMs,
        ) as EpisodeAccess?
    }
}
