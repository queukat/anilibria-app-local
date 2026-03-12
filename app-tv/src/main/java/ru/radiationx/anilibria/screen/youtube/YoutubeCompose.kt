package ru.radiationx.anilibria.screen.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
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
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingMessageCard
import ru.radiationx.anilibria.screen.watching.WatchingPosterCard
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus

@Composable
internal fun YoutubeScreen(
    cards: List<CardItem>,
    focusRequestToken: Int,
    onItemClick: (CardItem) -> Unit,
    onItemFocused: (CardItem?) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val itemIds = remember(cards) { cards.map(CardItem::getId) }
    val itemRequesters = remember(itemIds) {
        List(cards.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    var selectedItem by remember(itemIds) { mutableStateOf(cards.firstOrNull()) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemId by remember { mutableIntStateOf(Int.MIN_VALUE) }

    fun requestGridFocus(index: Int): Boolean {
        if (itemRequesters.isEmpty()) {
            return false
        }
        val targetIndex = index.coerceIn(0, itemRequesters.lastIndex)
        scope.launch {
            gridState.scrollToItem(targetIndex)
            withFrameNanos { }
            requestWatchingFocus(itemRequesters.getOrNull(targetIndex))
        }
        return true
    }

    LaunchedEffect(cards) {
        val selectedId = selectedItem?.getId()
        val stillVisible = selectedId != null && cards.any { it.getId() == selectedId }
        if (!stillVisible) {
            selectedItem = cards.firstOrNull()
        }
        if (lastFocusedItemId != Int.MIN_VALUE && cards.none { it.getId() == lastFocusedItemId }) {
            requestGridFocus(lastFocusedItemIndex)
        }
    }

    LaunchedEffect(selectedItem) {
        onItemFocused(selectedItem)
    }

    LaunchedEffect(focusRequestToken, itemIds) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        if (requestGridFocus(0)) {
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = if (selectedItem != null) 124.dp else 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item(
                    key = "youtube-header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "YouTube",
                            color = palette.textColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Последние видео и публикации AniLibria",
                            color = palette.secondaryTextColor,
                            fontSize = 15.sp,
                        )
                    }
                }

                itemsIndexed(
                    items = cards,
                    key = { _, item -> item.getId() },
                    span = { _, item -> spanForYoutubeItem(item) },
                ) { index, item ->
                    when (item) {
                        is LibriaCard -> WatchingPosterCard(
                            imageUrl = item.image,
                            palette = palette,
                            focusRequester = itemRequesters[index],
                            cardWidth = 420.dp,
                            contentAspectRatio = 330f / 185f,
                            onClick = { onItemClick(item) },
                            onFocused = {
                                selectedItem = item
                                lastFocusedItemIndex = index
                                lastFocusedItemId = item.getId()
                                scope.launch {
                                    gridState.scrollToItem(index)
                                }
                            },
                        )

                        is InfoCard -> WatchingMessageCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            palette = palette,
                            focusRequester = itemRequesters[index],
                            onClick = { onItemClick(item) },
                            onFocused = {
                                selectedItem = item
                                lastFocusedItemIndex = index
                                lastFocusedItemId = item.getId()
                                scope.launch {
                                    gridState.scrollToItem(index)
                                }
                            },
                        )

                        is LinkCard -> WatchingMessageCard(
                            title = item.title,
                            subtitle = "Нажмите, чтобы выполнить действие",
                            palette = palette,
                            focusRequester = itemRequesters[index],
                            onClick = { onItemClick(item) },
                            onFocused = {
                                selectedItem = item
                                lastFocusedItemIndex = index
                                lastFocusedItemId = item.getId()
                                scope.launch {
                                    gridState.scrollToItem(index)
                                }
                            },
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
                            focusRequester = itemRequesters[index],
                            onClick = { onItemClick(item) },
                            onFocused = {
                                selectedItem = item
                                lastFocusedItemIndex = index
                                lastFocusedItemId = item.getId()
                                scope.launch {
                                    gridState.scrollToItem(index)
                                }
                            },
                        )
                    }
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
}

private fun LazyGridItemSpanScope.spanForYoutubeItem(item: CardItem): GridItemSpan {
    return if (item is LibriaCard) GridItemSpan(1) else GridItemSpan(maxLineSpan)
}
