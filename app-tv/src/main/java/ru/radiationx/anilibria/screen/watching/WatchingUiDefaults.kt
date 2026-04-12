package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import kotlin.math.max

internal val TvScreenHorizontalPadding = 10.dp
internal val TvCardScreenHorizontalPadding = TvScreenHorizontalPadding
internal val TvDetailHorizontalPadding = 28.dp
internal val TvCollectionTopFiltersPanelPadding =
    PaddingValues(
        horizontal = 20.dp,
        vertical = 18.dp,
    )
internal val TvCollectionTopFiltersSpacing = 14.dp
internal val TvCollectionTopFiltersActionSpacing = 14.dp
internal val TvCollectionTopFiltersActionWidth = 148.dp
internal val TvRowsScreenVerticalPadding = 8.dp
internal val TvPageVerticalPadding = 16.dp
internal val TvPageHeaderSpacing = 18.dp
internal val TvBottomDescriptionInset = 124.dp
internal val TvGridBottomDescriptionInset = 164.dp
internal val TvCollectionGridTopContentPadding = 12.dp
internal val TvCollectionGridBottomDescriptionInset = 144.dp
internal val TvBottomContentInset = 28.dp
internal val TvFocusedItemBottomGap = 24.dp
internal val TvCollectionDescriptionBarPadding =
    PaddingValues(
        start = 20.dp,
        top = 4.dp,
        end = 20.dp,
        bottom = 6.dp,
    )
internal val TvCollectionSolidDescriptionBarMinHeight = 84.dp
internal val TvCollectionSolidDescriptionBarInnerPadding =
    PaddingValues(
        start = 20.dp,
        top = 8.dp,
        end = 20.dp,
        bottom = 8.dp,
    )
internal val TvSectionSpacing = 26.dp
internal val TvSectionHeaderSpacing = 12.dp
internal val TvFilterRowSpacing = 10.dp
internal val TvRowSpacing = 16.dp
internal val TvRowEndPadding = 0.dp
internal val TvPosterCardWidth = 152.dp
internal val TvPosterCardSlotWidth = 168.dp
internal val TvPickerTopInset = 72.dp
internal val TvDescriptionBarPadding =
    PaddingValues(
        start = 20.dp,
        top = 8.dp,
        end = 20.dp,
        bottom = 10.dp,
    )
internal val TvSolidDescriptionBarMinHeight = 92.dp
internal val TvSolidDescriptionBarInnerPadding =
    PaddingValues(
        start = 20.dp,
        top = 12.dp,
        end = 20.dp,
        bottom = 12.dp,
    )
internal val TvSolidDescriptionBarVerticalOffset = 4.dp
internal val TvSolidDescriptionBarContentVerticalOffset = 2.dp
internal val TvDetailDescriptionBarPadding =
    PaddingValues(
        start = TvDetailHorizontalPadding,
        top = 8.dp,
        end = TvDetailHorizontalPadding,
        bottom = 10.dp,
    )
internal val TvPlayerOverlayHorizontalPadding = 48.dp
internal val TvPlayerOverlayBottomPadding = 28.dp

internal data class TvDescriptionOverlayClearance(
    val bottomInset: Dp,
    val bottomClearancePx: Int,
    val measureModifier: Modifier,
)

@Composable
internal fun rememberTvDescriptionOverlayClearance(
    hasContent: Boolean,
    fallbackInset: Dp = TvBottomDescriptionInset,
    extraGap: Dp = TvFocusedItemBottomGap,
): TvDescriptionOverlayClearance {
    val density = LocalDensity.current
    var overlayHeightPx by remember(hasContent) { mutableIntStateOf(0) }
    val fallbackInsetPx =
        remember(density, fallbackInset) {
            with(density) { fallbackInset.roundToPx() }
        }
    val extraGapPx =
        remember(density, extraGap) {
            with(density) { extraGap.roundToPx() }
        }
    val bottomClearancePx =
        remember(hasContent, overlayHeightPx, fallbackInsetPx, extraGapPx) {
            if (!hasContent) {
                0
            } else {
                max(fallbackInsetPx, overlayHeightPx + extraGapPx)
            }
        }
    val bottomInset =
        remember(density, bottomClearancePx) {
            with(density) { bottomClearancePx.toDp() }
        }
    val measureModifier =
        if (hasContent) {
            Modifier.onSizeChanged { overlayHeightPx = it.height }
        } else {
            Modifier
        }
    return TvDescriptionOverlayClearance(
        bottomInset = bottomInset,
        bottomClearancePx = bottomClearancePx,
        measureModifier = measureModifier,
    )
}

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

    fun currentTarget() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    suspend fun adjustVisibleTarget(): Boolean {
        val target = currentTarget() ?: return false
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = (layoutInfo.viewportEndOffset - bottomClearancePx).coerceAtLeast(viewportStart)
        val itemStart = target.offset.y
        val itemEnd = target.offset.y + target.size.height
        val delta =
            when {
                itemStart < viewportStart -> itemStart - viewportStart
                itemEnd > viewportEnd -> itemEnd - viewportEnd
                else -> 0
            }
        if (delta != 0) {
            scrollBy(delta.toFloat())
            return true
        }
        return false
    }

    val visibleItems = layoutInfo.visibleItemsInfo
    if (currentTarget() == null) {
        val targetIndex =
            when {
                visibleItems.isEmpty() -> index
                index < visibleItems.first().index -> index
                else -> anchorIndex.coerceAtLeast(0)
            }
        scrollToItem(targetIndex)
        withFrameNanos { }
    }

    repeat(2) {
        if (!adjustVisibleTarget()) {
            return
        }
        withFrameNanos { }
    }
}
