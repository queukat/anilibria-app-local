package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerAspectRatioModeTest {

    @Test
    fun entries_keepExpectedPickerOrder() {
        assertEquals(
            listOf(
                PlayerAspectRatioMode.FIT,
                PlayerAspectRatioMode.ZOOM,
                PlayerAspectRatioMode.FILL,
            ),
            PlayerAspectRatioMode.entries,
        )
    }
}
