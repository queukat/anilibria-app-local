package ru.radiationx.anilibria.screen.watching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId

class TvWatchSyncDecisionsTest {
    @Test
    fun pickLatestLocalProgressOrNull_prefersNewestAccess() {
        val releaseId = ReleaseId(7)
        val older = localAccess(releaseId = releaseId, episodeOrdinal = "1", seekMs = 42_000L, lastAccess = 10L)
        val newer = localAccess(releaseId = releaseId, episodeOrdinal = "2", seekMs = 12_000L, lastAccess = 20L)

        assertEquals(newer, pickLatestLocalProgressOrNull(listOf(older, newer)))
    }

    @Test
    fun normalizeEpisodeOrdinal_normalizesFractionalOrdinal() {
        assertEquals("1", normalizeEpisodeOrdinal("1.0"))
        assertEquals("2.5", normalizeEpisodeOrdinal("2.500"))
    }

    @Test
    fun normalizeEpisodeOrdinal_returnsNull_forBlankValue() {
        assertNull(normalizeEpisodeOrdinal("   "))
    }

    private fun localAccess(
        releaseId: ReleaseId,
        episodeOrdinal: String,
        seekMs: Long,
        lastAccess: Long,
    ): EpisodeAccess {
        return EpisodeAccess(
            id = EpisodeId(episodeOrdinal, releaseId),
            seek = seekMs,
            isViewed = true,
            lastAccess = lastAccess,
        )
    }
}
