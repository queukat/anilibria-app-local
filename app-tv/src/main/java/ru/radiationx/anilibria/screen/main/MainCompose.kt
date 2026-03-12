package ru.radiationx.anilibria.screen.main

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingMessageCard
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.WatchingPosterCard
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded

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

@Composable
internal fun MainScreen(
    sections: List<MainSectionUiModel>,
    focusRequestToken: Int,
    contentRestoreState: MainContentRestoreState,
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
    var selectedItem by remember(sectionKeys, contentRestoreState.preferredItemId) {
        mutableStateOf(
            sections
                .asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.getId() == contentRestoreState.preferredItemId }
                ?: sections.asSequence().flatMap { it.items.asSequence() }.firstOrNull()
        )
    }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var descriptionTick by remember { mutableIntStateOf(0) }

    fun findRestoreTarget(
        preferredSectionIndex: Int,
        preferredItemIndex: Int,
        preferredItemId: Int = Int.MIN_VALUE,
    ): Pair<Int, Int>? {
        if (preferredItemId != Int.MIN_VALUE) {
            sections.forEachIndexed { sectionIndex, section ->
                val itemIndex = section.items.indexOfFirst { it.getId() == preferredItemId }
                if (itemIndex >= 0) {
                    return sectionIndex to itemIndex
                }
            }
        }
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
        val selectedId = selectedItem?.getId()
        val visibleItems = sections.asSequence().flatMap { it.items.asSequence() }.toList()
        val preferredItemId = contentRestoreState.preferredItemId
        val hadFocusedItem = preferredItemId != Int.MIN_VALUE
        val stillVisible = visibleItems.any { it.getId() == preferredItemId }
        selectedItem = visibleItems.firstOrNull { it.getId() == selectedId }
            ?: visibleItems.firstOrNull { it.getId() == preferredItemId }
            ?: visibleItems.firstOrNull()
        if (hadFocusedItem && !stillVisible) {
            val restoreTarget = findRestoreTarget(
                preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                preferredItemIndex = contentRestoreState.preferredItemIndex,
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

    LaunchedEffect(selectedItem) {
        while (isActive) {
            delay(60_000L)
            descriptionTick++
        }
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val restoreTarget = if (contentRestoreState.preferredItemId != Int.MIN_VALUE) {
            findRestoreTarget(
                preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                preferredItemIndex = contentRestoreState.preferredItemIndex,
                preferredItemId = contentRestoreState.preferredItemId,
            )
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
        verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
        if (requestWatchingFocusAfterAttach(sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex))) {
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
            ),
    ) {
        LazyColumn(
            state = verticalState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = if (selectedItem != null) 96.dp else 20.dp,
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
                    onItemClick = { item -> onItemClick(section.id, item) },
                    onItemFocused = { itemIndex, item ->
                        selectedItem = item
                        keepItemVisible(sectionIndex, itemIndex)
                        onItemFocused(sectionIndex, itemIndex, item)
                    },
                    onLeftEdge = onRequestRailFocus,
                    onUp = { itemIndex ->
                        if (sectionIndex == 0) {
                            onRequestHeaderFocus()
                        } else {
                            requestSectionFocus(sectionIndex, -1, itemIndex)
                        }
                    },
                        onDown = { itemIndex ->
                            requestSectionFocus(sectionIndex, 1, itemIndex)
                        },
                        modifier = Modifier.padding(start = 16.dp),
                )
            }
        }

        selectedItem?.let { item ->
            val currentDescriptionTick = descriptionTick
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

@Composable
internal fun MainSectionBlock(
    title: String,
    items: List<CardItem>,
    palette: WatchingPalette,
    rowState: LazyListState,
    requesters: List<androidx.compose.ui.focus.FocusRequester>,
    onItemClick: (CardItem) -> Unit,
    onItemFocused: (Int, CardItem) -> Unit,
    onLeftEdge: () -> Boolean,
    onUp: (Int) -> Boolean,
    onDown: (Int) -> Boolean,
    modifier: Modifier = Modifier,
    posterFocusedBackgroundColor: Color = palette.accentColor.copy(alpha = 0.12f),
    posterBorderColor: Color = palette.accentColor.copy(alpha = 0.92f),
    posterFocusedBorderWidth: androidx.compose.ui.unit.Dp = 2.dp,
    posterUnfocusedBorderWidth: androidx.compose.ui.unit.Dp = 1.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
                    is LibriaCard -> {
                        val isYoutube = item.type is LibriaCard.Type.Youtube
                        WatchingPosterCard(
                            imageUrl = item.image,
                            palette = palette,
                            focusRequester = requesters[index],
                            cardWidth = if (isYoutube) 346.dp else 152.dp,
                            contentAspectRatio = if (isYoutube) 330f / 185f else 130f / 185f,
                            focusedBackgroundColor = posterFocusedBackgroundColor,
                            focusedBorderColor = posterBorderColor,
                            focusedBorderWidth = posterFocusedBorderWidth,
                            unfocusedBorderWidth = posterUnfocusedBorderWidth,
                            onClick = { onItemClick(item) },
                            onFocused = { onItemFocused(index, item) },
                            onLeft = if (index == 0) onLeftEdge else null,
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )
                    }

                    is LinkCard -> WatchingMessageCard(
                        title = item.title,
                        subtitle = "",
                        palette = palette,
                        focusRequester = requesters[index],
                        onClick = { onItemClick(item) },
                        onFocused = { onItemFocused(index, item) },
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
                        onFocused = { onItemFocused(index, item) },
                        onLeft = if (index == 0) onLeftEdge else null,
                        onUp = { onUp(index) },
                        onDown = { onDown(index) },
                    )

                    is InfoCard -> WatchingMessageCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        palette = palette,
                        focusRequester = requesters[index],
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
