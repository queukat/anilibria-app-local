package ru.radiationx.anilibria.common

data class InfoCard(
    val title: String,
    val subtitle: String = "",
    override val stableKey: String = "info:$title:$subtitle",
) : CardItem
