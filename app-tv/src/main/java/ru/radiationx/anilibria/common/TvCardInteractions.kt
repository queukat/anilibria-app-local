package ru.radiationx.anilibria.common

internal data class TvCardDescription(
    val title: CharSequence = "",
    val subtitle: CharSequence = "",
)

internal fun Any?.toTvCardDescription(
    libriaSubtitle: (LibriaCard) -> CharSequence = { it.description }
): TvCardDescription {
    val card = this as? CardItem ?: return TvCardDescription()
    return when (card) {
        is LibriaCard -> TvCardDescription(title = card.title, subtitle = libriaSubtitle(card))
        is InfoCard -> TvCardDescription(title = card.title, subtitle = card.subtitle)
        is LinkCard -> TvCardDescription(title = card.title, subtitle = "")
        is LoadingCard -> TvCardDescription(title = card.title, subtitle = card.description)
    }
}

internal fun BaseCardsViewModel?.handleTvCardClick(item: Any?) {
    val card = item as? CardItem ?: return
    this?.onCardItemClick(card)
}

internal inline fun <T> MutableMap<Long, T>.getOrPutRow(
    rowId: Long,
    create: (Long) -> T,
): T {
    return getOrPut(rowId) { create(rowId) }
}
