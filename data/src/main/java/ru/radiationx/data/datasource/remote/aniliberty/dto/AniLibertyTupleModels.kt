package ru.radiationx.data.datasource.remote.aniliberty.dto

import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId

data class AniLibertyReleaseEpisodeTimecode(
    val releaseEpisodeId: AniLibertyReleaseEpisodeId,
    val time: Double,
    val isWatched: Boolean,
)

typealias AniLibertyViewTimecode = AniLibertyReleaseEpisodeTimecode

data class AniLibertyEpisodeTimecode(
    val time: Double,
    val isWatched: Boolean,
)

data class AniLibertyCollectionIdItem(
    val releaseId: AniLibertyReleaseId,
    val typeOfCollection: AniLibertyCollectionType,
)
