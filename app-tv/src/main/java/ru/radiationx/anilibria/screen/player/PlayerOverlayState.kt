package ru.radiationx.anilibria.screen.player

import androidx.annotation.StringRes
import ru.radiationx.anilibria.R

internal enum class PlayerOverlayFocusTarget {
    Root,
    Progress,
    PlayPause,
    SeekBack,
    SeekForward,
    PreviousEpisode,
    NextEpisode,
    Episodes,
    Quality,
    Speed,
    AspectRatio,
}

internal enum class PlayerOverlayPicker(
    @StringRes val titleRes: Int,
    private val returnTarget: PlayerOverlayFocusTarget,
) {
    Episodes(
        titleRes = R.string.player_action_episodes,
        returnTarget = PlayerOverlayFocusTarget.Episodes,
    ),
    Quality(
        titleRes = R.string.player_action_quality,
        returnTarget = PlayerOverlayFocusTarget.Quality,
    ),
    Speed(
        titleRes = R.string.player_action_speed,
        returnTarget = PlayerOverlayFocusTarget.Speed,
    ),
    AspectRatio(
        titleRes = R.string.player_action_aspect_ratio,
        returnTarget = PlayerOverlayFocusTarget.AspectRatio,
    ),
    ;

    fun focusTarget(): PlayerOverlayFocusTarget = returnTarget
}

internal data class PlayerPickerOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val onSelected: () -> Unit,
)
