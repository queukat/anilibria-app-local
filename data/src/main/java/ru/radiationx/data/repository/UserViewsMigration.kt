package ru.radiationx.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.radiationx.data.DataPreferences
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFieldName
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseExclude
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReleaseEpisodeTimecode
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.isNearEpisodeEnd
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.system.HttpException
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * One-time migration/sync of **local** episode progresses (EpisodeAccess) to AniLiberty user timecodes.
 *
 * Why:
 * - older app versions stored progress only locally and did not sync it to the server;
 * - after switching to AniLiberty API we want to preserve those progresses when user updates the app.
 *
 * Conflict policy:
 * - "watched" wins over any position;
 * - otherwise, the bigger position wins (max(local, remote)).
 *
 * Best-effort:
 * - does nothing when there is no AniLiberty token;
 * - does not crash callers;
 * - marks itself as "done" only if all releases were processed without *fatal* errors.
 */
class UserViewsMigration @Inject constructor(
    @DataPreferences private val dataPreferences: SharedPreferences,
    private val authTokenHolder: AuthTokenHolder,
    private val episodesCheckerHolder: EpisodesCheckerHolder,
    private val aniLibertyApi: AniLibertyApi,
) {

    private data class EpisodeInfo(
        val aniEpisodeId: AniLibertyReleaseEpisodeId,
        val ordinalKey: String,
        val durationMs: Long?,
    )

    private data class RemoteTimecode(
        val positionMs: Long,
        val isWatched: Boolean,
    )

    private data class ReleasePatch(
        val upserts: List<AniLibertyUserViewTimecodeUpsertBody>,
        val localUpdates: List<EpisodeAccess>,
    )

    private val mutex = Mutex()

    suspend fun syncIfNeeded(userId: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val token = authTokenHolder.getToken()
            if (token.isNullOrBlank()) return@withLock

            if (isAlreadySynced(userId)) return@withLock

            val now = System.currentTimeMillis()
            Timber.i("UserViewsMigration: start (userId=%d)", userId)

            val localAll = runCatching {
                episodesCheckerHolder.getEpisodes()
            }.getOrElse { error ->
                Timber.w(error, "UserViewsMigration: failed to read local episodes")
                emptyList()
            }

            val localEpisodes = localAll
                .filter { it.isViewed || it.seek > 0L || it.lastAccessRaw > 0L }

            if (localEpisodes.isEmpty()) {
                markSynced(userId, now)
                Timber.i("UserViewsMigration: nothing to sync, mark done (userId=%d)", userId)
                return@withLock
            }

            val grouped = localEpisodes.groupBy { it.id.releaseId }

            val allUpserts = mutableListOf<AniLibertyUserViewTimecodeUpsertBody>()
            val allLocalUpdates = mutableListOf<EpisodeAccess>()
            var hasFatalErrors = false
            var skippedReleases = 0

            for ((releaseId, releaseLocalEpisodes) in grouped) {
                val patch = try {
                    buildReleasePatch(releaseId, releaseLocalEpisodes)
                } catch (ex: Throwable) {
                    if (ex is CancellationException) throw ex

                    if (isSkippableReleaseError(ex)) {
                        skippedReleases++
                        Timber.w(ex, "UserViewsMigration: skip release=%s (non-fatal)", releaseId)
                    } else {
                        hasFatalErrors = true
                        Timber.w(ex, "UserViewsMigration: failed to build patch for release=%s", releaseId)
                    }
                    null
                }

                if (patch != null) {
                    allUpserts += patch.upserts
                    allLocalUpdates += patch.localUpdates
                }
            }

            // 1) Server upserts (batched).
            val upsertOk = upsertAll(allUpserts)

            // 2) Local updates (merge remote progress into local so app UI stays consistent).
            val localOk = updateLocal(allLocalUpdates)

            if (!hasFatalErrors && upsertOk && localOk) {
                markSynced(userId, now)
                Timber.i(
                    "UserViewsMigration: done (userId=%d, releases=%d, skipped=%d, upserts=%d, localUpdates=%d)",
                    userId,
                    grouped.size,
                    skippedReleases,
                    allUpserts.size,
                    allLocalUpdates.size
                )
            } else {
                Timber.w(
                    "UserViewsMigration: finished with fatal errors (userId=%d, fatal=%b, upsertOk=%b, localOk=%b) - will retry later",
                    userId,
                    hasFatalErrors,
                    upsertOk,
                    localOk
                )
            }
        }
    }

    private fun isAlreadySynced(userId: Int): Boolean {
        val syncedUserId = dataPreferences.getInt(KEY_SYNCED_USER_ID, -1)
        val syncedAt = dataPreferences.getLong(KEY_SYNCED_AT, 0L)
        return syncedUserId == userId && syncedAt > 0L
    }

    private fun markSynced(userId: Int, atMs: Long) {
        dataPreferences.edit {
            putInt(KEY_SYNCED_USER_ID, userId)
            putLong(KEY_SYNCED_AT, atMs)
        }
    }

    /**
     * Treat some 4xx errors as "non-fatal" for migration.
     *
     * Example:
     * - release can be deleted/hidden/geo-blocked => 403/404;
     * - trying again will never help, but should not block migration for other releases.
     */
    private fun isSkippableReleaseError(error: Throwable): Boolean {
        val http = error as? HttpException ?: return false
        val code = http.code

        // 401 => token is invalid / expired => migration must retry after re-login.
        // 429 => rate-limit => transient, should retry later.
        if (code == 401 || code == 429) return false

        return code in 400..499
    }

    private suspend fun buildReleasePatch(
        releaseId: ReleaseId,
        localEpisodes: List<EpisodeAccess>,
    ): ReleasePatch {
        val fieldsForEpisodes = AniLibertyReleaseFields(
            exclude = setOf(
                AniLibertyReleaseExclude.MEMBERS,
                AniLibertyReleaseExclude.TORRENTS,
            ),
            excludeRaw = setOf(
                AniLibertyFieldName("description"),
                AniLibertyFieldName("notification"),
            )
        )

        val key = AniLibertyReleaseKey.id(releaseId.id)

        // 1) Release details with episodes (id/ordinal/duration).
        val release = aniLibertyApi.getRelease(
            key = key,
            fields = fieldsForEpisodes,
        )

        val episodeInfos = release.episodes.orEmpty()
            .mapNotNull { episode ->
                val aniId = episode.id ?: return@mapNotNull null
                val ordinal = episode.ordinal ?: episode.sortOrder ?: return@mapNotNull null
                val keyOrdinal = normalizeOrdinalDouble(ordinal)
                val durationMs = episode.duration
                    ?.let { (it * 1000.0).roundToLong() }
                    ?.takeIf { it > 0L }
                EpisodeInfo(
                    aniEpisodeId = aniId,
                    ordinalKey = keyOrdinal,
                    durationMs = durationMs,
                )
            }

        val infoByOrdinal = episodeInfos.associateBy { it.ordinalKey }
        val infoByAniId = episodeInfos.associateBy { it.aniEpisodeId }

        // 2) Remote timecodes for this release.
        val remote = aniLibertyApi.getReleaseEpisodesTimecodes(key = key)
        val remoteMap = remote.toRemoteMap()

        // 3) Local map (for quick "exists" checks).
        val localByOrdinal = localEpisodes.associateBy { normalizeOrdinalString(it.id.id) }

        // 4) Build patch.
        val upserts = mutableMapOf<AniLibertyReleaseEpisodeId, AniLibertyUserViewTimecodeUpsertBody>()
        val localUpdates = mutableListOf<EpisodeAccess>()

        // 4a) Merge for episodes present locally (local -> remote, remote -> local).
        for (local in localEpisodes) {
            val ordinalKey = normalizeOrdinalString(local.id.id)
            val info = infoByOrdinal[ordinalKey] ?: continue

            val remoteTc = remoteMap[info.aniEpisodeId]
            val remotePos = remoteTc?.positionMs ?: 0L
            val remoteWatched = remoteTc?.isWatched ?: false

            val localPos = safePosition(local.seek)
            val localWatched = isLocalWatched(local, info.durationMs)

            val mergedWatched = remoteWatched || localWatched
            val mergedPos = if (mergedWatched) 0L else max(localPos, safePosition(remotePos))

            // Upsert to server only if it moves the server forward (or marks watched).
            if (shouldUpsertRemote(remoteTc, mergedPos, mergedWatched)) {
                val time = if (mergedWatched) 0.0 else msToSeconds(mergedPos)
                upserts[info.aniEpisodeId] = AniLibertyUserViewTimecodeUpsertBody.from(
                    time = time,
                    isWatched = mergedWatched,
                    releaseEpisodeId = info.aniEpisodeId
                )
            }

            // Update local if server is ahead (or server watched).
            val desiredSeek = when {
                mergedWatched -> info.durationMs?.let { max(localPos, it) } ?: localPos
                else -> max(localPos, safePosition(remotePos))
            }
            val desiredViewed = local.isViewed || remotePos > 0L || remoteWatched

            if (desiredSeek != local.seek || desiredViewed != local.isViewed) {
                localUpdates += local.copy(
                    seek = desiredSeek,
                    isViewed = desiredViewed,
                )
            }
        }

        // 4b) Create local entries for remote-only progress within releases that
        // are already present locally (helps "server ahead" on this release).
        for ((aniId, remoteTc) in remoteMap) {
            if (!remoteTc.isWatched && remoteTc.positionMs <= 0L) continue

            val info = infoByAniId[aniId] ?: continue
            if (localByOrdinal.containsKey(info.ordinalKey)) continue

            val seek = if (remoteTc.isWatched) {
                info.durationMs ?: 0L
            } else {
                safePosition(remoteTc.positionMs)
            }

            val episodeId = EpisodeId(
                id = info.ordinalKey,
                releaseId = releaseId,
            )

            // lastAccess = 0L intentionally: we don't know actual recency from server.
            localUpdates += EpisodeAccess(
                episodeId,
                seek,
                true,
                0L,
            )
        }

        return ReleasePatch(
            upserts = upserts.values.toList(),
            localUpdates = localUpdates,
        )
    }

    private fun List<AniLibertyReleaseEpisodeTimecode>.toRemoteMap(): Map<AniLibertyReleaseEpisodeId, RemoteTimecode> {
        return associate { item ->
            item.releaseEpisodeId to RemoteTimecode(
                positionMs = secondsToMs(item.time),
                isWatched = item.isWatched,
            )
        }
    }

    private suspend fun upsertAll(
        upserts: List<AniLibertyUserViewTimecodeUpsertBody>,
    ): Boolean {
        if (upserts.isEmpty()) return true

        var ok = true
        val batches = upserts.chunked(UPSERT_BATCH_SIZE)
        for (batch in batches) {
            try {
                aniLibertyApi.upsertUserViewTimecodes(batch)
            } catch (ex: Throwable) {
                if (ex is CancellationException) throw ex
                ok = false
                Timber.w(ex, "UserViewsMigration: upsert batch failed (size=%d)", batch.size)
            }
        }
        return ok
    }

    private suspend fun updateLocal(localUpdates: List<EpisodeAccess>): Boolean {
        if (localUpdates.isEmpty()) return true

        // Deduplicate by EpisodeId and keep the "max" seek.
        val merged = buildMap<EpisodeId, EpisodeAccess> {
            for (item in localUpdates) {
                val current = get(item.id)
                if (current == null) {
                    put(item.id, item)
                } else {
                    val mergedSeek = max(current.seek, item.seek)
                    val mergedViewed = current.isViewed || item.isViewed
                    put(item.id, current.copy(seek = mergedSeek, isViewed = mergedViewed))
                }
            }
        }.values.toList()

        return try {
            episodesCheckerHolder.putAllEpisode(merged)
            true
        } catch (ex: Throwable) {
            if (ex is CancellationException) throw ex
            Timber.w(ex, "UserViewsMigration: failed to update local episodes")
            false
        }
    }

    private fun shouldUpsertRemote(
        remote: RemoteTimecode?,
        mergedPositionMs: Long,
        mergedWatched: Boolean,
    ): Boolean {
        // Don't create "empty" timecodes on server.
        if (!mergedWatched && mergedPositionMs < MIN_POSITION_TO_SYNC_MS) return false

        // No remote entry => create one.
        if (remote == null) return true

        // Remote is already watched => final.
        if (remote.isWatched) return false

        // Mark watched.
        if (mergedWatched) return true

        // Move position forward (with threshold to avoid noise due to rounding).
        val delta = mergedPositionMs - remote.positionMs
        return delta >= MIN_REMOTE_ADVANCE_TO_UPSERT_MS
    }

    private fun isLocalWatched(
        local: EpisodeAccess,
        durationMs: Long?,
    ): Boolean {
        if (!local.isViewed) return false

        // Manual "mark as watched" in legacy storage often creates entries with seek=0 and lastAccess=0.
        if (local.seek == 0L && local.lastAccessRaw <= 0L) return true

        val duration = durationMs ?: return false
        return isNearEpisodeEnd(
            positionMs = local.seek,
            durationMs = duration,
        )
    }

    private fun safePosition(positionMs: Long): Long =
        positionMs
            .coerceAtLeast(0L)
            .coerceAtMost(MAX_POSITION_MS)

    private fun msToSeconds(ms: Long): Double = ms.toDouble() / 1000.0

    private fun secondsToMs(seconds: Double): Long =
        (seconds * 1000.0).roundToLong()

    private fun normalizeOrdinalString(value: String): String {
        val trimmed = value.trim()
        return runCatching {
            BigDecimal(trimmed).stripTrailingZeros().toPlainString()
        }.getOrDefault(trimmed)
    }

    private fun normalizeOrdinalDouble(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private companion object {
        private const val KEY_SYNCED_USER_ID = "data.views_migration.synced_user_id"
        private const val KEY_SYNCED_AT = "data.views_migration.synced_at"

        private const val UPSERT_BATCH_SIZE: Int = 50

        private const val MIN_POSITION_TO_SYNC_MS: Long = 1_000L
        private const val MIN_REMOTE_ADVANCE_TO_UPSERT_MS: Long = 1_000L

        // Safety cap: should be way above any episode duration.
        private const val MAX_POSITION_MS: Long = 12L * 60L * 60L * 1000L // 12h
    }
}
