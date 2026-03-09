package ru.radiationx.anilibria.contentprovider.suggestions

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
    private val onCacheUpdated: (query: String, items: List<T>) -> Unit = { _, _ -> },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "suggestions-provider").apply {
            isDaemon = true
        }
    },
    private val workerExecutor: ExecutorService = Executors.newCachedThreadPool { runnable ->
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
    private val lastAppliedRequestByQuery = mutableMapOf<String, Long>()
    private val cache = mutableMapOf<String, CacheEntry<T>>()

    fun execute(rawQuery: String, fetch: (String) -> List<T>): List<T> {
        val query = rawQuery.trim()
        if (query.length < minQueryLength) {
            return emptyList()
        }

        val now = nowMillis()
        val cacheEntry = synchronized(lock) { cache[query] }
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
            val elapsedSinceLastStart = now - lastRefreshStartedAtMs
            delayMs = (minRequestIntervalMs - elapsedSinceLastStart).coerceAtLeast(0L)
            scheduledQuery = query
            scheduledFuture?.cancel(false)
            scheduledFuture = scheduler.schedule(
                { refresh(query, requestId, fetch) },
                delayMs,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun refresh(
        query: String,
        requestId: Long,
        fetch: (String) -> List<T>,
    ) {
        val canStartRefresh = synchronized(lock) {
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
                val lastAppliedId = lastAppliedRequestByQuery[query] ?: Long.MIN_VALUE
                if (requestId >= lastAppliedId) {
                    lastAppliedRequestByQuery[query] = requestId
                    cache[query] = CacheEntry(query = query, savedAtMs = nowMillis(), items = newItems)
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
        val future = workerExecutor.submit<List<T>> {
            fetch(query).take(maxResults)
        }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
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
}

private data class CacheEntry<T>(
    val query: String,
    val savedAtMs: Long,
    val items: List<T>,
)
