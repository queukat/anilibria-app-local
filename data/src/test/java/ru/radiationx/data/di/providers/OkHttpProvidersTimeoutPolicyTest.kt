package ru.radiationx.data.di.providers

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.SharedBuildConfig
import ru.radiationx.data.analytics.features.SslCompatAnalytics
import ru.radiationx.data.sslcompat.SslCompat

class OkHttpProvidersTimeoutPolicyTest {

    private val context = mockk<Context>(relaxed = true)
    private val sslCompat = mockk<SslCompat>()
    private val sslCompatAnalytics = mockk<SslCompatAnalytics>(relaxed = true)

    private val sharedBuildConfig = object : SharedBuildConfig {
        override val applicationName: String = "test-app"
        override val applicationId: String = "test.app"
        override val versionName: String = "1.0.0"
        override val versionCode: Int = 1
        override val buildDate: String = "2026-02-23"
        override val debug: Boolean = false
        override val hasAds: Boolean = false
    }

    @Before
    fun setUp() {
        every { sslCompat.data } returns Result.failure(IllegalStateException("ssl-compat-disabled-for-test"))
    }

    @Test
    fun simpleProvider_appliesUnifiedTimeoutPolicy() {
        val client = SimpleOkHttpProvider(
            context = context,
            sharedBuildConfig = sharedBuildConfig,
            sslCompat = sslCompat,
            sslCompatAnalytics = sslCompatAnalytics,
        ).get()

        assertTimeoutPolicy(client)
    }

    @Test
    fun playerProvider_appliesUnifiedTimeoutPolicy() {
        val client = PlayerOkHttpProvider(
            context = context,
            sharedBuildConfig = sharedBuildConfig,
            sslCompat = sslCompat,
            sslCompatAnalytics = sslCompatAnalytics,
        ).get()

        assertTimeoutPolicy(client)
    }

    private fun assertTimeoutPolicy(client: OkHttpClient) {
        assertEquals(25_000, client.callTimeoutMillis)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
    }
}
