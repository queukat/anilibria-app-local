package ru.radiationx.anilibria.common

data class LinkCard(
    val title: String,
    override val stableKey: String = "link:$title",
) : CardItem
