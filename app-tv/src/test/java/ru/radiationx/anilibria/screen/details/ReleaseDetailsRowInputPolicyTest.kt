package ru.radiationx.anilibria.screen.details

import androidx.compose.ui.focus.FocusRequester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseDetailsRowInputPolicyTest {
    @Test
    fun missingTarget_consumesBoundaryMove() {
        val result = resolveReleaseDetailsVerticalMoveAction(hasTarget = false)

        assertEquals(ReleaseDetailsVerticalMoveAction.Consume, result)
    }

    @Test
    fun unattachedTarget_keepsBoundaryMoveConsumed() {
        val requester = FocusRequester()

        val result = requestReleaseDetailsFocusOrConsumeBoundary(requester)

        assertTrue(result)
    }
}
