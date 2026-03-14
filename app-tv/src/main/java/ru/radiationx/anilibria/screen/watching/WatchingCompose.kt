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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard

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
    var selectedCard by remember(sectionKeys) {
        mutableStateOf(
            sections
                .asSequence()
                .flatMap { it.items.asSequence() }
                .filterIsInstance<LibriaCard>()
                .firstOrNull()
        )
    }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemId by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }

    fun targetInSection(
        sectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty()
        if (requesters.isEmpty()) {
            return null
        }
        return sectionIndex to preferredItemIndex.coerceIn(0, requesters.lastIndex)
    }

    fun findRestoreTarget(
        preferredSectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val clampedSectionIndex = preferredSectionIndex.coerceIn(0, sections.lastIndex.coerceAtLeast(0))
        var target: Pair<Int, Int>? = null
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
                    verticalState.scrollToItem(targetSectionIndex)
                    rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
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
            verticalState.scrollToItem(sectionIndex)
            rowStates.getOrNull(sectionIndex)?.scrollToItem(itemIndex)
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
            selectedCard = visibleCards.firstOrNull()
        }
        if (hadFocusedItem && !stillVisible) {
            val restoreTarget = findRestoreTarget(lastFocusedSectionIndex, lastFocusedItemIndex)
            if (restoreTarget != null) {
                val (targetSectionIndex, targetItemIndex) = restoreTarget
                verticalState.scrollToItem(targetSectionIndex)
                rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
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
        val restoreTarget = findRestoreTarget(lastFocusedSectionIndex, lastFocusedItemIndex)
            ?: return@LaunchedEffect
        val (targetSectionIndex, targetItemIndex) = restoreTarget
        verticalState.scrollToItem(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
        selectedCard = sections.getOrNull(targetSectionIndex)
            ?.items
            ?.getOrNull(targetItemIndex) as? LibriaCard
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val restoreTarget = if (lastFocusedItemId != Int.MIN_VALUE) {
            findRestoreTarget(lastFocusedSectionIndex, lastFocusedItemIndex)
        } else {
            null
        }
        val (targetSectionIndex, targetItemIndex) = restoreTarget ?: run {
            val firstSectionIndex = sections.indexOfFirst { it.items.isNotEmpty() }
            if (firstSectionIndex < 0) {
                return@LaunchedEffect
            }
            firstSectionIndex to 0
        }
        verticalState.scrollToItem(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
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
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceColor.copy(alpha = 0.16f),
                        Color.Transparent,
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = verticalState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                contentPadding = PaddingValues(
                    top = 6.dp,
                    bottom = if (selectedCard != null) 124.dp else 28.dp,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.material3.Text(
                text = title,
                color = palette.textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(palette.textColor.copy(alpha = 0.08f))
            )
        }

        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 24.dp),
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
