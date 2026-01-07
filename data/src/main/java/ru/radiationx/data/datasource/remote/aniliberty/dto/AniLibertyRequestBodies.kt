package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId

@JsonClass(generateAdapter = true)
data class AniLibertyReleaseIdBody(
    @Json(name = "release_id") val releaseId: Int,
) {
    constructor(releaseId: AniLibertyReleaseId) : this(releaseId = releaseId.value)
}

@JsonClass(generateAdapter = true)
data class AniLibertyCollectionAddBody(
    @Json(name = "release_id") val releaseId: Int,
    @Json(name = "type_of_collection") val typeOfCollection: String,
) {
    constructor(
        releaseId: Int,
        typeOfCollection: AniLibertyCollectionType,
    ) : this(
        releaseId = releaseId,
        typeOfCollection = typeOfCollection.value,
    )

    constructor(
        releaseId: AniLibertyReleaseId,
        typeOfCollection: AniLibertyCollectionType,
    ) : this(
        releaseId = releaseId.value,
        typeOfCollection = typeOfCollection.value,
    )
}
