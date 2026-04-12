package ru.radiationx.anilibria.contentprovider.suggestions

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.runBlocking
import ru.radiationx.anilibria.App
import ru.radiationx.anilibria.contentprovider.SystemSuggestionEntity
import ru.radiationx.data.entity.domain.search.SuggestionItem
import ru.radiationx.data.interactors.tv.TvSuggestionsUseCase
import ru.radiationx.quill.Quill

class SuggestionsContentProvider : ContentProvider() {
    companion object {
        /**
         * Для Leanback/GlobalSearch. Оставляем как есть, чтобы не ломать интеграцию на ТВ,
         * но при необходимости можно заменить на Intent.ACTION_VIEW/SEARCH.
         */
        const val INTENT_ACTION = "GLOBALSEARCH"

        private val queryProjection =
            SystemSuggestionEntity.projection +
                arrayOf(
                    SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
                    SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID,
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
        private const val QUERY_TIMEOUT_MS = 3_000L
    }

    private val uriMatcher by lazy { buildUriMatcher() }
    private val suggestionsUseCase by lazy { Quill.getRootScope().get(TvSuggestionsUseCase::class) }
    private val queryHandlerLazy =
        lazy(LazyThreadSafetyMode.NONE) {
            SuggestionsContentProviderQueryHandler<Uri>(
                minQueryLength = MIN_QUERY_LENGTH,
                maxResults = MAX_SUGGESTIONS,
                timeoutMs = QUERY_TIMEOUT_MS,
                cacheTtlMs = CACHE_TTL_MS,
                minRequestIntervalMs = MIN_REQUEST_INTERVAL_MS,
                loadSuggestionsBlocking = { query ->
                    runBlocking { suggestionsUseCase.loadSuggestions(query) }
                },
                awaitAppInitializedBlocking = {
                    runBlocking { App.appInitialized.await() }
                },
                onRefreshReady = { refreshUri ->
                    context?.contentResolver?.notifyChange(refreshUri, null)
                },
            )
        }
    private val queryHandler get() = queryHandlerLazy.value

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        require(uriMatcher.match(uri) == SEARCH_SUGGEST) { "Unknown Uri: $uri" }

        val query = uri.lastPathSegment.orEmpty().trim()
        val items = queryHandler.query(uri, query)
        return MatrixCursor(queryProjection).apply {
            items.forEach {
                val entity = it.convertToEntity()
                addRow(entity.getRow() + INTENT_ACTION + entity.id)
            }
            context?.contentResolver?.let { resolver ->
                setNotificationUri(resolver, uri)
            }
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = throw UnsupportedOperationException("insert is not implemented.")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("update is not implemented.")

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("delete is not implemented.")

    override fun shutdown() {
        if (queryHandlerLazy.isInitialized()) {
            queryHandler.shutdown()
        }
        super.shutdown()
    }

    private fun SuggestionItem.convertToEntity() =
        SystemSuggestionEntity(
            id.id,
            names.joinToString(),
            duration = -1,
            productionYear = -1,
            cardImage = poster,
        )

    private fun buildUriMatcher() =
        UriMatcher(UriMatcher.NO_MATCH).apply {
            // UriMatcher ожидает path без ведущего "/"
            addURI(AUTHORITY, "search/${SearchManager.SUGGEST_URI_PATH_QUERY}", SEARCH_SUGGEST)
            addURI(AUTHORITY, "search/${SearchManager.SUGGEST_URI_PATH_QUERY}/*", SEARCH_SUGGEST)
        }
}
