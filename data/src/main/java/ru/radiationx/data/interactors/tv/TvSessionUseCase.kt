package ru.radiationx.data.interactors.tv

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.repository.AuthRepository
import javax.inject.Inject

interface TvSessionUseCase {
    fun observeAuthState(): Flow<AuthState>

    suspend fun getAuthState(): AuthState
}

class TvSessionUseCaseImpl
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : TvSessionUseCase {
        override fun observeAuthState(): Flow<AuthState> = authRepository.observeAuthState()

        override suspend fun getAuthState(): AuthState = authRepository.getAuthState()
    }
