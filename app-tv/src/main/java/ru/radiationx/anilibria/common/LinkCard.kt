package ru.radiationx.anilibria.common

data class LinkCard(
    val title: String,
    override val stableKey: String = "link:$title",
) : CardItem {
    @Deprecated("Use stableKey for Compose keys and TV focus identity.")
    override val itemId: Int
        get() = title.hashCode()
}
