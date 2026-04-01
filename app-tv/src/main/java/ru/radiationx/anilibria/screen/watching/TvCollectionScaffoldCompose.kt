package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel

internal data class TvCollectionFilterAction(
    val label: String,
    val emphasized: Boolean,
    val onClick: () -> Unit,
)

internal data class TvCollectionStatePanelUiModel(
    val title: String,
    val subtitle: String,
    val accent: Boolean,
    val loading: Boolean,
    val actionText: String? = null,
)

internal data class TvCollectionMessageCardUiModel(
    val title: String,
    val subtitle: String,
    val palette: WatchingPalette,
    val loading: Boolean = false,
)

@Composable
internal fun TvCollectionFiltersRow(
    filters: List<TvCollectionFilterAction>,
    palette: WatchingPalette,
    rowState: LazyListState,
    filterRequesters: List<FocusRequester>,
    interactionsEnabled: Boolean,
    onFilterFocused: (Int) -> Unit,
    onFilterLeft: (Int) -> (() -> Boolean)? = { null },
    onFilterUp: (Int) -> (() -> Boolean)? = { null },
    onFilterDown: (Int) -> (() -> Boolean)?,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(TvFilterRowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lazyItemsIndexed(filters) { index, filter ->
            WatchingFilterChip(
                text = filter.label,
                palette = palette,
                focusRequester = filterRequesters[index],
                enabled = interactionsEnabled,
                emphasized = filter.emphasized,
                onClick = filter.onClick,
                onFocused = { onFilterFocused(index) },
                onLeft = onFilterLeft(index),
                onUp = onFilterUp(index),
                onDown = onFilterDown(index),
            )
        }
    }
}

@Composable
internal fun TvCollectionGridStateContent(
    cards: List<CardItem>,
    palette: WatchingPalette,
    showStatePanel: Boolean,
    gridState: LazyGridState,
    itemRequesters: List<FocusRequester>,
    stateActionRequester: FocusRequester,
    interactionsEnabled: Boolean,
    columnsCount: Int,
    bottomContentPadding: Dp,
    selectedCard: LibriaCard?,
    statePanel: TvCollectionStatePanelUiModel,
    onStateActionClick: (() -> Unit)? = null,
    onStateActionLeft: (() -> Boolean)? = null,
    onStateActionUp: (() -> Boolean)? = null,
    onItemClick: (CardItem) -> Unit,
    onItemFocused: (CardItem, Int) -> Unit,
    onItemLeft: (Int, CardItem) -> (() -> Boolean)? = { _, _ -> null },
    onItemUp: (Int, CardItem) -> (() -> Boolean)?,
    messageCardModel: (CardItem, WatchingPalette) -> TvCollectionMessageCardUiModel?,
    descriptionContent: @Composable BoxScope.(LibriaCard) -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (showStatePanel) {
            TvContentStatePanel(
                title = statePanel.title,
                subtitle = statePanel.subtitle,
                palette = palette,
                accent = statePanel.accent,
                loading = statePanel.loading,
                modifier = Modifier.align(Alignment.TopCenter),
                action = if (statePanel.actionText != null && onStateActionClick != null) {
                    {
                        TvContentStateActionButton(
                            text = statePanel.actionText,
                            palette = palette,
                            focusRequester = stateActionRequester,
                            onClick = onStateActionClick,
                            onLeft = onStateActionLeft,
                            onUp = onStateActionUp,
                            onDown = { true },
                        )
                    }
                } else {
                    null
                },
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(TvRowSpacing),
            ) {
                itemsIndexed(
                    items = cards,
                    key = { _, item -> item.getId() },
                    span = { _, item -> spanForTvCollectionItem(item) },
                ) { index, item ->
                    when (item) {
                        is LibriaCard -> WatchingPosterCard(
                            imageUrl = item.image,
                            palette = palette,
                            focusRequester = itemRequesters[index],
                            enabled = interactionsEnabled,
                            scaleTransformOrigin = edgeAwareGridTransformOrigin(
                                index = index,
                                columnsCount = columnsCount,
                                itemsCount = cards.size,
                            ),
                            onClick = { onItemClick(item) },
                            onFocused = { onItemFocused(item, index) },
                            onLeft = onItemLeft(index, item),
                            onUp = onItemUp(index, item),
                        )

                        else -> {
                            val cardModel = messageCardModel(item, palette) ?: return@itemsIndexed
                            WatchingWideMessageCard(
                                title = cardModel.title,
                                subtitle = cardModel.subtitle,
                                palette = cardModel.palette,
                                focusRequester = itemRequesters[index],
                                enabled = interactionsEnabled,
                                loading = cardModel.loading,
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(item, index) },
                                onLeft = onItemLeft(index, item),
                                onUp = onItemUp(index, item),
                            )
                        }
                    }
                }
            }
        }

        overlayContent()

        if (!showStatePanel) {
            selectedCard?.let { card ->
                descriptionContent(card)
            }
        }
    }
}

private fun LazyGridItemSpanScope.spanForTvCollectionItem(item: CardItem): GridItemSpan {
    return if (item is LibriaCard) GridItemSpan(1) else GridItemSpan(maxLineSpan)
}
