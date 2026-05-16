package ru.radiationx.anilibria.screen.watching

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.ui.compose.TvAsyncImage
import ru.radiationx.anilibria.ui.compose.TvAsyncImageOptions
import ru.radiationx.anilibria.ui.compose.TvPosterCardFocusStyle
import ru.radiationx.anilibria.ui.compose.TvTextActionButton
import ru.radiationx.anilibria.ui.compose.TvUiDefaults

private const val WATCHING_CARD_ASPECT_RATIO = 130f / 185f

internal data class WatchingPalette(
    val backgroundColor: Color,
    val surfaceColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val chipColor: Color,
)

@Composable
internal fun rememberWatchingPalette(): WatchingPalette {
    return WatchingPalette(
        backgroundColor = colorResource(R.color.dark_windowBackground),
        surfaceColor = colorResource(R.color.dark_colorPrimary),
        textColor = colorResource(R.color.dark_textDefault),
        secondaryTextColor = colorResource(R.color.dark_textSecond),
        accentColor = colorResource(R.color.dark_colorAccent),
        chipColor = colorResource(R.color.dark_release_day_btn),
    )
}

internal fun requestWatchingFocus(requester: FocusRequester?): Boolean {
    if (requester == null) {
        return false
    }
    return runCatching {
        requester.requestFocus()
        true
    }.getOrDefault(false)
}

internal suspend fun requestWatchingFocusAfterAttach(
    requester: FocusRequester?,
    attempts: Int = 8,
): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        if (requestWatchingFocus(requester)) {
            return true
        }
    }
    return false
}

internal enum class TvCenterPressAction {
    Ignore,
    Consume,
    Click,
}

internal fun resolveTvCenterPressAction(
    canFocus: Boolean,
    enabled: Boolean,
    key: Key,
    eventType: KeyEventType,
): TvCenterPressAction {
    if (!canFocus || !enabled) {
        return TvCenterPressAction.Ignore
    }
    if (key != Key.DirectionCenter && key != Key.Enter && key != Key.NumPadEnter) {
        return TvCenterPressAction.Ignore
    }
    return when (eventType) {
        // Trigger TV actions on key-up so opening an overlay does not leak the same press
        // into its first focused action and instantly close/select it.
        KeyEventType.KeyDown -> TvCenterPressAction.Consume
        KeyEventType.KeyUp -> TvCenterPressAction.Click
        else -> TvCenterPressAction.Ignore
    }
}

internal suspend fun LazyListState.scrollItemIntoViewIfNeeded(index: Int) {
    scrollItemIntoViewIfNeeded(index = index, anchorIndex = (index - 1).coerceAtLeast(0))
}

internal suspend fun LazyListState.scrollItemIntoViewIfNeeded(
    index: Int,
    anchorIndex: Int = (index - 1).coerceAtLeast(0),
    bottomClearancePx: Int = 0,
) {
    if (index < 0) return

    fun currentTarget() = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    suspend fun adjustVisibleTarget(): Boolean {
        val target = currentTarget() ?: return false
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = (layoutInfo.viewportEndOffset - bottomClearancePx).coerceAtLeast(viewportStart)
        val itemStart = target.offset
        val itemEnd = target.offset + target.size
        val delta =
            when {
                itemStart < viewportStart -> itemStart - viewportStart
                itemEnd > viewportEnd -> itemEnd - viewportEnd
                else -> 0
            }
        if (delta != 0) {
            scrollBy(delta.toFloat())
            return true
        }
        return false
    }

    val visibleItems = layoutInfo.visibleItemsInfo
    if (currentTarget() == null) {
        val targetIndex =
            when {
                visibleItems.isEmpty() -> index
                index < visibleItems.first().index -> index
                else -> anchorIndex.coerceAtLeast(0)
            }
        scrollToItem(targetIndex)
        withFrameNanos { }
    }

    repeat(2) {
        if (!adjustVisibleTarget()) {
            return
        }
        withFrameNanos { }
    }
}

@Composable
internal fun WatchingFilterChip(
    text: String,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 92.dp,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    val colors =
        if (emphasized) {
            TvUiDefaults.accentActionColors(palette)
        } else {
            TvUiDefaults.chipActionColors(palette)
        }
    TvTextActionButton(
        text = text,
        palette = palette,
        focusRequester = focusRequester,
        onClick = onClick,
        modifier = modifier.widthIn(max = 160.dp),
        minWidth = minWidth,
        enabled = enabled,
        colors = colors,
        paddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onRight = onRight,
        onDown = onDown,
    )
}

@Composable
internal fun WatchingPosterCard(
    imageUrl: String,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cardWidth: Dp = TvPosterCardWidth,
    contentAspectRatio: Float = WATCHING_CARD_ASPECT_RATIO,
    focusStyle: TvPosterCardFocusStyle = TvUiDefaults.defaultPosterCardFocusStyle(palette),
    scaleTransformOrigin: TransformOrigin = TransformOrigin.Center,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = Color.Transparent,
        focusedBackgroundColor = focusStyle.focusedBackgroundColor,
        borderColor = focusStyle.borderColor,
        focusedBorderWidth = focusStyle.focusedBorderWidth,
        unfocusedBorderWidth = focusStyle.unfocusedBorderWidth,
        focusedScale = focusStyle.focusedScale,
        scaleTransformOrigin = scaleTransformOrigin,
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onDown = onDown,
        modifier = modifier.width(cardWidth),
        paddingValues = PaddingValues(8.dp),
        focusedShadowElevation = 0.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(contentAspectRatio)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.18f)),
        ) {
            TvAsyncImage(
                imageUrl = imageUrl,
                modifier = Modifier.fillMaxSize(),
                options = TvAsyncImageOptions(contentScale = ContentScale.Crop),
            )
        }
    }
}

@Composable
internal fun WatchingMessageCard(
    title: String,
    subtitle: String,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = palette.surfaceColor.copy(alpha = 0.9f),
        focusedBackgroundColor = palette.surfaceColor,
        borderColor = palette.accentColor,
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onDown = onDown,
        modifier = modifier.widthIn(min = 280.dp),
        paddingValues = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                    color = palette.textColor,
                    trackColor = palette.textColor.copy(alpha = 0.16f),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_alert_circle_outline),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = title,
                color = palette.textColor,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = palette.secondaryTextColor,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
internal fun WatchingWideMessageCard(
    title: String,
    subtitle: String,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        WatchingMessageCard(
            title = title,
            subtitle = subtitle,
            palette = palette,
            focusRequester = focusRequester,
            onClick = onClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
            enabled = enabled,
            loading = loading,
            onFocused = onFocused,
            onLeft = onLeft,
            onUp = onUp,
            onDown = onDown,
        )
    }
}

@Composable
internal fun WatchingDescriptionBar(
    title: String,
    subtitle: String,
    palette: WatchingPalette,
    contentPadding: PaddingValues = TvDescriptionBarPadding,
    solidSurface: Boolean = false,
    solidMinHeight: Dp = TvSolidDescriptionBarMinHeight,
    solidHeight: Dp? = null,
    solidInnerPadding: PaddingValues = TvSolidDescriptionBarInnerPadding,
    showBackdropScrim: Boolean = !solidSurface,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateLeftPadding(layoutDirection)
    val endPadding = contentPadding.calculateRightPadding(layoutDirection)
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = contentPadding.calculateBottomPadding()
    val solidStartPadding = solidInnerPadding.calculateLeftPadding(layoutDirection)
    val solidEndPadding = solidInnerPadding.calculateRightPadding(layoutDirection)
    val solidTopPadding = solidInnerPadding.calculateTopPadding()
    val solidBottomPadding = solidInnerPadding.calculateBottomPadding()
    val solidContainerVerticalOffset =
        if (solidSurface) {
            TvSolidDescriptionBarVerticalOffset
        } else {
            0.dp
        }
    val solidContentVerticalOffset =
        if (solidSurface) {
            TvSolidDescriptionBarContentVerticalOffset
        } else {
            0.dp
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .offset(y = solidContainerVerticalOffset)
                .then(
                    if (showBackdropScrim) {
                        Modifier.background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            if (solidSurface) {
                                                Color.Black.copy(alpha = 0.20f)
                                            } else {
                                                Color.Transparent
                                            },
                                            if (solidSurface) {
                                                Color.Black.copy(alpha = 0.96f)
                                            } else {
                                                Color.Black.copy(alpha = 0.88f)
                                            },
                                        ),
                                ),
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(top = topPadding, bottom = bottomPadding),
    ) {
        if (solidSurface) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(TvUiDefaults.ScreenPanelShape)
                        .background(palette.backgroundColor.copy(alpha = 0.94f))
                        .border(
                            width = 1.dp,
                            color = palette.textColor.copy(alpha = 0.10f),
                            shape = TvUiDefaults.ScreenPanelShape,
                        )
                        .then(
                            if (solidHeight != null) {
                                Modifier.height(solidHeight)
                            } else {
                                Modifier.heightIn(min = solidMinHeight)
                            },
                        ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = startPadding + solidStartPadding,
                                end = endPadding + solidEndPadding,
                                top = solidTopPadding,
                                bottom = solidBottomPadding,
                            )
                            .offset(y = solidContentVerticalOffset),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        color = palette.textColor,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = palette.secondaryTextColor,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = startPadding, end = endPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = palette.textColor,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = palette.secondaryTextColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun WatchingFocusableSurface(
    focusRequester: FocusRequester,
    backgroundColor: Color,
    focusedBackgroundColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowFocusWhenDisabled: Boolean = false,
    shape: Shape = TvUiDefaults.FocusableSurfaceShape,
    focusedBorderWidth: Dp = TvUiDefaults.FOCUSED_BORDER_WIDTH,
    unfocusedBorderWidth: Dp = TvUiDefaults.UNFOCUSED_BORDER_WIDTH,
    unfocusedBorderColor: Color = Color.Transparent,
    focusedScale: Float = TvUiDefaults.FOCUSED_SCALE,
    focusedShadowElevation: Dp = TvUiDefaults.FOCUSED_SHADOW_ELEVATION,
    scaleTransformOrigin: TransformOrigin = TransformOrigin.Center,
    selected: Boolean = false,
    selectedBorderColor: Color = borderColor,
    selectedBorderWidth: Dp = focusedBorderWidth,
    onFocused: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    paddingValues: PaddingValues,
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val canFocus = enabled || allowFocusWhenDisabled
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        label = "watchingFocusableScale",
    )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                    transformOrigin = scaleTransformOrigin
                    shadowElevation =
                        if (isFocused) {
                            focusedShadowElevation.toPx()
                        } else {
                            0f
                        }
                    this.shape = shape
                    this.clip = false
                }
                .clip(shape)
                .background(if (isFocused) focusedBackgroundColor else backgroundColor)
                .border(
                    width =
                        when {
                            isFocused -> focusedBorderWidth
                            selected -> selectedBorderWidth
                            else -> unfocusedBorderWidth
                        },
                    color =
                        when {
                            isFocused -> borderColor
                            selected -> selectedBorderColor
                            else -> unfocusedBorderColor
                        },
                    shape = shape,
                )
                .then(
                    if (canFocus) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                )
                .onFocusChanged {
                    val nowFocused = it.isFocused
                    isFocused = nowFocused
                    onFocusChanged?.invoke(nowFocused)
                    if (nowFocused) {
                        onFocused?.invoke()
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (!canFocus) {
                        return@onPreviewKeyEvent false
                    }
                    when (resolveTvCenterPressAction(canFocus, enabled, event.key, event.type)) {
                        TvCenterPressAction.Consume -> true
                        TvCenterPressAction.Click -> {
                            onClick()
                            true
                        }
                        TvCenterPressAction.Ignore ->
                            when (event.key) {
                                Key.DirectionLeft -> event.type == KeyEventType.KeyDown && onLeft?.invoke() == true
                                Key.DirectionUp -> event.type == KeyEventType.KeyDown && onUp?.invoke() == true
                                Key.DirectionRight -> event.type == KeyEventType.KeyDown && onRight?.invoke() == true
                                Key.DirectionDown -> event.type == KeyEventType.KeyDown && onDown?.invoke() == true
                                else -> false
                            }
                    }
                }
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .then(if (canFocus) Modifier.focusable() else Modifier)
                .padding(paddingValues),
    ) {
        content()
    }
}

internal fun edgeAwareHorizontalTransformOrigin(
    index: Int,
    lastIndex: Int,
): TransformOrigin {
    return when (index) {
        0 -> TransformOrigin(0f, 0.5f)
        lastIndex -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin.Center
    }
}

internal fun edgeAwareGridTransformOrigin(
    index: Int,
    columnsCount: Int,
    itemsCount: Int,
): TransformOrigin {
    if (columnsCount <= 0 || itemsCount <= 0) {
        return TransformOrigin.Center
    }
    val columnIndex = index % columnsCount
    val x =
        when {
            columnIndex == 0 -> 0f
            index == itemsCount - 1 || columnIndex == columnsCount - 1 -> 1f
            else -> 0.5f
        }
    // Keep grid cards vertically anchored from the top edge so horizontal focus
    // moves don't make lower rows appear to hop up/down as scale is applied.
    val y = 0f
    return TransformOrigin(x, y)
}
