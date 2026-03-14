package ru.radiationx.anilibria.screen.details

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.main.MainSectionBlock
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.TvBottomDescriptionInset
import ru.radiationx.anilibria.screen.watching.TvDescriptionBarPadding
import ru.radiationx.anilibria.screen.watching.TvScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded
import ru.radiationx.anilibria.ui.presenter.ReleaseDetailsCallbacks
import ru.radiationx.anilibria.ui.presenter.ReleaseDetailsRowContent
import ru.radiationx.anilibria.ui.presenter.ReleaseDetailsRowUiState

@Stable
internal data class DetailContentRestoreState(
    val focusToken: Int = 0,
    val preferredSectionIndex: Int = 0,
    val preferredItemIndex: Int = 0,
    val preferredItemId: Int = Int.MIN_VALUE,
)

@Composable
internal fun DetailScreen(
    headerUiState: ReleaseDetailsRowUiState,
    headerCallbacks: ReleaseDetailsCallbacks,
    sections: List<MainSectionUiModel>,
    contentRestoreState: DetailContentRestoreState,
    contentSelectionEnabled: Boolean,
    onRequestHeaderFocus: () -> Unit,
    onHeaderFocusSettled: () -> Unit,
    onSectionItemClick: (Long, CardItem) -> Unit,
    onContentItemFocused: (Int, Int, CardItem) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalState = remember { LazyListState() }
    val detailCardBackground = colorResource(R.color.dark_cardBackground).copy(alpha = 0.86f)
    val contentPageStartColor = colorResource(R.color.dark_windowBackground)
    val contentPageEndColor = colorResource(R.color.dark_colorPrimary)
    val sectionKeys = remember(sections) {
        sections.map { section ->
            section.id to section.items.map(CardItem::getId)
        }
    }
    val rowStates = remember(sectionKeys) { List(sections.size) { LazyListState() } }
    val sectionRequesters = remember(sectionKeys) {
        sections.map { section ->
            List(section.items.size) { androidx.compose.ui.focus.FocusRequester() }
        }
    }
    val firstContentRequester = remember(sectionKeys) {
        sectionRequesters.firstOrNull()?.firstOrNull() ?: androidx.compose.ui.focus.FocusRequester.Default
    }
    var selectedItem by remember(sectionKeys) { mutableStateOf<CardItem?>(null) }
    var handledContentRestoreToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    val hasContent = remember(sectionKeys) { sections.any { it.items.isNotEmpty() } }
    var isContentPageActive by remember { mutableStateOf(false) }

    fun targetInSection(
        sectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty()
        return requesters
            .takeIf { it.isNotEmpty() }
            ?.let { sectionIndex to preferredItemIndex.coerceIn(0, it.lastIndex) }
    }

    fun targetByItemId(preferredItemId: Int): Pair<Int, Int>? {
        var target: Pair<Int, Int>? = null
        sections.forEachIndexed { sectionIndex, section ->
            if (target == null) {
                val itemIndex = section.items.indexOfFirst { it.getId() == preferredItemId }
                if (itemIndex >= 0) {
                    target = sectionIndex to itemIndex
                }
            }
        }
        return target
    }

    fun findRestoreTarget(
        preferredSectionIndex: Int,
        preferredItemIndex: Int,
        preferredItemId: Int = Int.MIN_VALUE,
    ): Pair<Int, Int>? {
        var target = if (preferredItemId != Int.MIN_VALUE) {
            targetByItemId(preferredItemId)
        } else {
            null
        }
        val clampedSectionIndex = preferredSectionIndex.coerceIn(0, sections.lastIndex.coerceAtLeast(0))
        for (offset in 0..sections.size) {
            if (target == null) {
                target = targetInSection(
                    sectionIndex = clampedSectionIndex + offset,
                    preferredItemIndex = preferredItemIndex,
                )
            }
            if (target == null && offset > 0) {
                target = targetInSection(
                    sectionIndex = clampedSectionIndex - offset,
                    preferredItemIndex = preferredItemIndex,
                )
            }
        }
        return target
    }

    fun requestHeaderFocus() {
        selectedItem = null
        isContentPageActive = false
        onRequestHeaderFocus()
    }

    fun requestSectionFocus(
        currentSectionIndex: Int,
        direction: Int,
        preferredItemIndex: Int,
    ): Boolean {
        var targetSectionIndex = currentSectionIndex + direction
        while (targetSectionIndex in sections.indices) {
            val requesters = sectionRequesters.getOrNull(targetSectionIndex).orEmpty()
            if (requesters.isNotEmpty()) {
                val targetItemIndex = preferredItemIndex.coerceIn(0, requesters.lastIndex)
                scope.launch {
                    verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
                    rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
                    requestWatchingFocusAfterAttach(requesters.getOrNull(targetItemIndex))
                }
                return true
            }
            targetSectionIndex += direction
        }
        return false
    }

    fun keepItemVisible(sectionIndex: Int, itemIndex: Int) {
        scope.launch {
            verticalState.scrollItemIntoViewIfNeeded(sectionIndex)
            rowStates.getOrNull(sectionIndex)?.scrollItemIntoViewIfNeeded(itemIndex)
        }
    }

    LaunchedEffect(headerUiState.initialFocusToken) {
        selectedItem = null
        isContentPageActive = false
    }

    LaunchedEffect(sectionKeys) {
        val selectedId = selectedItem?.getId()
        val visibleItems = sections.asSequence().flatMap { it.items.asSequence() }.toList()
        val hadSelectedItem = selectedId != null
        val stillVisible = selectedId != null && visibleItems.any { it.getId() == selectedId }
        selectedItem = visibleItems.firstOrNull { it.getId() == selectedId }
        if (hadSelectedItem && !stillVisible) {
            val restoreTarget = findRestoreTarget(lastFocusedSectionIndex, lastFocusedItemIndex)
            if (restoreTarget != null) {
                val (targetSectionIndex, targetItemIndex) = restoreTarget
                isContentPageActive = true
                verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
                rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
                requestWatchingFocusAfterAttach(
                    sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
                )
            } else {
                onRequestHeaderFocus()
            }
        }
    }

    LaunchedEffect(contentRestoreState.focusToken, sectionKeys) {
        if (
            contentRestoreState.focusToken <= 0 ||
            contentRestoreState.focusToken <= handledContentRestoreToken ||
            sections.isEmpty()
        ) {
            return@LaunchedEffect
        }
        val restoreTarget = findRestoreTarget(
            preferredSectionIndex = contentRestoreState.preferredSectionIndex,
            preferredItemIndex = contentRestoreState.preferredItemIndex,
            preferredItemId = contentRestoreState.preferredItemId,
        ) ?: return@LaunchedEffect
        val (targetSectionIndex, targetItemIndex) = restoreTarget
        isContentPageActive = true
        verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
        if (requestWatchingFocusAfterAttach(
            sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
        )) {
            handledContentRestoreToken = contentRestoreState.focusToken
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceColor.copy(alpha = 0.16f),
                        Color.Transparent,
                    )
                )
            )
    ) {
        val headerHeight = maxHeight
        val headerOffsetY by animateDpAsState(
            targetValue = if (hasContent && isContentPageActive) -headerHeight else 0.dp,
            label = "detailHeaderPageOffset",
        )
        val contentOffsetY by animateDpAsState(
            targetValue = if (hasContent && isContentPageActive) 0.dp else headerHeight,
            label = "detailContentPageOffset",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = 0, y = headerOffsetY.roundToPx()) },
            ) {
                ReleaseDetailsRowContent(
                    uiState = headerUiState,
                    callbacks = headerCallbacks,
                    showMoreHint = hasContent && !isContentPageActive,
                    actionsDownRequester = firstContentRequester,
                    onInitialHeaderFocusApplied = onHeaderFocusSettled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = 0, y = contentOffsetY.roundToPx()) }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                contentPageStartColor.copy(alpha = 0.98f),
                                contentPageEndColor.copy(alpha = 0.94f),
                            )
                        )
                    ),
            ) {
                LazyColumn(
                    state = verticalState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
                    contentPadding = PaddingValues(
                        top = 36.dp,
                        bottom = if (hasContent) TvBottomDescriptionInset else 0.dp,
                    ),
                ) {
                    itemsIndexed(
                        items = sections,
                        key = { _, section -> section.id },
                    ) { sectionIndex, section ->
                        MainSectionBlock(
                            title = section.title,
                            items = section.items,
                            palette = palette,
                            rowState = rowStates.getOrNull(sectionIndex) ?: LazyListState(),
                            requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty(),
                            onItemClick = { item -> onSectionItemClick(section.id, item) },
                            onItemFocused = itemFocus@{ itemIndex, item ->
                                if (!contentSelectionEnabled) {
                                    return@itemFocus
                                }
                                isContentPageActive = true
                                selectedItem = item
                                lastFocusedSectionIndex = sectionIndex
                                lastFocusedItemIndex = itemIndex
                                keepItemVisible(sectionIndex, itemIndex)
                                onContentItemFocused(sectionIndex, itemIndex, item)
                            },
                            onLeftEdge = { false },
                            onUp = { itemIndex ->
                                if (sectionIndex == 0) {
                                    requestHeaderFocus()
                                    true
                                } else {
                                    requestSectionFocus(sectionIndex, -1, itemIndex)
                                }
                            },
                            onDown = { itemIndex ->
                                requestSectionFocus(sectionIndex, 1, itemIndex)
                            },
                            modifier = Modifier.padding(horizontal = TvScreenHorizontalPadding),
                            posterFocusedBackgroundColor = detailCardBackground,
                            posterBorderColor = palette.textColor.copy(alpha = 0.58f),
                            posterFocusedBorderWidth = 2.dp,
                            posterUnfocusedBorderWidth = 1.dp,
                        )
                    }
                }
            }

            if (isContentPageActive) {
                selectedItem?.let { item ->
                val description = item.toTvCardDescription { card ->
                    card.resolveDescription(context)
                }
                if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
                    WatchingDescriptionBar(
                        title = description.title.toString(),
                        subtitle = description.subtitle.toString(),
                        palette = palette,
                        contentPadding = TvDescriptionBarPadding,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            }
        }
    }
}
