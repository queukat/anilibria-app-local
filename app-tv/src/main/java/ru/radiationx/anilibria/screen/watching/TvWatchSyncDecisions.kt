package ru.radiationx.anilibria.screen.watching

import ru.radiationx.data.entity.domain.release.EpisodeAccess
import java.math.BigDecimal

internal fun pickLatestLocalProgressOrNull(accesses: Iterable<EpisodeAccess>): EpisodeAccess? {
    return accesses.maxByOrNull { access -> access.lastAccessRaw }
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
