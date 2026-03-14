package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
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
import kotlin.math.max

@Composable
internal fun WatchingFavoritesScreen(
    cards: List<CardItem>,
    filters: WatchingFavoritesViewModel.FiltersUiState,
    focusRequestToken: Int,
    visibilityRestoreToken: Int,
    restoreFilterIndex: Int,
    restoreFilterToken: Int,
    pickerState: WatchingChoiceDialogUiState?,
    pickerFocusRequestToken: Int,
    onYearClick: () -> Unit,
    onSeasonClick: () -> Unit,
    onGenreClick: () -> Unit,
    onSortClick: () -> Unit,
    onOnlyCompletedClick: () -> Unit,
    onPickerDismiss: () -> Unit,
    onPickerOptionClick: (Int) -> Unit,
    onItemClick: (CardItem) -> Unit,
    onRequestRailFocus: () -> Boolean,
    onRequestHeaderFocus: () -> Boolean,
    onContentMovedDown: () -> Unit,
    onContentMovedUp: () -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filtersScrollState = rememberScrollState()
    val gridState = rememberLazyGridState()
    val filterItems = remember(filters) {
        listOf(
            FavoritesFilterUiModel(
                label = filters.year.label,
                emphasized = filters.year.emphasized,
                onClick = onYearClick,
            ),
            FavoritesFilterUiModel(
                label = filters.season.label,
                emphasized = filters.season.emphasized,
                onClick = onSeasonClick,
            ),
            FavoritesFilterUiModel(
                label = filters.genre.label,
                emphasized = filters.genre.emphasized,
                onClick = onGenreClick,
            ),
            FavoritesFilterUiModel(
                label = filters.sort.label,
                emphasized = filters.sort.emphasized,
                onClick = onSortClick,
            ),
            FavoritesFilterUiModel(
                label = filters.onlyCompleted.label,
                emphasized = filters.onlyCompleted.emphasized,
                onClick = onOnlyCompletedClick,
            ),
        )
    }
    val filterRequesters = remember(filterItems.size) {
        List(filterItems.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    val itemIds = remember(cards) { cards.map(CardItem::getId) }
    val itemRequesters = remember(itemIds) {
        List(cards.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    var selectedCard by remember(cards) {
        mutableStateOf(cards.filterIsInstance<LibriaCard>().firstOrNull())
    }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var handledRestoreToken by remember { mutableIntStateOf(0) }
    var lastFocusedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemId by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var lastFocusWasGrid by rememberSaveable { mutableStateOf(false) }
    val interactionsEnabled = pickerState == null
    val hasContent = remember(itemIds) { cards.isNotEmpty() }

    fun requestGridFocus(index: Int): Boolean {
        if (itemRequesters.isEmpty()) {
            return false
        }
        val targetIndex = index.coerceIn(0, itemRequesters.lastIndex)
        scope.launch {
            gridState.scrollItemIntoViewIfNeeded(targetIndex)
            requestWatchingFocusAfterAttach(itemRequesters.getOrNull(targetIndex))
        }
        return true
    }

    LaunchedEffect(cards) {
        val selectedId = selectedCard?.getId()
        val visibleCards = cards.filterIsInstance<LibriaCard>()
        if (visibleCards.none { it.getId() == selectedId }) {
            selectedCard = visibleCards.firstOrNull()
        }
        if (lastFocusedItemId != Int.MIN_VALUE && cards.none { it.getId() == lastFocusedItemId }) {
            if (cards.isNotEmpty()) {
                requestGridFocus(lastFocusedItemIndex)
            } else {
                requestWatchingFocus(filterRequesters.firstOrNull())
            }
        }
    }

    LaunchedEffect(visibilityRestoreToken, cards, pickerState) {
        if (visibilityRestoreToken <= 0 || pickerState != null) {
            return@LaunchedEffect
        }
        if (lastFocusWasGrid && cards.isNotEmpty()) {
            val targetIndex = cards.indexOfItemId(lastFocusedItemId)
                ?: lastFocusedItemIndex.coerceIn(0, cards.lastIndex)
            gridState.scrollItemIntoViewIfNeeded(targetIndex)
            selectedCard = cards.getOrNull(targetIndex) as? LibriaCard
        } else {
            filtersScrollState.scrollTo(0)
            selectedCard = null
        }
    }

    LaunchedEffect(focusRequestToken, itemIds, pickerState) {
        if (pickerState != null || focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        filtersScrollState.scrollTo(0)
        val focused = when {
            lastFocusWasGrid && lastFocusedItemId != Int.MIN_VALUE -> {
                requestGridFocus(cards.indexOfItemId(lastFocusedItemId) ?: lastFocusedItemIndex)
            }
            filterRequesters.isNotEmpty() -> requestWatchingFocusAfterAttach(
                filterRequesters.getOrNull(lastFocusedFilterIndex)
                    ?: filterRequesters.firstOrNull()
            )
            else -> requestGridFocus(lastFocusedItemIndex)
        }
        if (focused) {
            handledFocusToken = focusRequestToken
        }
    }

    LaunchedEffect(restoreFilterToken, pickerState) {
        if (pickerState != null || restoreFilterToken <= handledRestoreToken) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (requestWatchingFocus(filterRequesters.getOrNull(restoreFilterIndex))) {
            handledRestoreToken = restoreFilterToken
        }
    }

    BoxWithConstraints(
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
            .padding(horizontal = TvScreenHorizontalPadding, vertical = TvRowsScreenVerticalPadding),
    ) {
        val columnsCount = max(1, (maxWidth / 168.dp).toInt())

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvPageVerticalPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(filtersScrollState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    filterItems.forEachIndexed { index, filter ->
                        WatchingFilterChip(
                            text = filter.label,
                            palette = palette,
                            focusRequester = filterRequesters[index],
                            minWidth = if (index == filterItems.lastIndex) 96.dp else 132.dp,
                            enabled = interactionsEnabled,
                            emphasized = filter.emphasized,
                            onClick = filter.onClick,
                            onFocused = {
                                lastFocusedFilterIndex = index
                                lastFocusWasGrid = false
                            },
                            onLeft = if (index == 0) onRequestRailFocus else null,
                            onUp = {
                                onContentMovedUp()
                                onRequestHeaderFocus()
                            },
                            onDown = {
                                val moved = requestGridFocus(lastFocusedItemIndex)
                                if (moved) {
                                    onContentMovedDown()
                                }
                                moved
                            },
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (hasContent) TvBottomDescriptionInset else TvBottomContentInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(TvRowSpacing),
                    ) {
                        itemsIndexed(
                            items = cards,
                            key = { _, item -> item.getId() },
                            span = { _, item -> spanForFavoriteItem(item) },
                        ) { index, item ->
                            when (item) {
                                is LibriaCard -> WatchingPosterCard(
                                    imageUrl = item.image,
                                    palette = palette,
                                    focusRequester = itemRequesters[index],
                                    enabled = interactionsEnabled,
                                    onClick = { onItemClick(item) },
                                    onFocused = {
                                        selectedCard = item
                                        lastFocusedItemIndex = index
                                        lastFocusedItemId = item.getId()
                                        lastFocusWasGrid = true
                                        scope.launch {
                                            gridState.scrollItemIntoViewIfNeeded(index)
                                        }
                                    },
                                    onLeft = if (index % columnsCount == 0) onRequestRailFocus else null,
                                    onUp = if (index < columnsCount) {
                                        {
                                            onContentMovedUp()
                                            requestWatchingFocus(
                                                filterRequesters.getOrNull(lastFocusedFilterIndex)
                                                    ?: filterRequesters.firstOrNull()
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )

                                is LinkCard -> WatchingMessageCard(
                                    title = item.title,
                                    subtitle = "Нажмите, чтобы выполнить действие",
                                    palette = palette,
                                    focusRequester = itemRequesters[index],
                                    enabled = interactionsEnabled,
                                    onClick = { onItemClick(item) },
                                    onFocused = {
                                        selectedCard = null
                                        lastFocusedItemIndex = index
                                        lastFocusedItemId = item.getId()
                                        lastFocusWasGrid = true
                                        scope.launch {
                                            gridState.scrollItemIntoViewIfNeeded(index)
                                        }
                                    },
                                    onLeft = onRequestRailFocus,
                                    onUp = {
                                        requestWatchingFocus(
                                            filterRequesters.getOrNull(lastFocusedFilterIndex)
                                                ?: filterRequesters.firstOrNull()
                                        )
                                    },
                                )

                                is LoadingCard -> WatchingMessageCard(
                                    title = item.title.ifBlank { "Загрузка" },
                                    subtitle = item.description,
                                    palette = palette.copy(
                                        accentColor = if (item.isError) {
                                            palette.accentColor
                                        } else {
                                            palette.textColor.copy(alpha = 0.4f)
                                        }
                                    ),
                                    focusRequester = itemRequesters[index],
                                    enabled = interactionsEnabled,
                                    onClick = { onItemClick(item) },
                                    onFocused = {
                                        selectedCard = null
                                        lastFocusedItemIndex = index
                                        lastFocusedItemId = item.getId()
                                        lastFocusWasGrid = true
                                        scope.launch {
                                            gridState.scrollItemIntoViewIfNeeded(index)
                                        }
                                    },
                                    onLeft = onRequestRailFocus,
                                    onUp = {
                                        requestWatchingFocus(
                                            filterRequesters.getOrNull(lastFocusedFilterIndex)
                                                ?: filterRequesters.firstOrNull()
                                        )
                                    },
                                )
                            }
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

            pickerState?.let { dialogState ->
                WatchingChoiceDialog(
                    state = dialogState,
                    palette = palette,
                    focusRequestToken = pickerFocusRequestToken,
                    onOptionClick = onPickerOptionClick,
                    onDismiss = onPickerDismiss,
                )
            }
        }
    }
}

private data class FavoritesFilterUiModel(
    val label: String,
    val emphasized: Boolean,
    val onClick: () -> Unit,
)

private fun LazyGridItemSpanScope.spanForFavoriteItem(item: CardItem): GridItemSpan {
    return if (item is LibriaCard) GridItemSpan(1) else GridItemSpan(maxLineSpan)
}
