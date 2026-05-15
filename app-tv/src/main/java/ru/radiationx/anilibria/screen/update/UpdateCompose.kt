package ru.radiationx.anilibria.screen.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import ru.radiationx.anilibria.screen.watching.TvPageHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceItem
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceList
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceSection
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.anilibria.ui.compose.TvPageHeader
import ru.radiationx.anilibria.ui.compose.TvTextActionButton
import ru.radiationx.anilibria.ui.compose.TvUiDefaults
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import ru.radiationx.anilibria.ui.compose.tvPanelSurface
import ru.radiationx.data.entity.domain.updater.UpdateData

private const val MIN_SCROLLBAR_THUMB_HEIGHT_PX = 18
private const val UPDATE_CONTENT_MAX_WIDTH = 980

@Composable
internal fun UpdateScreen(
    updateData: UpdateData?,
    isInitialLoading: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    isSourceChooserVisible: Boolean,
    focusRequestToken: Int,
    onActionClick: () -> Unit,
    onSourceSelected: (Int) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val actionRequester = remember { FocusRequester() }
    val notesRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequestToken, isInitialLoading) {
        if (!isInitialLoading && focusRequestToken > 0) {
            requestWatchingFocusAfterAttach(actionRequester)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .tvAppBackground(palette)
                    .padding(horizontal = TvScreenHorizontalPadding, vertical = TvPageVerticalPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvPageHeaderSpacing),
            ) {
                TvPageHeader(
                    title = "Обновление",
                    subtitle =
                        when {
                            isInitialLoading -> "Проверяем наличие новой версии и готовим заметки к релизу."
                            updateData?.hasUpdate == true ->
                                buildString {
                                    append("Новая версия")
                                    updateData.name?.takeIf { it.isNotBlank() }?.also {
                                        append(" ")
                                        append(it)
                                    }
                                    updateData.date?.takeIf { it.isNotBlank() }?.also {
                                        append(" • ")
                                        append(it)
                                    }
                                }
                            updateData != null -> "Клиент уже обновлён. Здесь останутся заметки к релизу и история изменений."
                            else -> "Не удалось получить данные об обновлении. Проверьте информацию немного позже."
                        },
                    palette = palette,
                    trailingContent = {
                        if (!isInitialLoading) {
                            UpdateActionButton(
                                text = if (isDownloading) "Отмена" else "Установить",
                                palette = palette,
                                focusRequester = actionRequester,
                                downRequester = notesRequester,
                                enabled = updateData?.hasUpdate == true || isDownloading,
                                onClick = onActionClick,
                            )
                        }
                    },
                )

                AnimatedVisibility(
                    visible = isDownloading,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = UPDATE_CONTENT_MAX_WIDTH.dp),
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
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(
                                            color = palette.textColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(999.dp),
                                        ),
                                color = palette.textColor,
                                trackColor = palette.textColor.copy(alpha = 0.12f),
                            )
                        }
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (isInitialLoading) {
                        TvContentStatePanel(
                            title = "Проверяем обновление",
                            subtitle = "Подождите немного: версия, заметки к релизу и основное действие появятся здесь автоматически.",
                            palette = palette,
                            loading = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = UPDATE_CONTENT_MAX_WIDTH.dp),
                        )
                    } else {
                        UpdateNotesCard(
                            updateData = updateData,
                            palette = palette,
                            focusRequester = notesRequester,
                            upRequester = actionRequester,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = UPDATE_CONTENT_MAX_WIDTH.dp)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }
        }

        if (isSourceChooserVisible) {
            TvOverlayScreen(
                title = "Источник обновления",
                subtitle = "Выберите, откуда загрузить новую версию приложения.",
                panelMaxWidth = 680.dp,
            ) { _ ->
                TvOverlayChoiceList(
                    sections =
                        listOf(
                            TvOverlayChoiceSection(
                                items =
                                    updateData
                                        ?.links
                                        .orEmpty()
                                        .mapIndexed { index, source ->
                                            TvOverlayChoiceItem(
                                                id = index.toLong(),
                                                title = source.name,
                                                subtitle =
                                                    when (source.type) {
                                                        UpdateData.LinkType.FILE -> "Скачать APK-файл"
                                                        UpdateData.LinkType.SITE -> "Открыть страницу загрузки"
                                                    },
                                                selected = index == 0,
                                            )
                                        },
                            ),
                        ),
                    onItemClick = { choice ->
                        onSourceSelected(choice.id.toInt())
                    },
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
    TvTextActionButton(
        text = text,
        palette = palette,
        focusRequester = focusRequester,
        onClick = onClick,
        enabled = enabled,
        allowFocusWhenDisabled = true,
        minWidth = 196.dp,
        colors =
            TvUiDefaults.accentActionColors(
                palette = palette,
                backgroundAlpha = 0.20f,
                focusedBackgroundAlpha = 0.30f,
                borderAlpha = 0.86f,
            ),
        paddingValues = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        onDown = {
            requestWatchingFocus(downRequester)
        },
    )
}

@Composable
private fun UpdateNotesCard(
    updateData: UpdateData?,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .tvPanelSurface(
                    TvUiDefaults.screenPanelStyle(
                        palette = palette,
                        focused = isFocused,
                    ),
                )
                .onFocusChanged { isFocused = it.isFocused }
                .focusRequester(focusRequester)
                .focusProperties {
                    up = upRequester
                }
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || viewportHeightPx <= 0) {
                        return@onPreviewKeyEvent false
                    }
                    val target =
                        when (event.key) {
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
                .padding(TvUiDefaults.ScreenPanelPadding),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeightPx = it.height },
        ) {
            Column(
                modifier =
                    Modifier
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
    val thumbHeightPx =
        ((viewportHeightPx.toFloat() / contentHeightPx) * viewportHeightPx)
            .toInt()
            .coerceAtLeast(MIN_SCROLLBAR_THUMB_HEIGHT_PX)
            .coerceAtMost(viewportHeightPx)
    val thumbOffsetPx by remember(scrollState, maxValue, viewportHeightPx, thumbHeightPx) {
        derivedStateOf {
            ((scrollState.value.toFloat() / maxValue) * (viewportHeightPx - thumbHeightPx))
                .toInt()
                .coerceIn(0, viewportHeightPx - thumbHeightPx)
        }
    }

    Box(
        modifier =
            modifier
                .width(3.dp)
                .height(with(androidx.compose.ui.platform.LocalDensity.current) { viewportHeightPx.toDp() })
                .clip(RoundedCornerShape(percent = 50))
                .background(color.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { thumbHeightPx.toDp() })
                    .offset { IntOffset(x = 0, y = thumbOffsetPx) }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color.copy(alpha = 0.52f)),
        )
    }
}
