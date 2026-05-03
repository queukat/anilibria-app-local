package ru.radiationx.data.contracts.tv

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId

interface TvPlayerFacade {
    fun observeAuthState(): Flow<AuthState>

    suspend fun loadWithFranchises(releaseId: ReleaseId): List<Release>

    suspend fun getLocalContinueEpisodeId(releaseId: ReleaseId): EpisodeId?

    suspend fun getLocalEpisodeSeek(episodeId: EpisodeId): Long

    suspend fun saveLocalEpisodeSeek(
        episodeId: EpisodeId,
        seek: Long,
    )

    suspend fun saveRemoteEpisodeProgress(
        episodeId: EpisodeId,
        positionMs: Long,
        isWatched: Boolean,
    )
}
