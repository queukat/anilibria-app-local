package ru.radiationx.anilibria.screen.watching

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvContentStatePanelOptions
import ru.radiationx.anilibria.ui.compose.TvTextActionButton
import ru.radiationx.anilibria.ui.compose.TvUiDefaults
import ru.radiationx.anilibria.ui.compose.tvPanelSurface
import ru.radiationx.anilibria.ui.compose.tvContentStateActionFocus
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed

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

internal data class TvCollectionTopAction(
    val label: String,
    val focusRequester: FocusRequester,
    val onClick: () -> Unit,
    val enabled: Boolean,
    val onFocused: (() -> Unit)? = null,
    val onLeft: (() -> Boolean)? = null,
    val onUp: (() -> Boolean)? = null,
    val onRight: (() -> Boolean)? = null,
    val onDown: (() -> Boolean)? = null,
)

@Composable
internal fun TvCollectionTopFiltersPanel(
    palette: WatchingPalette,
    filters: List<TvCollectionFilterAction>,
    rowState: LazyListState,
    filterRequesters: List<FocusRequester>,
    interactionsEnabled: Boolean,
    onFilterFocused: (Int) -> Unit,
    onFilterLeft: (Int) -> (() -> Boolean)? = { null },
    onFilterUp: (Int) -> (() -> Boolean)? = { null },
    onFilterRight: (Int) -> (() -> Boolean)? = { null },
    onFilterDown: (Int) -> (() -> Boolean)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    headerExpanded: Boolean = true,
    leadingAction: TvCollectionTopAction? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .tvPanelSurface(TvUiDefaults.screenPanelStyle(palette))
                .padding(TvCollectionTopFiltersPanelPadding),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(TvCollectionTopFiltersSpacing),
        ) {
            if (headerExpanded && (!title.isNullOrBlank() || !subtitle.isNullOrBlank())) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    title
                        ?.takeIf { it.isNotBlank() }
                        ?.let { text ->
                            Text(
                                text = text,
                                color = palette.textColor,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    subtitle
                        ?.takeIf { it.isNotBlank() }
                        ?.let { text ->
                            Text(
                                text = text,
                                color = palette.secondaryTextColor,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TvCollectionTopFiltersActionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingAction?.let { action ->
                    TvTextActionButton(
                        text = action.label,
                        palette = palette,
                        focusRequester = action.focusRequester,
                        onClick = action.onClick,
                        enabled = action.enabled,
                        colors = TvUiDefaults.chipActionColors(palette),
                        paddingValues = TvUiDefaults.CompactActionButtonPadding,
                        fontSize = 15.sp,
                        onFocused = action.onFocused,
                        onLeft = action.onLeft,
                        onUp = action.onUp,
                        onRight = action.onRight,
                        onDown = action.onDown,
                        modifier = Modifier.width(TvCollectionTopFiltersActionWidth),
                    )
                }

                Box(
                    modifier =
                        if (leadingAction != null) {
                            Modifier.weight(1f)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                ) {
                    TvCollectionFiltersRow(
                        filters = filters,
                        palette = palette,
                        rowState = rowState,
                        filterRequesters = filterRequesters,
                        interactionsEnabled = interactionsEnabled,
                        onFilterFocused = onFilterFocused,
                        onFilterLeft = onFilterLeft,
                        onFilterUp = onFilterUp,
                        onFilterRight = onFilterRight,
                        onFilterDown = onFilterDown,
                    )
                }
            }
        }
    }
}

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
    onFilterRight: (Int) -> (() -> Boolean)? = { null },
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
                onRight = onFilterRight(index),
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
    topContentPadding: Dp = 0.dp,
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
                modifier = Modifier.align(Alignment.TopCenter),
                options =
                    TvContentStatePanelOptions(
                        accent = statePanel.accent,
                        loading = statePanel.loading,
                    ),
                action =
                    if (statePanel.actionText != null && onStateActionClick != null) {
                        {
                            TvContentStateActionButton(
                                text = statePanel.actionText,
                                palette = palette,
                                focusRequester = stateActionRequester,
                                onClick = onStateActionClick,
                                focus =
                                    tvContentStateActionFocus(
                                        onLeft = onStateActionLeft,
                                        onUp = onStateActionUp,
                                        onDown = { true },
                                    ),
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
                contentPadding =
                    PaddingValues(
                        top = topContentPadding,
                        bottom = bottomContentPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(TvRowSpacing),
            ) {
                itemsIndexed(
                    items = cards,
                    key = { _, item -> item.stableKey },
                    span = { _, item -> spanForTvCollectionItem(item) },
                ) { index, item ->
                    when (item) {
                        is LibriaCard ->
                            WatchingPosterCard(
                                imageUrl = item.image,
                                palette = palette,
                                focusRequester = itemRequesters[index],
                                enabled = interactionsEnabled,
                                scaleTransformOrigin =
                                    edgeAwareGridTransformOrigin(
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
