package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.SecureDataPreferences
import ru.radiationx.data.datasource.SuspendMutableStateFlow
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class AuthTokenStorage @Inject constructor(
    @DataPreferences private val plaintextPreferences: SharedPreferences,
    @SecureDataPreferences private val encryptedPreferences: SharedPreferences,
) : AuthTokenHolder {

    companion object {
        private const val KEY_AUTH_TOKEN = "data.aniliberty_auth_token"
    }

    private val migrationDone = AtomicBoolean(false)

    private val tokenRelay = SuspendMutableStateFlow {
        migrateIfNeeded()
        encryptedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    override fun observeToken(): Flow<String?> = tokenRelay

    override suspend fun getToken(): String? = tokenRelay.getValue()

    override suspend fun saveToken(token: String) {
        migrateIfNeeded()
        encryptedPreferences.edit {
            putString(KEY_AUTH_TOKEN, token)
        }
        tokenRelay.setValue(token)
    }

    override suspend fun deleteToken() {
        migrateIfNeeded()
        encryptedPreferences.edit {
            remove(KEY_AUTH_TOKEN)
        }
        tokenRelay.setValue(null)
    }

    private fun migrateIfNeeded() {
        if (!migrationDone.compareAndSet(false, true)) {
            return
        }
        SensitivePreferenceMigrator.migrateKeys(
            keys = listOf(KEY_AUTH_TOKEN),
            source = preferencesStore(plaintextPreferences),
            target = preferencesStore(encryptedPreferences),
        )
    }

    private fun preferencesStore(sharedPreferences: SharedPreferences): StringKeyValueStore {
        return object : StringKeyValueStore {
            override fun getString(key: String): String? = sharedPreferences.getString(key, null)

            override fun putString(key: String, value: String) {
                sharedPreferences.edit { putString(key, value) }
            }

            override fun remove(key: String) {
                sharedPreferences.edit { remove(key) }
            }
        }
    }
}
