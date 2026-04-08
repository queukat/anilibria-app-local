package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem

internal data class TvSectionFocusTarget(
    val sectionIndex: Int,
    val itemIndex: Int,
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
    preferredItemId: Int = Int.MIN_VALUE,
    resolveTargetIndex: TvSectionTargetIndexResolver = ::defaultTvSectionTargetIndex,
): TvSectionFocusTarget? {
    if (sections.isEmpty()) {
        return null
    }
    if (preferredItemId != Int.MIN_VALUE) {
        sections.forEachIndexed { sectionIndex, items ->
            val itemIndex = items.indexOfItemId(preferredItemId)
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
    val requester = sectionRequesters.getOrNull(target.sectionIndex)
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
    val requester = sectionRequesters.getOrNull(target.sectionIndex)
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
