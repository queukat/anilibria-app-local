package ru.radiationx.data.contracts.tv.impl

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.contracts.tv.TvProfileFacade
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.data.repository.AuthRepository
import javax.inject.Inject

class TvProfileFacadeImpl @Inject constructor(
    private val authRepository: AuthRepository,
) : TvProfileFacade {

    override fun observeUser(): Flow<ProfileItem?> = authRepository.observeUser()

    override suspend fun signOut() {
        authRepository.signOut()
    }
}
