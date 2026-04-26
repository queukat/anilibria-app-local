package ru.radiationx.anilibria.common

fun GradientBackgroundManager.applyCard(card: CardItem?) {
    val imageUrl = card?.backgroundImageUrl
    if (imageUrl != null) {
        applyImage(imageUrl)
    } else {
        clearGradient()
    }
}
