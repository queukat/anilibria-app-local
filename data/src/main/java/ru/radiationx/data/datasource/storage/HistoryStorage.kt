package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.datasource.SuspendMutableStateFlow
import ru.radiationx.data.datasource.holders.HistoryHolder
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.shared.ktx.android.mapObjects
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by radiationx on 18.02.18.
 */
class HistoryStorage @Inject constructor(
    @DataPreferences private val sharedPreferences: SharedPreferences
) : HistoryHolder {

    companion object {
        private const val LOCAL_HISTORY_KEY = "data.local_history_new"
        private const val BULK_SAVE_MIN_INTERVAL_MS = 750L
    }

    private val localReleasesRelay = SuspendMutableStateFlow {
        loadAll()
    }
    private val writeMutex = Mutex()

    override suspend fun getIds() = localReleasesRelay.getValue()

    override fun observeIds(): Flow<List<ReleaseId>> = localReleasesRelay

    override suspend fun putId(id: ReleaseId) {
        writeMutex.withLock {
            var totalCount = 0
            var existed = false

            localReleasesRelay.update { localReleases ->
                val mutableLocalReleases = localReleases.toMutableList()
                existed = mutableLocalReleases.remove(id)
                mutableLocalReleases.add(id)
                totalCount = mutableLocalReleases.size
                mutableLocalReleases
            }

            saveAll()
            Timber.d(
                "HistoryStorage.write op=putId releaseId=%d existed=%s total=%d",
                id.id,
                existed,
                totalCount,
            )
        }
    }

    override suspend fun putAllIds(ids: List<ReleaseId>) {
        if (ids.isEmpty()) return
        putAllIdsBatched(
            ids = ids,
            batchSize = ids.size,
            saveEveryBatches = Int.MAX_VALUE,
        )
    }

    override suspend fun putAllIdsBatched(
        ids: List<ReleaseId>,
        batchSize: Int,
        saveEveryBatches: Int,
    ) {
        if (ids.isEmpty()) return

        val safeBatchSize = batchSize.coerceAtLeast(1)
        val safeSaveEveryBatches = saveEveryBatches.coerceAtLeast(1)

        writeMutex.withLock {
            val startedAtMs = nowMs()
            val initialSnapshot = localReleasesRelay.getValue()
            val historySet = LinkedHashSet(initialSnapshot)
            val uniqueIncomingIds = HashSet<ReleaseId>()

            var newIdsCount = 0
            var chunkIndex = 0
            var persistedSnapshots = 0
            var lastPersistAtMs = startedAtMs

            ids.chunked(safeBatchSize).forEach { chunk ->
                chunkIndex += 1

                chunk.forEach { id ->
                    uniqueIncomingIds.add(id)
                    val existed = historySet.remove(id)
                    historySet.add(id)
                    if (!existed) {
                        newIdsCount += 1
                    }
                }

                val nowMs = nowMs()
                val canPersistThisBatch = chunkIndex % safeSaveEveryBatches == 0
                val enoughTimeSinceLastPersist = nowMs - lastPersistAtMs >= BULK_SAVE_MIN_INTERVAL_MS
                if (canPersistThisBatch && enoughTimeSinceLastPersist) {
                    val saveStartedAtMs = nowMs()
                    saveAll(historySet.toList())
                    val saveDurationMs = nowMs() - saveStartedAtMs
                    persistedSnapshots += 1
                    lastPersistAtMs = nowMs()
                    Timber.d(
                        "HistoryStorage.write op=putAllBatched checkpoint batch=%d chunkSize=%d saveDurationMs=%d",
                        chunkIndex,
                        chunk.size,
                        saveDurationMs,
                    )
                }
            }

            val finalSnapshot = historySet.toList()
            localReleasesRelay.setValue(finalSnapshot)

            val finalSaveStartedAtMs = nowMs()
            saveAll(finalSnapshot)
            val finalSaveDurationMs = nowMs() - finalSaveStartedAtMs
            persistedSnapshots += 1
            val durationMs = nowMs() - startedAtMs
            val changed = finalSnapshot != initialSnapshot

            Timber.d(
                "HistoryStorage.write op=putAllBatched incoming=%d uniqueIncoming=%d newIds=%d changed=%s total=%d chunks=%d saves=%d batchSize=%d saveEvery=%d durationMs=%d finalSaveMs=%d",
                ids.size,
                uniqueIncomingIds.size,
                newIdsCount,
                changed,
                finalSnapshot.size,
                chunkIndex,
                persistedSnapshots,
                safeBatchSize,
                safeSaveEveryBatches,
                durationMs,
                finalSaveDurationMs,
            )
        }
    }

    override suspend fun removeId(id: ReleaseId) {
        writeMutex.withLock {
            var removed = false
            var totalCount = 0

            localReleasesRelay.update { localReleases ->
                val mutableLocalReleases = localReleases.toMutableList()
                removed = mutableLocalReleases.remove(id)
                totalCount = mutableLocalReleases.size
                mutableLocalReleases
            }

            saveAll()
            Timber.d(
                "HistoryStorage.write op=removeId releaseId=%d removed=%s total=%d",
                id.id,
                removed,
                totalCount,
            )
        }
    }

    private suspend fun saveAll() {
        saveAll(localReleasesRelay.getValue())
    }

    private suspend fun saveAll(ids: List<ReleaseId>) {
        withContext(Dispatchers.IO) {
            val jsonEpisodes = JSONArray()
            ids.forEach {
                jsonEpisodes.put(JSONObject().apply {
                    put("id", it.id)
                })
            }
            sharedPreferences
                .edit()
                .putString(LOCAL_HISTORY_KEY, jsonEpisodes.toString())
                .apply()
        }
    }

    private suspend fun loadAll(): List<ReleaseId> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<ReleaseId>()
            sharedPreferences
                .getString(LOCAL_HISTORY_KEY, null)
                ?.let { JSONArray(it) }
                ?.let { jsonEpisodes ->
                    jsonEpisodes.mapObjects { jsonRelease ->
                        val id = ReleaseId(jsonRelease.getInt("id"))
                        result.add(id)
                    }
                }
            result
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()
}
