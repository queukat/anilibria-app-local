package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import ru.radiationx.data.ApiClient
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionAddBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionIdItem
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReleaseIdBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import ru.radiationx.data.datasource.remote.fetchResponse
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.entity.response.aniliberty.AniLibertyRelease
import javax.inject.Inject

/**
 * Клиент под AniLiberty v1 (https://aniliberty.top/api/v1).
 *
 * Ничего не ломает в старых API-классах: это отдельный вход.
 * Дальше в репозиториях можно поэтапно переходить с ReleaseApi/SearchApi/FavoriteApi
 * на этот клиент.
 */
class AniLibertyApi @Inject constructor(
    @ApiClient private val client: IClient,
    private val moshi: Moshi,
) {

    /**
     * В идеале вынести в конфиг, но для начала – хардкод.
     * На старый Api.DEFAULT_ADDRESS не завязано, чтобы не ломать обратную совместимость.
     */
    private val baseUrl = "https://aniliberty.top/api/v1"

    private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
        if (!value.isNullOrBlank()) this[key] = value
    }

    // region Catalog

    suspend fun getCatalogReleases(
        page: Int,
        limit: Int,
        search: String? = null,
        genres: List<Int>? = null,
        fromYear: Int? = null,
        toYear: Int? = null,
        seasons: List<String>? = null,
        types: List<String>? = null,
        ageRatings: List<String>? = null,
        publishStatuses: List<String>? = null,
        productionStatuses: List<String>? = null,
        sorting: String? = null,
        include: String? = null,
        exclude: String? = null,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = mutableMapOf(
            "page" to page.toString(),
            "limit" to limit.toString(),
        )

        args.putIfNotBlank("f[search]", search)

        genres?.takeIf { it.isNotEmpty() }?.let { args["f[genres]"] = it.joinToString(",") }
        seasons?.takeIf { it.isNotEmpty() }?.let { args["f[seasons]"] = it.joinToString(",") }
        types?.takeIf { it.isNotEmpty() }?.let { args["f[types]"] = it.joinToString(",") }

        if (fromYear != null) args["f[years][from_year]"] = fromYear.toString()
        if (toYear != null) args["f[years][to_year]"] = toYear.toString()

        ageRatings?.takeIf { it.isNotEmpty() }?.let { args["f[age_ratings]"] = it.joinToString(",") }
        publishStatuses?.takeIf { it.isNotEmpty() }?.let { args["f[publish_statuses]"] = it.joinToString(",") }
        productionStatuses?.takeIf { it.isNotEmpty() }?.let { args["f[production_statuses]"] = it.joinToString(",") }
        args.putIfNotBlank("f[sorting]", sorting)

        args.putIfNotBlank("include", include)
        args.putIfNotBlank("exclude", exclude)

        val json = client.get("$baseUrl/anime/catalog/releases", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    // endregion

    // region Releases

    suspend fun getRelease(idOrAlias: String, include: String? = null, exclude: String? = null): AniLibertyRelease {
        val args = mutableMapOf<String, String>()
        args.putIfNotBlank("include", include)
        args.putIfNotBlank("exclude", exclude)

        val url = "$baseUrl/anime/releases/$idOrAlias"
        val json = client.get(url, args)
        return json.fetchResponse(moshi)
    }

    suspend fun getLatestReleases(limit: Int? = null, include: String? = null, exclude: String? = null): List<AniLibertyRelease> {
        val args = mutableMapOf<String, String>()
        if (limit != null && limit > 0) args["limit"] = limit.toString()
        args.putIfNotBlank("include", include)
        args.putIfNotBlank("exclude", exclude)

        val json = client.get("$baseUrl/anime/releases/latest", args)
        return json.fetchResponse(moshi)
    }

    suspend fun getRecommendedReleases(
        limit: Int? = null,
        releaseId: Int? = null,
        include: String? = null,
        exclude: String? = null,
    ): List<AniLibertyRelease> {
        val args = mutableMapOf<String, String>()
        if (limit != null && limit > 0) args["limit"] = limit.toString()
        if (releaseId != null && releaseId > 0) args["release_id"] = releaseId.toString()
        args.putIfNotBlank("include", include)
        args.putIfNotBlank("exclude", exclude)

        val json = client.get("$baseUrl/anime/releases/recommended", args)
        return json.fetchResponse(moshi)
    }

    suspend fun getReleasesList(
        ids: List<Int>? = null,
        aliases: List<String>? = null,
        page: Int = 1,
        limit: Int = 10,
        include: String? = null,
        exclude: String? = null,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = mutableMapOf(
            "page" to page.toString(),
            "limit" to limit.toString(),
        )

        ids?.takeIf { it.isNotEmpty() }?.let { args["ids"] = it.joinToString(",") }
        aliases?.takeIf { it.isNotEmpty() }?.let { args["aliases"] = it.joinToString(",") }

        args.putIfNotBlank("include", include)
        args.putIfNotBlank("exclude", exclude)

        val json = client.get("$baseUrl/anime/releases/list", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    suspend fun getRandomReleases(limit: Int = 1, include: String? = null, exclude: String? = null): List<AniLibertyRelease> {
        val args = mutableMapOf<String, String>()
        if (limit > 0) args["limit"] = limit.toString()
        args.putIfNotBlank("include", include)
        args.putIfNotBlank("exclude", exclude)

        val json = client.get("$baseUrl/anime/releases/random", args)
        return json.fetchResponse(moshi)
    }

    suspend fun getReleaseMembers(idOrAlias: String): List<ru.radiationx.data.entity.response.aniliberty.AniLibertyReleaseMember> {
        val json = client.get("$baseUrl/anime/releases/$idOrAlias/members", emptyMap())
        return json.fetchResponse(moshi)
    }

    // endregion

    // region Favorites

    suspend fun getUserFavoriteIds(): List<Int> {
        val json = client.get("$baseUrl/accounts/users/me/favorites/ids", emptyMap())
        return json.fetchResponse(moshi)
    }

    suspend fun getUserFavoriteReleases(
        page: Int,
        limit: Int,
        include: String? = null,
        exclude: String? = null,
    ): PaginatedResponse<AniLibertyRelease> {
        val args = mutableMapOf(
            "page" to page.toString(),
            "limit" to limit.toString(),
        )
        args.putIfNotBlank("include", include)
        args.putIfNotBlank("exclude", exclude)

        val json = client.get("$baseUrl/accounts/users/me/favorites/releases", args)
        val response = json.fetchAniLibertyPaginated<AniLibertyRelease>(moshi)
        return response.toPaginatedResponse()
    }

    // endregion

    // Favorites

    suspend fun addToFavorites(releaseIds: List<Int>): List<Int> {
        val body = releaseIds.distinct().map { AniLibertyReleaseIdBody(it) }
        val jsonBody = moshi.adapter<List<AniLibertyReleaseIdBody>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, AniLibertyReleaseIdBody::class.java)
        ).toJson(body)

        val json = client.postJson("$baseUrl/accounts/users/me/favorites", jsonBody)
        return json.fetchResponse(moshi)
    }

    suspend fun removeFromFavorites(releaseIds: List<Int>): List<Int> {
        val body = releaseIds.distinct().map { AniLibertyReleaseIdBody(it) }
        val jsonBody = moshi.adapter<List<AniLibertyReleaseIdBody>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, AniLibertyReleaseIdBody::class.java)
        ).toJson(body)

        val json = client.deleteJson("$baseUrl/accounts/users/me/favorites", jsonBody)
        return json.fetchResponse(moshi)
    }

    // Collections

    suspend fun getUserCollectionIds(): List<AniLibertyCollectionIdItem> {
        val json = client.get("$baseUrl/accounts/users/me/collections/ids", emptyMap())
        return json.fetchListOrNestedList<AniLibertyCollectionIdItem>(moshi)
    }

    suspend fun addToCollections(items: List<AniLibertyCollectionAddBody>): List<AniLibertyCollectionIdItem> {
        val safeItems = items.distinctBy { it.releaseId to it.typeOfCollection }
        val jsonBody = moshi.adapter<List<AniLibertyCollectionAddBody>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, AniLibertyCollectionAddBody::class.java)
        ).toJson(safeItems)

        val json = client.postJson("$baseUrl/accounts/users/me/collections", jsonBody)
        return json.fetchListOrNestedList<AniLibertyCollectionIdItem>(moshi)
    }

    suspend fun removeFromCollections(releaseIds: List<Int>): List<AniLibertyCollectionIdItem> {
        val body = releaseIds.distinct().map { AniLibertyReleaseIdBody(it) }
        val jsonBody = moshi.adapter<List<AniLibertyReleaseIdBody>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, AniLibertyReleaseIdBody::class.java)
        ).toJson(body)

        val json = client.deleteJson("$baseUrl/accounts/users/me/collections", jsonBody)
        return json.fetchListOrNestedList<AniLibertyCollectionIdItem>(moshi)
    }

    // Views timecodes (tuple array response)

    suspend fun getUserViewTimecodes(since: String? = null): List<AniLibertyViewTimecode> {
        val args = mutableMapOf<String, String>()
        args.putIfNotBlank("since", since) // параметр есть в спеках :contentReference[oaicite:8]{index=8}
        val json = client.get("$baseUrl/accounts/users/me/views/timecodes", args)
        return json.fetchListOrNestedList<AniLibertyViewTimecode>(moshi)
    }


}
