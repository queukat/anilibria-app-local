package ru.radiationx.anilibria.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.main.MainSectionBlock
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import androidx.compose.material3.Text

@Composable
internal fun ScheduleScreen(
    sections: List<MainSectionUiModel>,
    focusRequestToken: Int,
    onItemClick: (Long, CardItem) -> Unit,
    onItemFocused: (CardItem?) -> Unit,
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
    val rowStates = remember(sectionKeys) { List(sections.size) { LazyListState() } }
    val sectionRequesters = remember(sectionKeys) {
        sections.map { section ->
            List(section.items.size) { androidx.compose.ui.focus.FocusRequester() }
        }
    }
    var selectedItem by remember(sectionKeys) {
        mutableStateOf<CardItem?>(sections.asSequence().flatMap { it.items.asSequence() }.firstOrNull())
    }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }

    fun findRestoreTarget(
        preferredSectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val clampedSectionIndex = preferredSectionIndex.coerceIn(0, sections.lastIndex.coerceAtLeast(0))
        for (offset in 0..sections.size) {
            val downIndex = clampedSectionIndex + offset
            val downRequesters = sectionRequesters.getOrNull(downIndex).orEmpty()
            if (downRequesters.isNotEmpty()) {
                return downIndex to preferredItemIndex.coerceIn(0, downRequesters.lastIndex)
            }
            if (offset == 0) continue
            val upIndex = clampedSectionIndex - offset
            val upRequesters = sectionRequesters.getOrNull(upIndex).orEmpty()
            if (upRequesters.isNotEmpty()) {
                return upIndex to preferredItemIndex.coerceIn(0, upRequesters.lastIndex)
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
                scope.launch {
                    verticalState.scrollToItem(targetSectionIndex)
                    rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
                    withFrameNanos { }
                    requestWatchingFocus(requesters.getOrNull(targetItemIndex))
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
        val selectedId = selectedItem?.getId()
        val visibleItems = sections.asSequence().flatMap { it.items.asSequence() }.toList()
        val hadSelectedItem = selectedId != null
        val stillVisible = selectedId != null && visibleItems.any { it.getId() == selectedId }
        selectedItem = visibleItems.firstOrNull { it.getId() == selectedId } ?: visibleItems.firstOrNull()
        if (hadSelectedItem && !stillVisible) {
            val restoreTarget = findRestoreTarget(lastFocusedSectionIndex, lastFocusedItemIndex)
            if (restoreTarget != null) {
                val (targetSectionIndex, targetItemIndex) = restoreTarget
                verticalState.scrollToItem(targetSectionIndex)
                rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
                withFrameNanos { }
                requestWatchingFocus(
                    sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
                )
            }
        }
    }

    LaunchedEffect(selectedItem) {
        onItemFocused(selectedItem)
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val firstSectionIndex = sections.indexOfFirst { it.items.isNotEmpty() }
        if (firstSectionIndex < 0) {
            return@LaunchedEffect
        }
        verticalState.scrollToItem(firstSectionIndex)
        rowStates.getOrNull(firstSectionIndex)?.scrollToItem(0)
        withFrameNanos { }
        if (requestWatchingFocus(sectionRequesters.getOrNull(firstSectionIndex)?.firstOrNull())) {
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
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        LazyColumn(
            state = verticalState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = if (selectedItem != null) 124.dp else 28.dp,
            ),
        ) {
            item(key = "schedule-header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Расписание",
                        color = palette.textColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Свежие и ближайшие релизы по дням недели",
                        color = palette.secondaryTextColor,
                        fontSize = 15.sp,
                    )
                }
            }

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
                    onItemClick = { item -> onItemClick(section.id, item) },
                    onItemFocused = { itemIndex, item ->
                        selectedItem = item
                        lastFocusedSectionIndex = sectionIndex
                        lastFocusedItemIndex = itemIndex
                        keepItemVisible(sectionIndex, itemIndex)
                    },
                    onLeftEdge = { false },
                    onUp = { itemIndex -> requestSectionFocus(sectionIndex, -1, itemIndex) },
                    onDown = { itemIndex -> requestSectionFocus(sectionIndex, 1, itemIndex) },
                )
            }
        }

        selectedItem?.let { item ->
            val description = item.toTvCardDescription { card ->
                card.resolveDescription(context)
            }
            if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
                WatchingDescriptionBar(
                    title = description.title.toString(),
                    subtitle = description.subtitle.toString(),
                    palette = palette,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
