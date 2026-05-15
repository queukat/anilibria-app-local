package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import kotlin.math.abs

internal data class TvSectionFocusTarget(
    val sectionIndex: Int,
    val itemIndex: Int,
)

internal data class TvSectionVisibleItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

internal typealias TvSectionTargetIndexResolver = (items: List<CardItem>, preferredItemIndex: Int) -> Int?

internal fun defaultTvSectionTargetIndex(
    items: List<CardItem>,
    preferredItemIndex: Int,
): Int? {
    if (items.isEmpty()) {
        return null
    }
    return if (items.hasTvPosterContent()) {
        preferredItemIndex.coerceIn(0, items.lastIndex)
    } else {
        items.tvStateFocusIndex()
    }
}

internal fun clampedTvSectionTargetIndex(
    items: List<CardItem>,
    preferredItemIndex: Int,
): Int? {
    if (items.isEmpty()) {
        return null
    }
    return preferredItemIndex.coerceIn(0, items.lastIndex)
}

internal fun resolveTvSectionTargetInSection(
    sections: List<List<CardItem>>,
    sectionIndex: Int,
    preferredItemIndex: Int,
    resolveTargetIndex: TvSectionTargetIndexResolver = ::defaultTvSectionTargetIndex,
): TvSectionFocusTarget? {
    val items = sections.getOrNull(sectionIndex).orEmpty()
    val targetItemIndex = resolveTargetIndex(items, preferredItemIndex) ?: return null
    return TvSectionFocusTarget(
        sectionIndex = sectionIndex,
        itemIndex = targetItemIndex.coerceIn(0, items.lastIndex),
    )
}

internal fun findTvSectionRestoreTarget(
    sections: List<List<CardItem>>,
    preferredSectionIndex: Int,
    preferredItemIndex: Int,
    preferredItemKey: String? = null,
    resolveTargetIndex: TvSectionTargetIndexResolver = ::defaultTvSectionTargetIndex,
): TvSectionFocusTarget? {
    if (sections.isEmpty()) {
        return null
    }
    if (!preferredItemKey.isNullOrEmpty()) {
        sections.forEachIndexed { sectionIndex, items ->
            val itemIndex = items.indexOfStableKey(preferredItemKey)
            if (itemIndex != null) {
                return TvSectionFocusTarget(sectionIndex, itemIndex)
            }
        }
    }
    val clampedSectionIndex = preferredSectionIndex.coerceIn(0, sections.lastIndex)
    for (offset in 0..sections.size) {
        resolveTvSectionTargetInSection(
            sections = sections,
            sectionIndex = clampedSectionIndex + offset,
            preferredItemIndex = preferredItemIndex,
            resolveTargetIndex = resolveTargetIndex,
        )?.let { return it }
        if (offset > 0) {
            resolveTvSectionTargetInSection(
                sections = sections,
                sectionIndex = clampedSectionIndex - offset,
                preferredItemIndex = preferredItemIndex,
                resolveTargetIndex = resolveTargetIndex,
            )?.let { return it }
        }
    }
    return null
}

internal fun findAdjacentTvSectionTarget(
    sections: List<List<CardItem>>,
    currentSectionIndex: Int,
    direction: Int,
    preferredItemIndex: Int,
    resolveTargetIndex: TvSectionTargetIndexResolver = ::defaultTvSectionTargetIndex,
): TvSectionFocusTarget? {
    var targetSectionIndex = currentSectionIndex + direction
    while (targetSectionIndex in sections.indices) {
        resolveTvSectionTargetInSection(
            sections = sections,
            sectionIndex = targetSectionIndex,
            preferredItemIndex = preferredItemIndex,
            resolveTargetIndex = resolveTargetIndex,
        )?.let { return it }
        targetSectionIndex += direction
    }
    return null
}

internal fun resolveTvSectionTargetIndexFromViewport(
    items: List<CardItem>,
    visibleItems: List<TvSectionVisibleItem>,
    firstVisibleItemIndex: Int?,
    horizontalAnchorPx: Int?,
    preferredItemIndex: Int,
    resolveTargetIndex: TvSectionTargetIndexResolver = ::defaultTvSectionTargetIndex,
): Int? {
    if (items.isEmpty()) {
        return null
    }
    if (!items.hasTvPosterContent()) {
        return resolveTargetIndex(items, preferredItemIndex)
    }

    val validVisibleItems = visibleItems.filter { it.index in items.indices }
    if (validVisibleItems.isNotEmpty()) {
        val anchorPx = horizontalAnchorPx ?: validVisibleItems.first().centerPx
        return validVisibleItems.minByOrNull { item -> abs(item.centerPx - anchorPx) }?.index
    }

    val rowPreferredIndex = firstVisibleItemIndex ?: preferredItemIndex
    return resolveTargetIndex(items, rowPreferredIndex)
}

internal fun findAdjacentVisibleTvSectionTarget(
    sections: List<List<CardItem>>,
    rowStates: List<LazyListState>,
    currentSectionIndex: Int,
    direction: Int,
    preferredItemIndex: Int,
    resolveTargetIndex: TvSectionTargetIndexResolver = ::defaultTvSectionTargetIndex,
): TvSectionFocusTarget? {
    val horizontalAnchorPx =
        rowStates
            .getOrNull(currentSectionIndex)
            ?.focusedItemCenterPx(preferredItemIndex)
    var targetSectionIndex = currentSectionIndex + direction
    while (targetSectionIndex in sections.indices) {
        val items = sections.getOrNull(targetSectionIndex).orEmpty()
        val rowState = rowStates.getOrNull(targetSectionIndex)
        val targetItemIndex =
            resolveTvSectionTargetIndexFromViewport(
                items = items,
                visibleItems = rowState?.visibleTvSectionItems().orEmpty(),
                firstVisibleItemIndex = rowState?.firstVisibleItemIndex,
                horizontalAnchorPx = horizontalAnchorPx,
                preferredItemIndex = preferredItemIndex,
                resolveTargetIndex = resolveTargetIndex,
            )
        if (targetItemIndex != null) {
            return TvSectionFocusTarget(
                sectionIndex = targetSectionIndex,
                itemIndex = targetItemIndex.coerceIn(0, items.lastIndex),
            )
        }
        targetSectionIndex += direction
    }
    return null
}

internal fun launchTvSectionFocus(
    scope: CoroutineScope,
    verticalState: LazyListState,
    rowStates: List<LazyListState>,
    sectionRequesters: List<List<FocusRequester>>,
    target: TvSectionFocusTarget,
    verticalBottomClearancePx: Int = 0,
    sectionListIndex: (Int) -> Int = { it },
    onBeforeRequest: (() -> Unit)? = null,
): Boolean {
    val requester =
        sectionRequesters.getOrNull(target.sectionIndex)
            ?.getOrNull(target.itemIndex)
            ?: return false
    onBeforeRequest?.invoke()
    scope.launch {
        verticalState.scrollItemIntoViewIfNeeded(
            index = sectionListIndex(target.sectionIndex),
            bottomClearancePx = verticalBottomClearancePx,
        )
        rowStates.getOrNull(target.sectionIndex)?.scrollItemIntoViewIfNeeded(target.itemIndex)
        requestWatchingFocusAfterAttach(requester)
    }
    return true
}

internal suspend fun restoreTvSectionFocus(
    verticalState: LazyListState,
    rowStates: List<LazyListState>,
    sectionRequesters: List<List<FocusRequester>>,
    target: TvSectionFocusTarget,
    verticalBottomClearancePx: Int = 0,
    sectionListIndex: (Int) -> Int = { it },
): Boolean {
    val requester =
        sectionRequesters.getOrNull(target.sectionIndex)
            ?.getOrNull(target.itemIndex)
            ?: return false
    verticalState.scrollItemIntoViewIfNeeded(
        index = sectionListIndex(target.sectionIndex),
        bottomClearancePx = verticalBottomClearancePx,
    )
    rowStates.getOrNull(target.sectionIndex)?.scrollItemIntoViewIfNeeded(target.itemIndex)
    return requestWatchingFocusAfterAttach(requester)
}

internal fun launchKeepTvSectionItemVisible(
    scope: CoroutineScope,
    verticalState: LazyListState,
    rowStates: List<LazyListState>,
    sectionIndex: Int,
    itemIndex: Int,
    verticalBottomClearancePx: Int = 0,
    sectionListIndex: (Int) -> Int = { it },
) {
    scope.launch {
        verticalState.scrollItemIntoViewIfNeeded(
            index = sectionListIndex(sectionIndex),
            bottomClearancePx = verticalBottomClearancePx,
        )
        rowStates.getOrNull(sectionIndex)?.scrollItemIntoViewIfNeeded(itemIndex)
    }
}

private val TvSectionVisibleItem.centerPx: Int
    get() = offset + size / 2

private fun LazyListState.visibleTvSectionItems(): List<TvSectionVisibleItem> {
    return layoutInfo.visibleItemsInfo.map { item ->
        TvSectionVisibleItem(
            index = item.index,
            offset = item.offset,
            size = item.size,
        )
    }
}

private fun LazyListState.focusedItemCenterPx(itemIndex: Int): Int? {
    return visibleTvSectionItems()
        .firstOrNull { item -> item.index == itemIndex }
        ?.centerPx
}
