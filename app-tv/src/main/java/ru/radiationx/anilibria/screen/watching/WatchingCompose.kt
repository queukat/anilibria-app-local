package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvSectionHeader
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import ru.radiationx.anilibria.screen.watching.defaultTvSectionTargetIndex
import ru.radiationx.anilibria.screen.watching.findAdjacentTvSectionTarget
import ru.radiationx.anilibria.screen.watching.findTvSectionRestoreTarget
import ru.radiationx.anilibria.screen.watching.launchKeepTvSectionItemVisible
import ru.radiationx.anilibria.screen.watching.launchTvSectionFocus
import ru.radiationx.anilibria.screen.watching.rememberTvDescriptionOverlayClearance
import ru.radiationx.anilibria.screen.watching.restoreTvSectionFocus

internal data class WatchingSectionUiModel(
    val id: Long,
    val title: String,
    val items: List<CardItem>,
)

@Composable
internal fun WatchingScreen(
    sections: List<WatchingSectionUiModel>,
    interactionsEnabled: Boolean = true,
    focusRequestToken: Int,
    visibilityRestoreToken: Int,
    onItemClick: (Long, CardItem) -> Unit,
    onRequestRailFocus: () -> Boolean,
    onRequestHeaderFocus: () -> Boolean,
    onContentMovedDown: () -> Unit,
    onContentMovedUp: () -> Unit,
    onItemFocused: (Int, Int, CardItem) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalState = remember { LazyListState() }
    val sectionItems = remember(sections) { sections.map(WatchingSectionUiModel::items) }
    val sectionKeys = remember(sections) {
        sections.map { section ->
            section.id to section.items.map(CardItem::getId)
        }
    }
    val rowStates = remember(sectionKeys) {
        List(sections.size) { LazyListState() }
    }
    val sectionRequesters = remember(sectionKeys) {
        sections.map { section ->
            List(section.items.size) { androidx.compose.ui.focus.FocusRequester() }
        }
    }
    var selectedCard by remember(sectionKeys) { mutableStateOf<LibriaCard?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemId by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    val hasContent = remember(sectionKeys) { sections.any { section -> section.items.hasTvPosterContent() } }
    val descriptionOverlayClearance = rememberTvDescriptionOverlayClearance(hasContent = hasContent)

    fun requestSectionFocus(
        currentSectionIndex: Int,
        direction: Int,
        preferredItemIndex: Int,
    ): Boolean {
        val target = findAdjacentTvSectionTarget(
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
            onBeforeRequest = if (direction > 0) {
                onContentMovedDown
            } else {
                null
            },
        )
    }

    LaunchedEffect(sectionKeys) {
        val selectedId = selectedCard?.getId()
        val visibleItems = sections
            .asSequence()
            .flatMap { it.items.asSequence() }
            .toList()
        val visibleCards = sections
            .asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<LibriaCard>()
            .toList()
        val hadFocusedItem = lastFocusedItemId != Int.MIN_VALUE
        val stillVisible = visibleItems.any { it.getId() == lastFocusedItemId }
        if (visibleCards.none { it.getId() == selectedId }) {
            selectedCard = null
        }
        if (hadFocusedItem && !stillVisible) {
            val restoreTarget = findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
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
        if (visibilityRestoreToken <= 0 || lastFocusedItemId == Int.MIN_VALUE) {
            return@LaunchedEffect
        }
        val restoreTarget = findTvSectionRestoreTarget(
            sections = sectionItems,
            preferredSectionIndex = lastFocusedSectionIndex,
            preferredItemIndex = lastFocusedItemIndex,
            preferredItemId = lastFocusedItemId,
            resolveTargetIndex = ::defaultTvSectionTargetIndex,
        )
            ?: return@LaunchedEffect
        restoreTvSectionFocus(
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = restoreTarget,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
        )
        selectedCard = sections.getOrNull(restoreTarget.sectionIndex)
            ?.items
            ?.getOrNull(restoreTarget.itemIndex) as? LibriaCard
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val restoreTarget = if (lastFocusedItemId != Int.MIN_VALUE) {
            findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
                resolveTargetIndex = ::defaultTvSectionTargetIndex,
            )
        } else {
            null
        } ?: findTvSectionRestoreTarget(
            sections = sectionItems,
            preferredSectionIndex = 0,
            preferredItemIndex = 0,
            resolveTargetIndex = ::defaultTvSectionTargetIndex,
        )
            ?: return@LaunchedEffect
        val restoredFocus = restoreTvSectionFocus(
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = restoreTarget,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
        )
        if (restoredFocus) {
            handledFocusToken = focusRequestToken
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvAppBackground(palette)
            .padding(horizontal = TvCardScreenHorizontalPadding, vertical = TvRowsScreenVerticalPadding),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = verticalState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
                contentPadding = PaddingValues(
                    top = 6.dp,
                    bottom = if (hasContent) {
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
                    WatchingSectionBlock(
                        title = section.title,
                        items = section.items,
                        palette = palette,
                        interactionsEnabled = interactionsEnabled,
                        rowState = rowStates.getOrNull(sectionIndex) ?: LazyListState(),
                        requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty(),
                        onItemClick = { item ->
                            onItemClick(section.id, item)
                        },
                        onCardFocused = { itemIndex, card ->
                            selectedCard = card
                            lastFocusedSectionIndex = sectionIndex
                            lastFocusedItemIndex = itemIndex
                            lastFocusedItemId = card.getId()
                            launchKeepTvSectionItemVisible(
                                scope = scope,
                                verticalState = verticalState,
                                rowStates = rowStates,
                                sectionIndex = sectionIndex,
                                itemIndex = itemIndex,
                                verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                            )
                            onItemFocused(sectionIndex, itemIndex, card)
                        },
                        onMessageFocused = { itemIndex, item ->
                            selectedCard = null
                            lastFocusedSectionIndex = sectionIndex
                            lastFocusedItemIndex = itemIndex
                            lastFocusedItemId = item.getId()
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

            selectedCard?.let { card ->
                WatchingDescriptionBar(
                    title = card.title,
                    subtitle = card.resolveDescription(context),
                    palette = palette,
                    solidSurface = true,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(descriptionOverlayClearance.measureModifier),
                )
            }
        }
    }
}

@Composable
private fun WatchingSectionBlock(
    title: String,
    items: List<CardItem>,
    palette: WatchingPalette,
    interactionsEnabled: Boolean = true,
    rowState: LazyListState,
    requesters: List<androidx.compose.ui.focus.FocusRequester>,
    onItemClick: (CardItem) -> Unit,
    onCardFocused: (Int, LibriaCard) -> Unit,
    onMessageFocused: (Int, CardItem) -> Unit,
    onLeftEdge: () -> Boolean,
    onUp: (Int) -> Boolean,
    onDown: (Int) -> Boolean,
) {
    val stateItem = remember(items) { items.primaryTvStateItem() }
    val stateFocusIndex = remember(items) { items.tvStateFocusIndex() }
    val stateFocusItem = remember(items, stateFocusIndex) {
        stateFocusIndex?.let(items::getOrNull)
    }
    val stateActionLabel = remember(stateFocusItem) {
        when (stateFocusItem) {
            is LinkCard -> stateFocusItem.title
            is LoadingCard -> if (stateFocusItem.isError) "Повторить" else null
            else -> null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TvSectionHeaderSpacing),
    ) {
        TvSectionHeader(
            title = title,
            palette = palette,
        )

        if (items.isTvStateOnlySection() && stateItem != null && stateFocusItem != null) {
            TvContentStatePanel(
                title = when (stateItem) {
                    is LoadingCard -> stateItem.title.ifBlank { "Загрузка" }
                    is LinkCard -> stateItem.title
                    is InfoCard -> stateItem.title
                    else -> title
                },
                subtitle = when (stateItem) {
                    is LoadingCard -> stateItem.description.ifBlank {
                        if (stateItem.isError) {
                            "Не удалось обновить раздел. Попробуйте ещё раз."
                        } else {
                            "Раздел обновится автоматически."
                        }
                    }
                    is LinkCard -> "Откройте дополнительные элементы этого раздела."
                    is InfoCard -> stateItem.subtitle
                    else -> ""
                },
                palette = palette,
                accent = stateItem is LoadingCard && stateItem.isError,
                loading = stateItem is LoadingCard && !stateItem.isError,
                focusRequester = if (stateActionLabel == null) {
                    if (interactionsEnabled) {
                        requesters.getOrNull(stateFocusIndex ?: -1)
                    } else {
                        null
                    }
                } else {
                    null
                },
                onFocused = { onMessageFocused(stateFocusIndex ?: 0, stateFocusItem) },
                onLeft = onLeftEdge,
                onUp = { onUp(stateFocusIndex ?: 0) },
                onDown = { onDown(stateFocusIndex ?: 0) },
                action = stateActionLabel?.let { actionLabel ->
                    {
                        TvContentStateActionButton(
                            text = actionLabel,
                            palette = palette,
                            focusRequester = requesters.getOrNull(stateFocusIndex ?: -1)
                                ?: androidx.compose.ui.focus.FocusRequester.Default,
                            onClick = { onItemClick(stateFocusItem) },
                            enabled = interactionsEnabled,
                            onFocused = {
                                onMessageFocused(stateFocusIndex ?: 0, stateFocusItem)
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
                        is LibriaCard -> WatchingPosterCard(
                            imageUrl = item.image,
                            palette = palette,
                            focusRequester = requesters[index],
                            enabled = interactionsEnabled,
                            scaleTransformOrigin = edgeAwareHorizontalTransformOrigin(
                                index = index,
                                lastIndex = items.lastIndex,
                            ),
                            onClick = { onItemClick(item) },
                            onFocused = { onCardFocused(index, item) },
                            onLeft = if (index == 0) onLeftEdge else null,
                            onUp = {
                                onUp(index)
                            },
                            onDown = {
                                onDown(index)
                            },
                        )

                        is LinkCard -> WatchingMessageCard(
                            title = item.title,
                            subtitle = "Нажмите, чтобы загрузить ещё",
                            palette = palette,
                            focusRequester = requesters[index],
                            enabled = interactionsEnabled,
                            onClick = { onItemClick(item) },
                            onFocused = { onMessageFocused(index, item) },
                            onLeft = if (index == 0) onLeftEdge else null,
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )

                        is LoadingCard -> WatchingMessageCard(
                            title = item.title.ifBlank { "Загрузка" },
                            subtitle = item.description.ifBlank {
                                if (item.isError) "Нажмите, чтобы повторить попытку" else ""
                            },
                            palette = palette.copy(
                                accentColor = if (item.isError) {
                                    palette.accentColor
                                } else {
                                    palette.textColor.copy(alpha = 0.4f)
                                }
                            ),
                            focusRequester = requesters[index],
                            enabled = interactionsEnabled,
                            loading = !item.isError,
                            onClick = { onItemClick(item) },
                            onFocused = { onMessageFocused(index, item) },
                            onLeft = if (index == 0) onLeftEdge else null,
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )

                        is InfoCard -> WatchingMessageCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            palette = palette,
                            focusRequester = requesters[index],
                            enabled = interactionsEnabled,
                            onClick = { onItemClick(item) },
                            onFocused = { onMessageFocused(index, item) },
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
