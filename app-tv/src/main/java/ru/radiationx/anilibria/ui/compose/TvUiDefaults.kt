package ru.radiationx.anilibria.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import kotlin.math.max

internal data class TvFocusableSurfaceColors(
    val backgroundColor: Color,
    val focusedBackgroundColor: Color,
    val borderColor: Color,
)

internal data class TvPosterCardFocusStyle(
    val focusedBackgroundColor: Color,
    val borderColor: Color,
    val focusedBorderWidth: Dp = TvUiDefaults.FOCUSED_BORDER_WIDTH,
    val unfocusedBorderWidth: Dp = TvUiDefaults.UNFOCUSED_BORDER_WIDTH,
    val focusedScale: Float = TvUiDefaults.POSTER_FOCUSED_SCALE,
)

internal data class TvPanelSurfaceStyle(
    val shape: Shape,
    val backgroundColor: Color,
    val borderColor: Color,
    val borderWidth: Dp = TvUiDefaults.UNFOCUSED_BORDER_WIDTH,
)

internal object TvUiDefaults {
    val FocusableSurfaceShape = RoundedCornerShape(22.dp)
    val OverlayPanelShape = RoundedCornerShape(24.dp)
    val ContentStatePanelShape = RoundedCornerShape(28.dp)
    val ProfilePanelShape = RoundedCornerShape(32.dp)
    val ScreenPanelShape = RoundedCornerShape(26.dp)
    val ShellRailShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
    val InfoSurfaceShape = RoundedCornerShape(14.dp)
    val CircularIndicatorShape = RoundedCornerShape(percent = 50)

    val ActionButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    val CompactActionButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    val ChoiceRowPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    val OverlayPanelPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp)
    val ContentStatePanelPadding = PaddingValues(horizontal = 28.dp, vertical = 26.dp)
    val ProfilePanelPadding = PaddingValues(horizontal = 28.dp, vertical = 30.dp)
    val ScreenPanelPadding = PaddingValues(horizontal = 26.dp, vertical = 24.dp)
    val ShellHeaderPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
    val ShellRailPadding = PaddingValues(start = 24.dp, top = 30.dp, end = 56.dp, bottom = 30.dp)
    val ShellHeaderActionWidth = 172.dp

    val FOCUSED_BORDER_WIDTH = 2.dp
    val UNFOCUSED_BORDER_WIDTH = 1.dp
    val SELECTION_INDICATOR_SIZE = 12.dp
    val LARGE_SELECTION_INDICATOR_SIZE = 14.dp
    const val FOCUSED_SCALE = 1.035f
    const val POSTER_FOCUSED_SCALE = 1.02f
    val FOCUSED_SHADOW_ELEVATION = 18.dp
    const val APP_BACKGROUND_GLOW_ALPHA = 0.22f

    fun chipActionColors(
        palette: WatchingPalette,
        backgroundAlpha: Float = 0.92f,
        focusedBackgroundAlpha: Float = 1f,
        borderAlpha: Float = 0.72f,
    ): TvFocusableSurfaceColors {
        return TvFocusableSurfaceColors(
            backgroundColor = palette.chipColor.copy(alpha = backgroundAlpha),
            focusedBackgroundColor = palette.chipColor.copy(alpha = focusedBackgroundAlpha),
            borderColor = palette.textColor.copy(alpha = borderAlpha),
        )
    }

    fun accentActionColors(
        palette: WatchingPalette,
        backgroundAlpha: Float = 0.18f,
        focusedBackgroundAlpha: Float = 0.26f,
        borderAlpha: Float = 0.84f,
    ): TvFocusableSurfaceColors {
        return TvFocusableSurfaceColors(
            backgroundColor = palette.accentColor.copy(alpha = backgroundAlpha),
            focusedBackgroundColor = palette.accentColor.copy(alpha = focusedBackgroundAlpha),
            borderColor = palette.accentColor.copy(alpha = borderAlpha),
        )
    }

    fun defaultPosterCardFocusStyle(palette: WatchingPalette): TvPosterCardFocusStyle {
        return TvPosterCardFocusStyle(
            focusedBackgroundColor = Color.Transparent,
            borderColor = palette.accentColor.copy(alpha = 0.92f),
        )
    }

    fun subtlePosterCardFocusStyle(palette: WatchingPalette): TvPosterCardFocusStyle {
        return TvPosterCardFocusStyle(
            focusedBackgroundColor = Color.Transparent,
            borderColor = palette.textColor.copy(alpha = 0.58f),
        )
    }

    fun contentStatePanelStyle(
        palette: WatchingPalette,
        accent: Boolean,
        focused: Boolean,
    ): TvPanelSurfaceStyle {
        return TvPanelSurfaceStyle(
            shape = ContentStatePanelShape,
            backgroundColor =
                if (accent) {
                    palette.accentColor.copy(alpha = 0.12f)
                } else {
                    palette.surfaceColor.copy(alpha = 0.92f)
                },
            borderColor =
                when {
                    focused -> palette.textColor.copy(alpha = 0.74f)
                    accent -> palette.accentColor.copy(alpha = 0.34f)
                    else -> palette.textColor.copy(alpha = 0.08f)
                },
            borderWidth = if (focused) FOCUSED_BORDER_WIDTH else UNFOCUSED_BORDER_WIDTH,
        )
    }

    fun profilePanelStyle(palette: WatchingPalette): TvPanelSurfaceStyle {
        return TvPanelSurfaceStyle(
            shape = ProfilePanelShape,
            backgroundColor = palette.surfaceColor.copy(alpha = 0.92f),
            borderColor = palette.textColor.copy(alpha = 0.10f),
        )
    }

    fun screenPanelStyle(
        palette: WatchingPalette,
        focused: Boolean = false,
        accent: Boolean = false,
    ): TvPanelSurfaceStyle {
        return TvPanelSurfaceStyle(
            shape = ScreenPanelShape,
            backgroundColor =
                if (accent) {
                    palette.accentColor.copy(alpha = 0.12f)
                } else {
                    palette.surfaceColor.copy(alpha = 0.92f)
                },
            borderColor =
                when {
                    focused -> palette.textColor.copy(alpha = 0.74f)
                    accent -> palette.accentColor.copy(alpha = 0.32f)
                    else -> palette.textColor.copy(alpha = 0.10f)
                },
            borderWidth = if (focused) FOCUSED_BORDER_WIDTH else UNFOCUSED_BORDER_WIDTH,
        )
    }

    fun appBackgroundBrush(
        palette: WatchingPalette,
        glowAlpha: Float = APP_BACKGROUND_GLOW_ALPHA,
    ): Brush {
        return Brush.verticalGradient(
            colors =
                listOf(
                    palette.surfaceColor.copy(alpha = glowAlpha),
                    palette.backgroundColor,
                ),
        )
    }

    fun surfaceBackdropBrush(palette: WatchingPalette): Brush {
        return Brush.verticalGradient(
            colors =
                listOf(
                    palette.backgroundColor.copy(alpha = 0.98f),
                    palette.surfaceColor.copy(alpha = 0.94f),
                ),
        )
    }

    fun shellHeaderBrush(palette: WatchingPalette): Brush {
        return Brush.verticalGradient(
            colors =
                listOf(
                    palette.surfaceColor.copy(alpha = 0.98f),
                    palette.surfaceColor.copy(alpha = 0.94f),
                ),
        )
    }

    fun shellRailBrush(palette: WatchingPalette): Brush {
        return Brush.verticalGradient(
            colors =
                listOf(
                    palette.surfaceColor.copy(alpha = 0.98f),
                    palette.surfaceColor.copy(alpha = 0.95f),
                    palette.backgroundColor.copy(alpha = 0.94f),
                ),
        )
    }

    fun modalScrimColor(
        palette: WatchingPalette,
        alpha: Float = 0.74f,
    ): Color {
        return palette.backgroundColor.copy(alpha = alpha)
    }
}

internal fun Modifier.tvPanelSurface(style: TvPanelSurfaceStyle): Modifier {
    return clip(style.shape)
        .background(style.backgroundColor)
        .border(
            width = style.borderWidth,
            color = style.borderColor,
            shape = style.shape,
        )
}

internal fun Modifier.tvAppBackground(
    palette: WatchingPalette,
    glowAlpha: Float = TvUiDefaults.APP_BACKGROUND_GLOW_ALPHA,
): Modifier {
    return composed {
        val dynamicBackground = LocalTvAppBackgroundState.current
        drawWithCache {
            val overlayBrush = TvUiDefaults.appBackgroundBrush(palette, glowAlpha)
            val legacyBackdropBrush =
                Brush.linearGradient(
                    colors =
                        listOf(
                            Color.Black.copy(alpha = 0.82f),
                            Color.Black.copy(alpha = 0.18f),
                        ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width * 0.9f, size.height * 0.14f),
                )
            val dynamicGlowBrush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            lerp(dynamicBackground.baseColor, Color.White, 0.22f).copy(alpha = 0.34f),
                            dynamicBackground.baseColor.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    center = Offset(size.width * 0.28f, size.height * 0.16f),
                    radius = max(size.width, size.height) * 0.95f,
                )
            val foregroundColor =
                dynamicBackground.foregroundColor.copy(
                    alpha = dynamicBackground.foregroundAlpha,
                )
            onDrawBehind {
                if (dynamicBackground.enabled) {
                    drawRect(color = dynamicBackground.baseColor)
                    drawRect(brush = legacyBackdropBrush)
                }
                drawRect(brush = overlayBrush)
                if (dynamicBackground.enabled && dynamicBackground.foregroundAlpha < 1f) {
                    drawRect(brush = dynamicGlowBrush)
                }
                if (dynamicBackground.enabled && dynamicBackground.foregroundAlpha > 0f) {
                    drawRect(color = foregroundColor)
                }
            }
        }
    }
}

@Composable
internal fun TvTextActionButton(
    text: String,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    allowFocusWhenDisabled: Boolean = false,
    minWidth: Dp? = null,
    colors: TvFocusableSurfaceColors = TvUiDefaults.chipActionColors(palette),
    paddingValues: PaddingValues = TvUiDefaults.ActionButtonPadding,
    focusedBorderWidth: Dp = TvUiDefaults.FOCUSED_BORDER_WIDTH,
    unfocusedBorderWidth: Dp = TvUiDefaults.UNFOCUSED_BORDER_WIDTH,
    fontSize: TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.SemiBold,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    val buttonModifier =
        if (minWidth != null) {
            modifier.widthIn(min = minWidth)
        } else {
            modifier
        }
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        allowFocusWhenDisabled = allowFocusWhenDisabled,
        backgroundColor = colors.backgroundColor,
        focusedBackgroundColor = colors.focusedBackgroundColor,
        borderColor = colors.borderColor,
        focusedBorderWidth = focusedBorderWidth,
        unfocusedBorderWidth = unfocusedBorderWidth,
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onRight = onRight,
        onDown = onDown,
        modifier = buttonModifier,
        paddingValues = paddingValues,
    ) {
        Text(
            text = text,
            color = if (enabled) palette.textColor else palette.secondaryTextColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun TvSelectionIndicator(
    selected: Boolean,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    size: Dp = TvUiDefaults.SELECTION_INDICATOR_SIZE,
    inactiveBorderColor: Color = palette.textColor.copy(alpha = 0.22f),
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(TvUiDefaults.CircularIndicatorShape)
                .background(
                    color = if (selected) palette.accentColor else Color.Transparent,
                )
                .border(
                    width = 1.dp,
                    color = if (selected) palette.accentColor else inactiveBorderColor,
                    shape = TvUiDefaults.CircularIndicatorShape,
                ),
        contentAlignment = Alignment.Center,
    ) {}
}
