package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.datasource.SuspendMutableStateFlow
import ru.radiationx.data.datasource.holders.UserViewsSyncHolder
import javax.inject.Inject
import androidx.core.content.edit

class UserViewsSyncStorage @Inject constructor(
    @DataPreferences private val sharedPreferences: SharedPreferences,
) : UserViewsSyncHolder {

    companion object {
        private const val KEY_LAST_UPLOAD_TOKEN_HASH = "data.user_views_sync.last_upload_token_hash"
        private const val KEY_LAST_FULL_IMPORT_TOKEN_HASH = "data.user_views_sync.last_full_import_token_hash"
    }

    private val uploadTokenHashRelay = SuspendMutableStateFlow { load(KEY_LAST_UPLOAD_TOKEN_HASH) }
    private val fullImportTokenHashRelay = SuspendMutableStateFlow { load(KEY_LAST_FULL_IMPORT_TOKEN_HASH) }

    override suspend fun getLastUploadTokenHash(): String? = uploadTokenHashRelay.getValue()

    override suspend fun setLastUploadTokenHash(value: String?) {
        uploadTokenHashRelay.setValue(value)
        save(KEY_LAST_UPLOAD_TOKEN_HASH, value)
    }

    override suspend fun getLastFullImportTokenHash(): String? = fullImportTokenHashRelay.getValue()

    override suspend fun setLastFullImportTokenHash(value: String?) {
        fullImportTokenHashRelay.setValue(value)
        save(KEY_LAST_FULL_IMPORT_TOKEN_HASH, value)
    }

    private suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(key, null)
    }

    private suspend fun save(key: String, value: String?) = withContext(Dispatchers.IO) {
        sharedPreferences.edit {
            if (value == null) {
                remove(key)
            } else {
                putString(key, value)
            }
        }
    }
}
