package ru.radiationx.anilibria.screen.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.anilibria.screen.player.episodes.formatEpisodeAccessDescription
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId

class PlayerEpisodesViewModelTest {

    @Test
    fun formatEpisodeAccessDescription_showsStoppedTextWhenSeekPositive() {
        val access = EpisodeAccess(EpisodeId("1", ReleaseId(10)), 12_000L, true, 1L)
        val description = formatEpisodeAccessDescription(access)
        assertNotNull(description)
        assertTrue(description!!.startsWith("Остановлена на "))
    }

    @Test
    fun formatEpisodeAccessDescription_showsViewedWhenSeekZero() {
        val access = EpisodeAccess(EpisodeId("1", ReleaseId(10)), 0L, true, 1L)
        assertEquals("Просмотрено", formatEpisodeAccessDescription(access))
    }

    @Test
    fun formatEpisodeAccessDescription_returnsNullWhenNotViewed() {
        val access = EpisodeAccess(EpisodeId("1", ReleaseId(10)), 0L, false, 1L)
        assertEquals(null, formatEpisodeAccessDescription(access))
    }
}
