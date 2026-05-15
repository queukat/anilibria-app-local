package ru.radiationx.data.system

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class ApplicationCoroutineScope
    @Inject
    constructor() : CoroutineScope {
        private val job = SupervisorJob()
        override val coroutineContext = job + AppDispatchers.default

        fun shutdown() {
            cancel()
        }
    }
