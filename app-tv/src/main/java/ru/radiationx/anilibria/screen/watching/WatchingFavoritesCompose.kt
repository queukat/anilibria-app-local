package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import kotlin.math.max

@Composable
internal fun WatchingFavoritesScreen(
    cards: List<CardItem>,
    filters: TvCollectionFiltersUiState,
    focusRequestToken: Int,
    visibilityRestoreToken: Int,
    restoreFilterIndex: Int,
    restoreFilterToken: Int,
    pickerState: TvCollectionFilterPickerState?,
    pickerFocusRequestToken: Int,
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
    onRequestRailFocus: () -> Boolean,
    onRequestHeaderFocus: () -> Boolean,
    onContentMovedDown: () -> Unit,
    onContentMovedUp: () -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filtersRowState = rememberLazyListState()
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
    val stateActionRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val itemIds = remember(cards) { cards.map(CardItem::getId) }
    val itemRequesters = remember(itemIds) {
        List(cards.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    var selectedCard by remember(cards) { mutableStateOf<LibriaCard?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var handledRestoreToken by remember { mutableIntStateOf(0) }
    var lastFocusedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemId by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var lastFocusWasGrid by rememberSaveable { mutableStateOf(false) }
    val interactionsEnabled = pickerState == null
    val nonContentCards = remember(cards) { cards.filter { it !is LibriaCard } }
    val stateCard = remember(nonContentCards) {
        nonContentCards.firstOrNull { it is LoadingCard && it.isError }
            ?: nonContentCards.firstOrNull { it is LoadingCard }
            ?: nonContentCards.firstOrNull { it is InfoCard }
            ?: nonContentCards.firstOrNull()
    }
    val stateActionCard = remember(nonContentCards) { nonContentCards.filterIsInstance<LinkCard>().firstOrNull() }
    val hasCustomFilters = remember(filterItems) { filterItems.any { it.emphasized } }
    val showStatePanel = cards.isEmpty() || (cards.isNotEmpty() && cards.none { it is LibriaCard })
    val hasContent = remember(itemIds, showStatePanel) { cards.any { it is LibriaCard } && !showStatePanel }

    fun requestFilterFocus(index: Int): Boolean {
        if (filterRequesters.isEmpty()) {
            return false
        }
        val targetIndex = index.coerceIn(0, filterRequesters.lastIndex)
        scope.launch {
            filtersRowState.scrollItemIntoViewIfNeeded(targetIndex)
            requestWatchingFocusAfterAttach(filterRequesters.getOrNull(targetIndex))
        }
        return true
    }

    fun requestStateActionFocus(): Boolean {
        if (!showStatePanel || stateActionCard == null) {
            return false
        }
        scope.launch {
            requestWatchingFocusAfterAttach(stateActionRequester)
        }
        return true
    }

    fun requestGridFocus(index: Int): Boolean {
        val canFocusGrid = !showStatePanel && itemRequesters.isNotEmpty()
        return if (!canFocusGrid) {
            requestStateActionFocus()
        } else {
            val targetIndex = index.coerceIn(0, itemRequesters.lastIndex)
            scope.launch {
                gridState.scrollItemIntoViewIfNeeded(targetIndex)
                requestWatchingFocusAfterAttach(itemRequesters.getOrNull(targetIndex))
            }
            true
        }
    }

    LaunchedEffect(cards) {
        val selectedId = selectedCard?.getId()
        val visibleCards = cards.filterIsInstance<LibriaCard>()
        if (visibleCards.none { it.getId() == selectedId }) {
            selectedCard = null
        }
        if (lastFocusedItemId != Int.MIN_VALUE && cards.none { it.getId() == lastFocusedItemId }) {
            if (!showStatePanel && cards.isNotEmpty()) {
                requestGridFocus(lastFocusedItemIndex)
            } else {
                requestFilterFocus(lastFocusedFilterIndex)
            }
        }
    }

    LaunchedEffect(visibilityRestoreToken, cards, pickerState) {
        if (visibilityRestoreToken <= 0 || pickerState != null) {
            return@LaunchedEffect
        }
        if (lastFocusWasGrid && cards.isNotEmpty() && !showStatePanel) {
            val targetIndex = cards.indexOfItemId(lastFocusedItemId)
                ?: lastFocusedItemIndex.coerceIn(0, cards.lastIndex)
            gridState.scrollItemIntoViewIfNeeded(targetIndex)
            selectedCard = cards.getOrNull(targetIndex) as? LibriaCard
        } else {
            filtersRowState.scrollItemIntoViewIfNeeded(lastFocusedFilterIndex)
            selectedCard = null
        }
    }

    LaunchedEffect(focusRequestToken, itemIds, pickerState) {
        if (pickerState != null || focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val focused = when {
            lastFocusWasGrid && lastFocusedItemId != Int.MIN_VALUE && !showStatePanel -> {
                requestGridFocus(cards.indexOfItemId(lastFocusedItemId) ?: lastFocusedItemIndex)
            }
            filterRequesters.isNotEmpty() -> requestFilterFocus(lastFocusedFilterIndex)
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
        if (requestFilterFocus(restoreFilterIndex)) {
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
        val columnsCount = max(1, (maxWidth / TvPosterCardSlotWidth).toInt())

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvPageHeaderSpacing),
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    state = filtersRowState,
                    horizontalArrangement = Arrangement.spacedBy(TvFilterRowSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    lazyItemsIndexed(filterItems) { index, filter ->
                        WatchingFilterChip(
                            text = filter.label,
                            palette = palette,
                            focusRequester = filterRequesters[index],
                            enabled = interactionsEnabled,
                            emphasized = filter.emphasized,
                            onClick = filter.onClick,
                            onFocused = {
                                lastFocusedFilterIndex = index
                                lastFocusWasGrid = false
                                selectedCard = null
                                scope.launch {
                                    filtersRowState.scrollItemIntoViewIfNeeded(index)
                                }
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
                    if (showStatePanel) {
                        val stateTitle: String
                        val stateSubtitle: String
                        val stateAccent: Boolean
                        val stateLoading: Boolean
                        when (val item = stateCard) {
                            is LoadingCard -> {
                                stateTitle = item.title.ifBlank { "Загружаем избранное" }
                                stateSubtitle = item.description.ifBlank {
                                    if (item.isError) {
                                        "Проверьте подключение и попробуйте ещё раз."
                                    } else {
                                        "Подождите, список избранного обновляется."
                                    }
                                }
                                stateAccent = item.isError
                                stateLoading = !item.isError
                            }

                            is InfoCard -> {
                                stateTitle = item.title
                                stateSubtitle = item.subtitle
                                stateAccent = false
                                stateLoading = false
                            }

                            is LinkCard -> {
                                stateTitle = item.title
                                stateSubtitle = "Измените фильтры или попробуйте загрузить список ещё раз."
                                stateAccent = false
                                stateLoading = false
                            }

                            else -> {
                                stateLoading = false
                                stateAccent = false
                                if (hasCustomFilters) {
                                    stateTitle = "Ничего не найдено"
                                    stateSubtitle = "Ослабьте фильтры, чтобы снова увидеть релизы из избранного."
                                } else {
                                    stateTitle = "Избранное пока пусто"
                                    stateSubtitle = "Добавьте релизы в избранное, и они появятся здесь."
                                }
                            }
                        }

                        TvContentStatePanel(
                            title = stateTitle,
                            subtitle = stateSubtitle,
                            palette = palette,
                            accent = stateAccent,
                            loading = stateLoading,
                            modifier = Modifier.align(Alignment.TopCenter),
                            action = stateActionCard?.let { actionCard ->
                                {
                                    TvContentStateActionButton(
                                        text = actionCard.title,
                                        palette = palette,
                                        focusRequester = stateActionRequester,
                                        onClick = { onItemClick(actionCard) },
                                        onLeft = onRequestRailFocus,
                                        onUp = { requestFilterFocus(lastFocusedFilterIndex) },
                                        onDown = { true },
                                    )
                                }
                            },
                        )
                    } else {
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

                                    is InfoCard -> WatchingWideMessageCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
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
                                            onContentMovedUp()
                                            requestWatchingFocus(
                                                filterRequesters.getOrNull(lastFocusedFilterIndex)
                                                    ?: filterRequesters.firstOrNull()
                                            )
                                        },
                                    )

                                    is LinkCard -> WatchingWideMessageCard(
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
                                            onContentMovedUp()
                                            requestWatchingFocus(
                                                filterRequesters.getOrNull(lastFocusedFilterIndex)
                                                    ?: filterRequesters.firstOrNull()
                                            )
                                        },
                                    )

                                    is LoadingCard -> WatchingWideMessageCard(
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
                                        loading = !item.isError,
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
                                            onContentMovedUp()
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
            }

            pickerState?.let { dialogState ->
                WatchingFilterPickerDialog(
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

private data class FavoritesFilterUiModel(
    val label: String,
    val emphasized: Boolean,
    val onClick: () -> Unit,
)

private fun LazyGridItemSpanScope.spanForFavoriteItem(item: CardItem): GridItemSpan {
    return if (item is LibriaCard) GridItemSpan(1) else GridItemSpan(maxLineSpan)
}
