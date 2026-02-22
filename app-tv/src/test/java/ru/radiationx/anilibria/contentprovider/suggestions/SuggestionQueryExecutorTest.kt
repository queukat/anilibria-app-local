package ru.radiationx.anilibria.contentprovider.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

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

    @Test
    fun execute_appliesRateLimit_forFrequentRequests() {
        var now = 1_000L
        var calls = 0
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 20,
            timeoutMs = 1_000L,
            cacheTtlMs = 0L,
            minRequestIntervalMs = 150L,
            nowMillis = { now },
            executor = Executors.newSingleThreadExecutor(),
        )

        try {
            val first = executor.execute("naruto") {
                calls += 1
                listOf("first")
            }
            now += 50L
            val second = executor.execute("naruto shippuden") {
                calls += 1
                listOf("second")
            }
            now += 160L
            val third = executor.execute("naruto shippuden") {
                calls += 1
                listOf("third")
            }

            assertEquals(listOf("first"), first)
            assertTrue(second.isEmpty())
            assertEquals(listOf("third"), third)
            assertEquals(2, calls)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun execute_timeoutReturnsQuickly_withoutUnboundedBlocking() {
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 20,
            timeoutMs = 60L,
            cacheTtlMs = 1_000L,
            minRequestIntervalMs = 0L,
            executor = Executors.newSingleThreadExecutor(),
        )

        try {
            val elapsed = measureTimeMillis {
                val result = executor.execute("bleach") {
                    Thread.sleep(5_000L)
                    listOf("late")
                }
                assertTrue(result.isEmpty())
            }

            // Allow scheduler jitter, but the call must remain bounded by timeout path.
            assertTrue("elapsed=$elapsed", elapsed < 400L)
        } finally {
            executor.shutdown()
        }
    }
}
