package ru.radiationx.anilibria.common

data class LinkCard(
    val title: String,
) : CardItem {
    override val itemId: Int
        get() = title.hashCode()
}
