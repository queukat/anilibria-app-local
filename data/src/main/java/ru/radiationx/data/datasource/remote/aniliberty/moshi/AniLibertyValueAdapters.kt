package ru.radiationx.data.datasource.remote.aniliberty.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyAgeRating
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogProductionStatus
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogPublishStatus
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCatalogSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyDeviceId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyEmail
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyFavoriteSorting
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyOtpCode
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyPublishDay
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseAlias
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseMemberRoleType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertySeason
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertySocialProvider
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyTeamId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyTeamRoleId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyTeamUserId
import java.lang.reflect.Type

object AniLibertyValueAdapters : JsonAdapter.Factory {

    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null

        val raw = Types.getRawType(type)

        return when (raw) {
            AniLibertyReleaseType::class.java ->
                StringValueClassAdapter(::AniLibertyReleaseType) { it.value }

            AniLibertySeason::class.java ->
                StringValueClassAdapter(::AniLibertySeason) { it.value }

            AniLibertyAgeRating::class.java ->
                StringValueClassAdapter(::AniLibertyAgeRating) { it.value }

            AniLibertyCatalogSorting::class.java ->
                StringValueClassAdapter(::AniLibertyCatalogSorting) { it.value }

            AniLibertyCatalogPublishStatus::class.java ->
                StringValueClassAdapter(::AniLibertyCatalogPublishStatus) { it.value }

            AniLibertyCatalogProductionStatus::class.java ->
                StringValueClassAdapter(::AniLibertyCatalogProductionStatus) { it.value }

            AniLibertyReleaseMemberRoleType::class.java ->
                StringValueClassAdapter(::AniLibertyReleaseMemberRoleType) { it.value }

            AniLibertyReleaseAlias::class.java ->
                StringValueClassAdapter(::AniLibertyReleaseAlias) { it.value }

            AniLibertyReleaseEpisodeId::class.java ->
                StringValueClassAdapter(::AniLibertyReleaseEpisodeId) { it.value }

            AniLibertyTeamId::class.java ->
                StringValueClassAdapter(::AniLibertyTeamId) { it.value }

            AniLibertyTeamRoleId::class.java ->
                StringValueClassAdapter(::AniLibertyTeamRoleId) { it.value }

            AniLibertyTeamUserId::class.java ->
                StringValueClassAdapter(::AniLibertyTeamUserId) { it.value }

            AniLibertyCollectionType::class.java ->
                StringValueClassAdapter(::AniLibertyCollectionType) { it.value }

            AniLibertyFavoriteSorting::class.java ->
                StringValueClassAdapter(::AniLibertyFavoriteSorting) { it.value }

            AniLibertySocialProvider::class.java ->
                StringValueClassAdapter(::AniLibertySocialProvider) { it.value }

            AniLibertyDeviceId::class.java ->
                StringValueClassAdapter(::AniLibertyDeviceId) { it.value }

            AniLibertyEmail::class.java ->
                StringValueClassAdapter(::AniLibertyEmail) { it.value }

            AniLibertyReleaseId::class.java ->
                IntValueClassAdapter(::AniLibertyReleaseId) { it.value }

            AniLibertyPublishDay::class.java ->
                IntValueClassAdapter(::AniLibertyPublishDay) { it.value }

            AniLibertyOtpCode::class.java ->
                IntValueClassAdapter(::AniLibertyOtpCode) { it.value }

            else -> null
        }
    }

    private class StringValueClassAdapter<T>(
        private val wrap: (String) -> T,
        private val unwrap: (T) -> String,
    ) : JsonAdapter<T>() {

        override fun fromJson(reader: JsonReader): T? {
            if (reader.peek() == JsonReader.Token.NULL) {
                reader.nextNull<Unit>()
                return null
            }
            return wrap(reader.nextString())
        }

        override fun toJson(writer: JsonWriter, value: T?) {
            if (value == null) {
                writer.nullValue()
                return
            }
            writer.value(unwrap(value))
        }
    }

    private class IntValueClassAdapter<T>(
        private val wrap: (Int) -> T,
        private val unwrap: (T) -> Int,
    ) : JsonAdapter<T>() {

        override fun fromJson(reader: JsonReader): T? {
            if (reader.peek() == JsonReader.Token.NULL) {
                reader.nextNull<Unit>()
                return null
            }
            return wrap(reader.nextInt())
        }

        override fun toJson(writer: JsonWriter, value: T?) {
            if (value == null) {
                writer.nullValue()
                return
            }
            writer.value(unwrap(value))
        }
    }
}
