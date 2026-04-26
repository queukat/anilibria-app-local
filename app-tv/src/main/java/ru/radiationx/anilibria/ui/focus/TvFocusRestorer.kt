package ru.radiationx.anilibria.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

@Composable
internal fun rememberTvFocusRequesters(keys: List<String>): List<FocusRequester> {
    val cache = remember { mutableMapOf<String, FocusRequester>() }
    return remember(keys) {
        val activeKeys = keys.toSet()
        cache.keys.retainAll(activeKeys)
        keys.map { key ->
            cache.getOrPut(key) { FocusRequester() }
        }
    }
}

internal fun requestFocus(requester: FocusRequester?): Boolean {
    if (requester == null) {
        return false
    }
    return runCatching {
        requester.requestFocus()
        true
    }.getOrDefault(false)
}

internal suspend fun requestFocusAfterAttach(
    requester: FocusRequester?,
    attempts: Int = 8,
): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        if (requestFocus(requester)) {
            return true
        }
    }
    return false
}
