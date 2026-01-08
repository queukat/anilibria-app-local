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
    companion object {
        fun from(
            time: Double,
            isWatched: Boolean,
            releaseEpisodeId: AniLibertyReleaseEpisodeId,
        ): AniLibertyUserViewTimecodeUpsertBody = AniLibertyUserViewTimecodeUpsertBody(
            time = time,
            isWatched = isWatched,
            releaseEpisodeId = releaseEpisodeId.value,
        )
    }
}

@JsonClass(generateAdapter = true)
data class AniLibertyUserViewTimecodeDeleteBody(
    @Json(name = "release_episode_id") val releaseEpisodeId: String,
) {
    companion object {
        fun from(releaseEpisodeId: AniLibertyReleaseEpisodeId): AniLibertyUserViewTimecodeDeleteBody =
            AniLibertyUserViewTimecodeDeleteBody(releaseEpisodeId = releaseEpisodeId.value)
    }
}
