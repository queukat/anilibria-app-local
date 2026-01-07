package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ru.radiationx.data.ApiClient
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyAuthLoginBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyAuthTokenResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionAddBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionIdItem
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionsReleasesBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyEmailBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyFavoriteReleasesBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpAcceptBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpGetBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpGetResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpLoginBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyPasswordResetBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReferenceValueDescription
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReferenceValueLabelDescription
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReleaseEpisodeTimecode
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReleaseIdBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertySocialAuthenticateResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertySocialLoginResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeDeleteBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import ru.radiationx.data.datasource.remote.fetchResponse
import ru.radiationx.data.entity.response.PaginatedResponse
import javax.inject.Inject

/**
 * AniLiberty API v1 client implementation.
 *
 * Important:
 * This module is a scaffold right now. It is not wired into production flows yet.
 * We are shaping types and boundaries first, then will connect it to domain/UI.
 */
class AniLibertyApi @Inject constructor(
    @ApiClient private val client: IClient,
    private val moshi: Moshi,
) : AniLibertyClient {

    private val baseUrl = "https://aniliberty.top/api/v1"

    private inline fun <reified T> toJsonList(items: List<T>): String {
        val type = Types.newParameterizedType(List::class.java, T::class.java)
        return moshi.adapter<List<T>>(type).toJson(items)
    }

    private inline fun <reified T> toJsonObject(body: T): String {
        return moshi.adapter(T::class.java).toJson(body)
    }

    // Catalog

    override suspend fun getCatalogReleases(
        page: Int,
        limit: Int,
        search: String?,
        genres: List<Int>?,
        fromYear: Int?,
        toYear: Int?,
        seasons: List<AniLibertySeason>?,
        types: List<AniLibertyReleaseType>?,
        ageRatings: List<AniLibertyAgeRating>?,
        publishStatuses: List<AniLibertyCatalogPublishStatus>?,
        productionStatuses: List<AniLibertyCatalogProductionStatus>?,
        sorting: AniLibertyCatalogSorting?,
        fields: AniLibertyQueryFields?,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            put("page", page.toString())
            put("limit", limit.toString())

            putIfNotBlank("f[search]", search)

            putCsv("f[genres]", genres)
            putCsv("f[seasons]", seasons) { it.value }
            putCsv("f[types]", types) { it.value }

            if (fromYear != null) put("f[years][from_year]", fromYear.toString())
            if (toYear != null) put("f[years][to_year]", toYear.toString())

            putCsv("f[age_ratings]", ageRatings) { it.value }
            putCsv("f[publish_statuses]", publishStatuses) { it.value }
            putCsv("f[production_statuses]", productionStatuses) { it.value }

            putIfNotBlank("f[sorting]", sorting?.value)

            applyFields(fields)
        }

        val json = client.get("$baseUrl/anime/catalog/releases", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    // Releases

    override suspend fun getRelease(
        key: AniLibertyReleaseKey,
        fields: AniLibertyReleaseFields?,
        queryFields: AniLibertyQueryFields?,
    ): AniLibertyRelease {
        val args = AniLibertyQueryParams.build {
            val includeValue = fields?.includeParam() ?: queryFields?.includeParam()
            val excludeValue = fields?.excludeParam() ?: queryFields?.excludeParam()
            putIfNotBlank("include", includeValue)
            putIfNotBlank("exclude", excludeValue)
        }

        val url = "$baseUrl/anime/releases/${key.asPathSegment()}"
        val json = client.get(url, args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getLatestReleases(
        limit: Int?,
        fields: AniLibertyQueryFields?,
    ): List<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            putIfPositive("limit", limit)
            applyFields(fields)
        }
        val json = client.get("$baseUrl/anime/releases/latest", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getRecommendedReleases(
        limit: Int?,
        releaseId: AniLibertyReleaseId?,
        fields: AniLibertyQueryFields?,
    ): List<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            putIfPositive("limit", limit)
            if (releaseId != null) put("release_id", releaseId.value.toString())
            applyFields(fields)
        }
        val json = client.get("$baseUrl/anime/releases/recommended", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getReleasesList(
        ids: List<AniLibertyReleaseId>?,
        aliases: List<AniLibertyReleaseAlias>?,
        page: Int,
        limit: Int,
        fields: AniLibertyQueryFields?,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            put("page", page.toString())
            put("limit", limit.toString())

            if (!ids.isNullOrEmpty()) put("ids", ids.joinToString(",") { it.value.toString() })
            if (!aliases.isNullOrEmpty()) put("aliases", aliases.joinToString(",") { it.value })

            applyFields(fields)
        }

        val json = client.get("$baseUrl/anime/releases/list", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    override suspend fun getRandomReleases(
        limit: Int,
        fields: AniLibertyQueryFields?,
    ): List<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            putIfPositive("limit", limit)
            applyFields(fields)
        }
        val json = client.get("$baseUrl/anime/releases/random", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getReleaseMembers(
        key: AniLibertyReleaseKey,
        fields: AniLibertyQueryFields?,
    ): List<AniLibertyReleaseMember> {
        val args = AniLibertyQueryParams.build { applyFields(fields) }
        val json = client.get("$baseUrl/anime/releases/${key.asPathSegment()}/members", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getReleaseEpisodesTimecodes(
        key: AniLibertyReleaseKey,
        fields: AniLibertyQueryFields?,
    ): List<AniLibertyReleaseEpisodeTimecode> {
        val args = AniLibertyQueryParams.build { applyFields(fields) }
        val json = client.get("$baseUrl/anime/releases/${key.asPathSegment()}/episodes/timecodes", args)
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getEpisode(
        releaseEpisodeId: AniLibertyReleaseEpisodeId,
        fields: AniLibertyQueryFields?,
    ): AniLibertyEpisode {
        val args = AniLibertyQueryParams.build { applyFields(fields) }
        val json = client.get("$baseUrl/anime/releases/episodes/${releaseEpisodeId.value}", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getEpisodeTimecode(
        releaseEpisodeId: AniLibertyReleaseEpisodeId,
        fields: AniLibertyQueryFields?,
    ): ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyEpisodeTimecode {
        val args = AniLibertyQueryParams.build { applyFields(fields) }
        val json = client.get("$baseUrl/anime/releases/episodes/${releaseEpisodeId.value}/timecode", args)
        return json.fetchResponse(moshi)
    }

    // OTP

    override suspend fun otpGet(deviceId: AniLibertyDeviceId): AniLibertyOtpGetResponse {
        val body = AniLibertyOtpGetBody(deviceId = deviceId.value)
        val json = client.postJson("$baseUrl/accounts/otp/get", toJsonObject(body))
        return json.fetchResponse(moshi)
    }

    override suspend fun otpAccept(code: AniLibertyOtpCode) {
        val body = AniLibertyOtpAcceptBody(code = code.value)
        client.postJson("$baseUrl/accounts/otp/accept", toJsonObject(body))
    }

    override suspend fun otpLogin(code: AniLibertyOtpCode, deviceId: AniLibertyDeviceId): AniLibertyAuthTokenResponse {
        val body = AniLibertyOtpLoginBody(
            code = code.value,
            deviceId = deviceId.value,
        )
        val json = client.postJson("$baseUrl/accounts/otp/login", toJsonObject(body))
        return json.fetchResponse(moshi)
    }

    // Auth

    override suspend fun login(login: String, password: String): AniLibertyAuthTokenResponse {
        val body = AniLibertyAuthLoginBody(
            login = login,
            password = password,
        )
        val json = client.postJson("$baseUrl/accounts/users/auth/login", toJsonObject(body))
        return json.fetchResponse(moshi)
    }

    override suspend fun socialLogin(provider: AniLibertySocialProvider): AniLibertySocialLoginResponse {
        val json = client.get("$baseUrl/accounts/users/auth/social/${provider.value}/login", emptyMap())
        return json.fetchResponse(moshi)
    }

    override suspend fun socialAuthenticate(state: String): AniLibertySocialAuthenticateResponse {
        val args = AniLibertyQueryParams.build { putIfNotBlank("state", state) }
        val json = client.get("$baseUrl/accounts/users/auth/social/authenticate", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun logout(): AniLibertyAuthTokenResponse {
        val json = client.postJson("$baseUrl/accounts/users/auth/logout", "{}")
        return json.fetchResponse(moshi)
    }

    override suspend fun passwordForget(email: AniLibertyEmail) {
        val body = AniLibertyEmailBody(email = email.value)
        client.postJson("$baseUrl/accounts/users/auth/password/forget", toJsonObject(body))
    }

    override suspend fun passwordReset(
        token: String,
        password: String,
        passwordConfirmation: String,
    ) {
        val body = AniLibertyPasswordResetBody(
            token = token,
            password = password,
            passwordConfirmation = passwordConfirmation,
        )
        client.postJson("$baseUrl/accounts/users/auth/password/reset", toJsonObject(body))
    }

    // References (Collections)

    override suspend fun getUserCollectionsReferenceAgeRatings(): List<AniLibertyReferenceValueLabelDescription> {
        val json = client.get("$baseUrl/accounts/users/me/collections/references/age-ratings", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserCollectionsReferenceGenres(): List<AniLibertyGenre> {
        val json = client.get("$baseUrl/accounts/users/me/collections/references/genres", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserCollectionsReferenceTypes(): List<AniLibertyReferenceValueDescription> {
        val json = client.get("$baseUrl/accounts/users/me/collections/references/types", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserCollectionsReferenceYears(): List<Int> {
        val json = client.get("$baseUrl/accounts/users/me/collections/references/years", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    // References (Favorites)

    override suspend fun getUserFavoritesReferenceAgeRatings(): List<AniLibertyReferenceValueLabelDescription> {
        val json = client.get("$baseUrl/accounts/users/me/favorites/references/age-ratings", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserFavoritesReferenceGenres(): List<AniLibertyGenre> {
        val json = client.get("$baseUrl/accounts/users/me/favorites/references/genres", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserFavoritesReferenceSorting(): List<AniLibertyReferenceValueDescription> {
        val json = client.get("$baseUrl/accounts/users/me/favorites/references/sorting", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserFavoritesReferenceTypes(): List<AniLibertyReferenceValueDescription> {
        val json = client.get("$baseUrl/accounts/users/me/favorites/references/types", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserFavoritesReferenceYears(): List<Int> {
        val json = client.get("$baseUrl/accounts/users/me/favorites/references/years", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    // Favorites

    override suspend fun getUserFavoriteIds(): List<AniLibertyReleaseId> {
        val json = client.get("$baseUrl/accounts/users/me/favorites/ids", emptyMap())
        val raw: List<Int> = json.fetchResponse(moshi)
        return raw.map { AniLibertyReleaseId(it) }
    }

    override suspend fun getUserFavoriteReleases(
        page: Int,
        limit: Int,
        fields: AniLibertyQueryFields?,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            put("page", page.toString())
            put("limit", limit.toString())
            applyFields(fields)
        }
        val json = client.get("$baseUrl/accounts/users/me/favorites/releases", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    override suspend fun getUserFavoriteReleasesFiltered(
        page: Int,
        limit: Int,
        years: List<Int>?,
        types: List<AniLibertyReleaseType>?,
        genres: List<Int>?,
        search: String?,
        sorting: AniLibertyFavoriteSorting?,
        ageRatings: List<AniLibertyAgeRating>?,
        fields: AniLibertyQueryFields?,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            put("page", page.toString())
            put("limit", limit.toString())

            putCsv("f[years]", years)
            putCsv("f[types]", types) { it.value }
            putCsv("f[genres]", genres)
            putIfNotBlank("f[search]", search)
            putIfNotBlank("f[sorting]", sorting?.value)
            putCsv("f[age_ratings]", ageRatings) { it.value }

            applyFields(fields)
        }

        val json = client.get("$baseUrl/accounts/users/me/favorites/releases", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    override suspend fun getUserFavoriteReleasesByBody(body: AniLibertyFavoriteReleasesBody): PaginatedResponse<AniLibertyRelease> {
        val json = client.postJson("$baseUrl/accounts/users/me/favorites/releases", toJsonObject(body))
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    override suspend fun addToFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId> {
        val body = releaseIds.distinctBy { it.value }.map { AniLibertyReleaseIdBody(it) }
        val json = client.postJson("$baseUrl/accounts/users/me/favorites", toJsonList(body))
        val raw: List<Int> = json.fetchResponse(moshi)
        return raw.map { AniLibertyReleaseId(it) }
    }

    override suspend fun removeFromFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId> {
        val body = releaseIds.distinctBy { it.value }.map { AniLibertyReleaseIdBody(it) }
        val json = client.deleteJson("$baseUrl/accounts/users/me/favorites", toJsonList(body))
        val raw: List<Int> = json.fetchResponse(moshi)
        return raw.map { AniLibertyReleaseId(it) }
    }

    // Collections

    override suspend fun getUserCollectionIds(): List<AniLibertyCollectionIdItem> {
        val json = client.get("$baseUrl/accounts/users/me/collections/ids", emptyMap())
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun getUserCollectionReleasesFiltered(
        page: Int,
        limit: Int,
        typeOfCollection: AniLibertyCollectionType,
        genres: List<Int>?,
        types: List<AniLibertyReleaseType>?,
        years: List<Int>?,
        search: String?,
        ageRatings: List<AniLibertyAgeRating>?,
        fields: AniLibertyQueryFields?,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = AniLibertyQueryParams.build {
            put("page", page.toString())
            put("limit", limit.toString())
            put("type_of_collection", typeOfCollection.value)

            putCsv("f[genres]", genres)
            putCsv("f[types]", types) { it.value }
            putCsv("f[years]", years)
            putIfNotBlank("f[search]", search)
            putCsv("f[age_ratings]", ageRatings) { it.value }

            applyFields(fields)
        }

        val json = client.get("$baseUrl/accounts/users/me/collections/releases", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    override suspend fun getUserCollectionReleasesByBody(body: AniLibertyCollectionsReleasesBody): PaginatedResponse<AniLibertyRelease> {
        val json = client.postJson("$baseUrl/accounts/users/me/collections/releases", toJsonObject(body))
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    override suspend fun addToCollections(items: List<AniLibertyCollectionAddBody>): List<AniLibertyCollectionIdItem> {
        val safeItems = items.distinctBy { it.releaseId to it.typeOfCollection }
        val json = client.postJson("$baseUrl/accounts/users/me/collections", toJsonList(safeItems))
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun removeFromCollections(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyCollectionIdItem> {
        val body = releaseIds.distinctBy { it.value }.map { AniLibertyReleaseIdBody(it) }
        val json = client.deleteJson("$baseUrl/accounts/users/me/collections", toJsonList(body))
        return json.fetchListOrNestedList(moshi)
    }

    // Views timecodes

    override suspend fun getUserViewTimecodes(since: String?): List<AniLibertyViewTimecode> {
        val args = AniLibertyQueryParams.build { putIfNotBlank("since", since) }
        val json = client.get("$baseUrl/accounts/users/me/views/timecodes", args)
        return json.fetchListOrNestedList(moshi)
    }

    override suspend fun upsertUserViewTimecodes(items: List<AniLibertyUserViewTimecodeUpsertBody>) {
        val safeItems = items.distinctBy { it.releaseEpisodeId }
        client.postJson("$baseUrl/accounts/users/me/views/timecodes", toJsonList(safeItems))
    }

    override suspend fun deleteUserViewTimecodes(items: List<AniLibertyUserViewTimecodeDeleteBody>) {
        val safeItems = items.distinctBy { it.releaseEpisodeId }
        client.deleteJson("$baseUrl/accounts/users/me/views/timecodes", toJsonList(safeItems))
    }

    // Profile

    override suspend fun getMyProfile(fields: AniLibertyQueryFields?): AniLibertyUserProfile {
        val args = AniLibertyQueryParams.build { applyFields(fields) }
        val json = client.get("$baseUrl/accounts/users/me/profile", args)
        return json.fetchResponse(moshi)
    }

    // Views history

    override suspend fun getUserViewsHistory(
        page: Int,
        limit: Int,
        fields: AniLibertyQueryFields?,
    ): PaginatedResponse<AniLibertyUserViewHistoryItem> {
        val args = AniLibertyQueryParams.build {
            put("page", page.toString())
            put("limit", limit.toString())
            applyFields(fields)
        }

        val json = client.get("$baseUrl/accounts/users/me/views/history", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyUserViewHistoryItem>(moshi)
        return response.toPaginatedResponse()
    }


    // Franchises

    override suspend fun getFranchises(
        fields: AniLibertyQueryFields?,
    ): List<AniLibertyFranchise> {
        val args = mutableMapOf<String, String>()
        fields.applyTo(args)

        val json = client.get("$baseUrl/anime/franchises", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getFranchise(
        franchiseId: AniLibertyFranchiseId,
        fields: AniLibertyQueryFields?,
    ): AniLibertyFranchiseDetails {
        val args = mutableMapOf<String, String>()
        fields.applyTo(args)

        val json = client.get("$baseUrl/anime/franchises/${franchiseId.value}", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getRandomFranchises(
        limit: Int?,
        fields: AniLibertyQueryFields?,
    ): List<AniLibertyFranchise> {
        val args = mutableMapOf<String, String>()
        args.putIfPositive("limit", limit)
        fields.applyTo(args)

        val json = client.get("$baseUrl/anime/franchises/random", args)
        return json.fetchResponse(moshi)
    }

    override suspend fun getFranchisesByRelease(
        releaseId: AniLibertyReleaseId,
        fields: AniLibertyQueryFields?,
    ): AniLibertyFranchisesByRelease {
        val args = mutableMapOf<String, String>()
        fields.applyTo(args)

        val json = client.get("$baseUrl/anime/franchises/release/${releaseId.value}", args)
        return json.fetchResponse(moshi)
    }

}
