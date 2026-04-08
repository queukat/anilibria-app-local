package ru.radiationx.anilibria.screen.player

internal enum class PlayerQuickActionHandling {
    HideControls,
    KeepControlsVisible,
}

internal fun resolvePlayerQuickActionHandling(
    controlsVisible: Boolean,
): PlayerQuickActionHandling {
    return if (controlsVisible) {
        PlayerQuickActionHandling.KeepControlsVisible
    } else {
        PlayerQuickActionHandling.HideControls
    }
}
