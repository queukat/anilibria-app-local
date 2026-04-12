package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsFocusPolicyTest {
    @Test
    fun shouldRequestPlayerControlsFocus_blocksControlsWhileQuickActionsOwnFocus() {
        val result =
            shouldRequestPlayerControlsFocus(
                controlsVisible = true,
                hasActivePicker = false,
                skipVisible = true,
                quickActionsTransferPending = false,
            )

        assertFalse(result)
    }

    @Test
    fun shouldRequestPlayerControlsFocus_allowsExplicitTransferFromQuickActions() {
        val result =
            shouldRequestPlayerControlsFocus(
                controlsVisible = true,
                hasActivePicker = false,
                skipVisible = true,
                quickActionsTransferPending = true,
            )

        assertTrue(result)
    }
}
