package ru.radiationx.anilibria.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
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
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingFilterPickerDialog
import ru.radiationx.anilibria.screen.watching.TvCollectionFilterAction
import ru.radiationx.anilibria.screen.watching.TvCollectionDescriptionBarPadding
import ru.radiationx.anilibria.screen.watching.TvCollectionGridTopContentPadding
import ru.radiationx.anilibria.screen.watching.TvCollectionGridStateContent
import ru.radiationx.anilibria.screen.watching.TvCollectionGridBottomDescriptionInset
import ru.radiationx.anilibria.screen.watching.TvCollectionMessageCardUiModel
import ru.radiationx.anilibria.screen.watching.TvCollectionStatePanelUiModel
import ru.radiationx.anilibria.screen.watching.TvCollectionTopAction
import ru.radiationx.anilibria.screen.watching.TvCollectionTopFiltersPanel
import ru.radiationx.anilibria.screen.watching.TvCollectionSolidDescriptionBarHeight
import ru.radiationx.anilibria.screen.watching.TvCollectionSolidDescriptionBarInnerPadding
import ru.radiationx.anilibria.screen.watching.TvCollectionSolidDescriptionBarMinHeight
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.TvBottomContentInset
import ru.radiationx.anilibria.screen.watching.TvPageHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvPosterCardSlotWidth
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvPickerTopInset
import ru.radiationx.anilibria.screen.watching.TvCardScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.indexOfItemId
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.rememberTvDescriptionOverlayClearance
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import kotlin.math.max

private enum class CatalogFocusTarget {
    Search,
    Filter,
    Grid,
}

@Composable
internal fun CatalogScreen(
    cards: List<CardItem>,
    filters: TvCollectionFiltersUiState,
    progressVisible: Boolean,
    pickerState: TvCollectionFilterPickerState?,
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
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val filtersRowState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val filterItems = remember(filters) {
        listOf(
            TvCollectionFilterAction(filters.year.label, filters.year.emphasized, onYearClick),
            TvCollectionFilterAction(filters.season.label, filters.season.emphasized, onSeasonClick),
            TvCollectionFilterAction(filters.genre.label, filters.genre.emphasized, onGenreClick),
            TvCollectionFilterAction(filters.sort.label, filters.sort.emphasized, onSortClick),
            TvCollectionFilterAction(
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
    val stateActionRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val itemIds = remember(cards) { cards.map(CardItem::getId) }
    val itemRequesters = remember(itemIds) {
        List(cards.size) { androidx.compose.ui.focus.FocusRequester() }
    }
    var selectedItem by remember(itemIds) { mutableStateOf<LibriaCard?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var handledRestoreToken by remember { mutableIntStateOf(0) }
    var lastFocusedFilterIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemIndex by rememberSaveable { mutableIntStateOf(0) }
    var lastFocusedItemId by rememberSaveable { mutableIntStateOf(Int.MIN_VALUE) }
    var lastFocusTarget by rememberSaveable { mutableStateOf(CatalogFocusTarget.Search.name) }
    var lastAutoAppendToken by rememberSaveable { mutableStateOf("") }
    val interactionsEnabled = pickerState == null
    val nonContentCards = remember(cards) { cards.filter { it !is LibriaCard } }
    val stateCard = remember(nonContentCards) {
        nonContentCards.firstOrNull { it is LoadingCard && it.isError }
            ?: nonContentCards.firstOrNull { it is LoadingCard }
            ?: nonContentCards.firstOrNull { it is InfoCard }
            ?: nonContentCards.firstOrNull()
    }
    val stateActionCard = remember(nonContentCards) { nonContentCards.filterIsInstance<LinkCard>().firstOrNull() }
    val hasCustomFilters = remember(filters) {
        listOf(
            filters.year,
            filters.season,
            filters.genre,
            filters.sort,
            filters.onlyCompleted,
        ).any { it.emphasized }
    }
    val showStatePanel = cards.isEmpty() || (cards.isNotEmpty() && cards.none { it is LibriaCard })
    val hasContent = remember(itemIds, showStatePanel) { cards.any { it is LibriaCard } && !showStatePanel }
    val columnsCount = remember(configuration.screenWidthDp) {
        max(
            1,
            ((configuration.screenWidthDp.dp - (TvCardScreenHorizontalPadding * 2)) / TvPosterCardSlotWidth)
                .toInt(),
        )
    }
    val descriptionOverlayClearance = rememberTvDescriptionOverlayClearance(
        hasContent = hasContent,
        fallbackInset = TvCollectionGridBottomDescriptionInset,
    )
    val gridDescriptionInset = if (hasContent) {
        descriptionOverlayClearance.bottomInset
    } else {
        TvBottomContentInset
    }
    val gridBottomClearancePx = descriptionOverlayClearance.bottomClearancePx
    val statePanel = remember(stateCard, progressVisible, hasCustomFilters, stateActionCard) {
        val title: String
        val subtitle: String
        val accent: Boolean
        val loading: Boolean
        when (val item = stateCard) {
            is LoadingCard -> {
                title = item.title.ifBlank { "Ищем релизы" }
                subtitle = item.description.ifBlank {
                    if (item.isError) {
                        "Проверьте подключение и попробуйте снова."
                    } else {
                        "Подождите, каталог обновляет результаты."
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
                subtitle = "Выберите другой фильтр или откройте отдельный поиск."
                accent = false
                loading = false
            }

            else -> {
                loading = progressVisible
                accent = false
                if (progressVisible) {
                    title = "Ищем релизы"
                    subtitle = "Подождите, каталог обновляет результаты."
                } else if (hasCustomFilters) {
                    title = "Ничего не найдено"
                    subtitle = "Попробуйте ослабить фильтры или откройте отдельный поиск."
                } else {
                    title = "Каталог готов"
                    subtitle = "Откройте поиск или настройте фильтры, чтобы быстро сузить список релизов."
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

    fun requestSearchFocus(): Boolean {
        scope.launch {
            requestWatchingFocusAfterAttach(searchRequester)
        }
        return true
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

    fun requestTopFocus(): Boolean {
        return when {
            lastFocusTarget == CatalogFocusTarget.Search.name -> requestSearchFocus()
            filterRequesters.isNotEmpty() -> requestFilterFocus(lastFocusedFilterIndex)
            else -> requestSearchFocus()
        }
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

    fun triggerAutoAppendIfNeeded(index: Int, item: CardItem) {
        if (item !is LibriaCard || progressVisible || !interactionsEnabled) {
            return
        }
        val loadMoreCard = cards.lastOrNull() as? LinkCard ?: return
        val lastContentIndex = cards.indexOfLast { it is LibriaCard }
        if (lastContentIndex < 0) {
            return
        }
        val triggerIndex = (lastContentIndex - columnsCount).coerceAtLeast(0)
        if (index < triggerIndex) {
            return
        }
        val requestToken = buildString {
            append(cards.size)
            append(':')
            append(cards.firstOrNull()?.getId() ?: Int.MIN_VALUE)
            append(':')
            append(cards.lastOrNull()?.getId() ?: Int.MIN_VALUE)
        }
        if (lastAutoAppendToken == requestToken) {
            return
        }
        lastAutoAppendToken = requestToken
        onItemClick(loadMoreCard)
    }

    LaunchedEffect(cards) {
        val selectedId = selectedItem?.getId()
        val stillVisible = selectedId != null && cards.any { it.getId() == selectedId }
        if (!stillVisible) {
            selectedItem = null
        }
        if (cards.lastOrNull() !is LinkCard) {
            lastAutoAppendToken = ""
        }
        if (lastFocusedItemId != Int.MIN_VALUE && cards.none { it.getId() == lastFocusedItemId }) {
            when {
                cards.isNotEmpty() -> requestGridFocus(lastFocusedItemIndex)
                else -> requestTopFocus()
            }
        }
    }

    LaunchedEffect(focusRequestToken, itemIds, pickerState) {
        if (pickerState != null || focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        val focused = when {
            lastFocusTarget == CatalogFocusTarget.Search.name -> requestSearchFocus()
            lastFocusTarget == CatalogFocusTarget.Grid.name && lastFocusedItemId != Int.MIN_VALUE -> {
                requestGridFocus(cards.indexOfItemId(lastFocusedItemId) ?: lastFocusedItemIndex)
            }

            lastFocusTarget == CatalogFocusTarget.Filter.name && filterRequesters.isNotEmpty() -> {
                requestFilterFocus(lastFocusedFilterIndex)
            }

            else -> requestWatchingFocusAfterAttach(searchRequester)
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

    LaunchedEffect(selectedItem) {
        onItemFocused(selectedItem)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .tvAppBackground(palette)
            .padding(horizontal = TvCardScreenHorizontalPadding, vertical = TvPageVerticalPadding),
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
                    title = "Каталог",
                    subtitle = "Фильтруйте релизы и переходите в экран поиска",
                    headerExpanded = !hasContent || lastFocusTarget != CatalogFocusTarget.Grid.name,
                    leadingAction = TvCollectionTopAction(
                        label = "Поиск",
                        focusRequester = searchRequester,
                        onClick = onSearchClick,
                        enabled = interactionsEnabled,
                        onFocused = {
                            lastFocusTarget = CatalogFocusTarget.Search.name
                            selectedItem = null
                        },
                        onRight = { requestFilterFocus(0) },
                        onDown = { requestGridFocus(lastFocusedItemIndex) },
                    ),
                    onFilterFocused = { index ->
                        lastFocusedFilterIndex = index
                        lastFocusTarget = CatalogFocusTarget.Filter.name
                        selectedItem = null
                        scope.launch {
                            filtersRowState.scrollItemIntoViewIfNeeded(index)
                        }
                    },
                    onFilterLeft = { index ->
                        if (index == 0) {
                            { requestSearchFocus() }
                        } else {
                            { requestFilterFocus(index - 1) }
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
                        { requestGridFocus(lastFocusedItemIndex) }
                    },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                        selectedCard = selectedItem,
                        statePanel = statePanel,
                        onStateActionClick = stateActionCard?.let { actionCard ->
                            { onItemClick(actionCard) }
                        },
                        onStateActionUp = ::requestTopFocus,
                        onItemClick = onItemClick,
                        onItemFocused = { item, index ->
                            if (item is LibriaCard) {
                                selectedItem = item
                            } else {
                                selectedItem = null
                            }
                            lastFocusedItemIndex = index
                            lastFocusedItemId = item.getId()
                            lastFocusTarget = CatalogFocusTarget.Grid.name
                            triggerAutoAppendIfNeeded(index, item)
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
                                ::requestTopFocus
                            } else {
                                null
                            }
                        },
                        messageCardModel = { item, itemPalette ->
                            when (item) {
                                is InfoCard -> TvCollectionMessageCardUiModel(
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    palette = itemPalette,
                                )

                                is LinkCard -> TvCollectionMessageCardUiModel(
                                    title = item.title,
                                    subtitle = "Нажмите, чтобы выполнить действие",
                                    palette = itemPalette,
                                )

                                is LoadingCard -> TvCollectionMessageCardUiModel(
                                    title = item.title.ifBlank { "Загрузка" },
                                    subtitle = item.description.ifBlank {
                                        if (item.isError) {
                                            "Нажмите, чтобы повторить попытку"
                                        } else {
                                            ""
                                        }
                                    },
                                    palette = itemPalette.copy(
                                        accentColor = if (item.isError) {
                                            itemPalette.accentColor
                                        } else {
                                            itemPalette.textColor.copy(alpha = 0.4f)
                                        }
                                    ),
                                    loading = !item.isError,
                                )

                                else -> null
                            }
                        },
                        descriptionContent = { item ->
                            val description = item.toTvCardDescription { card ->
                                card.resolveDescription(context)
                            }
                            if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
                                WatchingDescriptionBar(
                                    title = description.title.toString(),
                                    subtitle = description.subtitle.toString(),
                                    palette = palette,
                                    contentPadding = TvCollectionDescriptionBarPadding,
                                    solidSurface = true,
                                    solidHeight = TvCollectionSolidDescriptionBarHeight,
                                    solidMinHeight = TvCollectionSolidDescriptionBarMinHeight,
                                    solidInnerPadding = TvCollectionSolidDescriptionBarInnerPadding,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .then(descriptionOverlayClearance.measureModifier),
                                )
                            }
                        },
                        overlayContent = {
                            if (progressVisible && hasContent) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp),
                                    color = palette.accentColor,
                                    trackColor = palette.textColor.copy(alpha = 0.18f),
                                )
                            }
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
