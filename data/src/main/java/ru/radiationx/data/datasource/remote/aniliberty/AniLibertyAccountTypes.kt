package ru.radiationx.data.datasource.remote.aniliberty

@JvmInline
value class AniLibertySocialProvider(val value: String) {
    companion object {
        val Vk = AniLibertySocialProvider("vk")
        val Google = AniLibertySocialProvider("google")
        val Patreon = AniLibertySocialProvider("patreon")
        val Discord = AniLibertySocialProvider("discord")
    }
}

/**
 * Тип коллекции пользователя.
 * Swagger: enums.accounts.users.user.collection.type
 *
 * Сервер может расширять значения, поэтому это value class, а не enum.
 */
@JvmInline
value class AniLibertyCollectionType(val value: String) {
    companion object {
        val Planned = AniLibertyCollectionType("PLANNED")
        val Watching = AniLibertyCollectionType("WATCHING")
        val Watched = AniLibertyCollectionType("WATCHED")
        val Postponed = AniLibertyCollectionType("POSTPONED")
        val Abandoned = AniLibertyCollectionType("ABANDONED")
    }
}

/**
 * Сортировка избранного.
 * Swagger: enums.accounts.users.user.favorite.filter.sorting
 *
 * OpenAPI enum:
 * CREATED_AT_DESC, CREATED_AT_ASC,
 * FRESH_AT_DESC, FRESH_AT_ASC,
 * RATING_DESC, RATING_ASC,
 * YEAR_DESC, YEAR_ASC
 */
@JvmInline
value class AniLibertyFavoriteSorting(val value: String) {
    companion object {
        val RatingDesc = AniLibertyFavoriteSorting("RATING_DESC")
        val RatingAsc = AniLibertyFavoriteSorting("RATING_ASC")

        // актуальные имена (как в каталоге)
        val FreshAtDesc = AniLibertyFavoriteSorting("FRESH_AT_DESC")
        val FreshAtAsc = AniLibertyFavoriteSorting("FRESH_AT_ASC")

        val CreatedAtDesc = AniLibertyFavoriteSorting("CREATED_AT_DESC")
        val CreatedAtAsc = AniLibertyFavoriteSorting("CREATED_AT_ASC")

        val YearDesc = AniLibertyFavoriteSorting("YEAR_DESC")
        val YearAsc = AniLibertyFavoriteSorting("YEAR_ASC")

        // backward aliases (если где-то уже использовались старые имена)
        val FreshDesc = FreshAtDesc
        val FreshAsc = FreshAtAsc
        val CreatedDesc = CreatedAtDesc
        val CreatedAsc = CreatedAtAsc
    }
}

/**
 * Тип релиза.
 * Swagger: enums.anime.releases.release.type
 *
 * enum из swagger:
 * TV, ONA, WEB, OVA, OAD, MOVIE, DORAMA, SPECIAL
 *
 * Сервер может расширять, поэтому value class. Основные значения фиксируем константами.
 */
@JvmInline
value class AniLibertyReleaseType(val value: String) {
    companion object {
        val Tv = AniLibertyReleaseType("TV")
        val Ona = AniLibertyReleaseType("ONA")
        val Web = AniLibertyReleaseType("WEB")
        val Ova = AniLibertyReleaseType("OVA")
        val Oad = AniLibertyReleaseType("OAD")
        val Movie = AniLibertyReleaseType("MOVIE")
        val Dorama = AniLibertyReleaseType("DORAMA")
        val Special = AniLibertyReleaseType("SPECIAL")
    }
}

/**
 * Сезон релиза.
 * Swagger: enums.anime.releases.release.season
 *
 * Важно: значения в swagger в нижнем регистре:
 * winter, spring, summer, autumn
 */
@JvmInline
value class AniLibertySeason(val value: String) {
    companion object {
        val Winter = AniLibertySeason("winter")
        val Spring = AniLibertySeason("spring")
        val Summer = AniLibertySeason("summer")
        val Autumn = AniLibertySeason("autumn")
    }
}

/**
 * Возрастной рейтинг релиза.
 * Swagger: enums.anime.releases.release.ageRating
 *
 * enum из swagger:
 * R0_PLUS, R6_PLUS, R12_PLUS, R16_PLUS, R18_PLUS
 *
 * Сервер может расширять, поэтому value class.
 */
@JvmInline
value class AniLibertyAgeRating(val value: String) {
    companion object {
        val R0Plus = AniLibertyAgeRating("R0_PLUS")
        val R6Plus = AniLibertyAgeRating("R6_PLUS")
        val R12Plus = AniLibertyAgeRating("R12_PLUS")
        val R16Plus = AniLibertyAgeRating("R16_PLUS")
        val R18Plus = AniLibertyAgeRating("R18_PLUS")
    }
}

/**
 * День выхода релиза.
 * Swagger: enums.anime.releases.release.publishDay
 *
 * enum из swagger: 1..7
 */
@JvmInline
value class AniLibertyPublishDay(val value: Int) {
    init {
        require(value in 1..7) { "AniLibertyPublishDay must be in 1..7" }
    }

    companion object {
        val Monday = AniLibertyPublishDay(1)
        val Tuesday = AniLibertyPublishDay(2)
        val Wednesday = AniLibertyPublishDay(3)
        val Thursday = AniLibertyPublishDay(4)
        val Friday = AniLibertyPublishDay(5)
        val Saturday = AniLibertyPublishDay(6)
        val Sunday = AniLibertyPublishDay(7)
    }
}

/**
 * Роль участника релиза.
 * Swagger: enums.anime.releases.release.member.role
 *
 * enum из swagger (в нижнем регистре):
 * poster, timing, voicing, editing, decorating, translating
 */
@JvmInline
value class AniLibertyReleaseMemberRoleType(val value: String) {
    companion object {
        val Poster = AniLibertyReleaseMemberRoleType("poster")
        val Timing = AniLibertyReleaseMemberRoleType("timing")
        val Voicing = AniLibertyReleaseMemberRoleType("voicing")
        val Editing = AniLibertyReleaseMemberRoleType("editing")
        val Decorating = AniLibertyReleaseMemberRoleType("decorating")
        val Translating = AniLibertyReleaseMemberRoleType("translating")
    }
}

/**
 * Статусы publish и production на самом релизе в swagger описаны как булевы поля:
 * is_ongoing, is_in_production.
 *
 * Оставляем эти типы только если они используются где-то ещё (например в других эндпоинтах).
 * Если нигде не нужны, их можно убрать позже.
 */
@JvmInline
value class AniLibertyPublishStatus(val value: String)

@JvmInline
value class AniLibertyProductionStatus(val value: String)

/**
 * Catalog filter: productionStatus.
 * Swagger: enums.anime.catalog.filter.productionStatus
 */
@JvmInline
value class AniLibertyCatalogProductionStatus(val value: String) {
    companion object {
        val IsInProduction = AniLibertyCatalogProductionStatus("IS_IN_PRODUCTION")
        val IsNotInProduction = AniLibertyCatalogProductionStatus("IS_NOT_IN_PRODUCTION")
    }
}

/**
 * Catalog filter: publishStatus.
 * Swagger: enums.anime.catalog.filter.publishStatus
 */
@JvmInline
value class AniLibertyCatalogPublishStatus(val value: String) {
    companion object {
        val IsOngoing = AniLibertyCatalogPublishStatus("IS_ONGOING")
        val IsNotOngoing = AniLibertyCatalogPublishStatus("IS_NOT_ONGOING")
    }
}

/**
 * Catalog filter: sorting.
 * Swagger: enums.anime.catalog.filter.sorting
 */
@JvmInline
value class AniLibertyCatalogSorting(val value: String) {
    companion object {
        val FreshAtDesc = AniLibertyCatalogSorting("FRESH_AT_DESC")
        val FreshAtAsc = AniLibertyCatalogSorting("FRESH_AT_ASC")
        val RatingDesc = AniLibertyCatalogSorting("RATING_DESC")
        val RatingAsc = AniLibertyCatalogSorting("RATING_ASC")
        val YearDesc = AniLibertyCatalogSorting("YEAR_DESC")
        val YearAsc = AniLibertyCatalogSorting("YEAR_ASC")
    }
}
