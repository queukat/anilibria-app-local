package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.datasource.SuspendMutableStateFlow
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import javax.inject.Inject

class AuthTokenStorage @Inject constructor(
    @DataPreferences private val sharedPreferences: SharedPreferences,
) : AuthTokenHolder {

    companion object {
        private const val KEY_AUTH_TOKEN = "data.aniliberty_auth_token"
    }

    private val tokenRelay = SuspendMutableStateFlow {
        sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    override fun observeToken(): Flow<String?> = tokenRelay

    override suspend fun getToken(): String? = tokenRelay.getValue()

    override suspend fun saveToken(token: String) {
        sharedPreferences.edit {
            putString(KEY_AUTH_TOKEN, token)
        }
        tokenRelay.setValue(token)
    }

    override suspend fun deleteToken() {
        sharedPreferences.edit {
            remove(KEY_AUTH_TOKEN)
        }
        tokenRelay.setValue(null)
    }
}