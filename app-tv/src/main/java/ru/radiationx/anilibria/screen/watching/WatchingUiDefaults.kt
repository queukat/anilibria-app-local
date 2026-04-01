package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard

internal val TvScreenHorizontalPadding = 10.dp
internal val TvCardScreenHorizontalPadding = TvScreenHorizontalPadding
internal val TvDetailHorizontalPadding = 28.dp
internal val TvRowsScreenVerticalPadding = 8.dp
internal val TvPageVerticalPadding = 16.dp
internal val TvPageHeaderSpacing = 18.dp
internal val TvBottomDescriptionInset = 124.dp
internal val TvGridBottomDescriptionInset = 164.dp
internal val TvBottomContentInset = 28.dp
internal val TvSectionSpacing = 26.dp
internal val TvSectionHeaderSpacing = 12.dp
internal val TvFilterRowSpacing = 10.dp
internal val TvRowSpacing = 16.dp
internal val TvRowEndPadding = 0.dp
internal val TvPosterCardWidth = 152.dp
internal val TvPosterCardSlotWidth = 168.dp
internal val TvPickerTopInset = 72.dp
internal val TvDescriptionBarPadding = PaddingValues(
    horizontal = 20.dp,
    vertical = 18.dp,
)
internal val TvDetailDescriptionBarPadding = PaddingValues(
    horizontal = TvDetailHorizontalPadding,
    vertical = 18.dp,
)
internal val TvPlayerOverlayHorizontalPadding = 48.dp
internal val TvPlayerOverlayBottomPadding = 28.dp

internal fun List<CardItem>.indexOfItemId(itemId: Int): Int? {
    if (itemId == Int.MIN_VALUE) {
        return null
    }
    val itemIndex = indexOfFirst { it.getId() == itemId }
    return itemIndex.takeIf { it >= 0 }
}

internal fun List<CardItem>.hasTvPosterContent(): Boolean {
    return any { it is LibriaCard }
}

internal fun List<CardItem>.isTvStateOnlySection(): Boolean {
    return isNotEmpty() && !hasTvPosterContent()
}

internal fun List<CardItem>.primaryTvStateItem(): CardItem? {
    return firstOrNull { it is LoadingCard && it.isError }
        ?: firstOrNull { it is LoadingCard }
        ?: firstOrNull { it is InfoCard }
        ?: firstOrNull { it is LinkCard }
        ?: firstOrNull()
}

internal fun List<CardItem>.tvStateFocusIndex(): Int? {
    val actionIndex = indexOfFirst { it is LinkCard }.takeIf { it >= 0 }
    val primaryStateIndex = primaryTvStateItem()?.getId()?.let(::indexOfItemId)
    return takeIf { it.isTvStateOnlySection() }?.let {
        actionIndex ?: primaryStateIndex
    }
}

internal suspend fun LazyGridState.scrollItemIntoViewIfNeeded(
    index: Int,
    anchorIndex: Int = (index - 1).coerceAtLeast(0),
    bottomClearancePx: Int = 0,
) {
    if (index < 0) return
    val visibleItems = layoutInfo.visibleItemsInfo
    val targetItem = visibleItems.firstOrNull { it.index == index }
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset - bottomClearancePx
    val needsAdjust = when {
        targetItem == null -> true
        targetItem.offset.y < viewportStart -> true
        targetItem.offset.y + targetItem.size.height > viewportEnd -> true
        else -> false
    }
    if (needsAdjust) {
        val targetIndex = when {
            visibleItems.isEmpty() -> index
            index < visibleItems.first().index -> index
            else -> anchorIndex.coerceAtLeast(0)
        }
        scrollToItem(targetIndex)
    }
}
