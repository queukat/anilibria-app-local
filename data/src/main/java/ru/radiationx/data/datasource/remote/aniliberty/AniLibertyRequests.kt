package ru.radiationx.data.datasource.remote.aniliberty

data class AniLibertyPage(val value: Int) {
    init {
        require(value > 0) { "AniLibertyPage must be positive" }
    }
}

data class AniLibertyLimit(val value: Int) {
    init {
        require(value > 0) { "AniLibertyLimit must be positive" }
    }
}

data class AniLibertyCatalogRequest(
    val page: AniLibertyPage = AniLibertyPage(1),
    val limit: AniLibertyLimit = AniLibertyLimit(10),

    val search: String? = null,
    val genres: List<Int>? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val seasons: List<AniLibertySeason>? = null,
    val types: List<AniLibertyReleaseType>? = null,
    val ageRatings: List<AniLibertyAgeRating>? = null,
    val publishStatuses: List<AniLibertyCatalogPublishStatus>? = null,
    val productionStatuses: List<AniLibertyCatalogProductionStatus>? = null,
    val sorting: AniLibertyCatalogSorting? = null,

    val fields: AniLibertyFieldSpec? = null,
)

data class AniLibertyFavoritesFilterRequest(
    val page: AniLibertyPage = AniLibertyPage(1),
    val limit: AniLibertyLimit = AniLibertyLimit(10),

    val years: List<Int>? = null,
    val types: List<AniLibertyReleaseType>? = null,
    val genres: List<Int>? = null,
    val search: String? = null,
    val sorting: AniLibertyFavoriteSorting? = null,
    val ageRatings: List<AniLibertyAgeRating>? = null,

    val fields: AniLibertyFieldSpec? = null,
)

data class AniLibertyCollectionsFilterRequest(
    val page: AniLibertyPage = AniLibertyPage(1),
    val limit: AniLibertyLimit = AniLibertyLimit(10),

    val typeOfCollection: AniLibertyCollectionType,
    val genres: List<Int>? = null,
    val types: List<AniLibertyReleaseType>? = null,
    val years: List<Int>? = null,
    val search: String? = null,
    val ageRatings: List<AniLibertyAgeRating>? = null,

    val fields: AniLibertyFieldSpec? = null,
)
