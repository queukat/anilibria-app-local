package ru.radiationx.anilibria.screen.player

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import ru.radiationx.anilibria.screen.watching.TvCenterPressAction
import ru.radiationx.anilibria.screen.watching.resolveTvCenterPressAction

internal enum class PlayerControlSurfaceKeyAction {
    Ignore,
    Consume,
    Click,
    MoveLeft,
    MoveUp,
    MoveRight,
    MoveDown,
}

internal fun shouldRequestPlayerControlsFocus(
    controlsVisible: Boolean,
    hasActivePicker: Boolean,
    skipVisible: Boolean,
    quickActionsTransferPending: Boolean,
): Boolean {
    if (!controlsVisible || hasActivePicker) {
        return false
    }
    if (skipVisible && !quickActionsTransferPending) {
        return false
    }
    return true
}

internal fun resolvePlayerControlSurfaceKeyAction(
    canFocus: Boolean,
    enabled: Boolean,
    key: Key,
    eventType: KeyEventType,
): PlayerControlSurfaceKeyAction {
    return when (resolveTvCenterPressAction(canFocus, enabled, key, eventType)) {
        TvCenterPressAction.Consume -> PlayerControlSurfaceKeyAction.Consume
        TvCenterPressAction.Click -> PlayerControlSurfaceKeyAction.Click
        TvCenterPressAction.Ignore -> {
            if (!canFocus || eventType != KeyEventType.KeyDown) {
                PlayerControlSurfaceKeyAction.Ignore
            } else {
                when (key) {
                    Key.DirectionLeft -> PlayerControlSurfaceKeyAction.MoveLeft
                    Key.DirectionUp -> PlayerControlSurfaceKeyAction.MoveUp
                    Key.DirectionRight -> PlayerControlSurfaceKeyAction.MoveRight
                    Key.DirectionDown -> PlayerControlSurfaceKeyAction.MoveDown
                    else -> PlayerControlSurfaceKeyAction.Ignore
                }
            }
        }
    }
}

internal fun requestFocus(focusRequester: FocusRequester): Boolean {
    return runCatching {
        focusRequester.requestFocus()
        true
    }.getOrDefault(false)
}
