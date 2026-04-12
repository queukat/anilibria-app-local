package ru.radiationx.anilibria.common

data class LoadingCard(
    val title: String = "",
    val description: String = "",
    val isError: Boolean = false,
) : CardItem {
    override val itemId: Int
        get() = title.hashCode()
}
