package ru.radiationx.anilibria.common

internal data class TvCardDescription(
    val title: CharSequence = "",
    val subtitle: CharSequence = "",
)

internal fun Any?.toTvCardDescription(
    libriaSubtitle: (LibriaCard) -> CharSequence = { it.description }
): TvCardDescription {
    return when (this) {
        is LibriaCard -> TvCardDescription(title = title, subtitle = libriaSubtitle(this))
        is LinkCard -> TvCardDescription(title = title, subtitle = "")
        is LoadingCard -> TvCardDescription(title = title, subtitle = description)
        else -> TvCardDescription()
    }
}

internal fun BaseCardsViewModel?.handleTvCardClick(item: Any?) {
    when (item) {
        is LinkCard -> this?.onLinkCardClick()
        is LoadingCard -> this?.onLoadingCardClick()
        is LibriaCard -> this?.onLibriaCardClick(item)
    }
}

internal inline fun <T> MutableMap<Long, T>.getOrPutRow(
    rowId: Long,
    create: (Long) -> T,
): T {
    return getOrPut(rowId) { create(rowId) }
}
