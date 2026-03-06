package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.system.ApplicationCoroutineScope
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class EpisodesCheckerStorageTest {

    @Test
    fun putEpisode_thenPutAllWithStaleData_doesNotDegradeState() = runBlocking {
        val storage = createStorage()
        val episodeId = EpisodeId(id = "1", releaseId = ReleaseId(100))
        val fresh = EpisodeAccess(
            id = episodeId,
            seek = 80_000L,
            isViewed = true,
            lastAccess = 20_000L,
        )
        val stale = EpisodeAccess(
            id = episodeId,
            seek = 500L,
            isViewed = false,
            lastAccess = 10_000L,
        )

        storage.putEpisode(fresh)
        storage.putAllEpisode(listOf(stale))

        val actual = storage.getEpisode(episodeId)
        assertEquals(fresh.seek, actual?.seek)
        assertEquals(fresh.isViewed, actual?.isViewed)
        assertEquals(fresh.lastAccessRaw, actual?.lastAccessRaw)
    }

    @Test
    fun putAll_resolverHonorsWatchedPrioritySeekDeltaAndLastAccessFallback() = runBlocking {
        val storage = createStorage()

        val watchedZeroId = EpisodeId(id = "1", releaseId = ReleaseId(200))
        val watchedZeroLocal = EpisodeAccess(watchedZeroId, seek = 60_000L, isViewed = false, lastAccess = 0L)
        val watchedZeroIncoming = EpisodeAccess(watchedZeroId, seek = 1_000L, isViewed = true, lastAccess = 0L)
        storage.putEpisode(watchedZeroLocal)
        storage.putAllEpisode(listOf(watchedZeroIncoming))
        assertEquals(watchedZeroIncoming, storage.getEpisode(watchedZeroId))

        val watchedEqualId = EpisodeId(id = "2", releaseId = ReleaseId(200))
        val watchedEqualLocal = EpisodeAccess(watchedEqualId, seek = 55_000L, isViewed = false, lastAccess = 7_000L)
        val watchedEqualIncoming = EpisodeAccess(watchedEqualId, seek = 1_500L, isViewed = true, lastAccess = 7_000L)
        storage.putEpisode(watchedEqualLocal)
        storage.putAllEpisode(listOf(watchedEqualIncoming))
        assertEquals(watchedEqualIncoming, storage.getEpisode(watchedEqualId))

        val seekDeltaId = EpisodeId(id = "3", releaseId = ReleaseId(200))
        val seekDeltaLocal = EpisodeAccess(seekDeltaId, seek = 10_000L, isViewed = true, lastAccess = 0L)
        val seekDeltaIncoming = EpisodeAccess(seekDeltaId, seek = 11_500L, isViewed = true, lastAccess = 0L)
        storage.putEpisode(seekDeltaLocal)
        storage.putAllEpisode(listOf(seekDeltaIncoming))
        assertEquals(seekDeltaIncoming, storage.getEpisode(seekDeltaId))

        val fallbackId = EpisodeId(id = "4", releaseId = ReleaseId(200))
        val fallbackLocal = EpisodeAccess(fallbackId, seek = 30_000L, isViewed = true, lastAccess = 0L)
        val fallbackIncoming = EpisodeAccess(fallbackId, seek = 30_600L, isViewed = true, lastAccess = 9_000L)
        storage.putEpisode(fallbackLocal)
        storage.putAllEpisode(listOf(fallbackIncoming))
        assertEquals(fallbackIncoming, storage.getEpisode(fallbackId))
    }

    @Test
    fun concurrentPutPutAllRemove_doesNotCrash_andStateRemainsConsistent() = runBlocking {
        val storage = createStorage()
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        coroutineScope {
            repeat(8) { worker ->
                launch(Dispatchers.Default) {
                    repeat(200) { step ->
                        runCatching {
                            val releaseId = ReleaseId((step % 5) + 1)
                            val episodeId = EpisodeId(
                                id = (((worker + step) % 4) + 1).toString(),
                                releaseId = releaseId,
                            )
                            val fresh = EpisodeAccess(
                                id = episodeId,
                                seek = (worker * 10_000L) + (step * 50L),
                                isViewed = step % 2 == 0,
                                lastAccess = (worker * 100_000L) + step.toLong() + 1L,
                            )
                            val stale = fresh.copy(
                                seek = (fresh.seek - 700L).coerceAtLeast(0L),
                                isViewed = false,
                                lastAccess = (fresh.lastAccessRaw - 1_000L).coerceAtLeast(0L),
                            )

                            when ((worker + step) % 3) {
                                0 -> storage.putEpisode(fresh)
                                1 -> storage.putAllEpisode(listOf(stale, fresh))
                                else -> storage.remove(releaseId)
                            }
                        }.exceptionOrNull()?.let(failures::add)
                    }
                }
            }
        }

        assertTrue("No write operation should fail", failures.isEmpty())

        val allEpisodes = storage.getEpisodes()
        assertEquals(allEpisodes.size, allEpisodes.map { it.id }.toSet().size)

        allEpisodes.forEach { episode ->
            assertTrue(episode.seek >= 0L)
            assertEquals(episode, storage.getEpisode(episode.id))
        }

        (1..5).map(::ReleaseId).forEach { releaseId ->
            assertTrue(storage.getEpisodes(releaseId).all { it.id.releaseId == releaseId })
        }
    }

    @Test
    fun saveAll_applyRunsOnBackgroundThread_notOnCallerThread() = runBlocking {
        val prefs = EpisodesInMemorySharedPreferences()
        val storage = createStorage(sharedPreferences = prefs)
        val callerThreadName = Thread.currentThread().name

        storage.putEpisode(
            EpisodeAccess(
                id = EpisodeId(id = "42", releaseId = ReleaseId(4242)),
                seek = 25_000L,
                isViewed = true,
                lastAccess = 777L,
            )
        )

        waitUntil { prefs.applyThreadNames.isNotEmpty() }
        assertTrue("Expected at least one SharedPreferences.apply call", prefs.applyThreadNames.isNotEmpty())
        assertTrue(
            "saveAll must run on IO/background dispatcher",
            prefs.applyThreadNames.all { it != callerThreadName },
        )
    }

    @Test
    fun putAllEpisodeBatched_savesInBatches_notPerItem() = runBlocking {
        val prefs = EpisodesInMemorySharedPreferences()
        val storage = createStorage(sharedPreferences = prefs)

        val episodes = (1..120).map { index ->
            EpisodeAccess(
                id = EpisodeId(id = index.toString(), releaseId = ReleaseId(index)),
                seek = index * 1_000L,
                isViewed = true,
                lastAccess = index.toLong(),
            )
        }

        storage.putAllEpisodeBatched(
            episodes = episodes,
            batchSize = 10,
            saveEveryBatches = 1,
        )

        assertEquals(episodes.size, storage.getEpisodes().size)
        assertTrue("Expected at least one persisted snapshot", prefs.applyCount > 0)
        assertTrue(
            "Batched save must not write once per item",
            prefs.applyCount < episodes.size,
        )
    }

    @Test
    fun rapidPutEpisode_coalescesSingleItemWrites() = runBlocking {
        val prefs = EpisodesInMemorySharedPreferences()
        val storage = createStorage(sharedPreferences = prefs)
        val episodeId = EpisodeId(id = "11", releaseId = ReleaseId(11))

        storage.putEpisode(EpisodeAccess(episodeId, seek = 1_000L, isViewed = true, lastAccess = 1L))
        storage.putEpisode(EpisodeAccess(episodeId, seek = 2_000L, isViewed = true, lastAccess = 2L))
        storage.putEpisode(EpisodeAccess(episodeId, seek = 3_000L, isViewed = true, lastAccess = 3L))

        waitUntil { prefs.applyCount == 1 }

        assertEquals(1, prefs.applyCount)
        assertEquals(3_000L, storage.getEpisode(episodeId)?.seek)
    }

    private fun createStorage(
        sharedPreferences: SharedPreferences = EpisodesInMemorySharedPreferences(),
    ): EpisodesCheckerStorage {
        return EpisodesCheckerStorage(
            sharedPreferences = sharedPreferences,
            moshi = Moshi.Builder().build(),
            applicationScope = ApplicationCoroutineScope(),
        )
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(25)
        }
        error("Condition was not met in time")
    }
}

private class EpisodesInMemorySharedPreferences(
    initial: Map<String, Any?> = emptyMap(),
) : SharedPreferences {

    private val values = ConcurrentHashMap(initial)
    val applyThreadNames = Collections.synchronizedList(mutableListOf<String>())
    val applyCount: Int
        get() = applyThreadNames.size

    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun edit(): SharedPreferences.Editor = EditorImpl(values, applyThreadNames)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op for tests
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op for tests
    }

    private class EditorImpl(
        private val target: ConcurrentHashMap<String, Any?>,
        private val applyThreadNames: MutableList<String>,
    ) : SharedPreferences.Editor {

        private var clearAll = false
        private val removals = mutableSetOf<String>()
        private val updates = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = values
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) {
                removals += key
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            applyThreadNames += Thread.currentThread().name

            if (clearAll) {
                target.clear()
            }
            removals.forEach { key ->
                target.remove(key)
            }
            updates.forEach { (key, value) ->
                target[key] = value
            }
        }
    }
}
