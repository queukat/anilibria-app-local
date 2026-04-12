package ru.radiationx.anilibria.common

data class InfoCard(
    val title: String,
    val subtitle: String = "",
) : CardItem {
    override val itemId: Int
        get() = ID_HASH_MULTIPLIER * title.hashCode() + subtitle.hashCode()

    private companion object {
        const val ID_HASH_MULTIPLIER = 31
    }
}
