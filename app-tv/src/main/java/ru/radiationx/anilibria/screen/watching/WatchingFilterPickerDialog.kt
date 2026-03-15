package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.TvCollectionFilterPickerState
import ru.radiationx.anilibria.ui.compose.TvOverlayOuterPadding
import ru.radiationx.anilibria.ui.compose.TvOverlayPanelSurface

private const val TV_COLLECTION_FILTER_PICKER_WIDTH_FRACTION = 0.62f

@Composable
internal fun WatchingFilterPickerDialog(
    state: TvCollectionFilterPickerState,
    palette: WatchingPalette,
    focusRequestToken: Int,
    onToggleOption: (Int) -> Unit,
    onSingleSelect: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val optionIds = remember(state.options) { state.options.indices.toList() }
    val optionRequesters = remember(optionIds) { List(optionIds.size) { androidx.compose.ui.focus.FocusRequester() } }
    val resetRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val applyRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lastFocusedOptionIndex by remember(state.options) {
        mutableIntStateOf(
            state.selectedIndices.minOrNull()?.coerceIn(0, state.options.lastIndex.coerceAtLeast(0)) ?: 0
        )
    }

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
        lastFocusedOptionIndex = clampedIndex
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

    LaunchedEffect(focusRequestToken, optionIds) {
        if (focusRequestToken <= 0 || optionIds.isEmpty()) {
            return@LaunchedEffect
        }
        val targetIndex = state.selectedIndices.minOrNull()?.coerceIn(0, optionIds.lastIndex) ?: 0
        lastFocusedOptionIndex = targetIndex
        listState.scrollToItem(targetIndex)
        withFrameNanos { }
        requestWatchingFocus(optionRequesters.getOrNull(targetIndex))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Back,
                    Key.Escape,
                    -> {
                        onDismiss()
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        TvOverlayPanelSurface(
            palette = palette,
            modifier = Modifier
                .padding(top = TvPickerTopInset)
                .fillMaxWidth(TV_COLLECTION_FILTER_PICKER_WIDTH_FRACTION)
                .heightIn(max = 640.dp),
            contentPadding = TvOverlayOuterPadding,
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
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 320.dp, max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
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
                                onFocused = {
                                    lastFocusedOptionIndex = index
                                },
                                onUp = if (index > 0) {
                                    { requestOptionFocus(index - 1) }
                                } else {
                                    { true }
                                },
                                onRight = if (state.multiSelect) {
                                    { requestWatchingFocus(applyRequester) }
                                } else {
                                    null
                                },
                                onDown = when {
                                    index < state.options.lastIndex -> {
                                        { requestOptionFocus(index + 1) }
                                    }

                                    state.multiSelect -> {
                                        { requestWatchingFocus(applyRequester) }
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
                                            .size(12.dp)
                                            .background(
                                                color = if (isSelected) {
                                                    palette.accentColor
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(percent = 50),
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) {
                                                    palette.accentColor
                                                } else {
                                                    palette.textColor.copy(alpha = 0.22f)
                                                },
                                                shape = RoundedCornerShape(percent = 50),
                                            )
                                    )
                                }
                            }
                        }
                    }

                    if (state.multiSelect) {
                        Column(
                            modifier = Modifier.width(220.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Действия",
                                color = palette.secondaryTextColor,
                                fontSize = 13.sp,
                            )
                            WatchingFocusableSurface(
                                focusRequester = applyRequester,
                                backgroundColor = palette.accentColor.copy(alpha = 0.22f),
                                focusedBackgroundColor = palette.accentColor.copy(alpha = 0.28f),
                                borderColor = palette.accentColor.copy(alpha = 0.86f),
                                onClick = onApply,
                                onLeft = {
                                    requestOptionFocus(lastFocusedOptionIndex)
                                },
                                onDown = {
                                    requestWatchingFocus(resetRequester)
                                },
                                modifier = Modifier.fillMaxWidth(),
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
                            WatchingFocusableSurface(
                                focusRequester = resetRequester,
                                backgroundColor = palette.chipColor.copy(alpha = 0.82f),
                                focusedBackgroundColor = palette.chipColor,
                                borderColor = palette.textColor.copy(alpha = 0.72f),
                                onClick = onReset,
                                onUp = {
                                    requestWatchingFocus(applyRequester)
                                },
                                onLeft = {
                                    requestOptionFocus(lastFocusedOptionIndex)
                                },
                                modifier = Modifier.fillMaxWidth(),
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
                            Text(
                                text = "Вправо: применить\nВлево: вернуться к списку",
                                color = palette.secondaryTextColor,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }

                Text(
                    text = "Назад: закрыть",
                    color = palette.secondaryTextColor,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
