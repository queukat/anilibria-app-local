package ru.radiationx.data.di

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

class CriticalSecureStorageStatus
    @Inject
    constructor() {
        private val available = AtomicBoolean(true)
        private val unavailableCause = AtomicReference<Throwable?>(null)

        fun isAvailable(): Boolean = available.get()

        fun getUnavailableCause(): Throwable? = unavailableCause.get()

        fun markUnavailable(cause: Throwable? = null) {
            available.set(false)
            unavailableCause.compareAndSet(null, cause)
        }
    }
