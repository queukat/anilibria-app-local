package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSkipsQuickActionOrderTest {
    @Test
    fun quickActionHandling_happensBeforeSeekAction() {
        val events = mutableListOf<String>()

        handlePlayerSkipQuickAction(
            controlsVisible = false,
            onQuickActionHandled = { events += "handled:$it" },
        ) {
            events += "seek"
        }

        assertEquals(
            listOf(
                "handled:${PlayerQuickActionHandling.HideControls}",
                "seek",
            ),
            events,
        )
    }
}
