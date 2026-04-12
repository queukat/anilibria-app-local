package ru.radiationx.anilibria.common

sealed interface CardItem {
    val itemId: Int

    val backgroundImageUrl: String?
        get() = null

    fun getId(): Int = itemId
}
