package ru.radiationx.anilibria.contentprovider.suggestions

import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class SuggestionQueryExecutor<T>(
    private val minQueryLength: Int,
    private val maxResults: Int,
    private val timeoutMs: Long,
    private val cacheTtlMs: Long,
    private val minRequestIntervalMs: Long,
    private val maxCacheEntries: Int = DEFAULT_MAX_CACHE_ENTRIES,
    private val onCacheUpdated: (query: String, items: List<T>) -> Unit = { _, _ -> },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "suggestions-provider").apply {
                isDaemon = true
            }
        },
    private val workerExecutor: ExecutorService =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "suggestions-provider-worker").apply {
                isDaemon = true
            }
        },
) {
    private val lock = Any()
    private var lastRefreshStartedAtMs: Long = 0L
    private var scheduledQuery: String? = null
    private var scheduledFuture: ScheduledFuture<*>? = null
    private var inFlightQuery: String? = null
    private var requestSequence: Long = 0L
    private val lastAppliedRequestByQuery =
        LinkedHashMap<String, AppliedRequestEntry>(
            maxCacheEntries,
            CACHE_LOAD_FACTOR,
            true,
        )
    private val cache =
        LinkedHashMap<String, CacheEntry<T>>(
            maxCacheEntries,
            CACHE_LOAD_FACTOR,
            true,
        )

    fun execute(
        rawQuery: String,
        fetch: (String) -> List<T>,
    ): List<T> {
        val query = rawQuery.trim()
        if (query.length < minQueryLength) {
            return emptyList()
        }

        val now = nowMillis()
        val cacheEntry =
            synchronized(lock) {
                cleanupStateLocked(now, keepQuery = query)
                cache[query]
            }
        val isCacheFresh = cacheEntry != null && now - cacheEntry.savedAtMs <= cacheTtlMs

        if (!isCacheFresh) {
            scheduleRefresh(query, fetch, now)
        }

        return cacheEntry?.items.orEmpty()
    }

    private fun scheduleRefresh(
        query: String,
        fetch: (String) -> List<T>,
        now: Long,
    ) {
        val requestId: Long
        val delayMs: Long
        synchronized(lock) {
            if (query == inFlightQuery || query == scheduledQuery) {
                return
            }
            requestId = ++requestSequence
            cleanupStateLocked(now, keepQuery = query)
            val elapsedSinceLastStart = now - lastRefreshStartedAtMs
            delayMs = (minRequestIntervalMs - elapsedSinceLastStart).coerceAtLeast(0L)
            scheduledQuery = query
            scheduledFuture?.cancel(false)
            scheduledFuture =
                scheduler.schedule(
                    { refresh(query, requestId, fetch) },
                    delayMs,
                    TimeUnit.MILLISECONDS,
                )
        }
    }

    private fun refresh(
        query: String,
        requestId: Long,
        fetch: (String) -> List<T>,
    ) {
        val canStartRefresh =
            synchronized(lock) {
                if (scheduledQuery != query) {
                    false
                } else {
                    scheduledQuery = null
                    inFlightQuery = query
                    lastRefreshStartedAtMs = nowMillis()
                    true
                }
            }
        if (!canStartRefresh) {
            return
        }

        val newItems = fetchWithTimeout(query, fetch)
        var appliedItems: List<T>? = null

        synchronized(lock) {
            if (inFlightQuery == query) {
                inFlightQuery = null
            }
            if (newItems != null) {
                val now = nowMillis()
                val lastAppliedId = lastAppliedRequestByQuery[query]?.requestId ?: Long.MIN_VALUE
                if (requestId >= lastAppliedId) {
                    lastAppliedRequestByQuery[query] =
                        AppliedRequestEntry(
                            requestId = requestId,
                            appliedAtMs = now,
                        )
                    cache[query] =
                        CacheEntry(
                            query = query,
                            savedAtMs = now,
                            requestId = requestId,
                            items = newItems,
                        )
                    cleanupStateLocked(now, keepQuery = query)
                    appliedItems = newItems
                }
            }
        }

        appliedItems?.also { onCacheUpdated(query, it) }
    }

    private fun fetchWithTimeout(
        query: String,
        fetch: (String) -> List<T>,
    ): List<T>? {
        val future =
            workerExecutor.submit<List<T>> {
                fetch(query).take(maxResults)
            }
        return try {
            future[timeoutMs, TimeUnit.MILLISECONDS]
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (_: Exception) {
            future.cancel(true)
            null
        }
    }

    fun shutdown() {
        synchronized(lock) {
            scheduledFuture?.cancel(true)
            scheduledFuture = null
        }
        scheduler.shutdownNow()
        workerExecutor.shutdownNow()
    }

    private fun cleanupStateLocked(
        now: Long,
        keepQuery: String? = null,
    ) {
        val activeQueries = setOfNotNull(keepQuery, scheduledQuery, inFlightQuery)
        pruneExpiredAppliedRequestsLocked(now, activeQueries)
        trimCacheToSizeLocked(now, activeQueries)
        trimToSize(lastAppliedRequestByQuery, maxCacheEntries, activeQueries)
    }

    private fun trimCacheToSizeLocked(
        now: Long,
        protectedQueries: Set<String>,
    ) {
        if (maxCacheEntries <= 0) {
            cache.clear()
            return
        }
        while (cache.size > maxCacheEntries) {
            val eldestExpiredKey =
                cache.entries.firstOrNull { entry ->
                    entry.key !in protectedQueries && now - entry.value.savedAtMs > cacheTtlMs
                }?.key
            val eldestKey =
                eldestExpiredKey
                    ?: cache.entries.firstOrNull { it.key !in protectedQueries }?.key
                    ?: break
            cache.remove(eldestKey)
        }
    }

    private fun pruneExpiredAppliedRequestsLocked(
        now: Long,
        protectedQueries: Set<String>,
    ) {
        val iterator = lastAppliedRequestByQuery.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val isExpired = now - entry.value.appliedAtMs > cacheTtlMs
            val hasCacheEntry = cache.containsKey(entry.key)
            if (isExpired && !hasCacheEntry && entry.key !in protectedQueries) {
                iterator.remove()
            }
        }
    }

    private fun <K, V> trimToSize(
        map: LinkedHashMap<K, V>,
        maxSize: Int,
        protectedKeys: Set<K>,
    ) {
        if (maxSize <= 0) {
            map.clear()
            return
        }
        while (map.size > maxSize) {
            val eldestKey = map.entries.firstOrNull { it.key !in protectedKeys }?.key ?: break
            map.remove(eldestKey)
        }
    }

    private companion object {
        const val DEFAULT_MAX_CACHE_ENTRIES = 32
        const val CACHE_LOAD_FACTOR = 0.75f
    }
}

private data class CacheEntry<T>(
    val query: String,
    val savedAtMs: Long,
    val requestId: Long,
    val items: List<T>,
)

private data class AppliedRequestEntry(
    val requestId: Long,
    val appliedAtMs: Long,
)
