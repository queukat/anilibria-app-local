package ru.radiationx.anilibria.screen.watching

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import androidx.compose.animation.core.animateFloatAsState
import ru.radiationx.anilibria.ui.compose.TvAsyncImage
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

internal suspend fun LazyListState.scrollItemIntoViewIfNeeded(index: Int) {
    if (index < 0) return
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.none { it.index == index }) {
        val targetIndex = when {
            visibleItems.isEmpty() -> index
            index < visibleItems.first().index -> index
            else -> (index - 1).coerceAtLeast(0)
        }
        scrollToItem(targetIndex)
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
    val colors = if (emphasized) {
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
    focusedBackgroundColor: Color = palette.accentColor.copy(alpha = 0.12f),
    focusedBorderColor: Color = palette.accentColor.copy(alpha = 0.92f),
    focusedBorderWidth: Dp = 2.dp,
    unfocusedBorderWidth: Dp = 1.dp,
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
        focusedBackgroundColor = focusedBackgroundColor,
        borderColor = focusedBorderColor,
        focusedBorderWidth = focusedBorderWidth,
        unfocusedBorderWidth = unfocusedBorderWidth,
        scaleTransformOrigin = scaleTransformOrigin,
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onDown = onDown,
        modifier = modifier.width(cardWidth),
        paddingValues = PaddingValues(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(contentAspectRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.18f)),
        ) {
            TvAsyncImage(
                imageUrl = imageUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
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
            modifier = Modifier
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
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
                    )
                )
            )
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (solidSurface) {
                        Modifier
                            .clip(TvUiDefaults.ScreenPanelShape)
                            .background(palette.backgroundColor.copy(alpha = 0.94f))
                            .border(
                                width = 1.dp,
                                color = palette.textColor.copy(alpha = 0.10f),
                                shape = TvUiDefaults.ScreenPanelShape,
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                color = palette.textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = palette.secondaryTextColor,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = 2,
                )
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
    focusedBorderWidth: Dp = TvUiDefaults.FocusedBorderWidth,
    unfocusedBorderWidth: Dp = TvUiDefaults.UnfocusedBorderWidth,
    unfocusedBorderColor: Color = Color.Transparent,
    focusedScale: Float = TvUiDefaults.FocusedScale,
    focusedShadowElevation: Dp = TvUiDefaults.FocusedShadowElevation,
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
        modifier = modifier
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
                transformOrigin = scaleTransformOrigin
                shadowElevation = if (isFocused) {
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
                width = when {
                    isFocused -> focusedBorderWidth
                    selected -> selectedBorderWidth
                    else -> unfocusedBorderWidth
                },
                color = when {
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
                }
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
                if (!canFocus || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        if (enabled) {
                            onClick()
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> onLeft?.invoke() == true
                    Key.DirectionUp -> onUp?.invoke() == true
                    Key.DirectionRight -> onRight?.invoke() == true
                    Key.DirectionDown -> onDown?.invoke() == true
                    else -> false
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
                }
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
