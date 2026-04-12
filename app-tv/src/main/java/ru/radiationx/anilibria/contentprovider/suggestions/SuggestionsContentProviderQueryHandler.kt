package ru.radiationx.anilibria.contentprovider.suggestions

import ru.radiationx.data.entity.domain.search.SuggestionItem
import java.util.LinkedHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

internal class SuggestionsContentProviderQueryHandler<Key>(
    private val minQueryLength: Int,
    maxResults: Int,
    timeoutMs: Long,
    cacheTtlMs: Long,
    minRequestIntervalMs: Long,
    private val maxTrackedQueries: Int = DEFAULT_MAX_TRACKED_QUERIES,
    private val loadSuggestionsBlocking: (String) -> List<SuggestionItem>,
    private val awaitAppInitializedBlocking: () -> Unit,
    private val onRefreshReady: (Key) -> Unit,
    nowMillis: () -> Long = { System.currentTimeMillis() },
    scheduler: ScheduledExecutorService? = null,
    workerExecutor: ExecutorService? = null,
) {
    private val lock = Any()
    private val nowMillis = nowMillis
    private val queryKeys =
        LinkedHashMap<String, QueryKeyEntry<Key>>(
            maxTrackedQueries,
            QUERY_CACHE_LOAD_FACTOR,
            true,
        )
    private val executor =
        SuggestionQueryExecutor<SuggestionItem>(
            minQueryLength = minQueryLength,
            maxResults = maxResults,
            timeoutMs = timeoutMs,
            cacheTtlMs = cacheTtlMs,
            minRequestIntervalMs = minRequestIntervalMs,
            maxCacheEntries = maxTrackedQueries,
            onCacheUpdated = { query, _ ->
                synchronized(lock) {
                    cleanupTrackedQueriesLocked(nowMillis(), keepQuery = query)
                    queryKeys[query]?.key
                }?.let(onRefreshReady)
            },
            nowMillis = this.nowMillis,
            scheduler = scheduler ?: createScheduler(),
            workerExecutor = workerExecutor ?: createWorkerExecutor(),
        )

    fun query(
        key: Key,
        rawQuery: String,
    ): List<SuggestionItem> {
        val query = rawQuery.trim()
        if (query.length >= minQueryLength) {
            synchronized(lock) {
                cleanupTrackedQueriesLocked(nowMillis(), keepQuery = query)
                queryKeys[query] =
                    QueryKeyEntry(
                        key = key,
                        savedAtMs = nowMillis(),
                    )
            }
        }
        return executor.execute(query) { normalizedQuery ->
            awaitAppInitializedBlocking()
            loadSuggestionsBlocking(normalizedQuery)
        }
    }

    fun shutdown() {
        executor.shutdown()
    }

    private fun cleanupTrackedQueriesLocked(
        now: Long,
        keepQuery: String? = null,
    ) {
        val iterator = queryKeys.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val isExpired = now - entry.value.savedAtMs > QUERY_KEY_TTL_MS
            if (isExpired && entry.key != keepQuery) {
                iterator.remove()
            }
        }
        trimTrackedQueriesLocked(keepQuery)
    }

    private fun trimTrackedQueriesLocked(keepQuery: String?) {
        if (maxTrackedQueries <= 0) {
            queryKeys.clear()
            return
        }
        while (queryKeys.size > maxTrackedQueries) {
            val eldestKey = queryKeys.entries.firstOrNull { it.key != keepQuery }?.key ?: break
            queryKeys.remove(eldestKey)
        }
    }

    private fun createScheduler(): ScheduledExecutorService {
        return java.util.concurrent.Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "suggestions-provider").apply {
                isDaemon = true
            }
        }
    }

    private fun createWorkerExecutor(): ExecutorService {
        return java.util.concurrent.Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "suggestions-provider-worker").apply {
                isDaemon = true
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_TRACKED_QUERIES = 32
        const val QUERY_KEY_TTL_MS = 30_000L
        const val QUERY_CACHE_LOAD_FACTOR = 0.75f
    }
}

private data class QueryKeyEntry<Key>(
    val key: Key,
    val savedAtMs: Long,
)
