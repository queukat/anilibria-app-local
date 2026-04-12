package ru.radiationx.anilibria.screen.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlSurfaceInputPolicyTest {
    @Test
    fun centerPress_consumesOnKeyDown() {
        val result =
            resolvePlayerControlSurfaceKeyAction(
                canFocus = true,
                enabled = true,
                key = Key.DirectionCenter,
                eventType = KeyEventType.KeyDown,
            )

        assertEquals(PlayerControlSurfaceKeyAction.Consume, result)
    }

    @Test
    fun centerPress_clicksOnKeyUp() {
        val result =
            resolvePlayerControlSurfaceKeyAction(
                canFocus = true,
                enabled = true,
                key = Key.DirectionCenter,
                eventType = KeyEventType.KeyUp,
            )

        assertEquals(PlayerControlSurfaceKeyAction.Click, result)
    }

    @Test
    fun moveRight_onlyMovesOnKeyDown() {
        val keyDownResult =
            resolvePlayerControlSurfaceKeyAction(
                canFocus = true,
                enabled = true,
                key = Key.DirectionRight,
                eventType = KeyEventType.KeyDown,
            )
        val keyUpResult =
            resolvePlayerControlSurfaceKeyAction(
                canFocus = true,
                enabled = true,
                key = Key.DirectionRight,
                eventType = KeyEventType.KeyUp,
            )

        assertEquals(PlayerControlSurfaceKeyAction.MoveRight, keyDownResult)
        assertEquals(PlayerControlSurfaceKeyAction.Ignore, keyUpResult)
    }
}
