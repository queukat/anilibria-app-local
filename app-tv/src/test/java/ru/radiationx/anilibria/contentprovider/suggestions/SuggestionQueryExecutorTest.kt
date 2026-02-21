package ru.radiationx.anilibria.contentprovider.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class SuggestionQueryExecutorTest {

    @Test
    fun execute_limitsResults_andUsesCache() {
        var now = 1_000L
        var calls = 0
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 2,
            timeoutMs = 200L,
            cacheTtlMs = 1_000L,
            minRequestIntervalMs = 0L,
            nowMillis = { now },
            executor = Executors.newSingleThreadExecutor(),
        )

        try {
            val first = executor.execute("naruto") {
                calls += 1
                listOf("a", "b", "c")
            }
            val second = executor.execute("naruto") {
                calls += 1
                listOf("x")
            }

            assertEquals(listOf("a", "b"), first)
            assertEquals(listOf("a", "b"), second)
            assertEquals(1, calls)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun execute_returnsEmpty_onTimeout() {
        var now = 1_000L
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 20,
            timeoutMs = 50L,
            cacheTtlMs = 1_000L,
            minRequestIntervalMs = 0L,
            nowMillis = { now },
            executor = Executors.newSingleThreadExecutor(),
        )

        try {
            val result = executor.execute("bleach") {
                Thread.sleep(200L)
                listOf("item")
            }
            assertTrue(result.isEmpty())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun execute_returnsEmpty_whenFetcherFails() {
        var now = 1_000L
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 20,
            timeoutMs = 200L,
            cacheTtlMs = 1_000L,
            minRequestIntervalMs = 0L,
            nowMillis = { now },
            executor = Executors.newSingleThreadExecutor(),
        )

        try {
            val result = executor.execute("one piece") {
                throw IllegalStateException("network unavailable")
            }
            assertTrue(result.isEmpty())
        } finally {
            executor.shutdown()
        }
    }
}
