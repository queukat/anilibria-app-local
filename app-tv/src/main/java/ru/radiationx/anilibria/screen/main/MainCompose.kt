package ru.radiationx.anilibria.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.TvStartupTrace
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.watching.TvBottomContentInset
import ru.radiationx.anilibria.screen.watching.TvDescriptionBarPadding
import ru.radiationx.anilibria.screen.watching.TvPosterCardWidth
import ru.radiationx.anilibria.screen.watching.TvRowEndPadding
import ru.radiationx.anilibria.screen.watching.TvRowSpacing
import ru.radiationx.anilibria.screen.watching.TvRowsScreenVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvSectionHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingMessageCard
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.WatchingPosterCard
import ru.radiationx.anilibria.screen.watching.defaultTvSectionTargetIndex
import ru.radiationx.anilibria.screen.watching.edgeAwareHorizontalTransformOrigin
import ru.radiationx.anilibria.screen.watching.findAdjacentTvSectionTarget
import ru.radiationx.anilibria.screen.watching.findTvSectionRestoreTarget
import ru.radiationx.anilibria.screen.watching.hasTvPosterContent
import ru.radiationx.anilibria.screen.watching.isTvStateOnlySection
import ru.radiationx.anilibria.screen.watching.launchKeepTvSectionItemVisible
import ru.radiationx.anilibria.screen.watching.launchTvSectionFocus
import ru.radiationx.anilibria.screen.watching.primaryTvStateItem
import ru.radiationx.anilibria.screen.watching.rememberTvDescriptionOverlayClearance
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.restoreTvSectionFocus
import ru.radiationx.anilibria.screen.watching.tvStateFocusIndex
import ru.radiationx.anilibria.ui.compose.DebouncedCardBackdropEffect
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvPosterCardFocusStyle
import ru.radiationx.anilibria.ui.compose.TvSectionHeader
import ru.radiationx.anilibria.ui.compose.TvUiDefaults

internal data class MainSectionUiModel(
    val id: Long,
    val title: String,
    val items: List<CardItem>,
)

@Stable
internal data class MainContentRestoreState(
    val preferredSectionIndex: Int = 0,
    val preferredItemIndex: Int = 0,
    val preferredItemId: Int = Int.MIN_VALUE,
)

private const val DESCRIPTION_REFRESH_INTERVAL_MS = 60_000L

@Composable
internal fun MainScreen(
    sections: List<MainSectionUiModel>,
    interactionsEnabled: Boolean = true,
    focusRequestToken: Int,
    visibilityRestoreToken: Int,
    contentRestoreState: MainContentRestoreState,
    onItemClick: (Long, CardItem) -> Unit,
    onRequestRailFocus: () -> Boolean,
    onRequestHeaderFocus: () -> Boolean,
    onContentMovedDown: () -> Unit,
    onContentMovedUp: () -> Unit,
    onItemFocused: (Int, Int, CardItem) -> Unit,
    onBackdropItemFocused: (CardItem) -> Unit,
) {
    LaunchedEffect(Unit) {
        TvStartupTrace.markOnce("main_loading_ui_visible")
    }
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalState = remember { LazyListState() }
    val sectionItems = remember(sections) { sections.map(MainSectionUiModel::items) }
    val sectionIds = remember(sections) { sections.map(MainSectionUiModel::id) }
    val sectionKeys =
        remember(sections) {
            sections.map { section ->
                section.id to section.items.map(CardItem::getId)
            }
        }
    val rowStates =
        remember(sectionIds) {
            List(sections.size) { LazyListState() }
        }
    val requesterCache =
        remember {
            mutableMapOf<Long, MutableMap<Int, androidx.compose.ui.focus.FocusRequester>>()
        }
    val sectionRequesters =
        remember(sectionKeys) {
            sections.map { section ->
                val cachedRequesters = requesterCache.getOrPut(section.id) { mutableMapOf() }
                val activeItemIds = section.items.map(CardItem::getId).toSet()
                cachedRequesters.keys.retainAll(activeItemIds)
                section.items.map { item ->
                    cachedRequesters.getOrPut(item.getId()) {
                        androidx.compose.ui.focus.FocusRequester()
                    }
                }
            }
        }
    var selectedItem by remember { mutableStateOf<LibriaCard?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    val hasContent = remember(sectionKeys) { sections.any { section -> section.items.hasTvPosterContent() } }
    val descriptionOverlayClearance = rememberTvDescriptionOverlayClearance(hasContent = hasContent)

    DebouncedCardBackdropEffect(
        card = selectedItem,
        onCardSettled = onBackdropItemFocused,
    )

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
                resolveTargetIndex = ::defaultTvSectionTargetIndex,
            ) ?: return false
        return launchTvSectionFocus(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = target,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
            onBeforeRequest =
                if (direction > 0) {
                    onContentMovedDown
                } else {
                    null
                },
        )
    }

    LaunchedEffect(sectionKeys) {
        val selectedId = selectedItem?.getId()
        val visibleCards =
            sections.asSequence()
                .flatMap { it.items.asSequence() }
                .filterIsInstance<LibriaCard>()
                .toList()
        val preferredItemId = contentRestoreState.preferredItemId
        val hadFocusedItem = preferredItemId != Int.MIN_VALUE
        val stillVisible =
            sections.asSequence()
                .flatMap { it.items.asSequence() }
                .any { it.getId() == preferredItemId }
        selectedItem = visibleCards.firstOrNull { it.getId() == selectedId }
            ?: visibleCards.firstOrNull { it.getId() == preferredItemId }
        if (hadFocusedItem && !stillVisible) {
            val restoreTarget =
                findTvSectionRestoreTarget(
                    sections = sectionItems,
                    preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                    preferredItemIndex = contentRestoreState.preferredItemIndex,
                    resolveTargetIndex = ::defaultTvSectionTargetIndex,
                )
            if (restoreTarget != null) {
                restoreTvSectionFocus(
                    verticalState = verticalState,
                    rowStates = rowStates,
                    sectionRequesters = sectionRequesters,
                    target = restoreTarget,
                    verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                )
            }
        }
    }

    LaunchedEffect(visibilityRestoreToken, sectionKeys) {
        if (visibilityRestoreToken <= 0 || contentRestoreState.preferredItemId == Int.MIN_VALUE) {
            return@LaunchedEffect
        }
        val restoreTarget =
            findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                preferredItemIndex = contentRestoreState.preferredItemIndex,
                preferredItemId = contentRestoreState.preferredItemId,
                resolveTargetIndex = ::defaultTvSectionTargetIndex,
            ) ?: return@LaunchedEffect
        selectedItem =
            sections.getOrNull(restoreTarget.sectionIndex)
                ?.items
                ?.getOrNull(restoreTarget.itemIndex) as? LibriaCard
        restoreTvSectionFocus(
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = restoreTarget,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
        )
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val restoreTarget =
            if (contentRestoreState.preferredItemId != Int.MIN_VALUE) {
                findTvSectionRestoreTarget(
                    sections = sectionItems,
                    preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                    preferredItemIndex = contentRestoreState.preferredItemIndex,
                    preferredItemId = contentRestoreState.preferredItemId,
                    resolveTargetIndex = ::defaultTvSectionTargetIndex,
                )
            } else {
                null
            } ?: findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = 0,
                preferredItemIndex = 0,
                resolveTargetIndex = ::defaultTvSectionTargetIndex,
            ) ?: return@LaunchedEffect
        if (restoreTvSectionFocus(
                verticalState = verticalState,
                rowStates = rowStates,
                sectionRequesters = sectionRequesters,
                target = restoreTarget,
                verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
            )
        ) {
            handledFocusToken = focusRequestToken
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(vertical = TvRowsScreenVerticalPadding),
    ) {
        LazyColumn(
            state = verticalState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
            contentPadding =
                PaddingValues(
                    top = 6.dp,
                    bottom =
                        if (hasContent) {
                            descriptionOverlayClearance.bottomInset
                        } else {
                            TvBottomContentInset
                        },
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
                    interactionsEnabled = interactionsEnabled,
                    rowState = rowStates.getOrNull(sectionIndex) ?: LazyListState(),
                    requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty(),
                    onItemClick = { item -> onItemClick(section.id, item) },
                    onItemFocused = { itemIndex, item ->
                        selectedItem = item as? LibriaCard
                        launchKeepTvSectionItemVisible(
                            scope = scope,
                            verticalState = verticalState,
                            rowStates = rowStates,
                            sectionIndex = sectionIndex,
                            itemIndex = itemIndex,
                            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                        )
                        onItemFocused(sectionIndex, itemIndex, item)
                    },
                    onLeftEdge = onRequestRailFocus,
                    onUp = { itemIndex ->
                        if (sectionIndex == 0) {
                            onContentMovedUp()
                            onRequestHeaderFocus()
                        } else {
                            requestSectionFocus(sectionIndex, -1, itemIndex)
                        }
                    },
                    onDown = { itemIndex ->
                        requestSectionFocus(sectionIndex, 1, itemIndex)
                    },
                )
            }
        }

        selectedItem?.let { item ->
            MainSelectedItemDescriptionBar(
                item = item,
                palette = palette,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .then(descriptionOverlayClearance.measureModifier),
            )
        }
    }
}

@Composable
private fun MainSelectedItemDescriptionBar(
    item: LibriaCard,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var descriptionTick by remember(
        item.itemId,
        item.description,
        item.relativeTimestampSec,
        item.relativePrefix,
    ) {
        mutableIntStateOf(0)
    }

    LaunchedEffect(item.itemId, item.relativeTimestampSec, item.relativePrefix) {
        if (item.relativeTimestampSec == null) {
            return@LaunchedEffect
        }
        while (isActive) {
            delay(DESCRIPTION_REFRESH_INTERVAL_MS)
            descriptionTick++
        }
    }

    val description =
        remember(item, descriptionTick, context) {
            item.toTvCardDescription { card ->
                card.resolveDescription(context)
            }
        }
    if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
        WatchingDescriptionBar(
            title = description.title.toString(),
            subtitle = description.subtitle.toString(),
            palette = palette,
            contentPadding = TvDescriptionBarPadding,
            solidSurface = true,
            modifier = modifier,
        )
    }
}

@Composable
internal fun MainSectionBlock(
    title: String,
    items: List<CardItem>,
    palette: WatchingPalette,
    interactionsEnabled: Boolean = true,
    rowState: LazyListState,
    requesters: List<androidx.compose.ui.focus.FocusRequester>,
    onItemClick: (CardItem) -> Unit,
    onItemFocused: (Int, CardItem) -> Unit,
    onLeftEdge: () -> Boolean,
    onUp: (Int) -> Boolean,
    onDown: (Int) -> Boolean,
    modifier: Modifier = Modifier,
    posterFocusStyle: TvPosterCardFocusStyle = TvUiDefaults.defaultPosterCardFocusStyle(palette),
) {
    val scope = rememberCoroutineScope()
    val stateItem = remember(items) { items.primaryTvStateItem() }
    val stateFocusIndex = remember(items) { items.tvStateFocusIndex() }
    val stateFocusItem =
        remember(items, stateFocusIndex) {
            stateFocusIndex?.let(items::getOrNull)
        }
    val stateActionLabel =
        remember(stateFocusItem) {
            when (stateFocusItem) {
                is LinkCard -> stateFocusItem.title
                is LoadingCard -> if (stateFocusItem.isError) "Повторить" else null
                else -> null
            }
        }

    fun moveFocusFromActionCard(index: Int) {
        val fallbackIndex =
            (index - 1 downTo 0)
                .firstOrNull { candidateIndex -> items.getOrNull(candidateIndex) is LibriaCard }
                ?: (index - 1).takeIf { it >= 0 }
                ?: return
        scope.launch {
            withFrameNanos { }
            requestWatchingFocusAfterAttach(
                requester = requesters.getOrNull(fallbackIndex),
                attempts = 4,
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TvSectionHeaderSpacing),
    ) {
        TvSectionHeader(
            title = title,
            palette = palette,
        )

        if (items.isTvStateOnlySection() && stateItem != null && stateFocusItem != null) {
            TvContentStatePanel(
                title =
                    when (stateItem) {
                        is LoadingCard -> stateItem.title.ifBlank { "Загрузка" }
                        is LinkCard -> stateItem.title
                        is InfoCard -> stateItem.title
                        else -> title
                    },
                subtitle =
                    when (stateItem) {
                        is LoadingCard ->
                            stateItem.description.ifBlank {
                                if (stateItem.isError) {
                                    "Проверьте подключение и повторите попытку."
                                } else {
                                    "Раздел обновится автоматически."
                                }
                            }
                        is LinkCard -> "Откройте полный раздел и продолжайте навигацию оттуда."
                        is InfoCard -> stateItem.subtitle
                        else -> ""
                    },
                palette = palette,
                accent = stateItem is LoadingCard && stateItem.isError,
                loading = stateItem is LoadingCard && !stateItem.isError,
                focusRequester =
                    if (stateActionLabel == null) {
                        if (interactionsEnabled) {
                            requesters.getOrNull(stateFocusIndex ?: -1)
                        } else {
                            null
                        }
                    } else {
                        null
                    },
                onFocused = {
                    onItemFocused(stateFocusIndex ?: 0, stateFocusItem)
                },
                onLeft = onLeftEdge,
                onUp = { onUp(stateFocusIndex ?: 0) },
                onDown = { onDown(stateFocusIndex ?: 0) },
                action =
                    stateActionLabel?.let { actionLabel ->
                        {
                            TvContentStateActionButton(
                                text = actionLabel,
                                palette = palette,
                                focusRequester =
                                    requesters.getOrNull(stateFocusIndex ?: -1)
                                        ?: androidx.compose.ui.focus.FocusRequester.Default,
                                onClick = { onItemClick(stateFocusItem) },
                                enabled = interactionsEnabled,
                                onFocused = {
                                    onItemFocused(stateFocusIndex ?: 0, stateFocusItem)
                                },
                                onLeft = onLeftEdge,
                                onUp = { onUp(stateFocusIndex ?: 0) },
                                onDown = { onDown(stateFocusIndex ?: 0) },
                            )
                        }
                    },
            )
        } else {
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TvRowSpacing),
                contentPadding = PaddingValues(end = TvRowEndPadding),
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.getId() },
                ) { index, item ->
                    when (item) {
                        is LibriaCard -> {
                            val isYoutube = item.type is LibriaCard.Type.Youtube
                            WatchingPosterCard(
                                imageUrl = item.image,
                                palette = palette,
                                focusRequester = requesters[index],
                                cardWidth = if (isYoutube) 346.dp else TvPosterCardWidth,
                                contentAspectRatio = if (isYoutube) 330f / 185f else 130f / 185f,
                                focusStyle = posterFocusStyle,
                                enabled = interactionsEnabled,
                                scaleTransformOrigin =
                                    edgeAwareHorizontalTransformOrigin(
                                        index = index,
                                        lastIndex = items.lastIndex,
                                    ),
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(index, item) },
                                onLeft = if (index == 0) onLeftEdge else null,
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )
                        }

                        is LinkCard ->
                            WatchingMessageCard(
                                title = item.title,
                                subtitle = "Нажмите, чтобы выполнить действие",
                                palette = palette,
                                focusRequester = requesters[index],
                                enabled = interactionsEnabled,
                                onClick = {
                                    moveFocusFromActionCard(index)
                                    onItemClick(item)
                                },
                                onFocused = { onItemFocused(index, item) },
                                onLeft = if (index == 0) onLeftEdge else null,
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )

                        is LoadingCard ->
                            WatchingMessageCard(
                                title = item.title.ifBlank { "Загрузка" },
                                subtitle =
                                    item.description.ifBlank {
                                        if (item.isError) "Нажмите, чтобы повторить попытку" else ""
                                    },
                                palette =
                                    palette.copy(
                                        accentColor =
                                            if (item.isError) {
                                                palette.accentColor
                                            } else {
                                                palette.textColor.copy(alpha = 0.4f)
                                            },
                                    ),
                                focusRequester = requesters[index],
                                enabled = interactionsEnabled,
                                loading = !item.isError,
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(index, item) },
                                onLeft = if (index == 0) onLeftEdge else null,
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )

                        is InfoCard ->
                            WatchingMessageCard(
                                title = item.title,
                                subtitle = item.subtitle,
                                palette = palette,
                                focusRequester = requesters[index],
                                enabled = interactionsEnabled,
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(index, item) },
                                onLeft = if (index == 0) onLeftEdge else null,
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )
                    }
                }
            }
        }
    }
}
