package ru.radiationx.shared.ktx

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow

class EventFlow<T> : Flow<T> {

    private val flow = MutableSharedFlow<T>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun observe(): Flow<T> = flow

    fun set(value: T) {
        flow.tryEmit(value)
    }

    fun emit(value: T) {
        set(value)
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        return observe().collect(collector)
    }
}
