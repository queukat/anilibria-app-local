package ru.radiationx.data.contracts.tv

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.entity.common.AuthState

interface TvWatchingFacade {
    data class RemoteAvailability(
        val hasHistory: Boolean,
        val hasContinue: Boolean,
    )

    fun observeAuthState(): Flow<AuthState>

    fun observeLocalContinueAvailable(): Flow<Boolean>

    fun observeLocalHistoryAvailable(): Flow<Boolean>

    suspend fun probeRemoteAvailability(limit: Int): RemoteAvailability
}
