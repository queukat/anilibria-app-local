package ru.radiationx.data.datasource.remote.aniliberty

import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyAuthTokenResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionAddBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionIdItem
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionsReleasesBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyFavoriteReleasesBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpGetResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReleaseEpisodeTimecode
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeDeleteBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import ru.radiationx.data.entity.response.PaginatedResponse

/**
 * AniLiberty client boundary.
 *
 * Important:
 * For now it is not used in production flows. We are creating it as a typed boundary first.
 */
interface AniLibertyClient {

    // Catalog
    suspend fun getCatalogReleases(
        page: Int,
        limit: Int,
        search: String? = null,
        genres: List<Int>? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
        seasons: List<AniLibertySeason>? = null,
        types: List<AniLibertyReleaseType>? = null,
        ageRatings: List<AniLibertyAgeRating>? = null,
        publishStatuses: List<AniLibertyCatalogPublishStatus>? = null,
        productionStatuses: List<AniLibertyCatalogProductionStatus>? = null,
        sorting: AniLibertyCatalogSorting? = null,
        fields: AniLibertyQueryFields? = null,
    ): PaginatedResponse<AniLibertyRelease>

    // Releases
    suspend fun getRelease(
        key: AniLibertyReleaseKey,
        fields: AniLibertyReleaseFields? = null,
        queryFields: AniLibertyQueryFields? = null,
    ): AniLibertyRelease

    suspend fun getLatestReleases(
        limit: Int? = null,
        fields: AniLibertyQueryFields? = null,
    ): List<AniLibertyRelease>

    suspend fun getRecommendedReleases(
        limit: Int? = null,
        releaseId: AniLibertyReleaseId? = null,
        fields: AniLibertyQueryFields? = null,
    ): List<AniLibertyRelease>

    suspend fun getReleasesList(
        ids: List<AniLibertyReleaseId>? = null,
        aliases: List<AniLibertyReleaseAlias>? = null,
        page: Int = 1,
        limit: Int = 10,
        fields: AniLibertyQueryFields? = null,
    ): PaginatedResponse<AniLibertyRelease>

    suspend fun getRandomReleases(
        limit: Int = 1,
        fields: AniLibertyQueryFields? = null,
    ): List<AniLibertyRelease>

    suspend fun getReleaseMembers(
        key: AniLibertyReleaseKey,
        fields: AniLibertyQueryFields? = null,
    ): List<AniLibertyReleaseMember>

    suspend fun getReleaseEpisodesTimecodes(
        key: AniLibertyReleaseKey,
        fields: AniLibertyQueryFields? = null,
    ): List<AniLibertyReleaseEpisodeTimecode>

    suspend fun getEpisode(
        releaseEpisodeId: AniLibertyReleaseEpisodeId,
        fields: AniLibertyQueryFields? = null,
    ): AniLibertyEpisode

    suspend fun getEpisodeTimecode(
        releaseEpisodeId: AniLibertyReleaseEpisodeId,
        fields: AniLibertyQueryFields? = null,
    ): ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyEpisodeTimecode

    // OTP
    suspend fun otpGet(deviceId: AniLibertyDeviceId): AniLibertyOtpGetResponse
    suspend fun otpAccept(code: AniLibertyOtpCode)
    suspend fun otpLogin(code: AniLibertyOtpCode, deviceId: AniLibertyDeviceId): AniLibertyAuthTokenResponse

    // Auth
    suspend fun login(login: String, password: String): AniLibertyAuthTokenResponse
    suspend fun socialLogin(provider: AniLibertySocialProvider): ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertySocialLoginResponse
    suspend fun socialAuthenticate(state: String): ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertySocialAuthenticateResponse
    suspend fun logout(): AniLibertyAuthTokenResponse
    suspend fun passwordForget(email: AniLibertyEmail)
    suspend fun passwordReset(token: String, password: String, passwordConfirmation: String)

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
        fields: AniLibertyQueryFields? = null,
    ): PaginatedResponse<AniLibertyRelease>

    suspend fun getUserFavoriteReleasesFiltered(
        page: Int,
        limit: Int,
        years: List<Int>? = null,
        types: List<AniLibertyReleaseType>? = null,
        genres: List<Int>? = null,
        search: String? = null,
        sorting: AniLibertyFavoriteSorting? = null,
        ageRatings: List<AniLibertyAgeRating>? = null,
        fields: AniLibertyQueryFields? = null,
    ): PaginatedResponse<AniLibertyRelease>

    suspend fun getUserFavoriteReleasesByBody(body: AniLibertyFavoriteReleasesBody): PaginatedResponse<AniLibertyRelease>
    suspend fun addToFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId>
    suspend fun removeFromFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId>

    // Collections
    suspend fun getUserCollectionIds(): List<AniLibertyCollectionIdItem>

    suspend fun getUserCollectionReleasesFiltered(
        page: Int,
        limit: Int,
        typeOfCollection: AniLibertyCollectionType,
        genres: List<Int>? = null,
        types: List<AniLibertyReleaseType>? = null,
        years: List<Int>? = null,
        search: String? = null,
        ageRatings: List<AniLibertyAgeRating>? = null,
        fields: AniLibertyQueryFields? = null,
    ): PaginatedResponse<AniLibertyRelease>

    suspend fun getUserCollectionReleasesByBody(body: AniLibertyCollectionsReleasesBody): PaginatedResponse<AniLibertyRelease>
    suspend fun addToCollections(items: List<AniLibertyCollectionAddBody>): List<AniLibertyCollectionIdItem>
    suspend fun removeFromCollections(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyCollectionIdItem>

    // Views
    suspend fun getUserViewTimecodes(since: String? = null): List<AniLibertyViewTimecode>
    suspend fun upsertUserViewTimecodes(items: List<AniLibertyUserViewTimecodeUpsertBody>)
    suspend fun deleteUserViewTimecodes(items: List<AniLibertyUserViewTimecodeDeleteBody>)

    suspend fun getMyProfile(fields: AniLibertyQueryFields? = null): AniLibertyUserProfile

    suspend fun getUserViewsHistory(
        page: Int,
        limit: Int,
        fields: AniLibertyQueryFields? = null,
    ): PaginatedResponse<AniLibertyUserViewHistoryItem>


    // Franchises

    suspend fun getFranchises(
        fields: AniLibertyQueryFields? = null,
    ): List<AniLibertyFranchise>

    suspend fun getFranchise(
        franchiseId: AniLibertyFranchiseId,
        fields: AniLibertyQueryFields? = null,
    ): AniLibertyFranchiseDetails

    suspend fun getRandomFranchises(
        limit: Int? = null,
        fields: AniLibertyQueryFields? = null,
    ): List<AniLibertyFranchise>

    suspend fun getFranchisesByRelease(
        releaseId: AniLibertyReleaseId,
        fields: AniLibertyQueryFields? = null,
    ): AniLibertyFranchisesByRelease

}
