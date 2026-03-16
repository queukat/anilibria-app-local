package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAspectRatioModeTest {

    @Test
    fun next_cyclesThroughTvAspectRatioModes() {
        assertEquals(PlayerAspectRatioMode.ZOOM, PlayerAspectRatioMode.FIT.next())
        assertEquals(PlayerAspectRatioMode.FILL, PlayerAspectRatioMode.ZOOM.next())
        assertEquals(PlayerAspectRatioMode.FIT, PlayerAspectRatioMode.FILL.next())
    }
}
