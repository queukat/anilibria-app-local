package ru.radiationx.data.datasource.remote.aniliberty

import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyAuthTokenResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionAddBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionIdItem
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionsReleasesBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpGetResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReleaseEpisodeTimecode
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertySocialAuthenticateResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertySocialLoginResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeDeleteBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import ru.radiationx.data.entity.response.PaginatedResponse

/**
 * AniLiberty boundary.
 *
 * Important: this module is a scaffold right now.
 * It is not wired into production flows yet.
 * We shape types and boundaries first, then connect it to domain and UI.
 */
interface AniLibertyClient :
    AniLibertyCatalogClient,
    AniLibertyCatalogReferencesClient,
    AniLibertyReleasesClient,
    AniLibertyGenresClient,
    AniLibertyScheduleClient,
    AniLibertyTorrentsClient,
    AniLibertyMediaClient,
    AniLibertyAppClient,
    AniLibertyAccountsClient,
    AniLibertyFranchisesClient,
    AniLibertyTeamsClient

fun interface AniLibertyCatalogClient {
    suspend fun getCatalogReleases(request: AniLibertyCatalogRequest): PaginatedResponse<AniLibertyRelease>
}

interface AniLibertyCatalogReferencesClient {
    suspend fun getCatalogReferenceAgeRatings(): List<AniLibertyCatalogReferenceAgeRating>

    suspend fun getCatalogReferenceGenres(): List<AniLibertyGenre>

    suspend fun getCatalogReferenceProductionStatuses(): List<AniLibertyCatalogReferenceProductionStatus>

    suspend fun getCatalogReferencePublishStatuses(): List<AniLibertyCatalogReferencePublishStatus>

    suspend fun getCatalogReferenceSeasons(): List<AniLibertyCatalogReferenceSeason>

    suspend fun getCatalogReferenceSorting(): List<AniLibertyCatalogReferenceSorting>

    suspend fun getCatalogReferenceTypes(): List<AniLibertyCatalogReferenceType>

    suspend fun getCatalogReferenceYears(): List<Int>
}

interface AniLibertyReleasesClient {
    suspend fun getRelease(
        key: AniLibertyReleaseKey,
        fields: AniLibertyFieldSpec? = null,
    ): AniLibertyRelease

    suspend fun getLatestReleases(
        limit: Int? = null,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyRelease>

    suspend fun getRecommendedReleases(
        limit: Int? = null,
        releaseId: AniLibertyReleaseId? = null,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyRelease>

    suspend fun getReleasesList(
        ids: List<AniLibertyReleaseId>? = null,
        aliases: List<AniLibertyReleaseAlias>? = null,
        page: Int = 1,
        limit: Int = 10,
        fields: AniLibertyFieldSpec? = null,
    ): PaginatedResponse<AniLibertyRelease>

    suspend fun getRandomReleases(
        limit: Int = 1,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyRelease>

    suspend fun getReleaseMembers(
        key: AniLibertyReleaseKey,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyReleaseMember>

    suspend fun getReleaseEpisodesTimecodes(
        key: AniLibertyReleaseKey,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyReleaseEpisodeTimecode>

    suspend fun getEpisode(
        releaseEpisodeId: AniLibertyReleaseEpisodeId,
        fields: AniLibertyFieldSpec? = null,
    ): AniLibertyEpisode

    suspend fun getEpisodeTimecode(
        releaseEpisodeId: AniLibertyReleaseEpisodeId,
        fields: AniLibertyFieldSpec? = null,
    ): ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyEpisodeTimecode
}

interface AniLibertyGenresClient {
    suspend fun getGenres(fields: AniLibertyFieldSpec? = null): List<AniLibertyGenre>

    suspend fun getGenre(
        genreId: AniLibertyGenreId,
        fields: AniLibertyFieldSpec? = null,
    ): AniLibertyGenre

    suspend fun getRandomGenres(
        limit: Int? = null,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyGenre>

    suspend fun getGenreReleases(
        genreId: AniLibertyGenreId,
        page: Int = 1,
        limit: Int = 10,
        fields: AniLibertyFieldSpec? = null,
    ): PaginatedResponse<AniLibertyRelease>
}

interface AniLibertyScheduleClient {
    suspend fun getScheduleNow(fields: AniLibertyFieldSpec? = null): AniLibertyScheduleNowResponse

    suspend fun getScheduleWeek(fields: AniLibertyFieldSpec? = null): AniLibertyScheduleWeekResponse
}

interface AniLibertyTorrentsClient {
    suspend fun getTorrents(
        page: Int = 1,
        limit: Int = 20,
    ): PaginatedResponse<AniLibertyTorrent>

    suspend fun getTorrent(key: AniLibertyTorrentKey): AniLibertyTorrent

    suspend fun getTorrentFile(key: AniLibertyTorrentKey): String

    suspend fun getTorrentsByRelease(releaseId: AniLibertyReleaseId): List<AniLibertyTorrent>

    suspend fun getTorrentsRss(): String

    suspend fun getTorrentsRssByRelease(releaseId: AniLibertyReleaseId): String
}

interface AniLibertyMediaClient {
    suspend fun getMediaVasts(): List<AniLibertyVast>

    suspend fun getMediaManifestXml(): String

    suspend fun getMediaPromotions(): AniLibertyMediaPromotionsResponse

    suspend fun getMediaVideos(): AniLibertyMediaVideosResponse
}

interface AniLibertyAppClient {
    suspend fun getAppStatus(): AniLibertyAppStatus

    suspend fun searchAppReleases(request: AniLibertyAppSearchReleasesRequest): List<AniLibertyRelease>

    suspend fun searchAppReleases(
        query: String,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyRelease> =
        searchAppReleases(
            AniLibertyAppSearchReleasesRequest(
                query = query,
                fields = fields,
            ),
        )
}

interface AniLibertyAccountsClient {
    // OTP
    suspend fun otpGet(deviceId: AniLibertyDeviceId): AniLibertyOtpGetResponse

    suspend fun otpAccept(code: AniLibertyOtpCode)

    suspend fun otpLogin(
        code: AniLibertyOtpCode,
        deviceId: AniLibertyDeviceId,
    ): AniLibertyAuthTokenResponse

    // Auth
    suspend fun login(
        login: String,
        password: String,
    ): AniLibertyAuthTokenResponse

    suspend fun socialLogin(provider: AniLibertySocialProvider): AniLibertySocialLoginResponse

    suspend fun socialAuthenticate(state: String): AniLibertySocialAuthenticateResponse

    suspend fun logout(): AniLibertyAuthTokenResponse

    suspend fun passwordForget(email: AniLibertyEmail)

    suspend fun passwordReset(
        token: String,
        password: String,
        passwordConfirmation: String,
    )

    // References (Collections)
    suspend fun getUserCollectionsReferenceAgeRatings(): List<ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReferenceValueLabelDescription>

    suspend fun getUserCollectionsReferenceGenres(): List<AniLibertyGenre>

    suspend fun getUserCollectionsReferenceTypes(): List<ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReferenceValueDescription>

    suspend fun getUserCollectionsReferenceYears(): List<Int>

    // References (Favorites)
    suspend fun getUserFavoritesReferenceAgeRatings(): List<ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReferenceValueLabelDescription>

    suspend fun getUserFavoritesReferenceGenres(): List<AniLibertyGenre>

    suspend fun getUserFavoritesReferenceSorting(): List<ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReferenceValueDescription>

    suspend fun getUserFavoritesReferenceTypes(): List<ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReferenceValueDescription>

    suspend fun getUserFavoritesReferenceYears(): List<Int>

    // Favorites
    suspend fun getUserFavoriteIds(): List<AniLibertyReleaseId>

    suspend fun getUserFavoriteReleases(
        page: Int,
        limit: Int,
        fields: AniLibertyFieldSpec? = null,
    ): PaginatedResponse<AniLibertyRelease>

    suspend fun getUserFavoriteReleasesFiltered(request: AniLibertyFavoritesFilterRequest): PaginatedResponse<AniLibertyRelease>

    // Favorites mutate
    suspend fun addToFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId>

    suspend fun removeFromFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId>

    // Collections
    suspend fun getUserCollectionIds(): List<AniLibertyCollectionIdItem>

    suspend fun getUserCollectionReleasesFiltered(request: AniLibertyCollectionsFilterRequest): PaginatedResponse<AniLibertyRelease>

    suspend fun getUserCollectionReleasesByBody(body: AniLibertyCollectionsReleasesBody): PaginatedResponse<AniLibertyRelease>

    suspend fun addToCollections(items: List<AniLibertyCollectionAddBody>): List<AniLibertyCollectionIdItem>

    suspend fun removeFromCollections(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyCollectionIdItem>

    // Views timecodes
    suspend fun getUserViewTimecodes(since: String? = null): List<AniLibertyViewTimecode>

    suspend fun upsertUserViewTimecodes(items: List<AniLibertyUserViewTimecodeUpsertBody>)

    suspend fun deleteUserViewTimecodes(items: List<AniLibertyUserViewTimecodeDeleteBody>)

    // Profile
    suspend fun getMyProfile(fields: AniLibertyFieldSpec? = null): AniLibertyUserProfile

    // Views history
    suspend fun getUserViewsHistory(
        page: Int = 1,
        limit: Int = 10,
        fields: AniLibertyFieldSpec? = null,
    ): PaginatedResponse<AniLibertyUserViewHistoryItem>
}

/**
 * Franchises API.
 * Mirrors AniLibertyApi implementation.
 */
interface AniLibertyFranchisesClient {
    suspend fun getFranchises(fields: AniLibertyFieldSpec? = null): List<AniLibertyFranchise>

    suspend fun getFranchise(
        franchiseId: AniLibertyFranchiseId,
        fields: AniLibertyFieldSpec? = null,
    ): AniLibertyFranchiseDetails

    suspend fun getRandomFranchises(
        limit: Int? = null,
        fields: AniLibertyFieldSpec? = null,
    ): List<AniLibertyFranchise>

    suspend fun getFranchisesByRelease(
        releaseId: AniLibertyReleaseId,
        fields: AniLibertyFieldSpec? = null,
    ): AniLibertyFranchisesByRelease
}

/**
 * Teams API.
 * Mirrors AniLibertyApi implementation.
 */
interface AniLibertyTeamsClient {
    suspend fun getTeams(fields: AniLibertyFieldSpec? = null): List<AniLibertyTeam>

    suspend fun getTeamRoles(fields: AniLibertyFieldSpec? = null): List<AniLibertyTeamRole>

    suspend fun getTeamUsers(fields: AniLibertyFieldSpec? = null): List<AniLibertyTeamUserItem>
}
