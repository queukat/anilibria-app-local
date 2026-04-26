package ru.radiationx.anilibria.screen.details

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.main.MainSectionBlock
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.screen.watching.TvDetailDescriptionBarPadding
import ru.radiationx.anilibria.screen.watching.TvDetailHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.clampedTvSectionTargetIndex
import ru.radiationx.anilibria.screen.watching.defaultTvSectionTargetIndex
import ru.radiationx.anilibria.screen.watching.findAdjacentTvSectionTarget
import ru.radiationx.anilibria.screen.watching.findTvSectionRestoreTarget
import ru.radiationx.anilibria.screen.watching.launchKeepTvSectionItemVisible
import ru.radiationx.anilibria.screen.watching.launchTvSectionFocus
import ru.radiationx.anilibria.screen.watching.rememberTvDescriptionOverlayClearance
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.resolveTvSectionTargetInSection
import ru.radiationx.anilibria.screen.watching.restoreTvSectionFocus
import ru.radiationx.anilibria.ui.compose.DebouncedCardBackdropEffect
import ru.radiationx.anilibria.ui.compose.TvUiDefaults
import ru.radiationx.anilibria.ui.compose.tvAppBackground

@Stable
internal data class DetailContentRestoreState(
    val focusToken: Int = 0,
    val preferredSectionIndex: Int = 0,
    val preferredItemIndex: Int = 0,
    val preferredItemKey: String? = null,
)

internal fun buildStableDetailSectionRequesters(
    sections: List<MainSectionUiModel>,
    requesterCache: MutableMap<Long, MutableMap<String, androidx.compose.ui.focus.FocusRequester>>,
): List<List<androidx.compose.ui.focus.FocusRequester>> {
    return sections.map { section ->
        val cachedRequesters = requesterCache.getOrPut(section.id) { mutableMapOf() }
        val activeItemKeys = section.items.map { it.stableKey }.toSet()
        cachedRequesters.keys.retainAll(activeItemKeys)
        section.items.map { item ->
            cachedRequesters.getOrPut(item.stableKey) {
                androidx.compose.ui.focus.FocusRequester()
            }
        }
    }
}

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
    onBackdropItemFocused: (CardItem) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalState = remember { LazyListState() }
    val sectionItems = remember(sections) { sections.map(MainSectionUiModel::items) }
    val sectionIds = remember(sections) { sections.map(MainSectionUiModel::id) }
    val sectionKeys =
        remember(sections) {
            sections.map { section ->
                section.id to section.items.map { it.stableKey }
            }
        }
    val rowStates = remember(sectionIds) { List(sections.size) { LazyListState() } }
    val requesterCache =
        remember {
            mutableMapOf<Long, MutableMap<String, androidx.compose.ui.focus.FocusRequester>>()
        }
    val sectionRequesters =
        remember(sectionKeys) {
            buildStableDetailSectionRequesters(
                sections = sections,
                requesterCache = requesterCache,
            )
        }
    val firstContentRequester =
        remember(sectionKeys) {
            sections.indices.asSequence()
                .mapNotNull { sectionIndex ->
                    val target =
                        resolveTvSectionTargetInSection(
                            sections = sectionItems,
                            sectionIndex = sectionIndex,
                            preferredItemIndex = 0,
                            resolveTargetIndex = ::defaultTvSectionTargetIndex,
                        ) ?: return@mapNotNull null
                    sectionRequesters.getOrNull(target.sectionIndex)?.getOrNull(target.itemIndex)
                }
                .firstOrNull()
                ?: androidx.compose.ui.focus.FocusRequester.Default
        }
    var selectedItem by remember { mutableStateOf<CardItem?>(null) }
    var handledContentRestoreToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    val hasContent = remember(sectionKeys) { sections.any { it.items.isNotEmpty() } }
    val descriptionOverlayClearance = rememberTvDescriptionOverlayClearance(hasContent = hasContent)
    var isContentPageActive by remember { mutableStateOf(false) }

    DebouncedCardBackdropEffect(
        card = selectedItem.takeIf { isContentPageActive },
        onCardSettled = onBackdropItemFocused,
    )

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
        val target =
            findAdjacentTvSectionTarget(
                sections = sectionItems,
                currentSectionIndex = currentSectionIndex,
                direction = direction,
                preferredItemIndex = preferredItemIndex,
                resolveTargetIndex = ::clampedTvSectionTargetIndex,
            ) ?: return false
        return launchTvSectionFocus(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = target,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
        )
    }

    LaunchedEffect(headerUiState.initialFocusToken) {
        selectedItem = null
        isContentPageActive = false
    }

    LaunchedEffect(sectionKeys) {
        val selectedKey = selectedItem?.stableKey
        val visibleItems = sections.asSequence().flatMap { it.items.asSequence() }.toList()
        val hadSelectedItem = selectedKey != null
        val stillVisible = selectedKey != null && visibleItems.any { it.stableKey == selectedKey }
        selectedItem = visibleItems.firstOrNull { it.stableKey == selectedKey }
            ?: visibleItems.firstOrNull { it.stableKey == contentRestoreState.preferredItemKey }
        if (hadSelectedItem && !stillVisible) {
            val restoreTarget =
                findTvSectionRestoreTarget(
                    sections = sectionItems,
                    preferredSectionIndex = lastFocusedSectionIndex,
                    preferredItemIndex = lastFocusedItemIndex,
                    resolveTargetIndex = ::clampedTvSectionTargetIndex,
                )
            if (restoreTarget != null) {
                isContentPageActive = true
                restoreTvSectionFocus(
                    verticalState = verticalState,
                    rowStates = rowStates,
                    sectionRequesters = sectionRequesters,
                    target = restoreTarget,
                    verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
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
        val restoreTarget =
            findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                preferredItemIndex = contentRestoreState.preferredItemIndex,
                preferredItemKey = contentRestoreState.preferredItemKey,
                resolveTargetIndex = ::clampedTvSectionTargetIndex,
            ) ?: return@LaunchedEffect
        isContentPageActive = true
        if (restoreTvSectionFocus(
                verticalState = verticalState,
                rowStates = rowStates,
                sectionRequesters = sectionRequesters,
                target = restoreTarget,
                verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
            )
        ) {
            handledContentRestoreToken = contentRestoreState.focusToken
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .tvAppBackground(palette),
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(x = 0, y = headerOffsetY.roundToPx()) },
            ) {
                ReleaseDetailsRowContent(
                    uiState = headerUiState,
                    callbacks = headerCallbacks,
                    showMoreHint = hasContent && !isContentPageActive,
                    actionsDownRequester = firstContentRequester,
                    onInitialHeaderFocusApplied = onHeaderFocusSettled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(headerHeight),
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset { IntOffset(x = 0, y = contentOffsetY.roundToPx()) }
                        .background(TvUiDefaults.surfaceBackdropBrush(palette)),
            ) {
                LazyColumn(
                    state = verticalState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
                    contentPadding =
                        PaddingValues(
                            top = 36.dp,
                            bottom = if (hasContent) descriptionOverlayClearance.bottomInset else 0.dp,
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
                                launchKeepTvSectionItemVisible(
                                    scope = scope,
                                    verticalState = verticalState,
                                    rowStates = rowStates,
                                    sectionIndex = sectionIndex,
                                    itemIndex = itemIndex,
                                    verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                                )
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
                            modifier = Modifier.padding(horizontal = TvDetailHorizontalPadding),
                            posterFocusStyle = TvUiDefaults.subtlePosterCardFocusStyle(palette),
                        )
                    }
                }
            }

            if (isContentPageActive) {
                selectedItem?.let { item ->
                    val description =
                        item.toTvCardDescription { card ->
                            card.resolveDescription(context)
                        }
                    if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
                        WatchingDescriptionBar(
                            title = description.title.toString(),
                            subtitle = description.subtitle.toString(),
                            palette = palette,
                            contentPadding = TvDetailDescriptionBarPadding,
                            solidSurface = true,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .then(descriptionOverlayClearance.measureModifier),
                        )
                    }
                }
            }
        }
    }
}
