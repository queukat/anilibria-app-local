package ru.radiationx.anilibria.common

sealed interface CardItem {
    val stableKey: String

    @Deprecated("Use stableKey for Compose keys and TV focus identity.")
    val itemId: Int

    val backgroundImageUrl: String?
        get() = null

    @Suppress("DEPRECATION")
    @Deprecated("Use stableKey for Compose keys and TV focus identity.")
    fun getId(): Int = itemId
}
