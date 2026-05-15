package ru.radiationx.data.interactors

import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.radiationx.shared.ktx.coRunCatching
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

class SharedRequests<KEY, DATA> {
    private val scope = CoroutineScope(AppDispatchers.default + SupervisorJob())

    private val requestEvent = MutableSharedFlow<Pair<KEY, Result<DATA>>>()

    private val jobs = Collections.synchronizedMap<KEY, Job>(mutableMapOf())

    suspend fun request(
        key: KEY,
        block: suspend () -> DATA,
    ): DATA {
        if (jobs[key]?.isActive != true) {
            val job =
                scope.launch {
                    val result =
                        coRunCatching {
                            block.invoke()
                        }
                    jobs[key] = null
                    requestEvent.emit(key to result)
                }
            jobs[key] = job
        }
        return requestEvent
            .filter { it.first == key }
            .map { it.second }
            .first()
            .getOrThrow()
    }
}
