package ru.radiationx.shared.ktx

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EventFlowTest {

    @Test
    fun set_burstEmits_allEventsDeliveredInOrder() = runTest {
        val eventFlow = EventFlow<Int>()
        val collected = mutableListOf<Int>()
        val collector: Job = launch(UnconfinedTestDispatcher(testScheduler)) {
            eventFlow.collect { collected += it }
        }

        repeat(50) { value ->
            eventFlow.set(value)
        }

        advanceUntilIdle()
        collector.cancel()
        assertEquals((0 until 50).toList(), collected)
    }
}
