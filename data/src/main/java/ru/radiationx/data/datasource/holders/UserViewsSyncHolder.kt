package ru.radiationx.data.datasource.holders

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
}
