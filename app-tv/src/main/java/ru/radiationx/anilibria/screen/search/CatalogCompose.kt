package ru.radiationx.anilibria.screen.search

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import ru.radiationx.anilibria.screen.watching.WatchingFilterChip
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingMessageCard
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.WatchingPosterCard
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import kotlin.math.max

private const val SEARCH_CATALOG_WIDTH_FRACTION = 0.62f

@Composable
internal fun CatalogScreen(
    cards: List<CardItem>,
    filters: SearchFormViewModel.FiltersUiState,
    progressVisible: Boolean,
    pickerState: SearchFormViewModel.FilterPickerState?,
    focusRequestToken: Int,
    pickerFocusRequestToken: Int,
    restoreFilterIndex: Int,
    restoreFilterToken: Int,
    onSearchClick: () -> Unit,
    onYearClick: () -> Unit,
    onSeasonClick: () -> Unit,
    onGenreClick: () -> Unit,
    onSortClick: () -> Unit,
    onOnlyCompletedClick: () -> Unit,
    onPickerToggleOption: (Int) -> Unit,
    onPickerSingleSelect: (Int) -> Unit,
    onPickerApply: () -> Unit,
    onPickerReset: () -> Unit,
    onPickerDismiss: () -> Unit,
    onItemClick: (CardItem) -> Unit,
    onItemFocused: (CardItem?) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filtersScrollState = rememberScrollState()
    val gridState = rememberLazyGridState()
    val filterItems = remember(filters) {
        listOf(
            CatalogFilterUiModel(filters.year.label, filters.year.emphasized, onYearClick),
            CatalogFilterUiModel(filters.season.label, filters.season.emphasized, onSeasonClick),
            CatalogFilterUiModel(filters.genre.label, filters.genre.emphasized, onGenreClick),
            CatalogFilterUiModel(filters.sort.label, filters.sort.emphasized, onSortClick),
            CatalogFilterUiModel(
                filters.onlyCompleted.label,
                filters.onlyCompleted.emphasized,
                onOnlyCompletedClick,
            ),
        )
    }
    val filterRequesters = remember(filterItems.size) {
        List(filterItems.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    val searchRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val itemIds = remember(cards) { cards.map(CardItem::getId) }
    val itemRequesters = remember(itemIds) {
        List(cards.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    var selectedItem by remember(itemIds) { mutableStateOf(cards.firstOrNull()) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var handledRestoreToken by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemId by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val interactionsEnabled = pickerState == null

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
            when {
                cards.isNotEmpty() -> requestGridFocus(lastFocusedItemIndex)
                filterRequesters.isNotEmpty() -> requestWatchingFocus(filterRequesters.firstOrNull())
                else -> requestWatchingFocus(searchRequester)
            }
        }
    }

    LaunchedEffect(focusRequestToken, itemIds, pickerState) {
        if (pickerState != null || focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        filtersScrollState.scrollTo(0)
        withFrameNanos { }
        val focused = when {
            filterRequesters.isNotEmpty() -> requestWatchingFocus(filterRequesters.firstOrNull())
            itemRequesters.isNotEmpty() -> requestGridFocus(0)
            else -> requestWatchingFocus(searchRequester)
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

    LaunchedEffect(selectedItem) {
        onItemFocused(selectedItem)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceColor.copy(alpha = 0.18f),
                        Color.Transparent,
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        val columnsCount = max(1, (maxWidth / 168.dp).toInt())

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CatalogHeader(
                    palette = palette,
                    searchRequester = searchRequester,
                    interactionsEnabled = interactionsEnabled,
                    onSearchClick = onSearchClick,
                    onSearchDown = {
                        requestWatchingFocus(filterRequesters.firstOrNull()) || requestGridFocus(0)
                    },
                )

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
                            enabled = interactionsEnabled,
                            emphasized = filter.emphasized,
                            onClick = filter.onClick,
                            onLeft = if (index == 0) {
                                { requestWatchingFocus(searchRequester) }
                            } else {
                                null
                            },
                            onDown = {
                                requestGridFocus(0)
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
                            bottom = if (selectedItem != null) 120.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(
                            items = cards,
                            key = { _, item -> item.getId() },
                            span = { _, item -> spanForCatalogItem(item) },
                        ) { index, item ->
                            when (item) {
                                is LibriaCard -> WatchingPosterCard(
                                    imageUrl = item.image,
                                    palette = palette,
                                    focusRequester = itemRequesters[index],
                                    enabled = interactionsEnabled,
                                    onClick = { onItemClick(item) },
                                    onFocused = {
                                        selectedItem = item
                                        lastFocusedItemIndex = index
                                        lastFocusedItemId = item.getId()
                                        scope.launch {
                                            gridState.scrollToItem(index)
                                        }
                                    },
                                    onUp = if (index < columnsCount) {
                                        { requestWatchingFocus(filterRequesters.firstOrNull()) }
                                    } else {
                                        null
                                    },
                                )

                                is InfoCard -> WatchingMessageCard(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    palette = palette,
                                    focusRequester = itemRequesters[index],
                                    enabled = interactionsEnabled,
                                    onClick = { onItemClick(item) },
                                    onFocused = {
                                        selectedItem = item
                                        lastFocusedItemIndex = index
                                        lastFocusedItemId = item.getId()
                                        scope.launch {
                                            gridState.scrollToItem(index)
                                        }
                                    },
                                    onUp = {
                                        requestWatchingFocus(filterRequesters.firstOrNull())
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
                                        selectedItem = item
                                        lastFocusedItemIndex = index
                                        lastFocusedItemId = item.getId()
                                        scope.launch {
                                            gridState.scrollToItem(index)
                                        }
                                    },
                                    onUp = {
                                        requestWatchingFocus(filterRequesters.firstOrNull())
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
                                    enabled = interactionsEnabled,
                                    onClick = { onItemClick(item) },
                                    onFocused = {
                                        selectedItem = item
                                        lastFocusedItemIndex = index
                                        lastFocusedItemId = item.getId()
                                        scope.launch {
                                            gridState.scrollToItem(index)
                                        }
                                    },
                                    onUp = {
                                        requestWatchingFocus(filterRequesters.firstOrNull())
                                    },
                                )
                            }
                        }
                    }

                    if (progressVisible) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp),
                            color = palette.accentColor,
                            trackColor = palette.textColor.copy(alpha = 0.18f),
                        )
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

            pickerState?.let { dialogState ->
                CatalogFilterPickerDialog(
                    state = dialogState,
                    palette = palette,
                    focusRequestToken = pickerFocusRequestToken,
                    onToggleOption = onPickerToggleOption,
                    onSingleSelect = onPickerSingleSelect,
                    onApply = onPickerApply,
                    onReset = onPickerReset,
                    onDismiss = onPickerDismiss,
                )
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    palette: WatchingPalette,
    searchRequester: androidx.compose.ui.focus.FocusRequester,
    interactionsEnabled: Boolean,
    onSearchClick: () -> Unit,
    onSearchDown: () -> Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Каталог",
                color = palette.textColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Фильтруйте релизы и переходите в экран поиска",
                color = palette.secondaryTextColor,
                fontSize = 15.sp,
            )
        }

        WatchingFocusableSurface(
            focusRequester = searchRequester,
            enabled = interactionsEnabled,
            backgroundColor = palette.chipColor.copy(alpha = 0.92f),
            focusedBackgroundColor = palette.chipColor,
            borderColor = palette.textColor.copy(alpha = 0.72f),
            onClick = onSearchClick,
            onDown = onSearchDown,
            paddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Поиск",
                color = palette.textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(112.dp),
            )
        }
    }
}

@Composable
private fun CatalogFilterPickerDialog(
    state: SearchFormViewModel.FilterPickerState,
    palette: WatchingPalette,
    focusRequestToken: Int,
    onToggleOption: (Int) -> Unit,
    onSingleSelect: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val optionIds = remember(state.options) { state.options.indices.toList() }
    val optionRequesters = remember(optionIds) {
        List(optionIds.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    val resetRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val applyRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun shouldScrollToOption(index: Int): Boolean {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            return true
        }
        return visibleItems.none { it.index == index }
    }

    fun requestOptionFocus(targetIndex: Int): Boolean {
        if (state.options.isEmpty()) {
            return false
        }
        val clampedIndex = targetIndex.coerceIn(0, state.options.lastIndex)
        scope.launch {
            if (shouldScrollToOption(clampedIndex)) {
                val anchorIndex = (clampedIndex - 1).coerceAtLeast(0)
                listState.scrollToItem(anchorIndex)
                withFrameNanos { }
            }
            requestWatchingFocus(optionRequesters.getOrNull(clampedIndex))
        }
        return true
    }

    LaunchedEffect(focusRequestToken, state) {
        if (focusRequestToken <= 0 || optionIds.isEmpty()) {
            return@LaunchedEffect
        }
        val targetIndex = state.selectedIndices.minOrNull()?.coerceIn(0, optionIds.lastIndex) ?: 0
        listState.scrollToItem(targetIndex)
        withFrameNanos { }
        requestWatchingFocus(optionRequesters.getOrNull(targetIndex))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    androidx.compose.ui.input.key.Key.Back,
                    androidx.compose.ui.input.key.Key.Escape,
                    -> {
                        onDismiss()
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(SEARCH_CATALOG_WIDTH_FRACTION)
                .heightIn(max = 640.dp)
                .background(palette.surfaceColor.copy(alpha = 0.98f))
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.title,
                        color = palette.textColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (state.multiSelect) {
                            "Можно выбрать несколько значений"
                        } else {
                            "Выберите одно значение"
                        },
                        color = palette.secondaryTextColor,
                        fontSize = 14.sp,
                    )
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lazyItemsIndexed(
                        items = state.options,
                        key = { index, option -> option.hashCode() * 31 + index },
                    ) { index, option ->
                        val isSelected = index in state.selectedIndices
                        WatchingFocusableSurface(
                            focusRequester = optionRequesters[index],
                            backgroundColor = if (isSelected) {
                                palette.accentColor.copy(alpha = 0.18f)
                            } else {
                                palette.chipColor.copy(alpha = 0.74f)
                            },
                            focusedBackgroundColor = if (isSelected) {
                                palette.accentColor.copy(alpha = 0.26f)
                            } else {
                                palette.chipColor
                            },
                            borderColor = if (isSelected) {
                                palette.accentColor.copy(alpha = 0.82f)
                            } else {
                                palette.textColor.copy(alpha = 0.12f)
                            },
                            onClick = {
                                if (state.multiSelect) {
                                    onToggleOption(index)
                                } else {
                                    onSingleSelect(index)
                                }
                            },
                            onUp = if (index > 0) {
                                { requestOptionFocus(index - 1) }
                            } else {
                                { true }
                            },
                            onDown = when {
                                index < state.options.lastIndex -> {
                                    { requestOptionFocus(index + 1) }
                                }

                                state.multiSelect -> {
                                    { requestWatchingFocus(resetRequester) }
                                }

                                else -> {
                                    { true }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            paddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = option,
                                    color = palette.textColor,
                                    fontSize = 17.sp,
                                    fontWeight = if (isSelected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            if (isSelected) palette.accentColor else Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                }

                if (state.multiSelect) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        WatchingFocusableSurface(
                            focusRequester = resetRequester,
                            backgroundColor = palette.chipColor.copy(alpha = 0.82f),
                            focusedBackgroundColor = palette.chipColor,
                            borderColor = palette.textColor.copy(alpha = 0.72f),
                            onClick = onReset,
                            onUp = {
                                requestOptionFocus(state.options.lastIndex)
                            },
                            onRight = {
                                requestWatchingFocus(applyRequester)
                            },
                            modifier = Modifier.weight(1f),
                            paddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "Сбросить",
                                color = palette.textColor,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        WatchingFocusableSurface(
                            focusRequester = applyRequester,
                            backgroundColor = palette.accentColor.copy(alpha = 0.22f),
                            focusedBackgroundColor = palette.accentColor.copy(alpha = 0.28f),
                            borderColor = palette.accentColor.copy(alpha = 0.86f),
                            onClick = onApply,
                            onUp = {
                                requestOptionFocus(state.options.lastIndex)
                            },
                            onLeft = {
                                requestWatchingFocus(resetRequester)
                            },
                            modifier = Modifier.weight(1f),
                            paddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = "Ок",
                                color = palette.textColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                Text(
                    text = "Назад: закрыть",
                    color = palette.secondaryTextColor,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private data class CatalogFilterUiModel(
    val label: String,
    val emphasized: Boolean,
    val onClick: () -> Unit,
)

private fun LazyGridItemSpanScope.spanForCatalogItem(item: CardItem): GridItemSpan {
    return if (item is LibriaCard) GridItemSpan(1) else GridItemSpan(maxLineSpan)
}
