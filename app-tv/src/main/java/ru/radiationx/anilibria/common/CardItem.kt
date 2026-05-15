package ru.radiationx.anilibria.common

sealed interface CardItem {
    val stableKey: String

    val backgroundImageUrl: String?
        get() = null
}
