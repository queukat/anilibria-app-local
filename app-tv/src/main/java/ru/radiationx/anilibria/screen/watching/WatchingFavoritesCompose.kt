package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.common.TvCollectionFiltersUiState
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import ru.radiationx.anilibria.ui.focus.rememberTvFocusRequesters
import kotlin.math.max

@Composable
internal fun WatchingFavoritesScreen(
    cards: List<CardItem>,
    filters: TvCollectionFiltersUiState,
    contentInteractionsEnabled: Boolean = true,
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
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filtersRowState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val filterItems =
        remember(filters) {
            listOf(
                TvCollectionFilterAction(
                    label = filters.year.label,
                    emphasized = filters.year.emphasized,
                    onClick = onYearClick,
                ),
                TvCollectionFilterAction(
                    label = filters.season.label,
                    emphasized = filters.season.emphasized,
                    onClick = onSeasonClick,
                ),
                TvCollectionFilterAction(
                    label = filters.genre.label,
                    emphasized = filters.genre.emphasized,
                    onClick = onGenreClick,
                ),
                TvCollectionFilterAction(
                    label = filters.sort.label,
                    emphasized = filters.sort.emphasized,
                    onClick = onSortClick,
                ),
                TvCollectionFilterAction(
                    label = filters.onlyCompleted.label,
                    emphasized = filters.onlyCompleted.emphasized,
                    onClick = onOnlyCompletedClick,
                ),
            )
        }
    val filterRequesters =
        remember(filterItems.size) {
            List(filterItems.size) { androidx.compose.ui.focus.FocusRequester() }
        }
    val stateActionRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val itemKeys = remember(cards) { cards.map { it.stableKey } }
    val itemRequesters = rememberTvFocusRequesters(itemKeys)
    var selectedCard by remember(cards) { mutableStateOf<LibriaCard?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var handledRestoreToken by remember { mutableIntStateOf(0) }
    var lastFocusedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusWasGrid by rememberSaveable { mutableStateOf(false) }
    val interactionsEnabled = contentInteractionsEnabled && pickerState == null
    val nonContentCards = remember(cards) { cards.filter { it !is LibriaCard } }
    val stateCard =
        remember(nonContentCards) {
            nonContentCards.firstOrNull { it is LoadingCard && it.isError }
                ?: nonContentCards.firstOrNull { it is LoadingCard }
                ?: nonContentCards.firstOrNull { it is InfoCard }
                ?: nonContentCards.firstOrNull()
        }
    val stateActionCard = remember(nonContentCards) { nonContentCards.filterIsInstance<LinkCard>().firstOrNull() }
    val hasCustomFilters = remember(filterItems) { filterItems.any { it.emphasized } }
    val showStatePanel = cards.isEmpty() || (cards.isNotEmpty() && cards.none { it is LibriaCard })
    val hasContent = remember(itemKeys, showStatePanel) { cards.any { it is LibriaCard } && !showStatePanel }
    val columnsCount =
        remember(configuration.screenWidthDp) {
            max(
                1,
                ((configuration.screenWidthDp.dp - (TvCardScreenHorizontalPadding * 2)) / TvPosterCardSlotWidth)
                    .toInt(),
            )
        }
    val descriptionOverlayClearance =
        rememberTvDescriptionOverlayClearance(
            hasContent = hasContent,
            fallbackInset = TvCollectionGridBottomDescriptionInset,
        )
    val gridDescriptionInset =
        if (hasContent) {
            descriptionOverlayClearance.bottomInset
        } else {
            TvBottomContentInset
        }
    val gridBottomClearancePx = descriptionOverlayClearance.bottomClearancePx
    val statePanel =
        remember(stateCard, hasCustomFilters, stateActionCard) {
            val title: String
            val subtitle: String
            val accent: Boolean
            val loading: Boolean
            when (val item = stateCard) {
                is LoadingCard -> {
                    title = item.title.ifBlank { "Загружаем избранное" }
                    subtitle =
                        item.description.ifBlank {
                            if (item.isError) {
                                "Проверьте подключение и попробуйте ещё раз."
                            } else {
                                "Подождите, список избранного обновляется."
                            }
                        }
                    accent = item.isError
                    loading = !item.isError
                }

                is InfoCard -> {
                    title = item.title
                    subtitle = item.subtitle
                    accent = false
                    loading = false
                }

                is LinkCard -> {
                    title = item.title
                    subtitle = "Измените фильтры или попробуйте загрузить список ещё раз."
                    accent = false
                    loading = false
                }

                else -> {
                    loading = false
                    accent = false
                    if (hasCustomFilters) {
                        title = "Ничего не найдено"
                        subtitle = "Ослабьте фильтры, чтобы снова увидеть релизы из избранного."
                    } else {
                        title = "Избранное пока пусто"
                        subtitle = "Добавьте релизы в избранное, и они появятся здесь."
                    }
                }
            }
            TvCollectionStatePanelUiModel(
                title = title,
                subtitle = subtitle,
                accent = accent,
                loading = loading,
                actionText = stateActionCard?.title,
            )
        }

    fun gridAnchorIndex(index: Int): Int {
        if (columnsCount <= 0) {
            return index.coerceAtLeast(0)
        }
        return (index - (index % columnsCount)).coerceAtLeast(0)
    }

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

    fun requestTopFiltersFocus(): Boolean {
        return requestFilterFocus(lastFocusedFilterIndex)
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
                gridState.scrollItemIntoViewIfNeeded(
                    index = targetIndex,
                    anchorIndex = gridAnchorIndex(targetIndex),
                    bottomClearancePx = gridBottomClearancePx,
                )
                requestWatchingFocusAfterAttach(itemRequesters.getOrNull(targetIndex))
            }
            true
        }
    }

    LaunchedEffect(cards) {
        val selectedKey = selectedCard?.stableKey
        val visibleCards = cards.filterIsInstance<LibriaCard>()
        if (visibleCards.none { it.stableKey == selectedKey }) {
            selectedCard = null
        }
        if (!lastFocusedItemKey.isNullOrEmpty() && cards.none { it.stableKey == lastFocusedItemKey }) {
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
            val targetIndex =
                cards.indexOfStableKey(lastFocusedItemKey)
                    ?: lastFocusedItemIndex.coerceIn(0, cards.lastIndex)
            selectedCard = cards.getOrNull(targetIndex) as? LibriaCard
            if (requestGridFocus(targetIndex)) {
                onContentMovedDown()
            }
        } else if (filterRequesters.isNotEmpty()) {
            selectedCard = null
            if (requestFilterFocus(lastFocusedFilterIndex)) {
                onContentMovedUp()
            }
        } else {
            selectedCard = null
        }
    }

    LaunchedEffect(focusRequestToken, itemKeys, pickerState) {
        if (pickerState != null || focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val focused =
            when {
                lastFocusWasGrid && !lastFocusedItemKey.isNullOrEmpty() && !showStatePanel -> {
                    requestGridFocus(cards.indexOfStableKey(lastFocusedItemKey) ?: lastFocusedItemIndex)
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .tvAppBackground(palette)
                .padding(TvRowsContentPadding),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvPageHeaderSpacing),
            ) {
                TvCollectionTopFiltersPanel(
                    palette = palette,
                    filters = filterItems,
                    rowState = filtersRowState,
                    filterRequesters = filterRequesters,
                    interactionsEnabled = interactionsEnabled,
                    onFilterFocused = { index ->
                        lastFocusedFilterIndex = index
                        lastFocusWasGrid = false
                        selectedCard = null
                        scope.launch {
                            filtersRowState.scrollItemIntoViewIfNeeded(index)
                        }
                    },
                    onFilterLeft = { index ->
                        if (index == 0) {
                            { onRequestRailFocus() }
                        } else {
                            { requestFilterFocus(index - 1) }
                        }
                    },
                    onFilterUp = {
                        {
                            onContentMovedUp()
                            onRequestHeaderFocus()
                        }
                    },
                    onFilterRight = { index ->
                        if (index >= filterItems.lastIndex) {
                            null
                        } else {
                            { requestFilterFocus(index + 1) }
                        }
                    },
                    onFilterDown = {
                        {
                            val moved = requestGridFocus(lastFocusedItemIndex)
                            if (moved) {
                                onContentMovedDown()
                            }
                            moved
                        }
                    },
                )

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    TvCollectionGridStateContent(
                        cards = cards,
                        palette = palette,
                        showStatePanel = showStatePanel,
                        gridState = gridState,
                        itemRequesters = itemRequesters,
                        stateActionRequester = stateActionRequester,
                        interactionsEnabled = interactionsEnabled,
                        columnsCount = columnsCount,
                        topContentPadding = TvCollectionGridTopContentPadding,
                        bottomContentPadding = gridDescriptionInset,
                        selectedCard = selectedCard,
                        statePanel = statePanel,
                        onStateActionClick =
                            stateActionCard?.let { actionCard ->
                                { onItemClick(actionCard) }
                            },
                        onStateActionUp = {
                            onContentMovedUp()
                            requestTopFiltersFocus()
                        },
                        onItemClick = onItemClick,
                        onItemFocused = { item, index ->
                            selectedCard = item as? LibriaCard
                            lastFocusedItemIndex = index
                            lastFocusedItemKey = item.stableKey
                            lastFocusWasGrid = true
                            scope.launch {
                                gridState.scrollItemIntoViewIfNeeded(
                                    index = index,
                                    anchorIndex = gridAnchorIndex(index),
                                    bottomClearancePx = gridBottomClearancePx,
                                )
                            }
                        },
                        onItemLeft = { _, _ -> null },
                        onItemUp = { index, item ->
                            if (item !is LibriaCard || index < columnsCount) {
                                {
                                    onContentMovedUp()
                                    requestTopFiltersFocus()
                                }
                            } else {
                                null
                            }
                        },
                        messageCardModel = { item, itemPalette ->
                            when (item) {
                                is InfoCard ->
                                    TvCollectionMessageCardUiModel(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        palette = itemPalette,
                                    )

                                is LinkCard ->
                                    TvCollectionMessageCardUiModel(
                                        title = item.title,
                                        subtitle = "Нажмите, чтобы выполнить действие",
                                        palette = itemPalette,
                                    )

                                is LoadingCard ->
                                    TvCollectionMessageCardUiModel(
                                        title = item.title.ifBlank { "Загрузка" },
                                        subtitle = item.description,
                                        palette =
                                            itemPalette.copy(
                                                accentColor =
                                                    if (item.isError) {
                                                        itemPalette.accentColor
                                                    } else {
                                                        itemPalette.textColor.copy(alpha = 0.4f)
                                                    },
                                            ),
                                        loading = !item.isError,
                                    )

                                else -> null
                            }
                        },
                        descriptionContent = { card ->
                            WatchingDescriptionBar(
                                title = card.title,
                                subtitle = card.resolveDescription(context),
                                palette = palette,
                                contentPadding = TvCollectionDescriptionBarPadding,
                                solidSurface = true,
                                solidMinHeight = TvCollectionSolidDescriptionBarMinHeight,
                                solidInnerPadding = TvCollectionSolidDescriptionBarInnerPadding,
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .then(descriptionOverlayClearance.measureModifier),
                            )
                        },
                    )
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
