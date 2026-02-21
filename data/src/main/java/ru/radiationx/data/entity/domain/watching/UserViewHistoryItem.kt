package ru.radiationx.data.entity.domain.watching

import ru.radiationx.data.entity.domain.types.ReleaseId

/**
 * Domain projection of AniLiberty view history item used by TV screens.
 * Keeps UI independent from remote wire models.
 */
data class UserViewHistoryItem(
    val releaseId: ReleaseId,
    val titleMain: String?,
    val titleEnglish: String?,
    val titleAlternative: String?,
    val posterPreview: String?,
    val posterThumbnail: String?,
    val episodeOrdinal: Double?,
    val timeSeconds: Double?,
    val isWatched: Boolean,
)
