package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPlaybackStartTest {
    @Test
    fun resolvePlayerStartPosition_prefersLocalSeek_whenResuming() {
        val result =
            resolvePlayerStartPosition(
                localSeekMs = 15_000L,
                playbackStart = PlayerPlaybackStart.ResumeSavedProgress,
            )

        assertEquals(15_000L, result)
    }

    @Test
    fun resolvePlayerStartPosition_ignoresSavedSeek_whenStartingFromBeginning() {
        val result =
            resolvePlayerStartPosition(
                localSeekMs = 15_000L,
                playbackStart = PlayerPlaybackStart.StartFromBeginning,
            )

        assertEquals(0L, result)
    }
}
