package ru.radiationx.anilibria.screen.watching

import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R
import ru.radiationx.shared_app.imageloader.showImageUrl

private const val WATCHING_CARD_ASPECT_RATIO = 130f / 185f
private const val WATCHING_DIALOG_WIDTH_FRACTION = 0.56f

internal data class WatchingPalette(
    val surfaceColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val accentColor: Color,
    val chipColor: Color,
)

internal data class WatchingChoiceDialogUiState(
    val title: String,
    val options: List<String>,
    val selectedIndex: Int,
)

@Composable
internal fun rememberWatchingPalette(): WatchingPalette {
    return WatchingPalette(
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
    minWidth: Dp = 132.dp,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = if (emphasized) {
            palette.accentColor.copy(alpha = 0.18f)
        } else {
            palette.chipColor.copy(alpha = 0.92f)
        },
        focusedBackgroundColor = if (emphasized) {
            palette.accentColor.copy(alpha = 0.26f)
        } else {
            palette.chipColor
        },
        borderColor = if (emphasized) {
            palette.accentColor.copy(alpha = 0.84f)
        } else {
            palette.textColor.copy(alpha = 0.72f)
        },
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onRight = onRight,
        onDown = onDown,
        modifier = modifier.widthIn(min = minWidth),
        paddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = palette.textColor,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun WatchingPosterCard(
    imageUrl: String,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cardWidth: Dp = 152.dp,
    contentAspectRatio: Float = WATCHING_CARD_ASPECT_RATIO,
    focusedBackgroundColor: Color = palette.accentColor.copy(alpha = 0.12f),
    focusedBorderColor: Color = palette.accentColor.copy(alpha = 0.92f),
    focusedBorderWidth: Dp = 2.dp,
    unfocusedBorderWidth: Dp = 1.dp,
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
            AndroidView(
                factory = { context ->
                    AppCompatImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.showImageUrl(imageUrl)
                },
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
            Image(
                painter = painterResource(R.drawable.ic_alert_circle_outline),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Fit,
            )
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
internal fun WatchingDescriptionBar(
    title: String,
    subtitle: String,
    palette: WatchingPalette,
    contentPadding: PaddingValues = TvDescriptionBarPadding,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.88f),
                    )
                )
            )
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
internal fun WatchingChoiceDialog(
    state: WatchingChoiceDialogUiState,
    palette: WatchingPalette,
    focusRequestToken: Int,
    onOptionClick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val optionIds = remember(state.options) { state.options.indices.toList() }
    val optionRequesters = remember(optionIds) { List(optionIds.size) { FocusRequester() } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
        val targetIndex = state.selectedIndex.coerceIn(0, optionIds.lastIndex)
        listState.scrollToItem(targetIndex)
        withFrameNanos { }
        requestWatchingFocus(optionRequesters.getOrNull(targetIndex))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onDismiss()
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(WATCHING_DIALOG_WIDTH_FRACTION)
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surfaceColor.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    color = palette.textColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(28.dp),
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = state.title,
                            color = palette.textColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Выберите значение фильтра",
                            color = palette.secondaryTextColor,
                            fontSize = 14.sp,
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.ic_anilibria_splash),
                        contentDescription = null,
                        modifier = Modifier.width(20.dp),
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items = state.options,
                        key = { index, option -> option.hashCode() * 31 + index },
                    ) { index, option ->
                        WatchingFocusableSurface(
                            focusRequester = optionRequesters[index],
                            backgroundColor = if (index == state.selectedIndex) {
                                palette.accentColor.copy(alpha = 0.18f)
                            } else {
                                palette.chipColor.copy(alpha = 0.74f)
                            },
                            focusedBackgroundColor = if (index == state.selectedIndex) {
                                palette.accentColor.copy(alpha = 0.26f)
                            } else {
                                palette.chipColor
                            },
                            borderColor = if (index == state.selectedIndex) {
                                palette.accentColor.copy(alpha = 0.82f)
                            } else {
                                palette.textColor.copy(alpha = 0.12f)
                            },
                            onClick = { onOptionClick(index) },
                            onLeft = {
                                onDismiss()
                                true
                            },
                            onRight = {
                                onDismiss()
                                true
                            },
                            onUp = if (index > 0) {
                                { requestOptionFocus(index - 1) }
                            } else {
                                { true }
                            },
                            onDown = if (index < state.options.lastIndex) {
                                { requestOptionFocus(index + 1) }
                            } else {
                                { true }
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
                                    fontWeight = if (index == state.selectedIndex) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (index == state.selectedIndex) {
                                                palette.accentColor
                                            } else {
                                                Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (index == state.selectedIndex) {
                                                palette.accentColor
                                            } else {
                                                palette.textColor.copy(alpha = 0.22f)
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                        )
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Назад или влево: закрыть",
                    color = palette.secondaryTextColor,
                    fontSize = 13.sp,
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
    focusedBorderWidth: Dp = 2.dp,
    unfocusedBorderWidth: Dp = 1.dp,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    paddingValues: PaddingValues,
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (isFocused) focusedBackgroundColor else backgroundColor)
            .border(
                width = if (isFocused) focusedBorderWidth else unfocusedBorderWidth,
                color = if (isFocused) borderColor else Color.Transparent,
                shape = RoundedCornerShape(22.dp),
            )
            .then(
                if (enabled) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                val nowFocused = it.isFocused
                isFocused = nowFocused
                if (nowFocused) {
                    onFocused?.invoke()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
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
            .then(if (enabled) Modifier.focusable() else Modifier)
            .padding(paddingValues),
    ) {
        content()
    }
}
