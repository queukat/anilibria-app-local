package ru.radiationx.data.di.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugNetworkLoggingPolicyTest {
    @Test
    fun shouldAttachChucker_returnsFalseForTvAppId() {
        val result =
            DebugNetworkLoggingPolicy.shouldAttachChucker(
                applicationId = "ru.radiationx.anilibria.app.tv",
            )

        assertFalse(result)
    }

    @Test
    fun shouldAttachChucker_returnsTrueForMobileAppId() {
        val result =
            DebugNetworkLoggingPolicy.shouldAttachChucker(
                applicationId = "ru.radiationx.anilibria",
            )

        assertTrue(result)
    }

    @Test
    fun shouldShowChuckerNotification_returnsFalseForTvAppId() {
        val result =
            DebugNetworkLoggingPolicy.shouldShowChuckerNotification(
                applicationId = "ru.radiationx.anilibria.app.tv",
            )

        assertFalse(result)
    }
}
