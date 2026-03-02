package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.datasource.SuspendMutableStateFlow
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.db.EpisodeAccessDb
import ru.radiationx.data.entity.db.EpisodeAccessLegacyDb
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.mapper.toDb
import ru.radiationx.data.entity.mapper.toDomain
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by radiationx on 17.02.18.
 */
class EpisodesCheckerStorage @Inject constructor(
    @DataPreferences private val sharedPreferences: SharedPreferences,
    private val moshi: Moshi,
) : EpisodesCheckerHolder {

    companion object {
        private const val LEGACY_LOCAL_EPISODES_KEY = "data.local_episodes"
        private const val LOCAL_EPISODES_KEY = "data.local_episodes_v2"
        private const val MIN_PROGRESS_DELTA_MS = 1_000L
        private const val BULK_SAVE_MIN_INTERVAL_MS = 750L
    }

    private val legacyDataAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, EpisodeAccessLegacyDb::class.java)
        moshi.adapter<List<EpisodeAccessLegacyDb>>(type)
    }

    private val dataAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, EpisodeAccessDb::class.java)
        moshi.adapter<List<EpisodeAccessDb>>(type)
    }

    private val localEpisodesRelay = SuspendMutableStateFlow {
        loadAll()
    }
    private val writeMutex = Mutex()

    override fun observeEpisodes(): Flow<List<EpisodeAccess>> =
        localEpisodesRelay

    override suspend fun getEpisodes(): List<EpisodeAccess> {
        return localEpisodesRelay.getValue()
    }

    override suspend fun putEpisode(episode: EpisodeAccess) {
        writeMutex.withLock {
            var replaced = false
            var totalCount = 0

            localEpisodesRelay.update { localEpisodes ->
                val mutableLocalEpisodes = localEpisodes.toMutableList()
                val index = mutableLocalEpisodes.indexOfFirst { it.id == episode.id }
                if (index >= 0) {
                    replaced = true
                    mutableLocalEpisodes[index] = episode
                } else {
                    mutableLocalEpisodes.add(episode)
                }
                totalCount = mutableLocalEpisodes.size
                mutableLocalEpisodes
            }

            saveAll()
            Timber.d(
                "EpisodesCheckerStorage.write op=putEpisode episodeId=%s seekMs=%d isViewed=%s lastAccessMs=%d replaced=%s total=%d",
                episode.id.toString(),
                episode.seek,
                episode.isViewed,
                episode.lastAccessRaw,
                replaced,
                totalCount,
            )
        }
    }

    override suspend fun putAllEpisode(episodes: List<EpisodeAccess>) {
        if (episodes.isEmpty()) return
        putAllEpisodeBatched(
            episodes = episodes,
            batchSize = episodes.size,
            saveEveryBatches = Int.MAX_VALUE,
        )
    }

    override suspend fun putAllEpisodeBatched(
        episodes: List<EpisodeAccess>,
        batchSize: Int,
        saveEveryBatches: Int,
    ) {
        if (episodes.isEmpty()) return

        val safeBatchSize = batchSize.coerceAtLeast(1)
        val safeSaveEveryBatches = saveEveryBatches.coerceAtLeast(1)

        writeMutex.withLock {
            val startedAtMs = nowMs()

            var changedCount = 0
            var incomingWins = 0
            var localWins = 0
            var chunkIndex = 0
            var persistedSnapshots = 0
            var lastPersistAtMs = startedAtMs

            val mergedByEpisodeId = localEpisodesRelay.getValue()
                .associateBy { it.id }
                .toMutableMap()

            episodes.chunked(safeBatchSize).forEach { chunk ->
                chunkIndex += 1

                chunk.forEach { incoming ->
                    val current = mergedByEpisodeId[incoming.id]
                    val winner = resolveEpisodeConflict(current = current, incoming = incoming)
                    if (winner === incoming) {
                        incomingWins++
                    } else {
                        localWins++
                    }
                    if (current != winner) {
                        changedCount++
                    }
                    mergedByEpisodeId[incoming.id] = winner
                }

                val nowMs = nowMs()
                val canPersistThisBatch = chunkIndex % safeSaveEveryBatches == 0
                val enoughTimeSinceLastPersist = nowMs - lastPersistAtMs >= BULK_SAVE_MIN_INTERVAL_MS
                if (canPersistThisBatch && enoughTimeSinceLastPersist) {
                    val saveStartedAtMs = nowMs()
                    saveAll(mergedByEpisodeId.values.toList())
                    val saveDurationMs = nowMs() - saveStartedAtMs
                    persistedSnapshots += 1
                    lastPersistAtMs = nowMs()
                    Timber.d(
                        "EpisodesCheckerStorage.write op=putAllBatched checkpoint batch=%d chunkSize=%d saveDurationMs=%d",
                        chunkIndex,
                        chunk.size,
                        saveDurationMs,
                    )
                }
            }

            val finalSnapshot = mergedByEpisodeId.values.toList()
            localEpisodesRelay.setValue(finalSnapshot)

            val finalSaveStartedAtMs = nowMs()
            saveAll(finalSnapshot)
            val finalSaveDurationMs = nowMs() - finalSaveStartedAtMs
            persistedSnapshots += 1
            val durationMs = nowMs() - startedAtMs

            Timber.d(
                "EpisodesCheckerStorage.write op=putAllBatched incoming=%d changed=%d incomingWins=%d localWins=%d total=%d chunks=%d saves=%d batchSize=%d saveEvery=%d durationMs=%d finalSaveMs=%d",
                episodes.size,
                changedCount,
                incomingWins,
                localWins,
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

    override suspend fun getEpisodes(releaseId: ReleaseId): List<EpisodeAccess> {
        return localEpisodesRelay.getValue().filter { it.id.releaseId == releaseId }
    }

    override suspend fun getEpisode(episodeId: EpisodeId): EpisodeAccess? {
        return localEpisodesRelay.getValue().find { it.id == episodeId }
    }

    override suspend fun remove(releaseId: ReleaseId) {
        writeMutex.withLock {
            var removedCount = 0
            var totalCount = 0

            localEpisodesRelay.update { localEpisodes ->
                val mutableLocalEpisodes = localEpisodes.toMutableList()
                val beforeSize = mutableLocalEpisodes.size
                mutableLocalEpisodes.removeAll { it.id.releaseId == releaseId }
                removedCount = beforeSize - mutableLocalEpisodes.size
                totalCount = mutableLocalEpisodes.size
                mutableLocalEpisodes
            }

            saveAll()
            Timber.d(
                "EpisodesCheckerStorage.write op=remove releaseId=%s removed=%d total=%d",
                releaseId.id,
                removedCount,
                totalCount,
            )
        }
    }

    private fun resolveEpisodeConflict(
        current: EpisodeAccess?,
        incoming: EpisodeAccess,
    ): EpisodeAccess {
        if (current == null) return incoming

        val currentLastAccess = current.lastAccessRaw
        val incomingLastAccess = incoming.lastAccessRaw

        val hasComparableFreshTimestamps = currentLastAccess > 0L &&
            incomingLastAccess > 0L &&
            currentLastAccess != incomingLastAccess

        if (hasComparableFreshTimestamps) {
            return if (incomingLastAccess > currentLastAccess) incoming else current
        }

        if (current.isViewed != incoming.isViewed) {
            return if (incoming.isViewed) incoming else current
        }

        return when {
            incoming.seek > current.seek + MIN_PROGRESS_DELTA_MS -> incoming
            current.seek > incoming.seek + MIN_PROGRESS_DELTA_MS -> current
            incomingLastAccess > currentLastAccess -> incoming
            else -> current
        }
    }

    private suspend fun saveAll() {
        saveAll(localEpisodesRelay.getValue())
    }

    private suspend fun saveAll(episodes: List<EpisodeAccess>) {
        withContext(Dispatchers.IO) {
            val jsonEpisodes = episodes
                .map { episode -> episode.toDb() }
                .let { dataAdapter.toJson(it) }
            sharedPreferences
                .edit()
                .putString(LOCAL_EPISODES_KEY, jsonEpisodes)
                .apply()
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    private suspend fun loadAll(): List<EpisodeAccess> {
        return withContext(Dispatchers.IO) {
            val actualData = sharedPreferences
                .getString(LOCAL_EPISODES_KEY, null)
                ?.let { dataAdapter.fromJson(it) }
            val data = actualData ?: loadAllLegacy()?.map { it.toDb() }
            data?.map { it.toDomain() }.orEmpty()
        }
    }

    private suspend fun loadAllLegacy(): List<EpisodeAccessLegacyDb>? {
        return withContext(Dispatchers.IO) {
            sharedPreferences
                .getString(LEGACY_LOCAL_EPISODES_KEY, null)
                ?.let { legacyDataAdapter.fromJson(it) }
        }
    }
}
