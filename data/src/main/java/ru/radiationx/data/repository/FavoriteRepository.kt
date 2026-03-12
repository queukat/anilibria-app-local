package ru.radiationx.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.CookieHolder
import ru.radiationx.data.datasource.remote.address.ApiConfig
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFavoriteSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.api.FavoriteApi
import ru.radiationx.data.entity.domain.Paginated
import ru.radiationx.data.entity.domain.release.BlockedInfo
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.mapper.toDomain
import ru.radiationx.data.entity.mapper.toLegacyReleaseOrNull
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.interactors.ReleaseUpdateMiddleware
import ru.radiationx.data.system.ApiUtils
import ru.radiationx.data.system.HttpException
import timber.log.Timber
import javax.inject.Inject

class FavoriteRepository @Inject constructor(
    // New API (token-based)
    private val aniLibertyApi: AniLibertyApi,
    // Legacy API (cookie-based) — fallback пока есть фичи на старом бэкенде
    private val favoriteApi: FavoriteApi,
    private val updateMiddleware: ReleaseUpdateMiddleware,
    private val apiUtils: ApiUtils,
    private val apiConfig: ApiConfig,
    private val authTokenHolder: AuthTokenHolder,
    private val cookieHolder: CookieHolder,
) {

    suspend fun getFavoritesAniLiberty(page: Int): Paginated<Release> = withContext(Dispatchers.IO) {
        mapAniLibertyFavorites(
            aniLibertyApi.getUserFavoriteReleasesFiltered(
                page = page,
                limit = DEFAULT_LIMIT,
                sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                fields = AniLibertyReleaseFields.FavoritesList,
            )
        )
    }

    suspend fun addFavoriteAniLiberty(releaseId: ReleaseId) = withContext(Dispatchers.IO) {
        aniLibertyApi.addToFavorites(listOf(AniLibertyReleaseId(releaseId.id)))
    }

    suspend fun deleteFavoriteAniLiberty(releaseId: ReleaseId) = withContext(Dispatchers.IO) {
        aniLibertyApi.removeFromFavorites(listOf(AniLibertyReleaseId(releaseId.id)))
    }

    /**
     * Favorites list (token-first).
     *
     * We map AniLiberty v1 wire releases into legacy domain [Release] (subset, safe for lists).
     */
    suspend fun getFavorites(page: Int): Paginated<Release> = withContext(Dispatchers.IO) {
        val hasToken = !authTokenHolder.getToken().isNullOrBlank()
        val hasLegacyCookie = cookieHolder.getCookies()[CookieHolder.PHPSESSID] != null

        // 1) v1 first
        runCatching {
            mapAniLibertyFavorites(
                aniLibertyApi.getUserFavoriteReleasesFiltered(
                    page = page,
                    limit = DEFAULT_LIMIT,
                    sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                    fields = AniLibertyReleaseFields.FavoritesList,
                )
            )
        }.getOrElse { error ->
            if (error is CancellationException) {
                throw error
            }
            if (!shouldFallbackToLegacy(error, hasToken, hasLegacyCookie)) {
                throw error
            }
            Timber.w(error, "AniLiberty favorites failed, fallback to legacy")

            // 2) legacy fallback
            favoriteApi
                .getFavorites(page)
                .toDomain { it.toDomain(apiUtils, apiConfig) }
                .also { updateMiddleware.handle(it.data) }
        }
    }

    private suspend fun mapAniLibertyFavorites(
        response: PaginatedResponse<ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease>,
    ): Paginated<Release> {
        val mapped = response.toDomain { it.toLegacyReleaseOrNull(apiUtils, isFavorite = true) }
        val filtered = Paginated(
            data = mapped.data.filterNotNull(),
            page = mapped.page,
            allPages = mapped.allPages,
            perPage = mapped.perPage,
            allItems = mapped.allItems,
        )

        updateMiddleware.handle(filtered.data)
        return filtered
    }

    private fun shouldFallbackToLegacy(
        error: Throwable,
        hasToken: Boolean,
        hasLegacyCookie: Boolean,
    ): Boolean {
        if (!hasLegacyCookie) {
            return false
        }
        if (!hasToken) {
            return true
        }
        val httpError = error as? HttpException ?: return true
        return httpError.code !in setOf(401, 403)
    }

    suspend fun deleteFavorite(releaseId: ReleaseId): Release = withContext(Dispatchers.IO) {
        // 1) v1 mutate (main source of truth after migration)
        runCatching {
            aniLibertyApi.removeFromFavorites(listOf(AniLibertyReleaseId(releaseId.id)))
        }.onFailure { Timber.w(it, "AniLiberty removeFromFavorites failed for $releaseId") }

        // 2) Return best-effort Release for callers that still expect it.
        // Prefer legacy response (richer model), otherwise fallback to v1 snapshot mapping.
        runCatching {
            favoriteApi.deleteFavorite(releaseId.id).toDomain(apiUtils, apiConfig)
        }.getOrElse { legacyError ->
            Timber.w(legacyError, "Legacy deleteFavorite failed for $releaseId, fallback to v1 getRelease")

            val v1 = runCatching {
                aniLibertyApi.getRelease(
                    key = AniLibertyReleaseKey.id(releaseId.id),
                    fields = null,
                )
            }.getOrNull()

            v1?.toLegacyReleaseOrNull(apiUtils, isFavorite = false)
                ?: createMinimalRelease(releaseId, isFavorite = false)
        }
    }

    suspend fun addFavorite(releaseId: ReleaseId): Release = withContext(Dispatchers.IO) {
        // 1) v1 mutate (main source of truth after migration)
        runCatching {
            aniLibertyApi.addToFavorites(listOf(AniLibertyReleaseId(releaseId.id)))
        }.onFailure { Timber.w(it, "AniLiberty addToFavorites failed for $releaseId") }

        // 2) Return best-effort Release for callers that still expect it.
        runCatching {
            favoriteApi.addFavorite(releaseId.id).toDomain(apiUtils, apiConfig)
        }.getOrElse { legacyError ->
            Timber.w(legacyError, "Legacy addFavorite failed for $releaseId, fallback to v1 getRelease")

            val v1 = runCatching {
                aniLibertyApi.getRelease(
                    key = AniLibertyReleaseKey.id(releaseId.id),
                    fields = null,
                )
            }.getOrNull()

            v1?.toLegacyReleaseOrNull(apiUtils, isFavorite = true)
                ?: createMinimalRelease(releaseId, isFavorite = true)
        }
    }

    private fun createMinimalRelease(releaseId: ReleaseId, isFavorite: Boolean): Release {
        return Release(
            id = releaseId,
            code = ReleaseCode(releaseId.id.toString()),
            names = listOf(releaseId.id.toString()),
            series = null,
            poster = null,
            torrentUpdate = 0,
            status = null,
            statusCode = Release.STATUS_CODE_NOTHING,
            types = emptyList(),
            genres = emptyList(),
            voices = emptyList(),
            members = null,
            year = null,
            season = null,
            days = emptyList(),
            description = null,
            announce = null,
            favoriteInfo = FavoriteInfo(rating = 0, isAdded = isFavorite),
            link = null,
            franchises = emptyList(),
            showDonateDialog = false,
            blockedInfo = BlockedInfo(isBlocked = false, reason = null),
            moonwalkLink = null,
            episodes = emptyList(),
            sourceEpisodes = emptyList(),
            externalPlaylists = emptyList(),
            rutubePlaylist = emptyList(),
            torrents = emptyList(),
        )
    }

    private companion object {
        // Favorites endpoints are usually cheap — take a bit more to reduce paging.
        const val DEFAULT_LIMIT = 25
    }
}
