package ru.radiationx.anilibria.screen.player

import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.entity.domain.release.PlayerSkips

data class Video(
    val url: String,
    val seek: Long,
    val title: String,
    val subtitle: String,
    val skips: PlayerSkips?,
)

fun PlayerQuality.asPlayerLabel(): String = when (this) {
    PlayerQuality.SD -> "480p"
    PlayerQuality.HD -> "720p"
    PlayerQuality.FULLHD -> "1080p"
}

fun Float.asPlayerLabel(): String {
    val normalized = if (this % 1f == 0f) {
        this.toInt().toString()
    } else {
        toString()
    }
    return "${normalized}x"
}
