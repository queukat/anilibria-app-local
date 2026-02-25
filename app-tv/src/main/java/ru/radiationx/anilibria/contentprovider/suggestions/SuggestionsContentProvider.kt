package ru.radiationx.anilibria.contentprovider.suggestions

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.App
import ru.radiationx.anilibria.contentprovider.SystemSuggestionEntity
import ru.radiationx.data.entity.domain.search.SuggestionItem
import ru.radiationx.data.interactors.tv.TvSuggestionsUseCase
import ru.radiationx.data.system.ApplicationCoroutineScope
import ru.radiationx.quill.Quill
import timber.log.Timber

class SuggestionsContentProvider : ContentProvider() {

    companion object {
        /**
         * Для Leanback/GlobalSearch. Оставляем как есть, чтобы не ломать интеграцию на ТВ,
         * но при необходимости можно заменить на Intent.ACTION_VIEW/SEARCH.
         */
        const val INTENT_ACTION = "GLOBALSEARCH"

        private val queryProjection = SystemSuggestionEntity.projection + arrayOf(
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID
        )

        private const val AUTHORITY = "ru.radiationx.anilibria.contentprovider.suggestions"
        private const val SEARCH_SUGGEST = 1

        /**
         * GlobalSearch может дёргать provider посимвольно.
         * Ограничиваем кол-во результатов, чтобы не раздувать Cursor.
         */
        private const val MAX_SUGGESTIONS = 20
        private const val CACHE_TTL_MS = 10L * 60L * 1000L
        private const val MIN_REQUEST_INTERVAL_MS = 150L
        private const val MIN_QUERY_LENGTH = 3
    }

    private val uriMatcher by lazy { buildUriMatcher() }
    private val suggestionsUseCase by lazy { Quill.getRootScope().get(TvSuggestionsUseCase::class) }
    private val applicationScope by lazy { Quill.getRootScope().get(ApplicationCoroutineScope::class) }
    private val cacheLock = Any()
    private val refreshJobs = mutableMapOf<String, Job>()
    private val suggestionsCache = mutableMapOf<String, SuggestionsCacheEntry>()
    private var lastRefreshStartedAtMs: Long = 0L

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (uriMatcher.match(uri) == SEARCH_SUGGEST) {
            val query = uri.lastPathSegment.orEmpty().trim()
            val items = getCachedSuggestions(query)
            scheduleRefreshIfNeeded(query)
            return MatrixCursor(queryProjection).apply {
                items.forEach {
                    val entity = it.convertToEntity()
                    addRow(entity.getRow() + INTENT_ACTION + entity.id)
                }
            }
        } else {
            throw IllegalArgumentException("Unknown Uri: $uri")
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("insert is not implemented.")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("update is not implemented.")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("delete is not implemented.")

    override fun shutdown() {
        synchronized(cacheLock) {
            refreshJobs.values.forEach { it.cancel() }
            refreshJobs.clear()
        }
        super.shutdown()
    }

    private fun getCachedSuggestions(rawQuery: String): List<SuggestionItem> {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            return emptyList()
        }
        return synchronized(cacheLock) {
            suggestionsCache[query]?.items.orEmpty()
        }
    }

    private fun scheduleRefreshIfNeeded(rawQuery: String) {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            return
        }
        val delayMs = synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            val cacheEntry = suggestionsCache[query]
            val isFresh = cacheEntry != null && now - cacheEntry.savedAtMs <= CACHE_TTL_MS
            if (isFresh || refreshJobs[query]?.isActive == true) {
                null
            } else {
                val elapsedSinceLastStart = now - lastRefreshStartedAtMs
                (MIN_REQUEST_INTERVAL_MS - elapsedSinceLastStart).coerceAtLeast(0L)
            }
        } ?: return

        val job = applicationScope.launch(start = CoroutineStart.LAZY) {
            if (delayMs > 0L) {
                delay(delayMs)
            }
            synchronized(cacheLock) {
                lastRefreshStartedAtMs = System.currentTimeMillis()
            }
            val result = runCatching {
                App.appInitialized.await()
                suggestionsUseCase.loadSuggestions(query)
                    .take(MAX_SUGGESTIONS)
            }
            synchronized(cacheLock) {
                if (result.isSuccess) {
                    suggestionsCache[query] = SuggestionsCacheEntry(
                        savedAtMs = System.currentTimeMillis(),
                        items = result.getOrDefault(emptyList()),
                    )
                }
            }
            result.exceptionOrNull()?.also { error ->
                Timber.w(error, "Suggestions refresh failed for query: %s", query)
            }
        }

        val scheduled = synchronized(cacheLock) {
            if (refreshJobs[query]?.isActive == true) {
                false
            } else {
                refreshJobs[query] = job
                true
            }
        }

        if (!scheduled) {
            job.cancel()
            return
        }

        job.start()
        job.invokeOnCompletion {
            synchronized(cacheLock) {
                if (refreshJobs[query] === job) {
                    refreshJobs.remove(query)
                }
            }
        }
    }

    private fun SuggestionItem.convertToEntity() = SystemSuggestionEntity(
        id.id,
        names.joinToString(),
        duration = -1,
        productionYear = -1,
        cardImage = poster
    )

    private fun buildUriMatcher() = UriMatcher(UriMatcher.NO_MATCH).apply {
        // UriMatcher ожидает path без ведущего "/"
        addURI(AUTHORITY, "search/${SearchManager.SUGGEST_URI_PATH_QUERY}", SEARCH_SUGGEST)
        addURI(AUTHORITY, "search/${SearchManager.SUGGEST_URI_PATH_QUERY}/*", SEARCH_SUGGEST)
    }
}

private data class SuggestionsCacheEntry(
    val savedAtMs: Long,
    val items: List<SuggestionItem>,
)
