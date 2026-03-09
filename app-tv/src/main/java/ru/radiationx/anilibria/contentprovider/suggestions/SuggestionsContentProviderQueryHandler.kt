package ru.radiationx.anilibria.contentprovider.suggestions

import kotlinx.coroutines.runBlocking
import ru.radiationx.data.entity.domain.search.SuggestionItem
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

internal class SuggestionsContentProviderQueryHandler<Key>(
    private val minQueryLength: Int,
    maxResults: Int,
    timeoutMs: Long,
    cacheTtlMs: Long,
    minRequestIntervalMs: Long,
    private val loadSuggestions: suspend (String) -> List<SuggestionItem>,
    private val awaitAppInitialized: suspend () -> Unit,
    private val onRefreshReady: (Key) -> Unit,
    nowMillis: () -> Long = { System.currentTimeMillis() },
    scheduler: ScheduledExecutorService? = null,
    workerExecutor: ExecutorService? = null,
) {

    private val lock = Any()
    private val queryKeys = mutableMapOf<String, Key>()
    private val executor = SuggestionQueryExecutor<SuggestionItem>(
        minQueryLength = minQueryLength,
        maxResults = maxResults,
        timeoutMs = timeoutMs,
        cacheTtlMs = cacheTtlMs,
        minRequestIntervalMs = minRequestIntervalMs,
        onCacheUpdated = { query, _ ->
            synchronized(lock) { queryKeys[query] }?.let(onRefreshReady)
        },
        nowMillis = nowMillis,
        scheduler = scheduler ?: createScheduler(),
        workerExecutor = workerExecutor ?: createWorkerExecutor(),
    )

    fun query(key: Key, rawQuery: String): List<SuggestionItem> {
        val query = rawQuery.trim()
        if (query.length >= minQueryLength) {
            synchronized(lock) {
                queryKeys[query] = key
            }
        }
        return executor.execute(query) { normalizedQuery ->
            runBlocking {
                awaitAppInitialized()
                loadSuggestions(normalizedQuery)
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
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
}
