package ru.radiationx.data.contracts.tv.impl

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.contracts.tv.TvPlayerFacade
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

class TvPlayerFacadeImpl @Inject constructor(
    private val releaseInteractor: ReleaseInteractor,
    private val userViewsRepository: UserViewsRepository,
    private val authRepository: AuthRepository,
) : TvPlayerFacade {

    override fun observeAuthState(): Flow<AuthState> = authRepository.observeAuthState()

    override suspend fun getAuthState(): AuthState = authRepository.getAuthState()

    override suspend fun loadWithFranchises(releaseId: ReleaseId): List<Release> {
        return releaseInteractor.loadWithFranchises(releaseId)
    }

    override suspend fun getLocalContinueEpisodeId(releaseId: ReleaseId): EpisodeId? {
        return releaseInteractor
            .getAccesses(releaseId)
            .maxByOrNull { it.lastAccessRaw }
            ?.id
    }

    override suspend fun getRemoteContinueEpisodeId(releaseId: ReleaseId): EpisodeId? {
        return userViewsRepository.findLatestEpisodeIdForRelease(releaseId)
    }

    override suspend fun getLocalEpisodeSeek(episodeId: EpisodeId): Long {
        return releaseInteractor.getAccess(episodeId)?.seek ?: 0L
    }

    override suspend fun saveLocalEpisodeSeek(episodeId: EpisodeId, seek: Long) {
        releaseInteractor.setAccessSeek(episodeId, seek)
    }

    override suspend fun getRemoteEpisodeSeek(episodeId: EpisodeId): Long {
        return userViewsRepository.getEpisodeTimecode(episodeId)?.positionMs ?: 0L
    }

    override suspend fun saveRemoteEpisodeProgress(
        episodeId: EpisodeId,
        positionMs: Long,
        isWatched: Boolean,
    ) {
        userViewsRepository.upsertEpisodeTimecode(
            episodeId = episodeId,
            positionMs = positionMs,
            isWatched = isWatched,
        )
    }
}
