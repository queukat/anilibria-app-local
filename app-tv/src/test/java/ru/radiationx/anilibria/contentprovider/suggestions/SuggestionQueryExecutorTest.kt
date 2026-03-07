package ru.radiationx.anilibria.contentprovider.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class SuggestionQueryExecutorTest {

    @Test
    fun execute_isCacheFirst_andUsesAsyncRefresh() {
        var now = 1_000L
        val calls = AtomicInteger(0)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 2,
            timeoutMs = 200L,
            cacheTtlMs = 1_000L,
            minRequestIntervalMs = 0L,
            nowMillis = { now },
            scheduler = scheduler,
            workerExecutor = worker,
        )

        try {
            val first = executor.execute("naruto") {
                calls.incrementAndGet()
                listOf("a", "b", "c")
            }
            assertTrue(first.isEmpty())

            var second: List<String> = emptyList()
            waitUntil {
                second = executor.execute("naruto") {
                    calls.incrementAndGet()
                    listOf("x")
                }
                second.isNotEmpty()
            }

            assertEquals(listOf("a", "b"), second)
            assertEquals(1, calls.get())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun execute_debouncesRapidRequests_andFetchesLatestQuery() {
        var now = 10L
        val calls = mutableListOf<String>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 20,
            timeoutMs = 200L,
            cacheTtlMs = 0L,
            minRequestIntervalMs = 150L,
            nowMillis = { now },
            scheduler = scheduler,
            workerExecutor = worker,
        )

        try {
            executor.execute("nar") {
                calls += it
                listOf("a")
            }
            now = 20L
            executor.execute("naru") {
                calls += it
                listOf("b")
            }
            now = 30L
            executor.execute("naruto") {
                calls += it
                listOf("c")
            }

            waitUntil { calls.isNotEmpty() }
            assertEquals(listOf("naruto"), calls)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun execute_keepsPreviousCache_whenRefreshFails() {
        var now = 1_000L
        val calls = AtomicInteger(0)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 20,
            timeoutMs = 200L,
            cacheTtlMs = 100L,
            minRequestIntervalMs = 0L,
            nowMillis = { now },
            scheduler = scheduler,
            workerExecutor = worker,
        )

        try {
            executor.execute("bleach") {
                calls.incrementAndGet()
                listOf("cached")
            }

            waitUntil { calls.get() == 1 }
            // The first worker call increments `calls` before cache write is committed under lock.
            // Give the async refresh a moment to publish the cached snapshot before advancing virtual time.
            Thread.sleep(50L)
            now += 150L

            val stale = executor.execute("bleach") {
                calls.incrementAndGet()
                throw IllegalStateException("network failed")
            }
            assertEquals(listOf("cached"), stale)

            waitUntil { calls.get() == 2 }

            val afterFailure = executor.execute("bleach") {
                calls.incrementAndGet()
                listOf("new")
            }
            assertEquals(listOf("cached"), afterFailure)
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun execute_returnsQuickly_withoutBlockingQueryThread() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val worker = Executors.newSingleThreadExecutor()
        val executor = SuggestionQueryExecutor<String>(
            minQueryLength = 3,
            maxResults = 20,
            timeoutMs = 60L,
            cacheTtlMs = 1_000L,
            minRequestIntervalMs = 0L,
            scheduler = scheduler,
            workerExecutor = worker,
        )

        try {
            val elapsed = measureTimeMillis {
                val result = executor.execute("bleach") {
                    Thread.sleep(5_000L)
                    listOf("late")
                }
                assertTrue(result.isEmpty())
            }

            assertTrue("elapsed=$elapsed", elapsed < 400L)
        } finally {
            executor.shutdown()
        }
    }

    private fun waitUntil(timeoutMs: Long = 1_500L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Condition was not met in ${timeoutMs}ms")
            }
            Thread.sleep(10L)
        }
    }
}
