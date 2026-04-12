package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.entity.domain.release.PlayerSkips

class PlayerSkipsPartTest {
    @Test
    fun update_showsOverlayWhenPositionEntersSkipWindow() {
        val skipsPart = PlayerSkipsPart(onSeek = {})

        skipsPart.setSkips(
            PlayerSkips(
                opening = PlayerSkips.Skip(start = 1_000L, end = 5_000L),
                ending = null,
            ),
        )

        skipsPart.update(2_000L)

        assertNotNull(skipsPart.visibleSkip)
        assertEquals(1, skipsPart.focusRequestToken)
    }

    @Test
    fun skipCurrent_seeksToSkipEndAndHidesOverlay() {
        var seekTarget = -1L
        val skipsPart = PlayerSkipsPart(onSeek = { seekTarget = it })
        skipsPart.setSkips(
            PlayerSkips(
                opening = PlayerSkips.Skip(start = 1_000L, end = 5_000L),
                ending = null,
            ),
        )
        skipsPart.update(2_000L)

        skipsPart.skipCurrent()

        assertEquals(5_000L, seekTarget)
        assertFalse(skipsPart.isVisible)
    }

    @Test
    fun cancelCurrent_marksSkipConsumedForCurrentWindow() {
        val skipsPart = PlayerSkipsPart(onSeek = {})
        skipsPart.setSkips(
            PlayerSkips(
                opening = PlayerSkips.Skip(start = 1_000L, end = 5_000L),
                ending = null,
            ),
        )
        skipsPart.update(2_000L)

        skipsPart.cancelCurrent()
        skipsPart.update(3_000L)

        assertFalse(skipsPart.isVisible)
    }

    @Test
    fun cancelCurrent_keepsSkipHiddenWhileStillInsideSegment() {
        val skipsPart = PlayerSkipsPart(onSeek = {})
        skipsPart.setSkips(
            PlayerSkips(
                opening = PlayerSkips.Skip(start = 1_000L, end = 5_000L),
                ending = null,
            ),
        )
        skipsPart.update(2_000L)

        skipsPart.cancelCurrent()
        skipsPart.update(4_000L)

        assertFalse(skipsPart.isVisible)
        assertNull(skipsPart.visibleSkip)
    }

    @Test
    fun cancelCurrent_showsSkipAgainAfterLeavingAndReEnteringSegment() {
        val skipsPart = PlayerSkipsPart(onSeek = {})
        skipsPart.setSkips(
            PlayerSkips(
                opening = PlayerSkips.Skip(start = 1_000L, end = 5_000L),
                ending = null,
            ),
        )
        skipsPart.update(2_000L)

        skipsPart.cancelCurrent()
        skipsPart.update(6_000L)
        skipsPart.update(2_000L)

        assertTrue(skipsPart.isVisible)
        assertNotNull(skipsPart.visibleSkip)
    }

    @Test
    fun requestControlsFocusTransfer_incrementsTokenOnlyWhenSkipVisible() {
        val skipsPart = PlayerSkipsPart(onSeek = {})
        skipsPart.setSkips(
            PlayerSkips(
                opening = PlayerSkips.Skip(start = 1_000L, end = 5_000L),
                ending = null,
            ),
        )
        skipsPart.update(2_000L)

        skipsPart.requestControlsFocusTransfer()

        assertEquals(1, skipsPart.controlsFocusTransferToken)
    }

    @Test
    fun requestControlsFocusTransfer_doesNothingWithoutVisibleSkip() {
        val skipsPart = PlayerSkipsPart(onSeek = {})

        skipsPart.requestControlsFocusTransfer()

        assertEquals(0, skipsPart.controlsFocusTransferToken)
    }
}
