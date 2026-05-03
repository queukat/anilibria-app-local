package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.di.CriticalSecureStorageStatus
import ru.radiationx.data.entity.domain.auth.CriticalSecureStorageUnavailableException

class AuthTokenStorageTest {
    companion object {
        private const val KEY_AUTH_TOKEN = "data.aniliberty_auth_token"
    }

    @Test
    fun getToken_migratesFromPlaintextToEncrypted_andClearsPlaintext() =
        runTest {
            val plaintext =
                InMemorySharedPreferences(
                    mutableMapOf(KEY_AUTH_TOKEN to "legacy-token"),
                )
            val encrypted = InMemorySharedPreferences()
            val status = CriticalSecureStorageStatus()
            val storage =
                AuthTokenStorage(
                    plaintextPreferences = plaintext,
                    encryptedPreferences = encrypted,
                    criticalSecureStorageStatus = status,
                )

            val token = storage.getToken()

            assertEquals("legacy-token", token)
            assertNull(plaintext.getString(KEY_AUTH_TOKEN, null))
            assertEquals("legacy-token", encrypted.getString(KEY_AUTH_TOKEN, null))
        }

    @Test
    fun saveToken_failsClosed_whenCriticalSecureStorageUnavailable() =
        runTest {
            val plaintext = InMemorySharedPreferences()
            val status = CriticalSecureStorageStatus().apply { markUnavailable() }
            val storage =
                AuthTokenStorage(
                    plaintextPreferences = plaintext,
                    encryptedPreferences = plaintext,
                    criticalSecureStorageStatus = status,
                )

            val error =
                runCatching {
                    storage.saveToken("secret-token")
                }.exceptionOrNull()

            assertTrue(error is CriticalSecureStorageUnavailableException)
            assertTrue(error?.message?.contains("Secure token storage is unavailable") == true)
            assertNull(plaintext.getString(KEY_AUTH_TOKEN, null))
        }

    @Test
    fun saveToken_propagatesUnavailableCause_whenCriticalSecureStorageUnavailable() =
        runTest {
            val plaintext = InMemorySharedPreferences()
            val rootCause = IllegalStateException("keystore init failed")
            val status = CriticalSecureStorageStatus().apply { markUnavailable(rootCause) }
            val storage =
                AuthTokenStorage(
                    plaintextPreferences = plaintext,
                    encryptedPreferences = plaintext,
                    criticalSecureStorageStatus = status,
                )

            val error =
                runCatching {
                    storage.saveToken("secret-token")
                }.exceptionOrNull()

            assertTrue(error is CriticalSecureStorageUnavailableException)
            assertTrue(error?.cause === rootCause)
        }

    @Test
    fun getToken_unavailable_doesNotUsePlaintextAfterMigration() =
        runTest {
            val plaintext =
                InMemorySharedPreferences(
                    mutableMapOf(KEY_AUTH_TOKEN to "legacy-token"),
                )
            val status = CriticalSecureStorageStatus().apply { markUnavailable() }
            val storage =
                AuthTokenStorage(
                    plaintextPreferences = plaintext,
                    encryptedPreferences = plaintext,
                    criticalSecureStorageStatus = status,
                )

            val token = storage.getToken()

            assertNull(token)
            assertNull(plaintext.getString(KEY_AUTH_TOKEN, null))

            plaintext.edit().putString(KEY_AUTH_TOKEN, "reintroduced-token").commit()

            val secondToken = storage.getToken()

            assertNull(secondToken)
            assertEquals("reintroduced-token", plaintext.getString(KEY_AUTH_TOKEN, null))
        }
}

private class InMemorySharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf(),
) : SharedPreferences {
    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? {
        return values[key] as? MutableSet<String> ?: defValues
    }

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun edit(): SharedPreferences.Editor = EditorImpl(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op for tests
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op for tests
    }

    private class EditorImpl(
        private val target: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private var clearAll = false
        private val removals = mutableSetOf<String>()
        private val updates = mutableMapOf<String, Any?>()

        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = values
            }
            return this
        }

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor {
            if (key != null) {
                updates[key] = value
            }
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) {
                removals += key
            }
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            if (clearAll) {
                target.clear()
            }
            removals.forEach { key ->
                target.remove(key)
            }
            updates.forEach { (key, value) ->
                target[key] = value
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
