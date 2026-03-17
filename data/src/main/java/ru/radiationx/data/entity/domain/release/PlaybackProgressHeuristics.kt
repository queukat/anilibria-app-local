package ru.radiationx.data.entity.domain.release

import kotlin.math.roundToLong

private const val WATCHED_TOLERANCE_PERCENT = 0.03
private const val WATCHED_TOLERANCE_MIN_MS = 5_000L
private const val WATCHED_TOLERANCE_MAX_MS = 20_000L

fun watchedToleranceMs(durationMs: Long): Long {
    val percent = (durationMs.toDouble() * WATCHED_TOLERANCE_PERCENT).roundToLong()
    return percent.coerceIn(WATCHED_TOLERANCE_MIN_MS, WATCHED_TOLERANCE_MAX_MS)
}

fun isNearEpisodeEnd(
    positionMs: Long,
    durationMs: Long,
): Boolean {
    if (durationMs <= 0L) return false
    val safePosition = positionMs.coerceAtLeast(0L)
    val threshold = (durationMs - watchedToleranceMs(durationMs)).coerceAtLeast(0L)
    return safePosition >= threshold
}
