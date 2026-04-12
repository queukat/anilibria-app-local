package ru.radiationx.anilibria.screen.player

import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.shared.ktx.asTimeSecString
import java.math.BigDecimal
import java.util.Date

internal fun List<Episode>.sortedByEpisodeOrdinalAsc(): List<Episode> {
    return sortedWith(
        compareBy<Episode> { it.id.id.toBigDecimalOrNullOrMax() }
            .thenBy { it.id.id },
    )
}

internal fun List<Release>.toPlaybackEpisodesOrder(): List<Episode> {
    return flatMap { release ->
        release.episodes.sortedByEpisodeOrdinalAsc()
    }
}

internal fun formatEpisodeAccessDescription(access: EpisodeAccess): String? {
    return when {
        !access.isViewed -> null
        access.seek > 0L -> "Остановлена на ${Date(access.seek).asTimeSecString()}"
        else -> "Просмотрено"
    }
}

private fun String.toBigDecimalOrNullOrMax(): BigDecimal {
    return runCatching { BigDecimal(trim()) }.getOrElse { BigDecimal.valueOf(Long.MAX_VALUE) }
}
