package ru.radiationx.data.di.providers

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import ru.radiationx.data.BuildConfig
import ru.radiationx.data.SharedBuildConfig

internal object DebugNetworkLoggingPolicy {

    // Keep header names explicit because logging interceptors redact exact names only.
    private val redactedHeaders = arrayOf(
        "Authorization",
        "Cookie",
        "Set-Cookie",
        "X-Api-Key",
        "Proxy-Authorization",
        "X-Auth",
        "X-Auth-Token",
        "X-Auth-Key",
        "X-Auth-Secret",
    )

    fun appendTo(
        builder: OkHttpClient.Builder,
        context: Context,
        sharedBuildConfig: SharedBuildConfig,
        allowBodyLogging: Boolean,
    ) {
        if (!sharedBuildConfig.debug) {
            return
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (allowBodyLogging && BuildConfig.ENABLE_HTTP_BODY_LOGGING) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.HEADERS
            }
            redactedHeaders.forEach(::redactHeader)
        }

        builder.addNetworkInterceptor(loggingInterceptor)
        if (!shouldAttachChucker(sharedBuildConfig.applicationId)) {
            return
        }

        val collector = ChuckerCollector(
            context = context,
            showNotification = shouldShowChuckerNotification(sharedBuildConfig.applicationId),
        )
        builder.addNetworkInterceptor(
            ChuckerInterceptor.Builder(context)
                .collector(collector)
                .redactHeaders(*redactedHeaders)
                .build()
        )
    }

    internal fun shouldShowChuckerNotification(applicationId: String): Boolean {
        return shouldAttachChucker(applicationId)
    }

    internal fun shouldAttachChucker(applicationId: String): Boolean {
        return !applicationId.endsWith(".app.tv", ignoreCase = true)
    }
}
