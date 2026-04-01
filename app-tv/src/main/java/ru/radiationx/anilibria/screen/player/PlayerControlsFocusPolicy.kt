package ru.radiationx.anilibria.screen.player

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
