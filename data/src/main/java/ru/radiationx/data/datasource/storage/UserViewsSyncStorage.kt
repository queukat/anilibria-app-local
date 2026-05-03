package ru.radiationx.data.datasource.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.datasource.SuspendMutableStateFlow
import ru.radiationx.data.datasource.holders.UserViewsSyncHolder
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.watching.UserViewPendingUpload
import javax.inject.Inject

class UserViewsSyncStorage
    @Inject
    constructor(
        @DataPreferences private val sharedPreferences: SharedPreferences,
        private val moshi: Moshi,
    ) : UserViewsSyncHolder {
        companion object {
            private const val KEY_LAST_UPLOAD_TOKEN_HASH = "data.user_views_sync.last_upload_token_hash"
            private const val KEY_LAST_FULL_IMPORT_TOKEN_HASH = "data.user_views_sync.last_full_import_token_hash"
            private const val KEY_PENDING_UPLOADS = "data.user_views_sync.pending_uploads"
        }

        private val pendingUploadsAdapter by lazy {
            val type = Types.newParameterizedType(List::class.java, UserViewPendingUpload::class.java)
            moshi.adapter<List<UserViewPendingUpload>>(type)
        }

        private val uploadTokenHashRelay = SuspendMutableStateFlow { load(KEY_LAST_UPLOAD_TOKEN_HASH) }
        private val fullImportTokenHashRelay = SuspendMutableStateFlow { load(KEY_LAST_FULL_IMPORT_TOKEN_HASH) }
        private val pendingUploadsRelay = SuspendMutableStateFlow { loadPendingUploads() }

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

        override suspend fun getPendingUploads(): List<UserViewPendingUpload> = pendingUploadsRelay.getValue()

        override suspend fun upsertPendingUpload(upload: UserViewPendingUpload) {
            val updated =
                pendingUploadsRelay.getValue()
                    .filterNot { pending -> pending.releaseId == upload.releaseId && pending.episodeId == upload.episodeId }
                    .plus(upload)
            pendingUploadsRelay.setValue(updated)
            savePendingUploads(updated)
        }

        override suspend fun removePendingUploadsByEpisodeIds(episodeIds: Collection<EpisodeId>) {
            if (episodeIds.isEmpty()) return
            val episodeIdSet =
                episodeIds.mapTo(mutableSetOf()) { episodeId ->
                    episodeId.releaseId.id to episodeId.id
                }
            val updated =
                pendingUploadsRelay.getValue()
                    .filterNot { pending -> (pending.releaseId to pending.episodeId) in episodeIdSet }
            pendingUploadsRelay.setValue(updated)
            savePendingUploads(updated)
        }

        override suspend fun removePendingUploadsByReleaseId(releaseId: ReleaseId) {
            val updated =
                pendingUploadsRelay.getValue()
                    .filterNot { pending -> pending.releaseId == releaseId.id }
            pendingUploadsRelay.setValue(updated)
            savePendingUploads(updated)
        }

        private suspend fun load(key: String): String? =
            withContext(Dispatchers.IO) {
                sharedPreferences.getString(key, null)
            }

        private suspend fun save(
            key: String,
            value: String?,
        ) = withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                if (value == null) {
                    remove(key)
                } else {
                    putString(key, value)
                }
            }
        }

        private suspend fun loadPendingUploads(): List<UserViewPendingUpload> =
            withContext(Dispatchers.IO) {
                sharedPreferences.getString(KEY_PENDING_UPLOADS, null)
                    ?.let { json -> pendingUploadsAdapter.fromJson(json) }
                    .orEmpty()
            }

        private suspend fun savePendingUploads(value: List<UserViewPendingUpload>) =
            withContext(Dispatchers.IO) {
                sharedPreferences.edit {
                    if (value.isEmpty()) {
                        remove(KEY_PENDING_UPLOADS)
                    } else {
                        putString(KEY_PENDING_UPLOADS, pendingUploadsAdapter.toJson(value))
                    }
                }
            }
    }
