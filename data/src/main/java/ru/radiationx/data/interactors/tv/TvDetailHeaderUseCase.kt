package ru.radiationx.data.interactors.tv

import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.FavoriteRepository
import javax.inject.Inject

interface TvDetailHeaderUseCase {
    suspend fun isAuthorized(): Boolean
    suspend fun addFavorite(releaseId: ReleaseId)
    suspend fun deleteFavorite(releaseId: ReleaseId)
}

class TvDetailHeaderUseCaseImpl @Inject constructor(
    private val authRepository: AuthRepository,
    private val favoriteRepository: FavoriteRepository,
) : TvDetailHeaderUseCase {

    override suspend fun isAuthorized(): Boolean {
        return authRepository.getAuthState() == AuthState.AUTH
    }

    override suspend fun addFavorite(releaseId: ReleaseId) {
        favoriteRepository.addFavoriteAniLiberty(releaseId)
    }

    override suspend fun deleteFavorite(releaseId: ReleaseId) {
        favoriteRepository.deleteFavoriteAniLiberty(releaseId)
    }
}
