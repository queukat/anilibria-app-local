package ru.radiationx.anilibria.screen.watching

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchingSurfaceInputPolicyTest {
    @Test
    fun centerPress_consumesOnKeyDown() {
        val result =
            resolveTvCenterPressAction(
                canFocus = true,
                enabled = true,
                key = Key.DirectionCenter,
                eventType = KeyEventType.KeyDown,
            )

        assertEquals(TvCenterPressAction.Consume, result)
    }

    @Test
    fun centerPress_clicksOnKeyUp() {
        val result =
            resolveTvCenterPressAction(
                canFocus = true,
                enabled = true,
                key = Key.DirectionCenter,
                eventType = KeyEventType.KeyUp,
            )

        assertEquals(TvCenterPressAction.Click, result)
    }

    @Test
    fun centerPress_isIgnoredWhenDisabled() {
        val result =
            resolveTvCenterPressAction(
                canFocus = true,
                enabled = false,
                key = Key.DirectionCenter,
                eventType = KeyEventType.KeyUp,
            )

        assertEquals(TvCenterPressAction.Ignore, result)
    }

    @Test
    fun nonCenterPress_isIgnored() {
        val result =
            resolveTvCenterPressAction(
                canFocus = true,
                enabled = true,
                key = Key.DirectionDown,
                eventType = KeyEventType.KeyDown,
            )

        assertEquals(TvCenterPressAction.Ignore, result)
    }
}
