package ru.radiationx.anilibria.screen.player

import ru.radiationx.data.entity.domain.release.Episode
import java.math.BigDecimal

internal fun List<Episode>.sortedByEpisodeOrdinalAsc(): List<Episode> {
    return sortedWith(
        compareBy<Episode> { it.id.id.toBigDecimalOrNullOrMax() }
            .thenBy { it.id.id }
    )
}

private fun String.toBigDecimalOrNullOrMax(): BigDecimal {
    return runCatching { BigDecimal(trim()) }.getOrElse { BigDecimal.valueOf(Long.MAX_VALUE) }
}

