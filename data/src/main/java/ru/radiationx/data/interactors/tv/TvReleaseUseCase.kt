package ru.radiationx.data.interactors.tv

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.ReleaseRepository
import javax.inject.Inject

interface TvReleaseUseCase {
    fun observeRelease(releaseId: ReleaseId): Flow<Release>
    suspend fun loadRelease(releaseId: ReleaseId): Release
    suspend fun loadWithFranchises(releaseId: ReleaseId): List<Release>
}

class TvReleaseUseCaseImpl @Inject constructor(
    private val releaseRepository: ReleaseRepository,
    private val releaseInteractor: ReleaseInteractor,
) : TvReleaseUseCase {

    override fun observeRelease(releaseId: ReleaseId): Flow<Release> {
        return flow {
            runCatching { loadRelease(releaseId) }
            emit(Unit)
        }.flatMapLatest {
            releaseInteractor.observeCachedFull(releaseId = releaseId)
        }
    }

    override suspend fun loadRelease(releaseId: ReleaseId): Release {
        return releaseRepository.getReleaseAniLiberty(releaseId).also(releaseInteractor::updateFullCache)
    }

    override suspend fun loadWithFranchises(releaseId: ReleaseId): List<Release> {
        return releaseRepository
            .loadWithFranchisesAniLiberty(releaseId)
            .onEach(releaseInteractor::updateFullCache)
    }
}
