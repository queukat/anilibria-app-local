package ru.radiationx.data.datasource.holders

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.entity.domain.types.ReleaseId

interface HistoryHolder {
    companion object {
        const val DEFAULT_BULK_BATCH_SIZE = 250
        const val DEFAULT_BULK_SAVE_EVERY_BATCHES = 4
    }

    suspend fun getIds(): List<ReleaseId>
    fun observeIds(): Flow<List<ReleaseId>>
    suspend fun putId(id: ReleaseId)
    suspend fun putAllIds(ids: List<ReleaseId>)
    suspend fun putAllIdsBatched(
        ids: List<ReleaseId>,
        batchSize: Int = DEFAULT_BULK_BATCH_SIZE,
        saveEveryBatches: Int = DEFAULT_BULK_SAVE_EVERY_BATCHES,
    ) {
        putAllIds(ids)
    }
    suspend fun removeId(id: ReleaseId)
}
