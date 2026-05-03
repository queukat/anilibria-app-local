
package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.withTimeoutOrNull
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
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.collections.distinctBy

internal const val MAX_USER_VIEWS_HISTORY_LIMIT = 50

/**
 * AniLiberty API v1 client implementation.
 *
 * Important: this module is a scaffold right now.
 * It is not wired into production flows yet.
 * We shape types and boundaries first, then connect it to domain and UI.
 */
class AniLibertyApi
    @Inject
    constructor(
        @ApiClient private val client: IClient,
        moshi: Moshi,
    ) : AniLibertyClient {
        private val moshi: Moshi = ru.radiationx.data.datasource.remote.aniliberty.moshi.AniLibertyMoshi.configure(moshi)

        private object Config {
            const val BaseUrl: String = "https://aniliberty.top/api/v1"
            const val ScheduleRequestTimeoutMs: Long = 12_000L
            const val RecommendedMaxLimit: Int = 14
        }

        private val scheduleFallbackLogged = AtomicBoolean(false)

        private inline fun <reified T> toJsonList(items: List<T>): String {
            val type = Types.newParameterizedType(List::class.java, T::class.java)
            return moshi.adapter<List<T>>(type).toJson(items)
        }

        private inline fun <reified T> toJsonObject(body: T): String = moshi.adapter(T::class.java).toJson(body)

        private suspend fun requestScheduleWeek(args: Map<String, String>): AniLibertyScheduleWeekResponse? {
            val json =
                withTimeoutOrNull(Config.ScheduleRequestTimeoutMs) {
                    client.get("${Config.BaseUrl}/anime/schedule/week", args)
                } ?: return null
            return parseScheduleWeekResponseJson(json, moshi) { error ->
                Timber.w(error, "AniLiberty schedule/week: unsupported payload, fallback to empty list.")
            }
        }

        private suspend fun requestScheduleNow(args: Map<String, String>): AniLibertyScheduleNowResponse? {
            val json =
                withTimeoutOrNull(Config.ScheduleRequestTimeoutMs) {
                    client.get("${Config.BaseUrl}/anime/schedule/now", args)
                } ?: return null
            return runCatching {
                json.fetchResponse<AniLibertyScheduleNowResponse>(moshi)
            }.onFailure {
                Timber.w(it, "AniLiberty schedule/now: unsupported payload, fallback to empty object.")
            }.getOrNull()
        }

        private fun AniLibertyScheduleNowResponse.isMeaningfullyEmpty(): Boolean {
            return today.orEmpty().isEmpty() &&
                tomorrow.orEmpty().isEmpty() &&
                yesterday.orEmpty().isEmpty()
        }

        // Catalog

        override suspend fun getCatalogReleases(request: AniLibertyCatalogRequest): PaginatedResponse<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    put("page", request.page.value.toString())
                    put("limit", request.limit.value.toString())

                    putIfNotBlank("f[search]", request.search)
                    putCsv("f[genres]", request.genres)
                    putCsv("f[seasons]", request.seasons) { it.value }
                    putCsv("f[types]", request.types) { it.value }

                    if (request.fromYear != null) put("f[years][from_year]", request.fromYear.toString())
                    if (request.toYear != null) put("f[years][to_year]", request.toYear.toString())

                    putCsv("f[age_ratings]", request.ageRatings) { it.value }
                    putCsv("f[publish_statuses]", request.publishStatuses) { it.value }
                    putCsv("f[production_statuses]", request.productionStatuses) { it.value }

                    putIfNotBlank("f[sorting]", request.sorting?.value)
                    applyFields(request.fields)
                }

            val json = client.get("${Config.BaseUrl}/anime/catalog/releases", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

        // Releases

        override suspend fun getRelease(
            key: AniLibertyReleaseKey,
            fields: AniLibertyFieldSpec?,
        ): AniLibertyRelease {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val url = "${Config.BaseUrl}/anime/releases/${key.asPathSegment()}"
            val json = client.get(url, args)
            return json.fetchResponse(moshi)
        }

        override suspend fun getLatestReleases(
            limit: Int?,
            fields: AniLibertyFieldSpec?,
        ): List<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    putIfPositive("limit", limit)
                    applyFields(fields)
                }
            val json = client.get("${Config.BaseUrl}/anime/releases/latest", args)
            return json.fetchListOrNestedList<AniLibertyRelease>(moshi)
        }

        override suspend fun getRecommendedReleases(
            limit: Int?,
            releaseId: AniLibertyReleaseId?,
            fields: AniLibertyFieldSpec?,
        ): List<AniLibertyRelease> {
            val safeLimit = limit?.coerceIn(1, Config.RecommendedMaxLimit)
            val baseArgs =
                AniLibertyQueryParams.build {
                    putIfPositive("limit", safeLimit)
                    if (releaseId != null) put("release_id", releaseId.value.toString())
                }
            val withFieldsArgs =
                AniLibertyQueryParams.build {
                    baseArgs.forEach { (key, value) -> put(key, value) }
                    applyFields(fields)
                }

            val withFields =
                client
                    .get("${Config.BaseUrl}/anime/releases/recommended", withFieldsArgs)
                    .fetchListOrNestedList<AniLibertyRelease>(moshi)

            if (fields == null || withFields.isNotEmpty()) {
                return withFields
            }

            val fallback =
                client
                    .get("${Config.BaseUrl}/anime/releases/recommended", baseArgs)
                    .fetchListOrNestedList<AniLibertyRelease>(moshi)

            if (fallback.isNotEmpty()) {
                Timber.w(
                    "AniLiberty releases/recommended: include/exclude returned empty, fallback without fields returned %d items.",
                    fallback.size,
                )
            }

            return fallback
        }

        override suspend fun getReleasesList(
            ids: List<AniLibertyReleaseId>?,
            aliases: List<AniLibertyReleaseAlias>?,
            page: Int,
            limit: Int,
            fields: AniLibertyFieldSpec?,
        ): PaginatedResponse<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    put("page", page.toString())
                    put("limit", limit.toString())

                    if (!ids.isNullOrEmpty()) put("ids", ids.joinToString(",") { it.value.toString() })
                    if (!aliases.isNullOrEmpty()) put("aliases", aliases.joinToString(",") { it.value })

                    applyFields(fields)
                }

            val json = client.get("${Config.BaseUrl}/anime/releases/list", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

        override suspend fun getRandomReleases(
            limit: Int,
            fields: AniLibertyFieldSpec?,
        ): List<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    putIfPositive("limit", limit)
                    applyFields(fields)
                }
            val json = client.get("${Config.BaseUrl}/anime/releases/random", args)
            return json.fetchResponse(moshi)
        }

        override suspend fun getReleaseMembers(
            key: AniLibertyReleaseKey,
            fields: AniLibertyFieldSpec?,
        ): List<AniLibertyReleaseMember> {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/releases/${key.asPathSegment()}/members", args)
            return json.fetchResponse(moshi)
        }

        override suspend fun getReleaseEpisodesTimecodes(
            key: AniLibertyReleaseKey,
            fields: AniLibertyFieldSpec?,
        ): List<AniLibertyReleaseEpisodeTimecode> {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/releases/${key.asPathSegment()}/episodes/timecodes", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getEpisode(
            releaseEpisodeId: AniLibertyReleaseEpisodeId,
            fields: AniLibertyFieldSpec?,
        ): AniLibertyEpisode {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/releases/episodes/${releaseEpisodeId.value}", args)
            return json.fetchResponse(moshi)
        }

        override suspend fun getEpisodeTimecode(
            releaseEpisodeId: AniLibertyReleaseEpisodeId,
            fields: AniLibertyFieldSpec?,
        ): ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyEpisodeTimecode {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/releases/episodes/${releaseEpisodeId.value}/timecode", args)
            return json.fetchResponse(moshi)
        }

        // OTP

        override suspend fun otpGet(deviceId: AniLibertyDeviceId): AniLibertyOtpGetResponse {
            val body = AniLibertyOtpGetBody(deviceId = deviceId.value)
            val json = client.postJson("${Config.BaseUrl}/accounts/otp/get", toJsonObject(body))
            return json.fetchResponse(moshi)
        }

        override suspend fun otpAccept(code: AniLibertyOtpCode) {
            val body = AniLibertyOtpAcceptBody(code = code.value)
            client.postJson("${Config.BaseUrl}/accounts/otp/accept", toJsonObject(body))
        }

        override suspend fun otpLogin(
            code: AniLibertyOtpCode,
            deviceId: AniLibertyDeviceId,
        ): AniLibertyAuthTokenResponse {
            val body = AniLibertyOtpLoginBody(code = code.value, deviceId = deviceId.value)
            val json = client.postJson("${Config.BaseUrl}/accounts/otp/login", toJsonObject(body))
            return json.fetchResponse(moshi)
        }

        // Auth

        override suspend fun login(
            login: String,
            password: String,
        ): AniLibertyAuthTokenResponse = login(login = login, password = password, code2fa = null)

        /**
         * Login with optional 2FA code.
         *
         * `code2fa` is sent as `fa2code` (legacy field name), because some backends keep this contract.
         * If backend ignores it, request still succeeds.
         */
        suspend fun login(
            login: String,
            password: String,
            code2fa: String?,
        ): AniLibertyAuthTokenResponse {
            val body =
                AniLibertyAuthLoginBody(
                    login = login,
                    password = password,
                    fa2Code = code2fa?.trim()?.takeIf { it.isNotEmpty() },
                )
            val json = client.postJson("${Config.BaseUrl}/accounts/users/auth/login", toJsonObject(body))
            return json.fetchResponse(moshi)
        }

        override suspend fun socialLogin(provider: AniLibertySocialProvider): AniLibertySocialLoginResponse {
            val json = client.get("${Config.BaseUrl}/accounts/users/auth/social/${provider.value}/login", emptyMap())
            return json.fetchResponse(moshi)
        }

        override suspend fun socialAuthenticate(state: String): AniLibertySocialAuthenticateResponse {
            val args = AniLibertyQueryParams.build { putIfNotBlank("state", state) }
            val json = client.get("${Config.BaseUrl}/accounts/users/auth/social/authenticate", args)
            return json.fetchResponse(moshi)
        }

        override suspend fun logout(): AniLibertyAuthTokenResponse {
            val json = client.postJson("${Config.BaseUrl}/accounts/users/auth/logout", "{}")
            return json.fetchResponse(moshi)
        }

        override suspend fun passwordForget(email: AniLibertyEmail) {
            val body = AniLibertyEmailBody(email = email.value)
            client.postJson("${Config.BaseUrl}/accounts/users/auth/password/forget", toJsonObject(body))
        }

        override suspend fun passwordReset(
            token: String,
            password: String,
            passwordConfirmation: String,
        ) {
            val body =
                AniLibertyPasswordResetBody(
                    token = token,
                    password = password,
                    passwordConfirmation = passwordConfirmation,
                )
            client.postJson("${Config.BaseUrl}/accounts/users/auth/password/reset", toJsonObject(body))
        }

        // References (Collections)

        override suspend fun getUserCollectionsReferenceAgeRatings(): List<AniLibertyReferenceValueLabelDescription> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/collections/references/age-ratings", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserCollectionsReferenceGenres(): List<AniLibertyGenre> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/collections/references/genres", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserCollectionsReferenceTypes(): List<AniLibertyReferenceValueDescription> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/collections/references/types", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserCollectionsReferenceYears(): List<Int> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/collections/references/years", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        // References (Favorites)

        override suspend fun getUserFavoritesReferenceAgeRatings(): List<AniLibertyReferenceValueLabelDescription> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/references/age-ratings", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserFavoritesReferenceGenres(): List<AniLibertyGenre> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/references/genres", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserFavoritesReferenceSorting(): List<AniLibertyReferenceValueDescription> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/references/sorting", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserFavoritesReferenceTypes(): List<AniLibertyReferenceValueDescription> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/references/types", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserFavoritesReferenceYears(): List<Int> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/references/years", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        // Favorites

        override suspend fun getUserFavoriteIds(): List<AniLibertyReleaseId> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/ids", emptyMap())
            val raw: List<Int> = json.fetchResponse(moshi)
            return raw.map { AniLibertyReleaseId(it) }
        }

        override suspend fun getUserFavoriteReleases(
            page: Int,
            limit: Int,
            fields: AniLibertyFieldSpec?,
        ): PaginatedResponse<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    put("page", page.toString())
                    put("limit", limit.toString())
                    applyFields(fields)
                }
            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/releases", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

        override suspend fun getUserFavoriteReleasesFiltered(
            request: AniLibertyFavoritesFilterRequest,
        ): PaginatedResponse<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    put("page", request.page.value.toString())
                    put("limit", request.limit.value.toString())

                    putCsv("f[years]", request.years)
                    putCsv("f[types]", request.types) { it.value }
                    putCsv("f[genres]", request.genres)
                    putIfNotBlank("f[search]", request.search)
                    putIfNotBlank("f[sorting]", request.sorting?.value)
                    putCsv("f[age_ratings]", request.ageRatings) { it.value }

                    applyFields(request.fields)
                }

            val json = client.get("${Config.BaseUrl}/accounts/users/me/favorites/releases", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

        suspend fun getUserFavoriteReleasesByBody(body: AniLibertyFavoriteReleasesBody): PaginatedResponse<AniLibertyRelease> {
            val json = client.postJson("${Config.BaseUrl}/accounts/users/me/favorites/releases", toJsonObject(body))
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

        override suspend fun addToFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId> {
            val body = releaseIds.distinctBy { it.value }.map { AniLibertyReleaseIdBody.from(it) }
            val json = client.postJson("${Config.BaseUrl}/accounts/users/me/favorites", toJsonList(body))
            val raw: List<Int> = json.fetchResponse(moshi)
            return raw.map { AniLibertyReleaseId(it) }
        }

        override suspend fun removeFromFavorites(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyReleaseId> {
            val body = releaseIds.distinctBy { it.value }.map { AniLibertyReleaseIdBody.from(it) }
            val json = client.deleteJson("${Config.BaseUrl}/accounts/users/me/favorites", toJsonList(body))
            val raw: List<Int> = json.fetchResponse(moshi)
            return raw.map { AniLibertyReleaseId(it) }
        }

        // Collections

        override suspend fun getUserCollectionIds(): List<AniLibertyCollectionIdItem> {
            val json = client.get("${Config.BaseUrl}/accounts/users/me/collections/ids", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getUserCollectionReleasesFiltered(
            request: AniLibertyCollectionsFilterRequest,
        ): PaginatedResponse<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    put("page", request.page.value.toString())
                    put("limit", request.limit.value.toString())
                    put("type_of_collection", request.typeOfCollection.value)

                    putCsv("f[genres]", request.genres)
                    putCsv("f[types]", request.types) { it.value }
                    putCsv("f[years]", request.years)
                    putIfNotBlank("f[search]", request.search)
                    putCsv("f[age_ratings]", request.ageRatings) { it.value }

                    applyFields(request.fields)
                }

            val json = client.get("${Config.BaseUrl}/accounts/users/me/collections/releases", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

        override suspend fun getUserCollectionReleasesByBody(
            body: AniLibertyCollectionsReleasesBody,
        ): PaginatedResponse<AniLibertyRelease> {
            val json = client.postJson("${Config.BaseUrl}/accounts/users/me/collections/releases", toJsonObject(body))
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

        override suspend fun addToCollections(items: List<AniLibertyCollectionAddBody>): List<AniLibertyCollectionIdItem> {
            val safeItems = items.distinctBy { it.releaseId to it.typeOfCollection }
            val json = client.postJson("${Config.BaseUrl}/accounts/users/me/collections", toJsonList(safeItems))
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun removeFromCollections(releaseIds: List<AniLibertyReleaseId>): List<AniLibertyCollectionIdItem> {
            val body =
                releaseIds
                    .distinctBy { it.value }
                    .map { AniLibertyReleaseIdBody.from(it) }

            val json = client.deleteJson("${Config.BaseUrl}/accounts/users/me/collections", toJsonList(body))
            return json.fetchListOrNestedList(moshi)
        }

        // Views timecodes

        override suspend fun getUserViewTimecodes(since: String?): List<AniLibertyViewTimecode> {
            val args = AniLibertyQueryParams.build { putIfNotBlank("since", since) }
            val json = client.get("${Config.BaseUrl}/accounts/users/me/views/timecodes", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun upsertUserViewTimecodes(items: List<AniLibertyUserViewTimecodeUpsertBody>) {
            val safeItems = items.distinctBy { it.releaseEpisodeId }
            client.postJson("${Config.BaseUrl}/accounts/users/me/views/timecodes", toJsonList(safeItems))
        }

        override suspend fun deleteUserViewTimecodes(items: List<AniLibertyUserViewTimecodeDeleteBody>) {
            val safeItems = items.distinctBy { it.releaseEpisodeId }
            client.deleteJson("${Config.BaseUrl}/accounts/users/me/views/timecodes", toJsonList(safeItems))
        }

        // Profile

        override suspend fun getMyProfile(fields: AniLibertyFieldSpec?): AniLibertyUserProfile {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/accounts/users/me/profile", args)
            return json.fetchResponse(moshi)
        }

        // Views history

        override suspend fun getUserViewsHistory(
            page: Int,
            limit: Int,
            fields: AniLibertyFieldSpec?,
        ): PaginatedResponse<AniLibertyUserViewHistoryItem> {
            // Live AniLiberty rejects limit > 50 with HTTP 422, so keep this guardrail
            // in the client even if current callers already request smaller pages.
            val safeLimit = limit.coerceIn(1, MAX_USER_VIEWS_HISTORY_LIMIT)
            val args =
                AniLibertyQueryParams.build {
                    put("page", page.toString())
                    put("limit", safeLimit.toString())
                    applyFields(fields)
                }
            val json = client.get("${Config.BaseUrl}/accounts/users/me/views/history", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyUserViewHistoryItem>(moshi)
            return response.toPaginatedResponse()
        }

        // Franchises

        override suspend fun getFranchises(fields: AniLibertyFieldSpec?): List<AniLibertyFranchise> {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/franchises", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getFranchise(
            franchiseId: AniLibertyFranchiseId,
            fields: AniLibertyFieldSpec?,
        ): AniLibertyFranchiseDetails {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/franchises/${franchiseId.value}", args)
            return json.fetchResponse(moshi)
        }

        override suspend fun getRandomFranchises(
            limit: Int?,
            fields: AniLibertyFieldSpec?,
        ): List<AniLibertyFranchise> {
            val args =
                AniLibertyQueryParams.build {
                    putIfPositive("limit", limit)
                    applyFields(fields)
                }
            val json = client.get("${Config.BaseUrl}/anime/franchises/random", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getFranchisesByRelease(
            releaseId: AniLibertyReleaseId,
            fields: AniLibertyFieldSpec?,
        ): AniLibertyFranchisesByRelease {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/franchises/release/${releaseId.value}", args)
            return json.fetchListOrNestedList(moshi)
        }

        // Teams

        override suspend fun getTeams(fields: AniLibertyFieldSpec?): List<AniLibertyTeam> {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/teams/", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getTeamRoles(fields: AniLibertyFieldSpec?): List<AniLibertyTeamRole> {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/teams/roles", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getTeamUsers(fields: AniLibertyFieldSpec?): List<AniLibertyTeamUserItem> {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/teams/users", args)
            return json.fetchListOrNestedList(moshi)
        }

        // Catalog references

        override suspend fun getCatalogReferenceAgeRatings(): List<AniLibertyCatalogReferenceAgeRating> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/age-ratings", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getCatalogReferenceGenres(): List<AniLibertyGenre> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/genres", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getCatalogReferenceProductionStatuses(): List<AniLibertyCatalogReferenceProductionStatus> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/production-statuses", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getCatalogReferencePublishStatuses(): List<AniLibertyCatalogReferencePublishStatus> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/publish-statuses", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getCatalogReferenceSeasons(): List<AniLibertyCatalogReferenceSeason> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/seasons", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getCatalogReferenceSorting(): List<AniLibertyCatalogReferenceSorting> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/sorting", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getCatalogReferenceTypes(): List<AniLibertyCatalogReferenceType> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/types", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getCatalogReferenceYears(): List<Int> {
            val json = client.get("${Config.BaseUrl}/anime/catalog/references/years", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

// Genres

        override suspend fun getGenres(fields: AniLibertyFieldSpec?): List<AniLibertyGenre> {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/genres", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getGenre(
            genreId: AniLibertyGenreId,
            fields: AniLibertyFieldSpec?,
        ): AniLibertyGenre {
            val args = AniLibertyQueryParams.build { applyFields(fields) }
            val json = client.get("${Config.BaseUrl}/anime/genres/${genreId.value}", args)
            return json.fetchResponse(moshi)
        }

        override suspend fun getRandomGenres(
            limit: Int?,
            fields: AniLibertyFieldSpec?,
        ): List<AniLibertyGenre> {
            val args =
                AniLibertyQueryParams.build {
                    putIfPositive("limit", limit)
                    applyFields(fields)
                }
            val json = client.get("${Config.BaseUrl}/anime/genres/random", args)
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getGenreReleases(
            genreId: AniLibertyGenreId,
            page: Int,
            limit: Int,
            fields: AniLibertyFieldSpec?,
        ): ru.radiationx.data.entity.response.PaginatedResponse<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    put("page", page.toString())
                    put("limit", limit.toString())
                    applyFields(fields)
                }
            val json = client.get("${Config.BaseUrl}/anime/genres/${genreId.value}/releases", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
            return response.toPaginatedResponse()
        }

// Schedule

        override suspend fun getScheduleNow(fields: AniLibertyFieldSpec?): AniLibertyScheduleNowResponse {
            val primaryResponse =
                requestScheduleNow(emptyMap())
                    ?: AniLibertyScheduleNowResponse(
                        today = emptyList(),
                        tomorrow = emptyList(),
                        yesterday = emptyList(),
                    )
            if (fields != null && primaryResponse.isMeaningfullyEmpty()) {
                // OpenAPI allows include/exclude for schedule endpoints, but production payload
                // with params is unstable for week/now in real traffic. We intentionally
                // keep a no-args production request to avoid empty schedules.
                if (scheduleFallbackLogged.compareAndSet(false, true)) {
                    Timber.w("AniLiberty schedule/now: include/exclude disabled in production request due unstable payload.")
                }
            }
            return primaryResponse
        }

        override suspend fun getScheduleWeek(fields: AniLibertyFieldSpec?): AniLibertyScheduleWeekResponse {
            val primaryResponse =
                requestScheduleWeek(emptyMap())
                    ?: AniLibertyScheduleWeekResponse(data = emptyList())
            if (fields != null && primaryResponse.data.orEmpty().isEmpty()) {
                if (scheduleFallbackLogged.compareAndSet(false, true)) {
                    Timber.w("AniLiberty schedule/week: include/exclude disabled in production request due unstable payload.")
                }
            }
            return primaryResponse
        }

// Torrents

        override suspend fun getTorrents(
            page: Int,
            limit: Int,
        ): ru.radiationx.data.entity.response.PaginatedResponse<AniLibertyTorrent> {
            val args =
                AniLibertyQueryParams.build {
                    put("page", page.toString())
                    put("limit", limit.toString())
                }
            val json = client.get("${Config.BaseUrl}/anime/torrents", args)
            val response = json.fetchAniLibertyPaginated<AniLibertyTorrent>(moshi)
            return response.toPaginatedResponse()
        }

        override suspend fun getTorrent(key: AniLibertyTorrentKey): AniLibertyTorrent {
            val json = client.get("${Config.BaseUrl}/anime/torrents/${key.asPathSegment()}", emptyMap())
            return json.fetchResponse(moshi)
        }

        override suspend fun getTorrentFile(key: AniLibertyTorrentKey): String {
            return client.get("${Config.BaseUrl}/anime/torrents/${key.asPathSegment()}/file", emptyMap())
        }

        override suspend fun getTorrentsByRelease(releaseId: AniLibertyReleaseId): List<AniLibertyTorrent> {
            val json = client.get("${Config.BaseUrl}/anime/torrents/release/${releaseId.value}", emptyMap())
            return json.fetchListOrNestedList(moshi)
        }

        override suspend fun getTorrentsRss(): String {
            return client.get("${Config.BaseUrl}/anime/torrents/rss", emptyMap())
        }

        override suspend fun getTorrentsRssByRelease(releaseId: AniLibertyReleaseId): String {
            return client.get("${Config.BaseUrl}/anime/torrents/rss/release/${releaseId.value}", emptyMap())
        }

// Media

        override suspend fun getMediaVasts(): List<AniLibertyVast> {
            val json = client.get("${Config.BaseUrl}/media/vasts", emptyMap())
            return json.fetchResponse(moshi)
        }

        override suspend fun getMediaManifestXml(): String {
            return client.get("${Config.BaseUrl}/media/manifest.xml", emptyMap())
        }

        override suspend fun getMediaPromotions(): AniLibertyMediaPromotionsResponse {
            val json = client.get("${Config.BaseUrl}/media/promotions", emptyMap())
            return json.fetchResponse(moshi)
        }

        override suspend fun getMediaVideos(): AniLibertyMediaVideosResponse {
            val json = client.get("${Config.BaseUrl}/media/videos", emptyMap())
            return json.fetchResponse(moshi)
        }

// App

        override suspend fun getAppStatus(): AniLibertyAppStatus {
            val json = client.get("${Config.BaseUrl}/app/status", emptyMap())
            return json.fetchResponse(moshi)
        }

        override suspend fun searchAppReleases(request: AniLibertyAppSearchReleasesRequest): List<AniLibertyRelease> {
            val args =
                AniLibertyQueryParams.build {
                    put("query", request.query)
                    applyFields(request.fields)
                }

            val json = client.get("${Config.BaseUrl}/app/search/releases", args)
            return json.fetchListOrNestedList(moshi)
        }
    }
