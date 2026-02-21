package ru.radiationx.anilibria.contentprovider.suggestions

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class SuggestionQueryExecutor<T>(
    private val minQueryLength: Int,
    private val maxResults: Int,
    private val timeoutMs: Long,
    private val cacheTtlMs: Long,
    private val minRequestIntervalMs: Long,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "suggestions-provider").apply {
            isDaemon = true
        }
    },
) {
    private val lock = Any()
    private var lastRequestAtMs: Long = 0L
    private var cache = CacheEntry<T>(query = "", savedAtMs = 0L, items = emptyList())

    fun execute(rawQuery: String, fetch: (String) -> List<T>): List<T> {
        val query = rawQuery.trim()
        if (query.length < minQueryLength) {
            return emptyList()
        }

        val now = nowMillis()
        synchronized(lock) {
            if (cache.query == query && now - cache.savedAtMs <= cacheTtlMs) {
                return cache.items
            }
            if (now - lastRequestAtMs < minRequestIntervalMs) {
                return emptyList()
            }
            lastRequestAtMs = now
        }

        val future = executor.submit<List<T>> {
            fetch(query).take(maxResults)
        }

        return try {
            val items = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            synchronized(lock) {
                cache = CacheEntry(query = query, savedAtMs = nowMillis(), items = items)
            }
            items
        } catch (_: TimeoutException) {
            future.cancel(true)
            emptyList()
        } catch (_: Exception) {
            future.cancel(true)
            emptyList()
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }
}

private data class CacheEntry<T>(
    val query: String,
    val savedAtMs: Long,
    val items: List<T>,
)
