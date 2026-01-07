package ru.radiationx.data.datasource.remote.aniliberty.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId

@JsonClass(generateAdapter = true)
data class AniLibertyUserViewTimecodeUpsertBody(
    @Json(name = "time") val time: Double,
    @Json(name = "is_watched") val isWatched: Boolean,
    @Json(name = "release_episode_id") val releaseEpisodeId: String,
) {
    constructor(
        time: Double,
        isWatched: Boolean,
        releaseEpisodeId: AniLibertyReleaseEpisodeId,
    ) : this(
        time = time,
        isWatched = isWatched,
        releaseEpisodeId = releaseEpisodeId.value,
    )
}

@JsonClass(generateAdapter = true)
data class AniLibertyUserViewTimecodeDeleteBody(
    @Json(name = "release_episode_id") val releaseEpisodeId: String,
) {
    constructor(releaseEpisodeId: AniLibertyReleaseEpisodeId) : this(releaseEpisodeId = releaseEpisodeId.value)
}
