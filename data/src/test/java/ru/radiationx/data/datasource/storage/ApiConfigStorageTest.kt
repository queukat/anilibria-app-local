package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.entity.response.config.ApiConfigAddressResponse
import ru.radiationx.data.entity.response.config.ApiConfigProxyResponse
import ru.radiationx.data.entity.response.config.ApiConfigResponse
import java.util.concurrent.ConcurrentHashMap

class ApiConfigStorageTest {

    @Test
    fun save_storesProxyCredentialsOnlyInSecurePreferences() = runBlocking {
        val plaintextPrefs = ApiConfigInMemorySharedPreferences()
        val securePrefs = ApiConfigInMemorySharedPreferences()
        val storage = ApiConfigStorage(
            sharedPreferences = plaintextPrefs,
            securePreferences = securePrefs,
            moshi = Moshi.Builder().build(),
        )

        storage.save(sampleConfig(user = "proxy-user", password = "proxy-password"))

        val plaintextJson = plaintextPrefs.getString("data.apiconfig_v2", null).orEmpty()
        assertFalse(plaintextJson.contains("proxy-user"))
        assertFalse(plaintextJson.contains("proxy-password"))

        val secureValues = securePrefs.all.values.joinToString(separator = " ")
        assertTrue(secureValues.contains("proxy-user"))
        assertTrue(secureValues.contains("proxy-password"))
    }

    @Test
    fun get_migratesLegacyPlaintextProxyCredentialsToSecurePreferences() = runBlocking {
        val plaintextPrefs = ApiConfigInMemorySharedPreferences()
        val securePrefs = ApiConfigInMemorySharedPreferences()
        val moshi = Moshi.Builder().build()
        val legacyConfig = sampleConfig(user = "legacy-user", password = "legacy-password")
        val legacyJson = moshi.adapter(ApiConfigResponse::class.java).toJson(legacyConfig)
        plaintextPrefs.edit().putString("data.apiconfig_v2", legacyJson).apply()

        val storage = ApiConfigStorage(
            sharedPreferences = plaintextPrefs,
            securePreferences = securePrefs,
            moshi = moshi,
        )

        val loaded = storage.get()
        val loadedProxy = loaded?.addresses?.firstOrNull()?.proxies?.firstOrNull()
        assertEquals("legacy-user", loadedProxy?.user)
        assertEquals("legacy-password", loadedProxy?.password)

        val migratedPlaintext = plaintextPrefs.getString("data.apiconfig_v2", null).orEmpty()
        assertFalse(migratedPlaintext.contains("legacy-user"))
        assertFalse(migratedPlaintext.contains("legacy-password"))

        val secureValues = securePrefs.all.values.joinToString(separator = " ")
        assertTrue(secureValues.contains("legacy-user"))
        assertTrue(secureValues.contains("legacy-password"))
    }

    private fun sampleConfig(user: String?, password: String?): ApiConfigResponse {
        return ApiConfigResponse(
            addresses = listOf(
                ApiConfigAddressResponse(
                    tag = "addr",
                    name = "Address",
                    desc = null,
                    widgetsSite = "https://example.org",
                    site = "https://example.org",
                    baseImages = "https://example.org",
                    base = "https://example.org",
                    api = "https://example.org/api",
                    ips = emptyList(),
                    proxies = listOf(
                        ApiConfigProxyResponse(
                            tag = "proxy",
                            name = "Proxy",
                            desc = null,
                            ip = "127.0.0.1",
                            port = 8080,
                            user = user,
                            password = password,
                        )
                    ),
                )
            )
        )
    }
}

private class ApiConfigInMemorySharedPreferences(
    initial: Map<String, Any?> = emptyMap(),
) : SharedPreferences {
    private val values = ConcurrentHashMap(initial)

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        return values[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        if (key == null) return defValues
        @Suppress("UNCHECKED_CAST")
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (key == null) return defValue
        return values[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (key == null) return defValue
        return values[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (key == null) return defValue
        return values[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue
        return values[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean {
        if (key == null) return false
        return values.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = EditorImpl(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op
    }

    private class EditorImpl(
        private val values: ConcurrentHashMap<String, Any?>,
    ) : SharedPreferences.Editor {

        private val pending = linkedMapOf<String, Any?>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = value
            }
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = values
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = value
            }
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = value
            }
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = value
            }
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = value
            }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) {
                pending[key] = null
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) {
                values.clear()
            }
            pending.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
            pending.clear()
        }
    }
}
