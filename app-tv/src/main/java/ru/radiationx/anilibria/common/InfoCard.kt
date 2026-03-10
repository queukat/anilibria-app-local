package ru.radiationx.anilibria.common

data class InfoCard(
    val title: String,
    val subtitle: String = "",
) : CardItem {

    override fun getId(): Int {
        return 31 * title.hashCode() + subtitle.hashCode()
    }
}
