package ru.radiationx.anilibria.extension

import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard

fun GradientBackgroundManager.applyCard(card: Any?) = when (card) {
    is LibriaCard -> applyImage(card.image)
    else -> clearGradient()
}
