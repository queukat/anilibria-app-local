package ru.radiationx.data.datasource.holders

import kotlinx.coroutines.flow.Flow
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId

interface EpisodesCheckerHolder {
    companion object {
        const val DEFAULT_BULK_BATCH_SIZE = 250
        const val DEFAULT_BULK_SAVE_EVERY_BATCHES = 4
    }

    fun observeEpisodes(): Flow<List<EpisodeAccess>>
    suspend fun getEpisodes(): List<EpisodeAccess>
    suspend fun putEpisode(episode: EpisodeAccess)
    suspend fun putAllEpisode(episodes: List<EpisodeAccess>)
    suspend fun putAllEpisodeBatched(
        episodes: List<EpisodeAccess>,
        batchSize: Int = DEFAULT_BULK_BATCH_SIZE,
        saveEveryBatches: Int = DEFAULT_BULK_SAVE_EVERY_BATCHES,
    ) {
        putAllEpisode(episodes)
    }
    suspend fun getEpisodes(releaseId: ReleaseId): List<EpisodeAccess>
    suspend fun getEpisode(episodeId: EpisodeId): EpisodeAccess?
    suspend fun remove(releaseId: ReleaseId)
}
