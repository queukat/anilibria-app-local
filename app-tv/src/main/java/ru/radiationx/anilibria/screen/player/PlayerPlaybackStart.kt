package ru.radiationx.anilibria.screen.player

internal enum class PlayerPlaybackStart {
    ResumeSavedProgress,
    StartFromBeginning,
}

internal fun resolvePlayerStartPosition(
    localSeekMs: Long,
    playbackStart: PlayerPlaybackStart,
): Long {
    return when (playbackStart) {
        PlayerPlaybackStart.ResumeSavedProgress -> localSeekMs.coerceAtLeast(0L)
        PlayerPlaybackStart.StartFromBeginning -> 0L
    }
}
