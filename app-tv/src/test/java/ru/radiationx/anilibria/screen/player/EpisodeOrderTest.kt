package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.QualityInfo
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId

class EpisodeOrderTest {

    @Test
    fun sortedByEpisodeOrdinalAsc_ordersByNumericEpisodeId() {
        val releaseId = ReleaseId(1001)
        val episodes = listOf(
            episode("10", releaseId),
            episode("2", releaseId),
            episode("1", releaseId),
            episode("2.5", releaseId),
        )

        val sorted = episodes.sortedByEpisodeOrdinalAsc().map { it.id.id }

        assertEquals(listOf("1", "2", "2.5", "10"), sorted)
    }

    private fun episode(id: String, releaseId: ReleaseId): Episode {
        return Episode(
            id = EpisodeId(id = id, releaseId = releaseId),
            title = "Episode $id",
            qualityInfo = QualityInfo(urlSd = "sd", urlHd = null, urlFullHd = null),
            updatedAt = null,
            skips = null,
        )
    }
}

