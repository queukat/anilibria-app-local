package ru.radiationx.data.repository

import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.holders.UserViewsSyncHolder
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFieldName
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseExclude
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyUserViewHistoryItem
import ru.radiationx.data.datasource.remote.aniliberty.MAX_USER_VIEWS_HISTORY_LIMIT
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeDeleteBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import ru.radiationx.data.entity.domain.watching.UserViewPendingUpload
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.system.ApplicationCoroutineScope
import ru.radiationx.shared.ktx.coroutines.AppDispatchers
import timber.log.Timber

/**
 * Repository for AniLiberty user views:
 * - timecodes (progress per episode)
 * - views history (recently watched)
 *
 * This repository is intentionally **best-effort**:
 * - if user isn't authenticated
 * - or backend is temporarily unavailable
 *
 * ...methods should not crash UI flows.
 */
class UserViewsRepository
    @Inject
    constructor(
        private val aniLibertyApi: AniLibertyApi,
        private val syncHolder: UserViewsSyncHolder,
        private val applicationScope: ApplicationCoroutineScope,
    ) {
        data class EpisodeTimecode(
            /** ExoPlayer position in ms */
            val positionMs: Long,
            /** backend "watched/finished" flag */
            val isWatched: Boolean,
        )

        /**
         * Cache per release:
         * - map "episode ordinal string" -> AniLiberty episode id (used by /accounts/users/me/views/timecodes)
         * - list of all AniLiberty episode ids for bulk operations
         */
        private data class ReleaseEpisodesCache(
            val byOrdinal: Map<String, AniLibertyReleaseEpisodeId>,
            val ordinalByEpisodeId: Map<AniLibertyReleaseEpisodeId, String>,
            val allIds: List<AniLibertyReleaseEpisodeId>,
        )

        private val cache = mutableMapOf<ReleaseId, ReleaseEpisodesCache>()

        // Timecodes cache (all user timecodes; backend endpoint doesn't support filtering by release).
        private val timecodesMutex = Mutex()
        private var timecodesCache: Map<AniLibertyReleaseEpisodeId, EpisodeTimecode> = emptyMap()
        private var timecodesLastSyncMs: Long = 0L
        private val pendingUploadsMutex = Mutex()
        private val pendingUploadsScheduleLock = Any()
        private var pendingUploadsJob: Job? = null

        /**
         * Fetch user views history from AniLiberty.
         *
         * Best-effort import/fallback data only.
         *
         * Live AniLiberty history lags roughly 25-30 seconds behind POST/DELETE writes,
         * so TV UI must not use it as immediate read-after-write truth.
         */
        suspend fun getViewsHistory(
            page: Int,
            limit: Int,
        ): PaginatedResponse<UserViewHistoryItem> =
            withContext(AppDispatchers.io) {
                val safeLimit = limit.coerceIn(1, MAX_USER_VIEWS_HISTORY_LIMIT)
                val response =
                    aniLibertyApi.getUserViewsHistory(
                        page = page,
                        limit = safeLimit,
                        // Live AniLiberty returns the nested release payload unreliably when include/exclude
                        // are applied here, so keep the raw contract for watch-sync/history reads.
                        fields = null,
                    )
                PaginatedResponse(
                    data = response.data.mapNotNull { it.toDomainOrNull() },
                    meta = response.meta,
                )
            }

        /**
         * Try to find the latest watched/continue episode for a given release using user views history.
         *
         * This is a best-effort remote restore helper.
         * It must not become the immediate TV resume source because AniLiberty history is delayed.
         */
        suspend fun findLatestEpisodeIdForRelease(
            releaseId: ReleaseId,
            maxPages: Int = DEFAULT_HISTORY_LOOKUP_PAGES,
            limit: Int = DEFAULT_HISTORY_LOOKUP_LIMIT,
        ): EpisodeId? =
            findLatestEpisodeIdForReleaseInternal(
                releaseId = releaseId,
                includeWatched = true,
                maxPages = maxPages,
                limit = limit,
            )

        /**
         * Same as [findLatestEpisodeIdForRelease], but skips fully watched entries.
         */
        suspend fun findLatestNotWatchedEpisodeIdForRelease(
            releaseId: ReleaseId,
            maxPages: Int = DEFAULT_HISTORY_LOOKUP_PAGES,
            limit: Int = DEFAULT_HISTORY_LOOKUP_LIMIT,
        ): EpisodeId? =
            findLatestEpisodeIdForReleaseInternal(
                releaseId = releaseId,
                includeWatched = false,
                maxPages = maxPages,
                limit = limit,
            )

        /**
         * Load remote timecode for a single episode.
         *
         * This enables "continue on another device" as a best-effort fallback.
         *
         * Important:
         * - local progress remains the primary source for TV UX
         * - this intentionally reads the global `/accounts/users/me/views/timecodes` snapshot
         * - release/history/per-episode endpoints lag behind live writes and are not suitable
         *   as immediate read-after-write truth for TV resume
         */
        suspend fun getEpisodeTimecode(episodeId: EpisodeId): EpisodeTimecode? {
            val aniEpisodeId = resolveAniEpisodeIdOrNull(episodeId) ?: return null
            val snapshot = getTimecodesSnapshot()
            return snapshot[aniEpisodeId]
        }

        /**
         * Resolve human-readable episode ordinal for an [EpisodeId].
         *
         * Useful when local episode id is AniLiberty UUID and UI needs "series N" label.
         */
        suspend fun resolveEpisodeOrdinal(episodeId: EpisodeId): String? =
            withContext(AppDispatchers.io) {
                normalizeOrdinalStringOrNull(episodeId.id)?.let { return@withContext it }

                val releaseCache = getReleaseEpisodesCacheOrNull(episodeId.releaseId) ?: return@withContext null
                val aniEpisodeId =
                    runCatching { AniLibertyReleaseEpisodeId(episodeId.id) }.getOrNull()
                        ?: return@withContext null

                releaseCache.ordinalByEpisodeId[aniEpisodeId]
            }

        /**
         * Save user progress (timecode) for an episode.
         *
         * Best-effort: swallows network/auth errors.
         *
         * @param positionMs ExoPlayer position in ms
         * @param isWatched  "watched/finished" flag
         */
        suspend fun upsertEpisodeTimecode(
            episodeId: EpisodeId,
            positionMs: Long,
            isWatched: Boolean,
        ) = withContext(AppDispatchers.io) {
            if (positionMs < 0) return@withContext

            syncHolder.upsertPendingUpload(
                UserViewPendingUpload.create(
                    episodeId = episodeId,
                    positionMs = positionMs,
                    isWatched = isWatched,
                ),
            )
            schedulePendingUploads(reason = "episode_progress")
        }

        /**
         * Remove progress for an episode (unview/reset).
         *
         * Best-effort: swallows network/auth errors.
         */
        suspend fun deleteEpisodeTimecode(episodeId: EpisodeId) =
            withContext(AppDispatchers.io) {
                val aniEpisodeId = resolveAniEpisodeIdOrNull(episodeId) ?: return@withContext
                val body = AniLibertyUserViewTimecodeDeleteBody.from(aniEpisodeId)
                syncHolder.removePendingUploadsByEpisodeIds(listOf(episodeId))
                removeTimecodeCache(aniEpisodeId)
                applicationScope.launch {
                    runCatching { aniLibertyApi.deleteUserViewTimecodes(listOf(body)) }
                        .onFailure { error ->
                            Timber.w(error, "UserViewsRepository: failed to delete remote timecode for $episodeId")
                        }
                }
            }

        /**
         * Clear all timecodes for a release.
         *
         * Best-effort: swallows network/auth errors.
         */
        suspend fun deleteAllTimecodesForRelease(releaseId: ReleaseId) =
            withContext(AppDispatchers.io) {
                syncHolder.removePendingUploadsByReleaseId(releaseId)
                cache[releaseId]?.allIds?.let { allIds ->
                    removeTimecodeCache(allIds)
                }
                applicationScope.launch {
                    val releaseCache = getReleaseEpisodesCacheOrNull(releaseId) ?: return@launch
                    if (releaseCache.allIds.isEmpty()) return@launch
                    val bodies = releaseCache.allIds.map { AniLibertyUserViewTimecodeDeleteBody.from(it) }
                    runCatching { aniLibertyApi.deleteUserViewTimecodes(bodies) }
                        .onFailure { error ->
                            Timber.w(error, "UserViewsRepository: failed to clear remote timecodes for releaseId=%s", releaseId.id)
                        }
                }
            }

        /**
         * Mark all episodes in a release as watched.
         *
         * Best-effort: swallows network/auth errors.
         */
        suspend fun markAllWatchedForRelease(releaseId: ReleaseId) =
            withContext(AppDispatchers.io) {
                syncHolder.removePendingUploadsByReleaseId(releaseId)
                cache[releaseId]?.allIds?.takeIf { allIds -> allIds.isNotEmpty() }?.let { allIds ->
                    updateTimecodeCache(
                        allIds.associateWith {
                            EpisodeTimecode(
                                positionMs = 0L,
                                isWatched = true,
                            )
                        },
                    )
                }
                applicationScope.launch {
                    val releaseCache = getReleaseEpisodesCacheOrNull(releaseId) ?: return@launch
                    if (releaseCache.allIds.isEmpty()) return@launch
                    val bodies =
                        releaseCache.allIds.map {
                            AniLibertyUserViewTimecodeUpsertBody.from(
                                time = 0.0,
                                isWatched = true,
                                releaseEpisodeId = it,
                            )
                        }
                    runCatching { aniLibertyApi.upsertUserViewTimecodes(bodies) }
                        .onFailure { error ->
                            Timber.w(error, "UserViewsRepository: failed to mark release as watched remotely releaseId=%s", releaseId.id)
                        }
                }
            }

        suspend fun flushPendingUploads(reason: String = "manual") =
            withContext(AppDispatchers.io) {
                pendingUploadsMutex.withLock {
                    val pendingUploads =
                        syncHolder.getPendingUploads()
                            .sortedBy { upload -> upload.updatedAtMs }
                    if (pendingUploads.isEmpty()) {
                        return@withLock
                    }

                    val groupedUploads = linkedMapOf<String, PendingUploadGroup>()

                    pendingUploads.forEach { upload ->
                        val episodeId = upload.toEpisodeId()
                        val aniEpisodeId = resolveAniEpisodeIdOrNull(episodeId) ?: return@forEach
                        val remotePositionMs = if (upload.isWatched) 0L else upload.positionMs.coerceAtLeast(0L)
                        val body =
                            AniLibertyUserViewTimecodeUpsertBody.from(
                                time = msToSeconds(remotePositionMs),
                                isWatched = upload.isWatched,
                                releaseEpisodeId = aniEpisodeId,
                            )
                        val existing = groupedUploads[body.releaseEpisodeId]
                        if (existing == null) {
                            groupedUploads[body.releaseEpisodeId] =
                                PendingUploadGroup(
                                    upload = upload,
                                    body = body,
                                    episodeIds = linkedSetOf(episodeId),
                                )
                        } else {
                            existing.episodeIds += episodeId
                            if (shouldReplacePendingUpload(candidate = upload, current = existing.upload)) {
                                groupedUploads[body.releaseEpisodeId] =
                                    existing.copy(
                                        upload = upload,
                                        body = body,
                                    )
                            }
                        }
                    }

                    if (groupedUploads.isEmpty()) {
                        return@withLock
                    }

                    groupedUploads.values
                        .chunked(UPSERT_BATCH_SIZE)
                        .forEach { chunk ->
                            val result =
                                runCatching {
                                    aniLibertyApi.upsertUserViewTimecodes(chunk.map { group -> group.body })
                                }
                            if (result.isSuccess) {
                                syncHolder.removePendingUploadsByEpisodeIds(
                                    chunk.flatMap { group -> group.episodeIds }.toSet(),
                                )
                            } else {
                                Timber.w(
                                    result.exceptionOrNull(),
                                    "UserViewsRepository: failed to flush %d pending uploads reason=%s",
                                    chunk.size,
                                    reason,
                                )
                            }
                        }
                }
            }

        private suspend fun findLatestEpisodeIdForReleaseInternal(
            releaseId: ReleaseId,
            includeWatched: Boolean,
            maxPages: Int,
            limit: Int,
        ): EpisodeId? =
            withContext(AppDispatchers.io) {
                if (maxPages <= 0 || limit <= 0) return@withContext null

                var page = 1
                while (page <= maxPages) {
                    val response =
                        runCatching {
                            getViewsHistory(page = page, limit = limit)
                        }.getOrNull() ?: return@withContext null

                    val item =
                        response.data.firstOrNull { history ->
                            val sameRelease = history.releaseId.id == releaseId.id
                            val allowedByWatched = includeWatched || !history.isWatched
                            sameRelease && allowedByWatched
                        }

                    if (item != null) {
                        val ordinal = item.episodeOrdinal
                        if (ordinal != null) {
                            return@withContext EpisodeId(
                                id = normalizeOrdinalDouble(ordinal),
                                releaseId = releaseId,
                            )
                        }
                    }

                    val currentPage = response.meta.page ?: page
                    val allPages = response.meta.allPages ?: currentPage
                    if (currentPage >= allPages) break

                    page += 1
                }

                return@withContext null
            }

        private suspend fun resolveAniEpisodeIdOrNull(episodeId: EpisodeId): AniLibertyReleaseEpisodeId? {
            val releaseCache = getReleaseEpisodesCacheOrNull(episodeId.releaseId) ?: return null

            val ordinalKey = normalizeOrdinalString(episodeId.id)
            return releaseCache.byOrdinal[ordinalKey]
                // fallback in case backend uses the same "episode id" format as legacy
                ?: runCatching { AniLibertyReleaseEpisodeId(episodeId.id) }.getOrNull()
        }

        private suspend fun getReleaseEpisodesCacheOrNull(releaseId: ReleaseId): ReleaseEpisodesCache? {
            cache[releaseId]?.let { return it }

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
                }.getOrNull() ?: return null

            val episodes = release.episodes.orEmpty()

            val byOrdinal =
                buildMap {
                    episodes.forEach { episode ->
                        val id = episode.id ?: return@forEach
                        val ordinal = episode.ordinal ?: episode.sortOrder ?: return@forEach
                        val key = normalizeOrdinalDouble(ordinal)
                        put(key, id)
                    }
                }

            val allIds = episodes.mapNotNull { it.id }
            val ordinalByEpisodeId =
                buildMap {
                    episodes.forEach { episode ->
                        val id = episode.id ?: return@forEach
                        val ordinal = episode.ordinal ?: episode.sortOrder ?: return@forEach
                        put(id, normalizeOrdinalDouble(ordinal))
                    }
                }

            return ReleaseEpisodesCache(
                byOrdinal = byOrdinal,
                ordinalByEpisodeId = ordinalByEpisodeId,
                allIds = allIds,
            )
                .also { cache[releaseId] = it }
        }

        private suspend fun getTimecodesSnapshot(): Map<AniLibertyReleaseEpisodeId, EpisodeTimecode> {
            val now = System.currentTimeMillis()

            return timecodesMutex.withLock {
                // If we have a fresh enough cache - use it.
                val ageMs = now - timecodesLastSyncMs
                if (timecodesCache.isNotEmpty() && ageMs in 0 until TIMECODES_TTL_MS) {
                    return@withLock timecodesCache
                }

                val remote = runCatching { aniLibertyApi.getUserViewTimecodes(since = null) }.getOrNull()
                if (remote != null) {
                    timecodesCache = remote.toCacheMap()
                    timecodesLastSyncMs = now
                }

                // If remote failed, keep stale cache (might be empty).
                return@withLock timecodesCache
            }
        }

        private suspend fun updateTimecodeCache(
            aniEpisodeId: AniLibertyReleaseEpisodeId,
            timecode: EpisodeTimecode,
        ) {
            timecodesMutex.withLock {
                val mutable = timecodesCache.toMutableMap()
                mutable[aniEpisodeId] = timecode
                timecodesCache = mutable
                timecodesLastSyncMs = System.currentTimeMillis()
            }
        }

        private suspend fun updateTimecodeCache(patch: Map<AniLibertyReleaseEpisodeId, EpisodeTimecode>) {
            timecodesMutex.withLock {
                val mutable = timecodesCache.toMutableMap()
                mutable.putAll(patch)
                timecodesCache = mutable
                timecodesLastSyncMs = System.currentTimeMillis()
            }
        }

        private suspend fun removeTimecodeCache(aniEpisodeId: AniLibertyReleaseEpisodeId) {
            timecodesMutex.withLock {
                if (timecodesCache.isEmpty()) return@withLock
                val mutable = timecodesCache.toMutableMap()
                mutable.remove(aniEpisodeId)
                timecodesCache = mutable
                timecodesLastSyncMs = System.currentTimeMillis()
            }
        }

        private suspend fun removeTimecodeCache(aniEpisodeIds: List<AniLibertyReleaseEpisodeId>) {
            timecodesMutex.withLock {
                if (timecodesCache.isEmpty()) return@withLock
                val mutable = timecodesCache.toMutableMap()
                aniEpisodeIds.forEach { mutable.remove(it) }
                timecodesCache = mutable
                timecodesLastSyncMs = System.currentTimeMillis()
            }
        }

        private fun List<AniLibertyViewTimecode>.toCacheMap(): Map<AniLibertyReleaseEpisodeId, EpisodeTimecode> {
            return associate { item ->
                item.releaseEpisodeId to
                    EpisodeTimecode(
                        positionMs = secondsToMs(item.time),
                        isWatched = item.isWatched,
                    )
            }
        }

        private fun msToSeconds(ms: Long): Double = ms.toDouble() / 1000.0

        private fun secondsToMs(seconds: Double): Long = (seconds * 1000.0).roundToLong()

        private fun normalizeOrdinalString(value: String): String {
            return normalizeOrdinalStringOrNull(value) ?: value.trim()
        }

        private fun normalizeOrdinalStringOrNull(value: String): String? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            return runCatching {
                BigDecimal(trimmed).stripTrailingZeros().toPlainString()
            }.getOrNull()
        }

        private fun normalizeOrdinalDouble(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

        private fun AniLibertyUserViewHistoryItem.toDomainOrNull(): UserViewHistoryItem? {
            val release = release
            val releaseIdValue = release?.id?.value ?: releaseId?.value ?: return null
            val releaseTitleMain = release?.name?.main
            val releaseTitleEnglish = release?.name?.english
            val releaseTitleAlternative = release?.name?.alternative
            val posterPreview = release?.poster?.optimized?.preview ?: release?.poster?.preview
            val posterThumbnail = release?.poster?.optimized?.thumbnail ?: release?.poster?.thumbnail

            return UserViewHistoryItem(
                releaseId = ReleaseId(releaseIdValue),
                titleMain = releaseTitleMain,
                titleEnglish = releaseTitleEnglish,
                titleAlternative = releaseTitleAlternative,
                posterPreview = posterPreview,
                posterThumbnail = posterThumbnail,
                episodeOrdinal = episode?.ordinal,
                timeSeconds = time,
                isWatched = isWatched == true,
            )
        }

        private companion object {
            const val TIMECODES_TTL_MS: Long = 60_000L
            const val DEFAULT_HISTORY_LOOKUP_PAGES: Int = 5
            const val DEFAULT_HISTORY_LOOKUP_LIMIT: Int = 25
            private const val PENDING_UPLOAD_DEBOUNCE_MS: Long = 1_000L
            private const val UPSERT_BATCH_SIZE: Int = 100
        }

        private data class PendingUploadGroup(
            val upload: UserViewPendingUpload,
            val body: AniLibertyUserViewTimecodeUpsertBody,
            val episodeIds: LinkedHashSet<EpisodeId>,
        )

        private fun schedulePendingUploads(reason: String) {
            synchronized(pendingUploadsScheduleLock) {
                pendingUploadsJob?.cancel()
                pendingUploadsJob =
                    applicationScope.launch {
                        delay(PENDING_UPLOAD_DEBOUNCE_MS)
                        flushPendingUploads(reason = reason)
                    }
            }
        }

        private fun shouldReplacePendingUpload(
            candidate: UserViewPendingUpload,
            current: UserViewPendingUpload,
        ): Boolean {
            return when {
                candidate.updatedAtMs > current.updatedAtMs -> true
                candidate.updatedAtMs < current.updatedAtMs -> false
                candidate.isWatched && !current.isWatched -> true
                !candidate.isWatched && current.isWatched -> false
                else -> candidate.positionMs > current.positionMs
            }
        }
    }
