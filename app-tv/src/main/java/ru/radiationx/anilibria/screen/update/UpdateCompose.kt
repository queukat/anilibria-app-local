package ru.radiationx.anilibria.screen.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.data.entity.domain.updater.UpdateData

@Composable
internal fun UpdateScreen(
    updateData: UpdateData?,
    isInitialLoading: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    focusRequestToken: Int,
    onActionClick: () -> Unit,
) {
    val palette = rememberWatchingPalette()
    val actionRequester = remember { FocusRequester() }
    val notesRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequestToken, isInitialLoading) {
        if (!isInitialLoading && focusRequestToken > 0) {
            actionRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceColor.copy(alpha = 0.18f),
                        Color.Transparent,
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        if (isInitialLoading) {
            CircularProgressIndicator(
                color = palette.textColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Обновление",
                            color = palette.textColor,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val versionLine = buildString {
                            updateData?.name?.takeIf { it.isNotBlank() }?.also {
                                append(it)
                            }
                            updateData?.date?.takeIf { it.isNotBlank() }?.also {
                                if (isNotEmpty()) append(" • ")
                                append(it)
                            }
                        }
                        if (versionLine.isNotBlank()) {
                            Text(
                                text = versionLine,
                                color = palette.secondaryTextColor,
                                fontSize = 15.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    UpdateActionButton(
                        text = if (isDownloading) "Отмена" else "Установить",
                        palette = palette,
                        focusRequester = actionRequester,
                        downRequester = notesRequester,
                        enabled = updateData?.hasUpdate == true || isDownloading,
                        onClick = onActionClick,
                    )
                }

                AnimatedVisibility(
                    visible = isDownloading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (downloadProgress > 0) "$downloadProgress%" else "Подготовка загрузки",
                            color = palette.secondaryTextColor,
                            fontSize = 14.sp,
                        )
                        LinearProgressIndicator(
                            progress = {
                                if (downloadProgress > 0) {
                                    downloadProgress / 100f
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = palette.textColor,
                            trackColor = palette.textColor.copy(alpha = 0.12f),
                        )
                    }
                }

                UpdateNotesCard(
                    updateData = updateData,
                    palette = palette,
                    focusRequester = notesRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UpdateActionButton(
    text: String,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
    focusRequester: FocusRequester,
    downRequester: FocusRequester,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val modifier = if (enabled) {
        Modifier
            .focusRequester(focusRequester)
            .focusProperties { down = downRequester }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    } else {
        Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = androidx.compose.ui.res.colorResource(R.color.dark_release_day_btn),
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) palette.textColor.copy(alpha = 0.75f) else Color.Transparent,
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Text(
            text = text,
            color = if (enabled) palette.textColor else palette.secondaryTextColor,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun UpdateNotesCard(
    updateData: UpdateData?,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceColor.copy(alpha = 0.78f))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) palette.textColor.copy(alpha = 0.75f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || viewportHeightPx <= 0) {
                    return@onPreviewKeyEvent false
                }
                val target = when (event.key) {
                    Key.DirectionDown -> (scrollState.value + viewportHeightPx).coerceAtMost(scrollState.maxValue)
                    Key.DirectionUp -> (scrollState.value - viewportHeightPx).coerceAtLeast(0)
                    else -> return@onPreviewKeyEvent false
                }
                if (target == scrollState.value) {
                    false
                } else {
                    scope.launch { scrollState.animateScrollTo(target) }
                    true
                }
            }
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (scrollState.maxValue > 0) 10.dp else 0.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (updateData == null) {
                    UpdateSectionTitle(
                        title = "Нет данных",
                        palette = palette,
                    )
                    Text(
                        text = "Не удалось получить информацию об обновлении.",
                        color = palette.secondaryTextColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    )
                } else if (!updateData.hasUpdate) {
                    UpdateSectionTitle(
                        title = "У вас актуальная версия",
                        palette = palette,
                    )
                    Text(
                        text = "Новых обновлений для TV-клиента сейчас нет.",
                        color = palette.secondaryTextColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    )
                } else {
                    UpdateChangesContent(updateData = updateData, palette = palette)
                }
            }

            UpdateScrollIndicator(
                visible = scrollState.maxValue > 0,
                scrollState = scrollState,
                viewportHeightPx = viewportHeightPx,
                color = palette.textColor,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun UpdateChangesContent(
    updateData: UpdateData,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
) {
    UpdateMetaLine(label = "Версия", value = updateData.name.orEmpty(), palette = palette)
    UpdateMetaLine(label = "Дата", value = updateData.date.orEmpty(), palette = palette)
    UpdateListSection(title = "Важно", items = updateData.important, palette = palette)
    UpdateListSection(title = "Добавлено", items = updateData.added, palette = palette)
    UpdateListSection(title = "Исправлено", items = updateData.fixed, palette = palette)
    UpdateListSection(title = "Изменено", items = updateData.changed, palette = palette)
}

@Composable
private fun UpdateMetaLine(
    label: String,
    value: String,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = palette.textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = palette.secondaryTextColor,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun UpdateListSection(
    title: String,
    items: List<String>,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UpdateSectionTitle(title = title, palette = palette)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { item ->
                Text(
                    text = "• $item",
                    color = palette.secondaryTextColor,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun UpdateSectionTitle(
    title: String,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
) {
    Text(
        text = title,
        color = palette.textColor,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun UpdateScrollIndicator(
    visible: Boolean,
    scrollState: ScrollState,
    viewportHeightPx: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val maxValue = scrollState.maxValue
    if (!visible || maxValue <= 0 || viewportHeightPx <= 0) {
        return
    }

    val contentHeightPx = viewportHeightPx + maxValue
    val thumbHeightPx = ((viewportHeightPx.toFloat() / contentHeightPx) * viewportHeightPx)
        .toInt()
        .coerceAtLeast(18)
        .coerceAtMost(viewportHeightPx)
    val thumbOffsetPx by remember(scrollState, maxValue, viewportHeightPx, thumbHeightPx) {
        derivedStateOf {
            (((scrollState.value.toFloat() / maxValue) * (viewportHeightPx - thumbHeightPx)))
                .toInt()
                .coerceIn(0, viewportHeightPx - thumbHeightPx)
        }
    }

    Box(
        modifier = modifier
            .width(3.dp)
            .height(with(androidx.compose.ui.platform.LocalDensity.current) { viewportHeightPx.toDp() })
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(androidx.compose.ui.platform.LocalDensity.current) { thumbHeightPx.toDp() })
                .offset { IntOffset(x = 0, y = thumbOffsetPx) }
                .clip(RoundedCornerShape(percent = 50))
                .background(color.copy(alpha = 0.52f)),
        )
    }
}
