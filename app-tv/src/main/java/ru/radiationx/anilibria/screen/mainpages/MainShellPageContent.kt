package ru.radiationx.anilibria.screen.mainpages

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal data class MainShellCallbacks(
    val onRequestRailFocus: () -> Boolean,
    val onContentMovedDown: () -> Unit,
    val onContentMovedUp: () -> Unit,
    val onRequestHeaderFocus: () -> Boolean,
    val contentInteractionsEnabled: Boolean,
)

internal interface MainShellPageContent {
    fun bind(owner: LifecycleOwner)

    fun onSelected()

    fun onBackPressed(): Boolean = false

    fun requestContentFocus(): Boolean

    @Composable
    fun Render(callbacks: MainShellCallbacks)
}

internal fun <T> LifecycleOwner.collectStarted(
    flow: Flow<T>,
    action: (T) -> Unit,
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(action)
        }
    }
}
