package ru.radiationx.anilibria.screen.player

internal enum class PlayerPlaybackStart {
    ResumeSavedProgress,
    StartFromBeginning,
}

internal fun resolvePlayerStartPosition(
    localSeekMs: Long,
    remoteSeekMs: Long,
    playbackStart: PlayerPlaybackStart,
): Long {
    return when (playbackStart) {
        PlayerPlaybackStart.ResumeSavedProgress -> {
            maxOf(localSeekMs.coerceAtLeast(0L), remoteSeekMs.coerceAtLeast(0L))
        }

        PlayerPlaybackStart.StartFromBeginning -> 0L
    }
}
