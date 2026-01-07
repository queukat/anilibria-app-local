package ru.radiationx.data.datasource.remote.aniliberty.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyAgeRating
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogProductionStatus
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogPublishStatus
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPublishDay
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseMemberRoleType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertySeason

/**
 * Подключается в Moshi.Builder().add(AniLibertyValueAdapters)
 *
 * Сейчас это может быть не подключено в DI, но файл готов.
 */
object AniLibertyValueAdapters {

    @FromJson fun fromReleaseType(value: String): AniLibertyReleaseType = AniLibertyReleaseType(value)
    @ToJson fun toReleaseType(value: AniLibertyReleaseType): String = value.value

    @FromJson fun fromSeason(value: String): AniLibertySeason = AniLibertySeason(value)
    @ToJson fun toSeason(value: AniLibertySeason): String = value.value

    @FromJson fun fromAgeRating(value: String): AniLibertyAgeRating = AniLibertyAgeRating(value)
    @ToJson fun toAgeRating(value: AniLibertyAgeRating): String = value.value

    @FromJson fun fromPublishDay(value: Int): AniLibertyPublishDay = AniLibertyPublishDay(value)
    @ToJson fun toPublishDay(value: AniLibertyPublishDay): Int = value.value

    @FromJson fun fromMemberRoleType(value: String): AniLibertyReleaseMemberRoleType = AniLibertyReleaseMemberRoleType(value)
    @ToJson fun toMemberRoleType(value: AniLibertyReleaseMemberRoleType): String = value.value

    @FromJson fun fromCatalogSorting(value: String): AniLibertyCatalogSorting = AniLibertyCatalogSorting(value)
    @ToJson fun toCatalogSorting(value: AniLibertyCatalogSorting): String = value.value

    @FromJson fun fromCatalogPublishStatus(value: String): AniLibertyCatalogPublishStatus = AniLibertyCatalogPublishStatus(value)
    @ToJson fun toCatalogPublishStatus(value: AniLibertyCatalogPublishStatus): String = value.value

    @FromJson fun fromCatalogProductionStatus(value: String): AniLibertyCatalogProductionStatus = AniLibertyCatalogProductionStatus(value)
    @ToJson fun toCatalogProductionStatus(value: AniLibertyCatalogProductionStatus): String = value.value
}
