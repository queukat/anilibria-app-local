package ru.radiationx.anilibria.screen.watching

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem

class TvWatchSyncDecisionsTest {

    @Test
    fun shouldUseLocalProgressForRemoteContinueItem_returnsTrue_forSameEpisodeOrdinal() {
        val remoteItem = remoteHistoryItem(episodeOrdinal = 1.0)
        val localAccess = localAccess(episodeOrdinal = "1", seekMs = 42_000L)

        assertTrue(shouldUseLocalProgressForRemoteContinueItem(remoteItem, localAccess))
    }

    @Test
    fun shouldUseLocalProgressForRemoteContinueItem_returnsFalse_forDifferentEpisodeOrdinal() {
        val remoteItem = remoteHistoryItem(episodeOrdinal = 2.0)
        val localAccess = localAccess(episodeOrdinal = "1", seekMs = 42_000L)

        assertFalse(shouldUseLocalProgressForRemoteContinueItem(remoteItem, localAccess))
    }

    @Test
    fun shouldUseLocalProgressForRemoteContinueItem_returnsFalse_whenRemoteEpisodeMissing() {
        val remoteItem = remoteHistoryItem(episodeOrdinal = null)
        val localAccess = localAccess(episodeOrdinal = "1", seekMs = 42_000L)

        assertFalse(shouldUseLocalProgressForRemoteContinueItem(remoteItem, localAccess))
    }

    @Test
    fun shouldUseLocalProgressForRemoteContinueItem_returnsFalse_whenLocalSeekIsEmpty() {
        val remoteItem = remoteHistoryItem(episodeOrdinal = 1.0)
        val localAccess = localAccess(episodeOrdinal = "1", seekMs = 0L)

        assertFalse(shouldUseLocalProgressForRemoteContinueItem(remoteItem, localAccess))
    }

    private fun remoteHistoryItem(episodeOrdinal: Double?): UserViewHistoryItem {
        return UserViewHistoryItem(
            releaseId = ReleaseId(7),
            titleMain = "Release",
            titleEnglish = null,
            titleAlternative = null,
            posterPreview = null,
            posterThumbnail = null,
            episodeOrdinal = episodeOrdinal,
            timeSeconds = 120.0,
            isWatched = false,
        )
    }

    private fun localAccess(
        episodeOrdinal: String,
        seekMs: Long,
    ): EpisodeAccess {
        return EpisodeAccess(
            id = EpisodeId(episodeOrdinal, ReleaseId(7)),
            seek = seekMs,
            isViewed = true,
            lastAccess = 123L,
        )
    }
}
