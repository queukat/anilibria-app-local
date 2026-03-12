package ru.radiationx.data.interactors.tv

import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.data.repository.UserViewsRepository
import javax.inject.Inject

interface TvDetailHeaderUseCase {
    suspend fun isAuthorized(): Boolean
    suspend fun findLatestNotWatchedEpisodeIdForRelease(releaseId: ReleaseId): EpisodeId?
    suspend fun findLatestEpisodeIdForRelease(releaseId: ReleaseId): EpisodeId?
    suspend fun addFavorite(releaseId: ReleaseId)
    suspend fun deleteFavorite(releaseId: ReleaseId)
}

class TvDetailHeaderUseCaseImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
    private val userViewsRepository: UserViewsRepository,
) : TvDetailHeaderUseCase {

    override suspend fun isAuthorized(): Boolean {
        return authRepository.getAuthState() == AuthState.AUTH
    }

    override suspend fun findLatestNotWatchedEpisodeIdForRelease(releaseId: ReleaseId): EpisodeId? {
        return userViewsRepository.findLatestNotWatchedEpisodeIdForRelease(releaseId)
    }

    override suspend fun findLatestEpisodeIdForRelease(releaseId: ReleaseId): EpisodeId? {
        return userViewsRepository.findLatestEpisodeIdForRelease(releaseId)
    }

    override suspend fun addFavorite(releaseId: ReleaseId) {
        favoriteRepository.addFavoriteAniLiberty(releaseId)
    }

    override suspend fun deleteFavorite(releaseId: ReleaseId) {
        favoriteRepository.deleteFavoriteAniLiberty(releaseId)
    }
}
