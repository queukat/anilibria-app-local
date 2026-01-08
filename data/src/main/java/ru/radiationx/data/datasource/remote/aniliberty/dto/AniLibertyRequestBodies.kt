package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId

@JsonClass(generateAdapter = true)
data class AniLibertyReleaseIdBody(
    @Json(name = "release_id") val releaseId: Int,
) {
    companion object {
        fun from(releaseId: AniLibertyReleaseId): AniLibertyReleaseIdBody =
            AniLibertyReleaseIdBody(releaseId = releaseId.value)
    }
}
