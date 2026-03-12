package ru.radiationx.anilibria.screen.mainpages

interface MainShellContentFragment {
    var onRequestRailFocus: (() -> Boolean)?
    var onContentMovedDown: (() -> Unit)?
    var onContentMovedUp: (() -> Unit)?
    var onRequestHeaderFocus: (() -> Boolean)?

    fun requestContentFocus(): Boolean = false
}
