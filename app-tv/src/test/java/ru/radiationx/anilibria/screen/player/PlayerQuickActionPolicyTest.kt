package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerQuickActionPolicyTest {
    @Test
    fun hiddenControls_quickActionHidesControls() {
        val result = resolvePlayerQuickActionHandling(controlsVisible = false)

        assertEquals(PlayerQuickActionHandling.HideControls, result)
    }

    @Test
    fun visibleControls_quickActionKeepsControlsVisible() {
        val result = resolvePlayerQuickActionHandling(controlsVisible = true)

        assertEquals(PlayerQuickActionHandling.KeepControlsVisible, result)
    }
}
