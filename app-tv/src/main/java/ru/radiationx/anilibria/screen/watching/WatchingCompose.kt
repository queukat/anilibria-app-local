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
import androidx.compose.runtime.withFrameNanos
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

internal data class WatchingSectionUiModel(
    val id: Long,
    val title: String,
    val items: List<CardItem>,
)

@Composable
internal fun WatchingScreen(
    sections: List<WatchingSectionUiModel>,
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

    fun targetInSection(
        sectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val items = sections.getOrNull(sectionIndex)?.items.orEmpty()
        val requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty()
        val targetItemIndex = requesters.takeIf { it.isNotEmpty() }?.let {
            if (items.hasTvPosterContent()) {
                preferredItemIndex.coerceIn(0, it.lastIndex)
            } else {
                items.tvStateFocusIndex()
            }
        }
        return targetItemIndex?.let { sectionIndex to it.coerceIn(0, requesters.lastIndex) }
    }

    fun targetByItemId(preferredItemId: Int): Pair<Int, Int>? {
        var target: Pair<Int, Int>? = null
        sections.forEachIndexed { sectionIndex, section ->
            if (target == null) {
                val itemIndex = section.items.indexOfItemId(preferredItemId)
                if (itemIndex != null) {
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
            if (target != null) {
                return target
            }
        }
        return null
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
                if (direction > 0) {
                    onContentMovedDown()
                }
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
            val restoreTarget = findRestoreTarget(
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
            )
            if (restoreTarget != null) {
                val (targetSectionIndex, targetItemIndex) = restoreTarget
                verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
                rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
                requestWatchingFocusAfterAttach(
                    sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
                )
            }
        }
    }

    LaunchedEffect(visibilityRestoreToken, sectionKeys) {
        if (visibilityRestoreToken <= 0 || lastFocusedItemId == Int.MIN_VALUE) {
            return@LaunchedEffect
        }
        val restoreTarget = findRestoreTarget(
            preferredSectionIndex = lastFocusedSectionIndex,
            preferredItemIndex = lastFocusedItemIndex,
            preferredItemId = lastFocusedItemId,
        )
            ?: return@LaunchedEffect
        val (targetSectionIndex, targetItemIndex) = restoreTarget
        verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
        selectedCard = sections.getOrNull(targetSectionIndex)
            ?.items
            ?.getOrNull(targetItemIndex) as? LibriaCard
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val restoreTarget = if (lastFocusedItemId != Int.MIN_VALUE) {
            findRestoreTarget(
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
            )
        } else {
            null
        }
        val (targetSectionIndex, targetItemIndex) = restoreTarget ?: run {
            val firstSectionIndex = sections.indexOfFirst { section ->
                section.items.hasTvPosterContent() || section.items.tvStateFocusIndex() != null
            }
            if (firstSectionIndex < 0) {
                return@LaunchedEffect
            }
            val firstTargetIndex = if (sections[firstSectionIndex].items.hasTvPosterContent()) {
                0
            } else {
                sections[firstSectionIndex].items.tvStateFocusIndex() ?: 0
            }
            firstSectionIndex to firstTargetIndex
        }
        verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
        val restoredFocus = requestWatchingFocusAfterAttach(
            sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
        )
        if (restoredFocus) {
            handledFocusToken = focusRequestToken
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvAppBackground(palette)
            .padding(horizontal = TvScreenHorizontalPadding, vertical = TvRowsScreenVerticalPadding),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = verticalState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
                contentPadding = PaddingValues(
                    top = 6.dp,
                    bottom = if (hasContent) TvBottomDescriptionInset else TvBottomContentInset,
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
                            keepItemVisible(sectionIndex, itemIndex)
                            onItemFocused(sectionIndex, itemIndex, card)
                        },
                        onMessageFocused = { itemIndex, item ->
                            selectedCard = null
                            lastFocusedSectionIndex = sectionIndex
                            lastFocusedItemIndex = itemIndex
                            lastFocusedItemId = item.getId()
                            keepItemVisible(sectionIndex, itemIndex)
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
                    modifier = Modifier.align(Alignment.BottomCenter),
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
                    requesters.getOrNull(stateFocusIndex ?: -1)
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
                            loading = !item.isError,
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
