package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.data.CriticalSecureDataPreferences
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.di.CriticalSecureStorageStatus
import ru.radiationx.data.entity.response.config.ApiConfigResponse
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ApiConfigStorage
    @Inject
    constructor(
        @DataPreferences private val sharedPreferences: SharedPreferences,
        @CriticalSecureDataPreferences private val securePreferences: SharedPreferences,
        private val criticalSecureStorageStatus: CriticalSecureStorageStatus,
        private val moshi: Moshi,
    ) {
        companion object {
            private const val KEY_API_CONFIG = "data.apiconfig_v2"
            private const val KEY_API_CONFIG_ACTIVE = "data.apiconfig_active_v2"
            private const val KEY_API_CONFIG_PROXY_CREDS = "data.apiconfig_proxy_creds_v1"
        }

        private val adapter by lazy {
            moshi.adapter(ApiConfigResponse::class.java)
        }
        private val proxyCredsAdapter by lazy {
            moshi.adapter(ApiConfigProxyCredsPayload::class.java)
        }
        private val degradedModeWarningPrinted = AtomicBoolean(false)

        suspend fun save(config: ApiConfigResponse) {
            withContext(Dispatchers.IO) {
                try {
                    warnIfSecureStorageUnavailable()
                    val sanitizedConfig = sanitizeConfig(config)
                    val json = adapter.toJson(sanitizedConfig)
                    val credsPayload = extractProxyCreds(config)
                    val credsJson = proxyCredsAdapter.toJson(credsPayload)

                    sharedPreferences.edit { putString(KEY_API_CONFIG, json) }
                    securePreferences.edit { putString(KEY_API_CONFIG_PROXY_CREDS, credsJson) }
                } catch (ex: Throwable) {
                    Timber.e(ex)
                }
            }
        }

        suspend fun get(): ApiConfigResponse? {
            return withContext(Dispatchers.IO) {
                val config =
                    sharedPreferences
                        .getString(KEY_API_CONFIG, null)
                        ?.let { adapter.fromJson(it) }
                        ?: return@withContext null

                val migrated = migrateLegacyProxyCredsIfNeeded(config)
                applyProxyCreds(migrated)
            }
        }

        suspend fun setActive(tag: String) {
            withContext(Dispatchers.IO) {
                sharedPreferences.edit().putString(KEY_API_CONFIG_ACTIVE, tag).apply()
            }
        }

        suspend fun getActive(): String? {
            return withContext(Dispatchers.IO) {
                sharedPreferences
                    .getString(KEY_API_CONFIG_ACTIVE, null)
            }
        }

        private fun migrateLegacyProxyCredsIfNeeded(config: ApiConfigResponse): ApiConfigResponse {
            val legacyCreds = extractProxyCreds(config)
            if (legacyCreds.items.isEmpty()) {
                return config
            }
            val currentCreds = loadProxyCreds()
            val mergedCredsMap =
                currentCreds.items
                    .associateBy { "${it.addressTag}::${it.proxyTag}" }
                    .toMutableMap()
                    .apply {
                        legacyCreds.items.forEach { item ->
                            this["${item.addressTag}::${item.proxyTag}"] = item
                        }
                    }
            val mergedPayload = ApiConfigProxyCredsPayload(mergedCredsMap.values.toList())
            securePreferences.edit {
                putString(KEY_API_CONFIG_PROXY_CREDS, proxyCredsAdapter.toJson(mergedPayload))
            }

            val sanitized = sanitizeConfig(config)
            sharedPreferences.edit {
                putString(KEY_API_CONFIG, adapter.toJson(sanitized))
            }
            return sanitized
        }

        private fun loadProxyCreds(): ApiConfigProxyCredsPayload {
            warnIfSecureStorageUnavailable()
            return runCatching {
                securePreferences
                    .getString(KEY_API_CONFIG_PROXY_CREDS, null)
                    ?.let { proxyCredsAdapter.fromJson(it) }
            }.getOrNull() ?: ApiConfigProxyCredsPayload(emptyList())
        }

        private fun applyProxyCreds(config: ApiConfigResponse): ApiConfigResponse {
            val credsByKey = loadProxyCreds().items.associateBy { "${it.addressTag}::${it.proxyTag}" }
            return config.copy(
                addresses =
                    config.addresses.map { address ->
                        address.copy(
                            proxies =
                                address.proxies.map { proxy ->
                                    val key = "${address.tag}::${proxy.tag}"
                                    val creds = credsByKey[key]
                                    if (creds != null) {
                                        proxy.copy(
                                            user = creds.user,
                                            password = creds.password,
                                        )
                                    } else {
                                        proxy
                                    }
                                },
                        )
                    },
            )
        }

        private fun sanitizeConfig(config: ApiConfigResponse): ApiConfigResponse {
            return config.copy(
                addresses =
                    config.addresses.map { address ->
                        address.copy(
                            proxies =
                                address.proxies.map { proxy ->
                                    proxy.copy(user = null, password = null)
                                },
                        )
                    },
            )
        }

        private fun extractProxyCreds(config: ApiConfigResponse): ApiConfigProxyCredsPayload {
            val items =
                config.addresses
                    .flatMap { address ->
                        address.proxies.mapNotNull { proxy ->
                            val hasCreds = !proxy.user.isNullOrBlank() || !proxy.password.isNullOrBlank()
                            if (!hasCreds) {
                                null
                            } else {
                                ApiConfigProxyCred(
                                    addressTag = address.tag,
                                    proxyTag = proxy.tag,
                                    user = proxy.user,
                                    password = proxy.password,
                                )
                            }
                        }
                    }
            return ApiConfigProxyCredsPayload(items)
        }

        private fun warnIfSecureStorageUnavailable() {
            if (!criticalSecureStorageStatus.isAvailable()) {
                if (degradedModeWarningPrinted.compareAndSet(false, true)) {
                    Timber.w(
                        criticalSecureStorageStatus.getUnavailableCause(),
                        "Secure proxy credentials storage unavailable. Proxy credentials are not persisted in plaintext.",
                    )
                }
            }
        }
    }

@JsonClass(generateAdapter = true)
internal data class ApiConfigProxyCredsPayload(
    val items: List<ApiConfigProxyCred>,
)

@JsonClass(generateAdapter = true)
internal data class ApiConfigProxyCred(
    val addressTag: String,
    val proxyTag: String,
    val user: String?,
    val password: String?,
)
