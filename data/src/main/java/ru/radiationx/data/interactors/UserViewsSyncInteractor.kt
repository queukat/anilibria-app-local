package ru.radiationx.data.interactors

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.datasource.holders.HistoryHolder
import ru.radiationx.data.datasource.holders.UserViewsSyncHolder
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFieldName
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseExclude
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyUserViewHistoryItem
import ru.radiationx.data.datasource.remote.aniliberty.MAX_USER_VIEWS_HISTORY_LIMIT
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.isNearEpisodeEnd
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.repository.UserViewsRepository
import ru.radiationx.data.system.ApplicationCoroutineScope
import ru.radiationx.shared.ktx.coroutines.AppDispatchers
import timber.log.Timber

/**
 * One-time migration + lightweight ongoing sync for AniLiberty user views.
 *
 * Goals:
 * 1) After updating from an old version (only local progress), upload local timecodes to server.
 * 2) Import remote-only releases (and remote progress) into local storage.
 * 3) Resolve conflicts so that dirty local TV state remains authoritative.
 *
 * Runtime behavior:
 * - Initial LOCAL -> REMOTE upload runs once (per auth token hash), then pending uploads keep server fresh.
 * - REMOTE -> LOCAL import runs on each app start in "light" mode (few pages),
 *   and once in "full" mode (all pages) to cover remote-only old history.
 */
class UserViewsSyncInteractor
    @Inject
    constructor(
        private val aniLibertyApi: AniLibertyApi,
        private val authTokenHolder: AuthTokenHolder,
        private val episodesCheckerHolder: EpisodesCheckerHolder,
        private val historyHolder: HistoryHolder,
        private val syncHolder: UserViewsSyncHolder,
        private val userViewsRepository: UserViewsRepository,
        private val applicationScope: ApplicationCoroutineScope,
    ) {
        private val syncMutex = Mutex()
        private val scheduleLock = Any()
        private var scheduledSyncJob: Job? = null

        private val releaseEpisodesMutex = Mutex()
        private val releaseEpisodesCache = mutableMapOf<ReleaseId, ReleaseEpisodesCache>()

        fun scheduleSyncIfNeeded(reason: String) {
            synchronized(scheduleLock) {
                scheduledSyncJob?.cancel()
                scheduledSyncJob =
                    applicationScope.launch {
                        delay(SCHEDULE_SYNC_DEBOUNCE_MS)
                        runCatching { syncIfNeeded() }
                            .onFailure { error ->
                                if (error is CancellationException) {
                                    throw error
                                }
                                Timber.w(error, "UserViewsSync: scheduled sync failed reason=%s", reason)
                            }
                    }
            }
        }

        /**
         * Best-effort sync. Never throws (except coroutine cancellation).
         *
         * NOTE: minSdk is 21, so we avoid a hard dependency on java.time here.
         */
        suspend fun syncIfNeeded() =
            withContext(AppDispatchers.io) {
                val token = authTokenHolder.getToken()?.takeIf { it.isNotBlank() } ?: return@withContext

                // Hash is used only to detect "same user/session" across launches without persisting raw token.
                val tokenHash = sha256(token)

                syncMutex.withLock {
                    val syncSessionStartedAtMs = System.currentTimeMillis()
                    val needUpload = syncHolder.getLastUploadTokenHash() != tokenHash
                    val needFullImport = syncHolder.getLastFullImportTokenHash() != tokenHash
                    var uploadStatus = if (needUpload) "pending" else "skipped"
                    var pendingUploadStatus = "pending"
                    var importStatus = "pending"
                    var finishReason = "success"

                    Timber.d(
                        "UserViewsSync.sync start sessionStartedAtMs=%d needUpload=%s needFullImport=%s",
                        syncSessionStartedAtMs,
                        needUpload,
                        needFullImport,
                    )

                    try {
                        // 1) One-time upload (migration)
                        if (needUpload) {
                            val uploadOk =
                                runCatching { uploadLocalToRemoteWithConflicts() }
                                    .getOrElse { error ->
                                        if (error is CancellationException) throw error
                                        Timber.w(error, "UserViewsSync: upload failed")
                                        false
                                    }

                            if (uploadOk) {
                                syncHolder.setLastUploadTokenHash(tokenHash)
                            }
                            uploadStatus = if (uploadOk) "ok" else "failed"
                            if (!uploadOk) {
                                finishReason = "upload_failed"
                            }
                        }

                        val pendingUploadOk =
                            runCatching {
                                userViewsRepository.flushPendingUploads(reason = "sync_if_needed")
                                true
                            }.getOrElse { error ->
                                if (error is CancellationException) throw error
                                Timber.w(error, "UserViewsSync: pending upload flush failed")
                                false
                            }
                        pendingUploadStatus = if (pendingUploadOk) "ok" else "failed"
                        if (!pendingUploadOk && finishReason == "success") {
                            finishReason = "pending_upload_failed"
                        }

                        // 2) Import remote -> local.
                        //    - If full import is not done yet: do full import (all pages).
                        //    - Otherwise: do light import to catch new remote-only items.
                        val importOk =
                            if (needFullImport) {
                                importRemoteHistoryToLocal(
                                    maxPages = MAX_HISTORY_PAGES,
                                    syncSessionStartedAtMs = syncSessionStartedAtMs,
                                )
                                    .also { ok ->
                                        if (ok) {
                                            syncHolder.setLastFullImportTokenHash(tokenHash)
                                        }
                                    }
                            } else {
                                importRemoteHistoryToLocal(
                                    maxPages = LIGHT_IMPORT_PAGES,
                                    syncSessionStartedAtMs = syncSessionStartedAtMs,
                                )
                            }
                        importStatus = if (importOk) "ok" else "failed"

                        if (!importOk) {
                            // Not fatal: we'll try again on the next AUTH trigger.
                            Timber.d("UserViewsSync: import failed (will retry later)")
                            if (finishReason == "success") {
                                finishReason = "import_failed"
                            }
                        }
                    } catch (error: Throwable) {
                        if (error is CancellationException) {
                            finishReason = "cancelled"
                            throw error
                        }
                        finishReason = "unexpected_error"
                        Timber.w(error, "UserViewsSync: unexpected error")
                    } finally {
                        Timber.d(
                            "UserViewsSync.sync finish sessionStartedAtMs=%d needUpload=%s needFullImport=%s uploadStatus=%s pendingUploadStatus=%s importStatus=%s reason=%s",
                            syncSessionStartedAtMs,
                            needUpload,
                            needFullImport,
                            uploadStatus,
                            pendingUploadStatus,
                            importStatus,
                            finishReason,
                        )
                    }
                }
            }

        /**
         * Upload local progress to server with conflict resolution.
         *
         * Returns true only if:
         * - we could load remote snapshot (auth/network ok)
         * - and all upsert chunks completed successfully
         */
        private suspend fun uploadLocalToRemoteWithConflicts(): Boolean =
            withContext(AppDispatchers.io) {
                val remoteSnapshot =
                    runCatching { aniLibertyApi.getUserViewTimecodes(since = null) }
                        .onFailure { Timber.w(it, "UserViewsSync: failed to load remote timecodes snapshot") }
                        .getOrNull()
                        ?: return@withContext false

                val remoteMap: Map<AniLibertyReleaseEpisodeId, RemoteTimecode> =
                    remoteSnapshot
                        .associate { it.releaseEpisodeId to RemoteTimecode(positionMs = secondsToMs(it.time), isWatched = it.isWatched) }

                val localEpisodes =
                    runCatching { episodesCheckerHolder.getEpisodes() }
                        .onFailure { Timber.w(it, "UserViewsSync: failed to read local episodes") }
                        .getOrNull()
                        ?.filter { it.isViewed || it.seek > 0L }
                        ?: return@withContext false

                if (localEpisodes.isEmpty()) {
                    Timber.d("UserViewsSync: local is empty, upload skipped")
                    return@withContext true
                }

                val bodiesByEpisodeId = mutableMapOf<String, AniLibertyUserViewTimecodeUpsertBody>()

                localEpisodes
                    .groupBy { it.id.releaseId }
                    .forEach { (releaseId, accesses) ->
                        val cache = getReleaseEpisodesCacheOrNull(releaseId) ?: return@forEach

                        accesses.forEach { access ->
                            val ordinalKey = normalizeOrdinalString(access.id.id)
                            val aniEpisodeId = cache.byOrdinal[ordinalKey] ?: return@forEach
                            val durationMs = cache.durationByOrdinalMs[ordinalKey]

                            val localIsWatched = isLocalWatched(access, durationMs)

                            val sendPositionMs = if (localIsWatched) 0L else access.seek
                            if (!localIsWatched && sendPositionMs < MIN_UPLOAD_POSITION_MS) return@forEach

                            val remote = remoteMap[aniEpisodeId]
                            if (!shouldUpload(localIsWatched, access.seek, remote)) return@forEach

                            val body =
                                AniLibertyUserViewTimecodeUpsertBody.from(
                                    time = msToSeconds(sendPositionMs),
                                    isWatched = localIsWatched,
                                    releaseEpisodeId = aniEpisodeId,
                                )

                            val key = body.releaseEpisodeId
                            val existing = bodiesByEpisodeId[key]
                            if (existing == null || isUpsertBodyBetter(body, existing)) {
                                bodiesByEpisodeId[key] = body
                            }
                        }
                    }

                if (bodiesByEpisodeId.isEmpty()) {
                    Timber.d("UserViewsSync: nothing to upload (all conflicts won by remote or no mappable episodes)")
                    return@withContext true
                }

                val bodies = bodiesByEpisodeId.values.toList()

                var hadErrors = false
                bodies
                    .chunked(UPSERT_BATCH_SIZE)
                    .forEach { chunk ->
                        val result = runCatching { aniLibertyApi.upsertUserViewTimecodes(chunk) }
                        if (result.isFailure) {
                            hadErrors = true
                            Timber.w(result.exceptionOrNull(), "UserViewsSync: upsert chunk failed (size=${chunk.size})")
                        }
                    }

                !hadErrors
            }

        /**
         * Import AniLiberty views history to local storage.
         *
         * What we do:
         * - update local EpisodeAccess (max progress wins)
         * - add missing releases to local history (remote-only import)
         *
         * @param maxPages how many pages to fetch (1-3 for light import, many for full import)
         */
        private suspend fun importRemoteHistoryToLocal(
            maxPages: Int,
            syncSessionStartedAtMs: Long,
        ): Boolean =
            withContext(AppDispatchers.io) {
                val dirtyLocalEpisodeIds =
                    syncHolder.getPendingUploads()
                        .mapTo(linkedSetOf()) { upload -> upload.toEpisodeId() }
                val initialLocalHistoryIds: Set<ReleaseId> =
                    runCatching { historyHolder.getIds().toSet() }
                        .onFailure { Timber.w(it, "UserViewsSync: failed to read local history ids") }
                        .getOrNull()
                        ?: return@withContext false

                // We'll compare against a mutable snapshot so that we don't depend on write ordering.
                val localEpisodeMap =
                    runCatching { episodesCheckerHolder.getEpisodes() }
                        .onFailure { Timber.w(it, "UserViewsSync: failed to read local episodes for import") }
                        .getOrNull()
                        ?.associateBy { it.id }
                        ?.toMutableMap()
                        ?: return@withContext false

                val pagesLoad =
                    loadHistoryPages(
                        maxPages = maxPages,
                        knownLocalHistoryIds = initialLocalHistoryIds,
                        knownLocalEpisodeIds = localEpisodeMap.keys,
                    ) ?: return@withContext false

                if (pagesLoad.pages.isEmpty()) {
                    Timber.d(
                        "UserViewsSync.import summary maxPages=%d pagesScanned=%d stopReason=%s (empty payload)",
                        maxPages,
                        pagesLoad.pagesScanned,
                        pagesLoad.stopReason,
                    )
                    return@withContext true
                }

                val importStartedAtMs = nowMs()
                val updatesByEpisodeId = linkedMapOf<EpisodeId, EpisodeAccess>()
                val missingReleaseIdsOrdered = LinkedHashSet<ReleaseId>()

                var processedItems = 0
                var skippedLowProgress = 0
                var skippedMalformed = 0
                var updateTouches = 0
                var missingReleaseTouches = 0

                // Process from oldest to newest to keep latest item as the final state.
                pagesLoad.pages.asReversed().forEach { page ->
                    page.data.asReversed().forEach { item ->
                        processedItems += 1

                        val episodeId =
                            item.extractEpisodeId() ?: run {
                                skippedMalformed += 1
                                return@forEach
                            }
                        val releaseId = episodeId.releaseId

                        // Remote progress
                        val durationMs = item.episode?.duration?.let(::secondsToMs)
                        val remoteIsWatched = item.isWatched == true
                        val remoteTimeMs = item.time?.let(::secondsToMs) ?: 0L
                        val remoteSeekMs =
                            if (remoteIsWatched && durationMs != null && durationMs > 0L) {
                                durationMs
                            } else {
                                remoteTimeMs
                            }

                        if (!remoteIsWatched && remoteSeekMs < MIN_IMPORT_POSITION_MS) {
                            skippedLowProgress += 1
                            return@forEach
                        }

                        // Remote-only release import (do not reorder existing local history)
                        if (releaseId !in initialLocalHistoryIds) {
                            // "move to end" semantics: last occurrence wins
                            missingReleaseIdsOrdered.remove(releaseId)
                            missingReleaseIdsOrdered.add(releaseId)
                            missingReleaseTouches += 1
                        }

                        // Merge with local
                        val local = localEpisodeMap[episodeId]
                        val remoteTimestamp = extractRemoteTimestamp(item)
                        val remoteProgress =
                            RemoteEpisodeProgress(
                                seekMs = remoteSeekMs,
                                isWatched = remoteIsWatched,
                                durationMs = durationMs,
                                lastAccessMs = remoteTimestamp.lastAccessMs,
                                timestampTrusted = remoteTimestamp.isTrusted,
                            )
                        val merged =
                            mergeEpisodeProgress(
                                episodeId = episodeId,
                                local = local,
                                localHasPendingUpload = episodeId in dirtyLocalEpisodeIds,
                                remote = remoteProgress,
                                syncSessionStartedAtMs = syncSessionStartedAtMs,
                            ) ?: return@forEach

                        if (merged != local) {
                            updatesByEpisodeId[episodeId] = merged
                            localEpisodeMap[episodeId] = merged
                            updateTouches += 1
                        }
                    }
                }

                val updates = updatesByEpisodeId.values.toList()
                if (updates.isNotEmpty()) {
                    val saveStartedAtMs = nowMs()
                    val ok =
                        runCatching {
                            episodesCheckerHolder.putAllEpisodeBatched(
                                episodes = updates,
                                batchSize = SYNC_EPISODE_WRITE_BATCH_SIZE,
                                saveEveryBatches = SYNC_BULK_SAVE_EVERY_BATCHES,
                            )
                        }
                            .onFailure { Timber.w(it, "UserViewsSync: failed to update local episodes") }
                            .isSuccess
                    if (!ok) return@withContext false

                    Timber.d(
                        "UserViewsSync.import saveEpisodes count=%d batchSize=%d saveEvery=%d durationMs=%d",
                        updates.size,
                        SYNC_EPISODE_WRITE_BATCH_SIZE,
                        SYNC_BULK_SAVE_EVERY_BATCHES,
                        nowMs() - saveStartedAtMs,
                    )
                }

                val missingReleaseIds = missingReleaseIdsOrdered.toList()
                if (missingReleaseIds.isNotEmpty()) {
                    val saveStartedAtMs = nowMs()
                    val ok =
                        runCatching {
                            historyHolder.putAllIdsBatched(
                                ids = missingReleaseIds,
                                batchSize = SYNC_HISTORY_WRITE_BATCH_SIZE,
                                saveEveryBatches = SYNC_BULK_SAVE_EVERY_BATCHES,
                            )
                        }
                            .onFailure { Timber.w(it, "UserViewsSync: failed to update local history (remote-only import)") }
                            .isSuccess
                    if (!ok) return@withContext false

                    Timber.d(
                        "UserViewsSync.import saveHistory count=%d batchSize=%d saveEvery=%d durationMs=%d",
                        missingReleaseIds.size,
                        SYNC_HISTORY_WRITE_BATCH_SIZE,
                        SYNC_BULK_SAVE_EVERY_BATCHES,
                        nowMs() - saveStartedAtMs,
                    )
                }

                Timber.d(
                    "UserViewsSync.import summary maxPages=%d pagesScanned=%d itemsScanned=%d newMarkers=%d duplicateMarkers=%d noNewMarkerStreak=%d noNewLocalTargetStreak=%d processedItems=%d skippedLowProgress=%d skippedMalformed=%d updates=%d updateTouches=%d missingReleaseIds=%d missingReleaseTouches=%d stopReason=%s durationMs=%d",
                    maxPages,
                    pagesLoad.pagesScanned,
                    pagesLoad.itemsScanned,
                    pagesLoad.newMarkers,
                    pagesLoad.duplicateMarkers,
                    pagesLoad.noNewMarkerStreak,
                    pagesLoad.noNewLocalTargetStreak,
                    processedItems,
                    skippedLowProgress,
                    skippedMalformed,
                    updates.size,
                    updateTouches,
                    missingReleaseIds.size,
                    missingReleaseTouches,
                    pagesLoad.stopReason,
                    nowMs() - importStartedAtMs,
                )

                true
            }

        private fun mergeEpisodeProgress(
            episodeId: EpisodeId,
            local: EpisodeAccess?,
            localHasPendingUpload: Boolean,
            remote: RemoteEpisodeProgress,
            syncSessionStartedAtMs: Long,
        ): EpisodeAccess? {
            val localSeekMs = local?.seek ?: 0L
            val localLastAccessMs = local?.lastAccessRaw ?: 0L
            val remoteSeekMs = remote.seekMs
            val remoteIsWatched = remote.isWatched
            val durationMs = remote.durationMs
            val remoteLastAccessMs = remote.lastAccessMs
            val remoteTimestampTrusted = remote.timestampTrusted

            if (localHasPendingUpload && local != null) {
                logMergeDecision(
                    episodeId = episodeId,
                    local = local,
                    remote = remote,
                    winner = "local",
                    reason = "local_pending_upload",
                )
                return local
            }

            // If remote has no meaningful data and local exists - keep local.
            if (!remoteIsWatched && remoteSeekMs <= 0L) {
                logMergeDecision(
                    episodeId = episodeId,
                    local = local,
                    remote = remote,
                    winner = if (local == null) "skip" else "local",
                    reason = "remote_empty_progress",
                )
                return local
            }

            if (local != null && localLastAccessMs > syncSessionStartedAtMs) {
                logMergeDecision(
                    episodeId = episodeId,
                    local = local,
                    remote = remote,
                    winner = "local",
                    reason = "local_modified_after_sync_start",
                )
                return local
            }

            if (
                local != null &&
                localLastAccessMs > 0L &&
                remoteTimestampTrusted &&
                remoteLastAccessMs != null &&
                remoteLastAccessMs + REMOTE_TIMESTAMP_DRIFT_TOLERANCE_MS < localLastAccessMs
            ) {
                logMergeDecision(
                    episodeId = episodeId,
                    local = local,
                    remote = remote.copy(timestampTrusted = true),
                    winner = "local",
                    reason = "remote_timestamp_older_than_local",
                )
                return local
            }

            val localIsWatched = local?.let { isLocalWatched(it, durationMs) } ?: false

            val remoteComparable =
                when {
                    remoteIsWatched && durationMs != null && durationMs > 0L -> durationMs
                    else -> remoteSeekMs
                }

            val localComparable =
                when {
                    localIsWatched && durationMs != null && durationMs > 0L -> durationMs
                    else -> localSeekMs
                }

            val remoteBetter =
                when {
                    remoteIsWatched && !localIsWatched -> true
                    !remoteIsWatched && localIsWatched -> false
                    else -> remoteComparable > localComparable + MIN_PROGRESS_DELTA_MS
                }

            if (!remoteBetter) {
                logMergeDecision(
                    episodeId = episodeId,
                    local = local,
                    remote = remote,
                    winner = if (local == null) "skip" else "local",
                    reason = "progress_not_better",
                )
                return local
            }

            val mergedSeekMs =
                when {
                    remoteIsWatched && durationMs != null && durationMs > 0L -> durationMs
                    else -> maxOf(localSeekMs, remoteSeekMs)
                }

            val remoteFreshnessMs =
                when {
                    remoteTimestampTrusted && remoteLastAccessMs != null -> remoteLastAccessMs
                    local != null -> localLastAccessMs
                    else -> syncSessionStartedAtMs
                }
            val mergedLastAccess = maxOf(localLastAccessMs, remoteFreshnessMs)

            val merged =
                EpisodeAccess(
                    id = episodeId,
                    seek = mergedSeekMs,
                    isViewed = true,
                    lastAccess = mergedLastAccess,
                )

            logMergeDecision(
                episodeId = episodeId,
                local = local,
                remote = remote,
                winner = "remote",
                reason = "remote_progress_selected",
            )

            return merged
        }

        private suspend fun loadHistoryPages(
            maxPages: Int,
            knownLocalHistoryIds: Set<ReleaseId>,
            knownLocalEpisodeIds: Set<EpisodeId>,
        ): HistoryPagesLoadResult? {
            val result = mutableListOf<PaginatedResponse<AniLibertyUserViewHistoryItem>>()
            val seenItemMarkers = HashSet<String>()
            val seenPageHeadMarkers = HashSet<String>()
            val knownHistoryIds = knownLocalHistoryIds.toMutableSet()
            val knownEpisodeIds = knownLocalEpisodeIds.toMutableSet()

            var pagesScanned = 0
            var itemsScanned = 0
            var newMarkers = 0
            var duplicateMarkers = 0
            var noNewMarkerStreak = 0
            var noNewLocalTargetStreak = 0
            var stopReason = "max_pages_reached"

            var page = 1
            while (page <= maxPages) {
                val response =
                    runCatching {
                        aniLibertyApi.getUserViewsHistory(
                            page = page,
                            limit = MAX_USER_VIEWS_HISTORY_LIMIT,
                            // Keep history import on the unfiltered contract: live include/exclude usage
                            // can drop nested release data that the merge path relies on.
                            // This endpoint is eventual-consistency input for background import only;
                            // it must not become immediate TV read-after-write truth.
                            fields = null,
                        )
                    }.onFailure {
                        Timber.w(it, "UserViewsSync: failed to load views history page=$page")
                    }.getOrNull() ?: return null

                if (response.data.isEmpty()) {
                    stopReason = "empty_page"
                    break
                }

                val pageHeadMarker = buildHistoryPageHeadMarker(response.data.firstOrNull())
                if (pageHeadMarker != null && !seenPageHeadMarkers.add(pageHeadMarker)) {
                    stopReason = "repeated_page_head"
                    Timber.d(
                        "UserViewsSync.history page=%d repeated head marker detected, stop loading",
                        page,
                    )
                    break
                }

                var pageNewMarkers = 0
                var pageDuplicateMarkers = 0
                var pageNewHistoryIds = 0
                var pageNewEpisodeIds = 0
                response.data.forEach { item ->
                    itemsScanned += 1

                    buildHistoryItemMarker(item)?.let { marker ->
                        if (seenItemMarkers.add(marker)) {
                            pageNewMarkers += 1
                        } else {
                            pageDuplicateMarkers += 1
                        }
                    }

                    item.extractReleaseId()
                        ?.let { releaseId ->
                            if (knownHistoryIds.add(releaseId)) {
                                pageNewHistoryIds += 1
                            }
                        }

                    item.extractEpisodeId()
                        ?.let { episodeId ->
                            if (knownEpisodeIds.add(episodeId)) {
                                pageNewEpisodeIds += 1
                            }
                        }
                }

                newMarkers += pageNewMarkers
                duplicateMarkers += pageDuplicateMarkers
                // Guard against backend pagination loops or "stuck" pages that keep repeating old history.
                noNewMarkerStreak = if (pageNewMarkers == 0) noNewMarkerStreak + 1 else 0
                // If we do not discover any history IDs/episodes unknown to local for several pages,
                // deep paging becomes mostly redundant for incremental sync.
                val hasNewLocalTargets = pageNewHistoryIds > 0 || pageNewEpisodeIds > 0
                noNewLocalTargetStreak = if (hasNewLocalTargets) 0 else noNewLocalTargetStreak + 1

                pagesScanned += 1
                result.add(response)

                val allPages = response.meta.allPages
                Timber.d(
                    "UserViewsSync.history page=%d/%s items=%d newMarkers=%d duplicateMarkers=%d newHistoryIds=%d newEpisodeIds=%d noNewMarkerStreak=%d noNewLocalTargetStreak=%d",
                    page,
                    allPages?.toString() ?: "?",
                    response.data.size,
                    pageNewMarkers,
                    pageDuplicateMarkers,
                    pageNewHistoryIds,
                    pageNewEpisodeIds,
                    noNewMarkerStreak,
                    noNewLocalTargetStreak,
                )

                if (allPages != null && page >= allPages) {
                    stopReason = "meta_last_page"
                    break
                }
                if (allPages == null && response.data.size < MAX_USER_VIEWS_HISTORY_LIMIT) {
                    stopReason = "short_page"
                    break
                }
                if (noNewMarkerStreak >= HISTORY_STOP_ON_NO_NEW_MARKER_PAGES) {
                    stopReason = "no_new_markers"
                    break
                }
                if (noNewLocalTargetStreak >= HISTORY_STOP_ON_NO_NEW_LOCAL_TARGET_PAGES) {
                    stopReason = "no_new_local_targets"
                    break
                }

                page++
            }

            Timber.d(
                "UserViewsSync.history summary maxPages=%d pagesScanned=%d itemsScanned=%d newMarkers=%d duplicateMarkers=%d noNewMarkerStreak=%d noNewLocalTargetStreak=%d stopReason=%s",
                maxPages,
                pagesScanned,
                itemsScanned,
                newMarkers,
                duplicateMarkers,
                noNewMarkerStreak,
                noNewLocalTargetStreak,
                stopReason,
            )

            return HistoryPagesLoadResult(
                pages = result,
                stopReason = stopReason,
                pagesScanned = pagesScanned,
                itemsScanned = itemsScanned,
                newMarkers = newMarkers,
                duplicateMarkers = duplicateMarkers,
                noNewMarkerStreak = noNewMarkerStreak,
                noNewLocalTargetStreak = noNewLocalTargetStreak,
            )
        }

        private suspend fun getReleaseEpisodesCacheOrNull(releaseId: ReleaseId): ReleaseEpisodesCache? {
            releaseEpisodesMutex.withLock {
                releaseEpisodesCache[releaseId]?.also { return it }
            }

            val fieldsForEpisodes =
                AniLibertyReleaseFields(
                    exclude =
                        setOf(
                            AniLibertyReleaseExclude.MEMBERS,
                            AniLibertyReleaseExclude.TORRENTS,
                        ),
                    excludeRaw =
                        setOf(
                            AniLibertyFieldName("description"),
                            AniLibertyFieldName("notification"),
                        ),
                )

            val release =
                runCatching {
                    aniLibertyApi.getRelease(
                        key = AniLibertyReleaseKey.id(releaseId.id),
                        fields = fieldsForEpisodes,
                    )
                }.onFailure {
                    Timber.w(it, "UserViewsSync: failed to load release episodes for releaseId=${releaseId.id}")
                }.getOrNull() ?: return null

            val episodes =
                release.episodes.orEmpty()
                    .mapNotNull { ep ->
                        val id = ep.id ?: return@mapNotNull null
                        val ordinal = ep.ordinal ?: ep.sortOrder ?: return@mapNotNull null
                        val ordinalKey = normalizeOrdinalDouble(ordinal)

                        EpisodeMeta(
                            ordinalKey = ordinalKey,
                            episodeId = id,
                            durationMs = ep.duration?.let(::secondsToMs),
                        )
                    }

            if (episodes.isEmpty()) return null

            val cache =
                ReleaseEpisodesCache(
                    byOrdinal = episodes.associate { it.ordinalKey to it.episodeId },
                    durationByOrdinalMs =
                        episodes.mapNotNull { meta ->
                            meta.durationMs?.let { meta.ordinalKey to it }
                        }.toMap(),
                )

            releaseEpisodesMutex.withLock {
                releaseEpisodesCache[releaseId] = cache
            }

            return cache
        }

        private fun shouldUpload(
            localIsWatched: Boolean,
            localSeekMs: Long,
            remote: RemoteTimecode?,
        ): Boolean {
            if (remote == null) return true

            return when {
                localIsWatched && !remote.isWatched -> true
                !localIsWatched && remote.isWatched -> false
                localIsWatched && remote.isWatched -> false
                else -> localSeekMs > remote.positionMs + MIN_PROGRESS_DELTA_MS
            }
        }

        /**
         * When local storage contains duplicated entries for the same AniLiberty episode (for example "1" vs "1.0"),
         * we keep the "best" upsert body:
         * - watched wins over not-watched
         * - otherwise bigger time wins
         */
        private fun isUpsertBodyBetter(
            candidate: AniLibertyUserViewTimecodeUpsertBody,
            current: AniLibertyUserViewTimecodeUpsertBody,
        ): Boolean {
            return when {
                candidate.isWatched && !current.isWatched -> true
                !candidate.isWatched && current.isWatched -> false
                candidate.isWatched && current.isWatched -> false
                else -> candidate.time > current.time + 0.001
            }
        }

        /**
         * Local "watched" heuristics:
         * - normal case: seek is within [tolerance] from duration
         * - legacy/manual case: isViewed=true and seek=0 and lastAccess=0 (mark-all-viewed in old builds)
         */
        private fun isLocalWatched(
            access: EpisodeAccess,
            durationMs: Long?,
        ): Boolean {
            if (!access.isViewed) return false

            // Old "mark as watched": isViewed=true, seek=0, lastAccess=0
            if (access.seek <= 0L && access.lastAccessRaw <= 0L) return true

            val duration = durationMs ?: return false
            return isNearEpisodeEnd(
                positionMs = access.seek,
                durationMs = duration,
            )
        }

        private fun normalizeOrdinalString(value: String): String =
            runCatching {
                BigDecimal(value.trim()).stripTrailingZeros().toPlainString()
            }.getOrElse { value.trim() }

        private fun normalizeOrdinalDouble(value: Double): String =
            runCatching {
                BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
            }.getOrElse { value.toString() }

        private fun msToSeconds(ms: Long): Double = BigDecimal(ms).divide(BigDecimal(1000), 3, RoundingMode.HALF_UP).toDouble()

        private fun secondsToMs(seconds: Double): Long = (seconds * 1000.0).roundToLong()

        private fun sha256(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            return buildString(bytes.size * 2) {
                bytes.forEach { b ->
                    append(((b.toInt() and 0xFF).toString(16)).padStart(2, '0'))
                }
            }
        }

        /**
         * Parses ISO-8601-like timestamps (createdAt/updatedAt from AniLiberty).
         *
         * We avoid java.time direct calls because minSdk is 21 and coreLibraryDesugaring is not enabled here.
         */
        private fun parseTimestampMs(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            val trimmed = value.trim()

            // 1) Try java.time via reflection (works on API 26+ or if desugaring is enabled in the future)
            javaTimeInstantParser?.let { parser ->
                runCatching {
                    val instant = parser.parseMethod.invoke(null, trimmed)
                    (parser.toEpochMilliMethod.invoke(instant) as Long)
                }.getOrNull()?.let { return it }
            }

            // 2) Fallback: SimpleDateFormat with a normalized offset (+0300 / +0000).
            val normalized = normalizeIso8601ForSdf(trimmed)
            return parseWithSdf(normalized)
        }

        private fun parseWithSdf(value: String): Long? {
            // Thread-safe approach: create new formatter per call.
            SIMPLE_DATE_PATTERNS.forEach { pattern ->
                val formatter =
                    SimpleDateFormat(pattern, Locale.US).apply {
                        isLenient = false
                    }
                runCatching { formatter.parse(value)?.time }
                    .getOrNull()
                    ?.let { return it }
            }
            return null
        }

        private fun extractRemoteTimestamp(item: AniLibertyUserViewHistoryItem): RemoteTimestampInfo {
            parseTimestampMs(item.updatedAt)?.let {
                return RemoteTimestampInfo(lastAccessMs = it, isTrusted = true)
            }
            parseTimestampMs(item.createdAt)?.let {
                return RemoteTimestampInfo(lastAccessMs = it, isTrusted = true)
            }
            return RemoteTimestampInfo(lastAccessMs = null, isTrusted = false)
        }

        private fun logMergeDecision(
            episodeId: EpisodeId,
            local: EpisodeAccess?,
            remote: RemoteEpisodeProgress,
            winner: String,
            reason: String,
        ) {
            Timber.d(
                "UserViewsSync.merge episodeId=%s localSeekMs=%d remoteSeekMs=%d localViewed=%s remoteViewed=%s localLastAccessMs=%d remoteLastAccessMs=%d remoteTimestampTrusted=%s winner=%s reason=%s",
                episodeId,
                local?.seek ?: 0L,
                remote.seekMs,
                local?.isViewed == true,
                remote.isWatched,
                local?.lastAccessRaw ?: 0L,
                remote.lastAccessMs ?: -1L,
                remote.timestampTrusted,
                winner,
                reason,
            )
        }

        private fun normalizeIso8601ForSdf(value: String): String {
            var s = value

            // Normalize fractional seconds to 3 digits (SimpleDateFormat uses .SSS)
            s = normalizeFractionalSeconds(s)

            // Convert trailing Z to RFC822 timezone
            if (s.endsWith("Z")) {
                s = s.dropLast(1) + "+0000"
            }

            // Convert "+03:00" to "+0300"
            s = s.replace(TZ_COLON_REGEX, "$1$2")

            // If timezone is still missing, assume UTC.
            if (!TZ_RFC822_REGEX.containsMatchIn(s)) {
                s += "+0000"
            }

            return s
        }

        private fun normalizeFractionalSeconds(value: String): String {
            val match = FRACTION_REGEX.find(value) ?: return value
            val fraction = match.groupValues[1]
            val normalized =
                when {
                    fraction.length == 3 -> fraction
                    fraction.length < 3 -> fraction.padEnd(3, '0')
                    else -> fraction.substring(0, 3)
                }
            return value.replaceRange(match.range, ".$normalized")
        }

        private fun buildHistoryPageHeadMarker(item: AniLibertyUserViewHistoryItem?): String? {
            item ?: return null
            return buildHistoryItemMarker(item)
                ?: item.updatedAt
                ?: item.createdAt
        }

        private fun buildHistoryItemMarker(item: AniLibertyUserViewHistoryItem): String? {
            val episodeId = item.extractEpisodeId() ?: return null
            val watchedFlag = if (item.isWatched == true) "1" else "0"
            val timeMs = item.time?.let(::secondsToMs) ?: 0L
            return buildString(96) {
                append(episodeId.releaseId.id)
                append('|')
                append(episodeId.id)
                append('|')
                append(timeMs)
                append('|')
                append(watchedFlag)
                append('|')
                append(item.updatedAt ?: "")
                append('|')
                append(item.createdAt ?: "")
            }
        }

        private fun AniLibertyUserViewHistoryItem.extractReleaseId(): ReleaseId? {
            val id =
                releaseId?.value
                    ?: release?.id?.value
                    ?: episode?.releaseId?.value
            return id?.let(::ReleaseId)
        }

        private fun AniLibertyUserViewHistoryItem.extractEpisodeId(): EpisodeId? {
            val releaseId = extractReleaseId() ?: return null
            val ordinal = episode?.ordinal ?: episode?.sortOrder ?: return null
            return EpisodeId(
                id = normalizeOrdinalDouble(ordinal),
                releaseId = releaseId,
            )
        }

        private data class ReleaseEpisodesCache(
            val byOrdinal: Map<String, AniLibertyReleaseEpisodeId>,
            val durationByOrdinalMs: Map<String, Long>,
        )

        private data class EpisodeMeta(
            val ordinalKey: String,
            val episodeId: AniLibertyReleaseEpisodeId,
            val durationMs: Long?,
        )

        private data class RemoteTimecode(
            val positionMs: Long,
            val isWatched: Boolean,
        )

        private data class RemoteTimestampInfo(
            val lastAccessMs: Long?,
            val isTrusted: Boolean,
        )

        private data class RemoteEpisodeProgress(
            val seekMs: Long,
            val isWatched: Boolean,
            val durationMs: Long?,
            val lastAccessMs: Long?,
            val timestampTrusted: Boolean,
        )

        private data class HistoryPagesLoadResult(
            val pages: List<PaginatedResponse<AniLibertyUserViewHistoryItem>>,
            val stopReason: String,
            val pagesScanned: Int,
            val itemsScanned: Int,
            val newMarkers: Int,
            val duplicateMarkers: Int,
            val noNewMarkerStreak: Int,
            val noNewLocalTargetStreak: Int,
        )

        private data class JavaTimeInstantParser(
            val parseMethod: java.lang.reflect.Method,
            val toEpochMilliMethod: java.lang.reflect.Method,
        )

        private fun nowMs(): Long = System.currentTimeMillis()

        private companion object {
            private const val LIGHT_IMPORT_PAGES = 3

            /**
             * Full import safety cap.
             * We also stop earlier when history pages become stable (no new markers/local targets).
             */
            private const val MAX_HISTORY_PAGES = 50
            private const val HISTORY_STOP_ON_NO_NEW_MARKER_PAGES = 2
            private const val HISTORY_STOP_ON_NO_NEW_LOCAL_TARGET_PAGES = 4

            private const val SYNC_EPISODE_WRITE_BATCH_SIZE = 250
            private const val SYNC_HISTORY_WRITE_BATCH_SIZE = 250
            private const val SYNC_BULK_SAVE_EVERY_BATCHES = 4

            private const val UPSERT_BATCH_SIZE = 100

            private const val MIN_UPLOAD_POSITION_MS = 5_000L
            private const val MIN_IMPORT_POSITION_MS = 5_000L
            private const val MIN_PROGRESS_DELTA_MS = 1_000L
            private const val REMOTE_TIMESTAMP_DRIFT_TOLERANCE_MS = 5_000L
            private const val SCHEDULE_SYNC_DEBOUNCE_MS = 1_500L

            private val SIMPLE_DATE_PATTERNS =
                arrayOf(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                    "yyyy-MM-dd'T'HH:mm:ssZ",
                )

            private val TZ_COLON_REGEX = Regex("([+-]\\d\\d):(\\d\\d)$")
            private val TZ_RFC822_REGEX = Regex("([+-]\\d\\d\\d\\d)$")
            private val FRACTION_REGEX = Regex("\\.(\\d{1,9})(?=[Z+-]|$)")

            private val javaTimeInstantParser: JavaTimeInstantParser? by lazy {
                runCatching {
                    val cls = Class.forName("java.time.Instant")
                    val parseMethod = cls.getMethod("parse", String::class.java)
                    val toEpochMilliMethod = cls.getMethod("toEpochMilli")
                    JavaTimeInstantParser(parseMethod, toEpochMilliMethod)
                }.getOrNull()
            }
        }
    }
