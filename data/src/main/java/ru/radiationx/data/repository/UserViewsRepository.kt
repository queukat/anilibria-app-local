package ru.radiationx.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFieldName
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseExclude
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyUserViewHistoryItem
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeDeleteBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.response.PaginatedResponse
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.math.roundToLong

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
class UserViewsRepository @Inject constructor(
    private val aniLibertyApi: AniLibertyApi,
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
        val allIds: List<AniLibertyReleaseEpisodeId>,
    )

    private val cache = mutableMapOf<ReleaseId, ReleaseEpisodesCache>()

    // Timecodes cache (all user timecodes; backend endpoint doesn't support filtering by release).
    private val timecodesMutex = Mutex()
    private var timecodesCache: Map<AniLibertyReleaseEpisodeId, EpisodeTimecode> = emptyMap()
    private var timecodesLastSyncMs: Long = 0L

    /**
     * Fetch user views history from AniLiberty.
     *
     * Throws on hard failures (caller may fallback to local history).
     */
    suspend fun getViewsHistory(
        page: Int,
        limit: Int,
    ): PaginatedResponse<UserViewHistoryItem> = withContext(Dispatchers.IO) {
        val response = aniLibertyApi.getUserViewsHistory(
            page = page,
            limit = limit,
            fields = AniLibertyReleaseFields.Suggestions,
        )
        PaginatedResponse(
            data = response.data.mapNotNull { it.toDomainOrNull() },
            meta = response.meta,
        )
    }

    /**
     * Try to find the latest watched/continue episode for a given release using user views history.
     *
     * This is a best-effort helper for UI (Details -> Continue / Episodes preselect).
     */
    suspend fun findLatestEpisodeIdForRelease(
        releaseId: ReleaseId,
        maxPages: Int = DEFAULT_HISTORY_LOOKUP_PAGES,
        limit: Int = DEFAULT_HISTORY_LOOKUP_LIMIT,
    ): EpisodeId? = findLatestEpisodeIdForReleaseInternal(
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
    ): EpisodeId? = findLatestEpisodeIdForReleaseInternal(
        releaseId = releaseId,
        includeWatched = false,
        maxPages = maxPages,
        limit = limit,
    )

    /**
     * Load remote timecode for a single episode.
     *
     * This enables "continue on another device" behavior:
     * - we still keep local progress as a primary source
     * - but if local is empty/outdated, we can use AniLiberty timecodes
     *
     * Best-effort: returns null on errors.
     */
    suspend fun getEpisodeTimecode(
        episodeId: EpisodeId,
    ): EpisodeTimecode? = withContext(Dispatchers.IO) {
        val aniEpisodeId = resolveAniEpisodeIdOrNull(episodeId) ?: return@withContext null
        val snapshot = getTimecodesSnapshot()
        snapshot[aniEpisodeId]
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
    ) = withContext(Dispatchers.IO) {
        if (positionMs < 0) return@withContext

        val aniEpisodeId = resolveAniEpisodeIdOrNull(episodeId) ?: return@withContext
        val body = AniLibertyUserViewTimecodeUpsertBody.from(
            time = msToSeconds(positionMs),
            isWatched = isWatched,
            releaseEpisodeId = aniEpisodeId,
        )

        val result = runCatching { aniLibertyApi.upsertUserViewTimecodes(listOf(body)) }
        if (result.isSuccess) {
            updateTimecodeCache(
                aniEpisodeId,
                EpisodeTimecode(
                    positionMs = positionMs,
                    isWatched = isWatched,
                )
            )
        }
    }

    /**
     * Remove progress for an episode (unview/reset).
     *
     * Best-effort: swallows network/auth errors.
     */
    suspend fun deleteEpisodeTimecode(
        episodeId: EpisodeId,
    ) = withContext(Dispatchers.IO) {
        val aniEpisodeId = resolveAniEpisodeIdOrNull(episodeId) ?: return@withContext
        val body = AniLibertyUserViewTimecodeDeleteBody.from(aniEpisodeId)

        val result = runCatching { aniLibertyApi.deleteUserViewTimecodes(listOf(body)) }
        if (result.isSuccess) {
            removeTimecodeCache(aniEpisodeId)
        }
    }

    /**
     * Clear all timecodes for a release.
     *
     * Best-effort: swallows network/auth errors.
     */
    suspend fun deleteAllTimecodesForRelease(
        releaseId: ReleaseId,
    ) = withContext(Dispatchers.IO) {
        val releaseCache = getReleaseEpisodesCacheOrNull(releaseId) ?: return@withContext
        if (releaseCache.allIds.isEmpty()) return@withContext

        val bodies = releaseCache.allIds.map { AniLibertyUserViewTimecodeDeleteBody.from(it) }
        val result = runCatching { aniLibertyApi.deleteUserViewTimecodes(bodies) }
        if (result.isSuccess) {
            removeTimecodeCache(releaseCache.allIds)
        }
    }

    /**
     * Mark all episodes in a release as watched.
     *
     * Best-effort: swallows network/auth errors.
     */
    suspend fun markAllWatchedForRelease(
        releaseId: ReleaseId,
    ) = withContext(Dispatchers.IO) {
        val releaseCache = getReleaseEpisodesCacheOrNull(releaseId) ?: return@withContext
        if (releaseCache.allIds.isEmpty()) return@withContext

        val bodies = releaseCache.allIds.map {
            AniLibertyUserViewTimecodeUpsertBody.from(
                time = 0.0,
                isWatched = true,
                releaseEpisodeId = it,
            )
        }
        val result = runCatching { aniLibertyApi.upsertUserViewTimecodes(bodies) }
        if (result.isSuccess) {
            updateTimecodeCache(
                releaseCache.allIds.associateWith {
                    EpisodeTimecode(
                        positionMs = 0L,
                        isWatched = true,
                    )
                }
            )
        }
    }

    private suspend fun findLatestEpisodeIdForReleaseInternal(
        releaseId: ReleaseId,
        includeWatched: Boolean,
        maxPages: Int,
        limit: Int,
    ): EpisodeId? = withContext(Dispatchers.IO) {
        if (maxPages <= 0 || limit <= 0) return@withContext null

        var page = 1
        while (page <= maxPages) {
            val response = runCatching {
                getViewsHistory(page = page, limit = limit)
            }.getOrNull() ?: return@withContext null

            val item = response.data.firstOrNull { history ->
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

    private suspend fun resolveAniEpisodeIdOrNull(
        episodeId: EpisodeId,
    ): AniLibertyReleaseEpisodeId? {
        val releaseCache = getReleaseEpisodesCacheOrNull(episodeId.releaseId) ?: return null

        val ordinalKey = normalizeOrdinalString(episodeId.id)
        return releaseCache.byOrdinal[ordinalKey]
        // fallback in case backend uses the same "episode id" format as legacy
            ?: runCatching { AniLibertyReleaseEpisodeId(episodeId.id) }.getOrNull()
    }

    private suspend fun getReleaseEpisodesCacheOrNull(
        releaseId: ReleaseId,
    ): ReleaseEpisodesCache? {
        cache[releaseId]?.let { return it }

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

        val release = runCatching {
            aniLibertyApi.getRelease(
                key = AniLibertyReleaseKey.id(releaseId.id),
                fields = fieldsForEpisodes,
            )
        }.getOrNull() ?: return null

        val episodes = release.episodes.orEmpty()

        val byOrdinal = buildMap {
            episodes.forEach { episode ->
                val id = episode.id ?: return@forEach
                val ordinal = episode.ordinal ?: episode.sortOrder ?: return@forEach
                val key = normalizeOrdinalDouble(ordinal)
                put(key, id)
            }
        }

        val allIds = episodes.mapNotNull { it.id }

        return ReleaseEpisodesCache(byOrdinal = byOrdinal, allIds = allIds)
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

    private suspend fun updateTimecodeCache(
        patch: Map<AniLibertyReleaseEpisodeId, EpisodeTimecode>,
    ) {
        timecodesMutex.withLock {
            val mutable = timecodesCache.toMutableMap()
            mutable.putAll(patch)
            timecodesCache = mutable
            timecodesLastSyncMs = System.currentTimeMillis()
        }
    }

    private suspend fun removeTimecodeCache(
        aniEpisodeId: AniLibertyReleaseEpisodeId,
    ) {
        timecodesMutex.withLock {
            if (timecodesCache.isEmpty()) return@withLock
            val mutable = timecodesCache.toMutableMap()
            mutable.remove(aniEpisodeId)
            timecodesCache = mutable
            timecodesLastSyncMs = System.currentTimeMillis()
        }
    }

    private suspend fun removeTimecodeCache(
        aniEpisodeIds: List<AniLibertyReleaseEpisodeId>,
    ) {
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
            item.releaseEpisodeId to EpisodeTimecode(
                positionMs = secondsToMs(item.time),
                isWatched = item.isWatched,
            )
        }
    }

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
    }
}
