package ru.radiationx.data.contracts.tv

import kotlinx.coroutines.flow.Flow

interface TvWatchingFacade {
    fun observeLocalContinueAvailable(): Flow<Boolean>

    fun observeLocalHistoryAvailable(): Flow<Boolean>

    fun requestBackgroundSync()
}
