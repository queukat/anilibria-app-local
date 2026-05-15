package ru.radiationx.shared.ktx.android

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

inline fun <T> Fragment.subscribeTo(
    liveData: Flow<T>,
    crossinline action: (T) -> Unit,
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            liveData.collect { action.invoke(it) }
        }
    }
}

inline fun <T> FragmentActivity.subscribeTo(
    liveData: Flow<T>,
    crossinline action: (T) -> Unit,
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            liveData.collect { action.invoke(it) }
        }
    }
}

fun <T> Flow<T>.launchInResumed(lifecycleOwner: LifecycleOwner): Job {
    return flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
        .launchIn(lifecycleOwner.lifecycleScope)
}

fun <T> Flow<T>.launchInStarted(lifecycleOwner: LifecycleOwner): Job {
    return flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
        .launchIn(lifecycleOwner.lifecycleScope)
}
