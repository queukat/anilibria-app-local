package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.FavoriteInfo
import ru.radiationx.data.entity.domain.release.QualityInfo
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseCode
import ru.radiationx.data.entity.domain.types.ReleaseId

class EpisodeOrderTest {
    @Test
    fun sortedByEpisodeOrdinalAsc_ordersByNumericEpisodeId() {
        val releaseId = ReleaseId(1001)
        val episodes =
            listOf(
                episode("10", releaseId),
                episode("2", releaseId),
                episode("1", releaseId),
                episode("2.5", releaseId),
            )

        val sorted = episodes.sortedByEpisodeOrdinalAsc().map { it.id.id }

        assertEquals(listOf("1", "2", "2.5", "10"), sorted)
    }

    @Test
    fun toPlaybackEpisodesOrder_keepsReleaseOrderWithoutInterleavingSeasons() {
        val release1 =
            release(
                id = 1001,
                episodes =
                    listOf(
                        episode("1", ReleaseId(1001)),
                        episode("2", ReleaseId(1001)),
                    ),
            )
        val release2 =
            release(
                id = 2002,
                episodes =
                    listOf(
                        episode("1", ReleaseId(2002)),
                        episode("2", ReleaseId(2002)),
                    ),
            )

        val sorted =
            listOf(release1, release2)
                .toPlaybackEpisodesOrder()
                .map { "${it.id.releaseId.id}:${it.id.id}" }

        assertEquals(
            listOf("1001:1", "1001:2", "2002:1", "2002:2"),
            sorted,
        )
    }

    private fun episode(
        id: String,
        releaseId: ReleaseId,
    ): Episode {
        return Episode(
            id = EpisodeId(id = id, releaseId = releaseId),
            title = "Episode $id",
            qualityInfo = QualityInfo(urlSd = "sd", urlHd = null, urlFullHd = null),
            updatedAt = null,
            skips = null,
        )
    }

    private fun release(
        id: Int,
        episodes: List<Episode>,
    ): Release {
        return Release(
            id = ReleaseId(id),
            code = ReleaseCode("release-$id"),
            names = listOf("Release $id"),
            series = null,
            poster = null,
            torrentUpdate = 0,
            status = null,
            statusCode = null,
            types = emptyList(),
            genres = emptyList(),
            voices = emptyList(),
            members = null,
            year = null,
            season = null,
            days = emptyList(),
            description = null,
            announce = null,
            favoriteInfo = FavoriteInfo(rating = 0, isAdded = false),
            link = null,
            franchises = emptyList(),
            showDonateDialog = false,
            blockedInfo = ru.radiationx.data.entity.domain.release.BlockedInfo(false, null),
            moonwalkLink = null,
            episodes = episodes,
            sourceEpisodes = emptyList(),
            externalPlaylists = emptyList(),
            rutubePlaylist = emptyList(),
            torrents = emptyList(),
        )
    }
}
