package ru.radiationx.anilibria.extension

import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager

fun GradientBackgroundManager.applyCard(card: CardItem?) {
    val imageUrl = card?.backgroundImageUrl
    if (imageUrl != null) {
        applyImage(imageUrl)
    } else {
        clearGradient()
    }
}
