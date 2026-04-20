package ru.radiationx.data.entity.domain.watching

import com.squareup.moshi.JsonClass
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId

/**
 * Persisted outbox entry for best-effort AniLiberty progress upload.
 *
 * The UI remains local-authoritative; this model exists only to deliver local
 * mutations to the remote replica later, outside critical UX paths.
 */
@JsonClass(generateAdapter = true)
data class UserViewPendingUpload(
    val releaseId: Int,
    val episodeId: String,
    val positionMs: Long,
    val isWatched: Boolean,
    val updatedAtMs: Long,
) {
    fun toEpisodeId(): EpisodeId {
        return EpisodeId(
            id = episodeId,
            releaseId = ReleaseId(releaseId),
        )
    }

    companion object {
        fun create(
            episodeId: EpisodeId,
            positionMs: Long,
            isWatched: Boolean,
            updatedAtMs: Long = System.currentTimeMillis(),
        ): UserViewPendingUpload {
            return UserViewPendingUpload(
                releaseId = episodeId.releaseId.id,
                episodeId = episodeId.id,
                positionMs = positionMs,
                isWatched = isWatched,
                updatedAtMs = updatedAtMs,
            )
        }
    }
}
