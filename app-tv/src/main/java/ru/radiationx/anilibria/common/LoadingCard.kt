package ru.radiationx.anilibria.common

data class LoadingCard(
    val title: String = "",
    val description: String = "",
    val isError: Boolean = false,
    override val stableKey: String = "loading:$title:$description:$isError",
) : CardItem {
    @Deprecated("Use stableKey for Compose keys and TV focus identity.")
    override val itemId: Int
        get() = title.hashCode()
}
