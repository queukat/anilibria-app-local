package ru.radiationx.data.datasource.holders

import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.watching.UserViewPendingUpload

/**
 * Persisted state for user views synchronization (AniLiberty timecodes/history).
 *
 * We keep it minimal:
 * - initial upload of LOCAL progress to REMOTE should run once (per auth token hash)
 * - full import of REMOTE-only releases to LOCAL should run once (per auth token hash)
 *
 * Any further incremental sync is done on app start (light import) and on player exit (per-episode upsert).
 */
interface UserViewsSyncHolder {

    suspend fun getLastUploadTokenHash(): String?

    suspend fun setLastUploadTokenHash(value: String?)

    suspend fun getLastFullImportTokenHash(): String?

    suspend fun setLastFullImportTokenHash(value: String?)

    suspend fun getPendingUploads(): List<UserViewPendingUpload>

    suspend fun upsertPendingUpload(upload: UserViewPendingUpload)

    suspend fun upsertPendingUploads(uploads: Collection<UserViewPendingUpload>) {
        uploads.forEach { upload ->
            upsertPendingUpload(upload)
        }
    }

    suspend fun removePendingUploadsByEpisodeIds(episodeIds: Collection<EpisodeId>)

    suspend fun removePendingUploadsByReleaseId(releaseId: ReleaseId)
}
