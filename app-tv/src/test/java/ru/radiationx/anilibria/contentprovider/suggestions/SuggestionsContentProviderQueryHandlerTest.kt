package ru.radiationx.anilibria.contentprovider.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.entity.domain.search.SuggestionItem
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class SuggestionsContentProviderQueryHandlerTest {
    @Test
    fun query_notifiesOriginalGlobalSearchRequest_afterFirstAsyncRefresh() {
        val calls = AtomicInteger(0)
        val refreshKeys = mutableListOf<String>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        val handler =
            SuggestionsContentProviderQueryHandler<String>(
                minQueryLength = 3,
                maxResults = 2,
                timeoutMs = 200L,
                cacheTtlMs = 1_000L,
                minRequestIntervalMs = 0L,
                loadSuggestionsBlocking = { _ ->
                    calls.incrementAndGet()
                    listOf(
                        suggestion(1, "Naruto"),
                        suggestion(2, "Naruto Shippuden"),
                        suggestion(3, "Boruto"),
                    )
                },
                awaitAppInitializedBlocking = {},
                onRefreshReady = { key: String -> refreshKeys += key },
                scheduler = scheduler,
                workerExecutor = worker,
            )

        try {
            val first = handler.query("content://suggest/naruto", "naruto")
            assertTrue(first.isEmpty())

            waitUntil { refreshKeys.isNotEmpty() }
            val second = handler.query("content://suggest/naruto", "naruto")

            assertEquals(listOf("content://suggest/naruto"), refreshKeys)
            assertEquals(listOf("Naruto", "Naruto Shippuden"), second.map { it.names.first() })
            assertEquals(1, calls.get())
        } finally {
            handler.shutdown()
        }
    }

    @Test
    fun query_keepsServingStaleCache_untilRefreshPublishesUpdatedItems() {
        var now = 1_000L
        val refreshKeys = mutableListOf<String>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        val handler =
            SuggestionsContentProviderQueryHandler<String>(
                minQueryLength = 3,
                maxResults = 20,
                timeoutMs = 200L,
                cacheTtlMs = 100L,
                minRequestIntervalMs = 0L,
                nowMillis = { now },
                loadSuggestionsBlocking = { query ->
                    if (query == "bleach" && refreshKeys.isEmpty()) {
                        listOf(suggestion(1, "Bleach"))
                    } else {
                        listOf(suggestion(2, "Bleach: Thousand-Year Blood War"))
                    }
                },
                awaitAppInitializedBlocking = {},
                onRefreshReady = { key: String -> refreshKeys += key },
                scheduler = scheduler,
                workerExecutor = worker,
            )

        try {
            assertTrue(handler.query("content://suggest/bleach", "bleach").isEmpty())
            waitUntil { refreshKeys.size == 1 }

            val cached = handler.query("content://suggest/bleach", "bleach")
            assertEquals(listOf("Bleach"), cached.map { it.names.first() })

            now += 150L
            val stale = handler.query("content://suggest/bleach", "bleach")
            assertEquals(listOf("Bleach"), stale.map { it.names.first() })

            waitUntil { refreshKeys.size == 2 }
            val refreshed = handler.query("content://suggest/bleach", "bleach")

            assertEquals(
                listOf("Bleach: Thousand-Year Blood War"),
                refreshed.map { it.names.first() },
            )
            assertEquals(
                listOf("content://suggest/bleach", "content://suggest/bleach"),
                refreshKeys,
            )
        } finally {
            handler.shutdown()
        }
    }

    @Test
    fun query_ignoresRefreshForEvictedTrackedKey_whenKeyCacheIsBounded() {
        val refreshKeys = mutableListOf<String>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        val handler =
            SuggestionsContentProviderQueryHandler<String>(
                minQueryLength = 3,
                maxResults = 20,
                timeoutMs = 500L,
                cacheTtlMs = 1_000L,
                minRequestIntervalMs = 0L,
                maxTrackedQueries = 1,
                loadSuggestionsBlocking = { query ->
                    if (query == "naruto") {
                        Thread.sleep(150L)
                    }
                    listOf(suggestion(query.hashCode(), query))
                },
                awaitAppInitializedBlocking = {},
                onRefreshReady = { key: String -> refreshKeys += key },
                scheduler = scheduler,
                workerExecutor = worker,
            )

        try {
            assertTrue(handler.query("content://suggest/naruto", "naruto").isEmpty())
            assertTrue(handler.query("content://suggest/onepiece", "onepiece").isEmpty())

            waitUntil { refreshKeys.size == 1 }

            assertEquals(
                listOf("content://suggest/onepiece"),
                refreshKeys,
            )
        } finally {
            handler.shutdown()
        }
    }

    private fun suggestion(
        id: Int,
        title: String,
    ): SuggestionItem {
        return SuggestionItem(
            id = ReleaseId(id),
            code = ReleaseCode("code-$id"),
            names = listOf(title),
            poster = null,
        )
    }

    private fun waitUntil(
        timeoutMs: Long = 1_500L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Condition was not met in ${timeoutMs}ms")
            }
            Thread.sleep(10L)
        }
    }
}
