package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId

@JsonClass(generateAdapter = true)
data class AniLibertyCollectionAddBody(
    @Json(name = "release_id") val releaseId: AniLibertyReleaseId,
    @Json(name = "type_of_collection") val typeOfCollection: AniLibertyCollectionType,
) {
    companion object {
        fun from(
            releaseId: AniLibertyReleaseId,
            type: AniLibertyCollectionType,
        ): AniLibertyCollectionAddBody {
            return AniLibertyCollectionAddBody(
                releaseId = releaseId,
                typeOfCollection = type,
            )
        }
    }
}
