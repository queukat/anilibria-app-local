package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId

@JsonClass(generateAdapter = true)
data class AniLibertyReleaseEpisodeTimecode(
    @Json(name = "release_episode_id") val releaseEpisodeId: AniLibertyReleaseEpisodeId,
    @Json(name = "time") val time: Double,
    @Json(name = "is_watched") val isWatched: Boolean,
)

typealias AniLibertyViewTimecode = AniLibertyReleaseEpisodeTimecode

@JsonClass(generateAdapter = true)
data class AniLibertyEpisodeTimecode(
    @Json(name = "time") val time: Double,
    @Json(name = "is_watched") val isWatched: Boolean,
)

@JsonClass(generateAdapter = true)
data class AniLibertyCollectionIdItem(
    @Json(name = "release_id") val releaseId: AniLibertyReleaseId,
    @Json(name = "type_of_collection") val typeOfCollection: AniLibertyCollectionType,
)
