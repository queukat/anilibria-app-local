package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.CardItem

internal val TvScreenHorizontalPadding = 20.dp
internal val TvRowsScreenVerticalPadding = 8.dp
internal val TvPageVerticalPadding = 16.dp
internal val TvPageHeaderSpacing = 18.dp
internal val TvBottomDescriptionInset = 124.dp
internal val TvBottomContentInset = 28.dp
internal val TvSectionSpacing = 26.dp
internal val TvSectionHeaderSpacing = 12.dp
internal val TvRowSpacing = 16.dp
internal val TvRowEndPadding = 24.dp
internal val TvDescriptionBarPadding = PaddingValues(
    horizontal = 20.dp,
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

internal suspend fun LazyGridState.scrollItemIntoViewIfNeeded(
    index: Int,
    anchorIndex: Int = (index - 1).coerceAtLeast(0),
) {
    if (index < 0) return
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.none { it.index == index }) {
        val targetIndex = when {
            visibleItems.isEmpty() -> index
            index < visibleItems.first().index -> index
            else -> anchorIndex.coerceAtLeast(0)
        }
        scrollToItem(targetIndex)
    }
}
