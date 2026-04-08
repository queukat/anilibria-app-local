package ru.radiationx.anilibria.screen.watching

import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import java.math.BigDecimal

internal fun shouldUseLocalProgressForRemoteContinueItem(
    remoteItem: UserViewHistoryItem,
    localAccess: EpisodeAccess?,
): Boolean {
    localAccess ?: return false
    if (localAccess.seek <= 0L) return false

    val localOrdinal = normalizeEpisodeOrdinal(localAccess.id.id) ?: return false
    val remoteOrdinal = normalizeEpisodeOrdinal(remoteItem.episodeOrdinal) ?: return false

    return localOrdinal == remoteOrdinal
}

internal fun normalizeEpisodeOrdinal(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return runCatching {
        BigDecimal(trimmed).stripTrailingZeros().toPlainString()
    }.getOrNull()
}

internal fun normalizeEpisodeOrdinal(value: Double?): String? {
    value ?: return null
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
