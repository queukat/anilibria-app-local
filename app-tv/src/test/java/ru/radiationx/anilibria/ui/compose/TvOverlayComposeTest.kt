package ru.radiationx.anilibria.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Test

class TvOverlayComposeTest {

    @Test
    fun verticalMoveAction_movesFocus_whenTargetExists() {
        val result = resolveTvOverlayVerticalMoveAction(hasTarget = true)

        assertEquals(TvOverlayVerticalMoveAction.MoveFocus, result)
    }

    @Test
    fun verticalMoveAction_consumesBoundary_whenTargetMissing() {
        val result = resolveTvOverlayVerticalMoveAction(hasTarget = false)

        assertEquals(TvOverlayVerticalMoveAction.Consume, result)
    }
}
