package ru.radiationx.data.datasource.remote.aniliberty.dto

data class AniLibertyViewTimecode(
    val releaseEpisodeId: String,
    val time: Double,
    val isWatched: Boolean,
)

data class AniLibertyCollectionIdItem(
    val releaseId: Int,
    val typeOfCollection: String,
)
