package ru.radiationx.anilibria.common

import android.os.SystemClock
import android.util.Log
import java.util.Collections

internal object TvStartupTrace {

    private const val TAG = "AniLibriaTvStartup"

    private val processStartMs = SystemClock.elapsedRealtime()
    private val emittedStages = Collections.synchronizedSet(mutableSetOf<String>())

    fun mark(stage: String) {
        val elapsedMs = SystemClock.elapsedRealtime() - processStartMs
        Log.i(TAG, "$stage +${elapsedMs}ms")
    }

    fun markOnce(stage: String) {
        if (emittedStages.add(stage)) {
            mark(stage)
        }
    }
}
