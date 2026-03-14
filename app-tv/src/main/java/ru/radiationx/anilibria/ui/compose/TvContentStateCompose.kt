package ru.radiationx.anilibria.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.focusable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingPalette

@Composable
internal fun TvContentStatePanel(
    title: String,
    subtitle: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    loading: Boolean = false,
    panelMaxWidth: Dp = 840.dp,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    val backgroundColor = if (accent) {
        palette.accentColor.copy(alpha = 0.12f)
    } else {
        palette.surfaceColor.copy(alpha = 0.92f)
    }
    val isFocusable = focusRequester != null
    var isFocused by remember(focusRequester) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = panelMaxWidth)
            .clip(shape)
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> palette.textColor.copy(alpha = 0.74f)
                    accent -> palette.accentColor.copy(alpha = 0.34f)
                    else -> palette.textColor.copy(alpha = 0.08f)
                },
                shape = shape,
            )
            .then(
                if (isFocusable) {
                    Modifier
                        .focusRequester(focusRequester!!)
                        .onFocusChanged {
                            val nowFocused = it.isFocused
                            isFocused = nowFocused
                            if (nowFocused) {
                                onFocused?.invoke()
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
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
                        .focusable()
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 28.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = palette.textColor,
                trackColor = palette.textColor.copy(alpha = 0.16f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(34.dp),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_alert_circle_outline),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                color = palette.textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = if (accent) {
                        palette.textColor.copy(alpha = 0.92f)
                    } else {
                        palette.secondaryTextColor
                    },
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
            }
            action?.let {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = it,
                )
            }
        }
    }
}

@Composable
internal fun TvContentStateActionButton(
    text: String,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = palette.chipColor.copy(alpha = 0.9f),
        focusedBackgroundColor = palette.chipColor,
        borderColor = palette.textColor.copy(alpha = 0.78f),
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onRight = onRight,
        onDown = onDown,
        modifier = modifier.widthIn(min = 188.dp),
        paddingValues = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 13.dp,
        ),
    ) {
        Text(
            text = text,
            color = if (enabled) palette.textColor else palette.secondaryTextColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
