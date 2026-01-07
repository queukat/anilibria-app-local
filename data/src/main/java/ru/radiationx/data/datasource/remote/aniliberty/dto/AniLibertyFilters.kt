package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyAgeRating
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFavoriteSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseType

private fun List<Int>.toCsv(): String = joinToString(",")
private fun <T> List<T>.toCsv(mapper: (T) -> String): String = joinToString(",") { mapper(it) }

@JsonClass(generateAdapter = true)
data class AniLibertyCommonFiltersBody(
    @Json(name = "genres") val genres: String? = null,
    @Json(name = "types") val types: List<String>? = null,
    @Json(name = "years") val years: String? = null,
    @Json(name = "search") val search: String? = null,
    @Json(name = "age_ratings") val ageRatings: List<String>? = null,
) {
    companion object {
        fun from(
            genres: List<Int>? = null,
            types: List<AniLibertyReleaseType>? = null,
            years: List<Int>? = null,
            search: String? = null,
            ageRatings: List<AniLibertyAgeRating>? = null,
        ): AniLibertyCommonFiltersBody = AniLibertyCommonFiltersBody(
            genres = genres?.takeIf { it.isNotEmpty() }?.toCsv(),
            types = types?.takeIf { it.isNotEmpty() }?.map { it.value },
            years = years?.takeIf { it.isNotEmpty() }?.toCsv(),
            search = search,
            ageRatings = ageRatings?.takeIf { it.isNotEmpty() }?.map { it.value },
        )
    }
}

@JsonClass(generateAdapter = true)
data class AniLibertyCollectionsReleasesBody(
    @Json(name = "page") val page: Int? = null,
    @Json(name = "limit") val limit: Int? = null,
    @Json(name = "type_of_collection") val typeOfCollection: String,
    @Json(name = "f") val f: AniLibertyCommonFiltersBody? = null,
    @Json(name = "include") val include: String? = null,
    @Json(name = "exclude") val exclude: String? = null,
) {
    constructor(
        page: Int? = null,
        limit: Int? = null,
        typeOfCollection: AniLibertyCollectionType,
        f: AniLibertyCommonFiltersBody? = null,
        include: String? = null,
        exclude: String? = null,
    ) : this(
        page = page,
        limit = limit,
        typeOfCollection = typeOfCollection.value,
        f = f,
        include = include,
        exclude = exclude,
    )
}

@JsonClass(generateAdapter = true)
data class AniLibertyFavoriteFiltersBody(
    @Json(name = "years") val years: String? = null,
    @Json(name = "types") val types: List<String>? = null,
    @Json(name = "genres") val genres: String? = null,
    @Json(name = "search") val search: String? = null,
    @Json(name = "sorting") val sorting: String? = null,
    @Json(name = "age_ratings") val ageRatings: List<String>? = null,
) {
    companion object {
        fun from(
            years: List<Int>? = null,
            types: List<AniLibertyReleaseType>? = null,
            genres: List<Int>? = null,
            search: String? = null,
            sorting: AniLibertyFavoriteSorting? = null,
            ageRatings: List<AniLibertyAgeRating>? = null,
        ): AniLibertyFavoriteFiltersBody = AniLibertyFavoriteFiltersBody(
            years = years?.takeIf { it.isNotEmpty() }?.toCsv(),
            types = types?.takeIf { it.isNotEmpty() }?.map { it.value },
            genres = genres?.takeIf { it.isNotEmpty() }?.toCsv(),
            search = search,
            sorting = sorting?.value,
            ageRatings = ageRatings?.takeIf { it.isNotEmpty() }?.map { it.value },
        )
    }
}

@JsonClass(generateAdapter = true)
data class AniLibertyFavoriteReleasesBody(
    @Json(name = "page") val page: Int? = null,
    @Json(name = "limit") val limit: Int? = null,
    @Json(name = "f") val f: AniLibertyFavoriteFiltersBody? = null,
    @Json(name = "include") val include: String? = null,
    @Json(name = "exclude") val exclude: String? = null,
)
