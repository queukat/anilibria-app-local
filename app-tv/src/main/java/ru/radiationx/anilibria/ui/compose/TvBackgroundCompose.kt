package ru.radiationx.anilibria.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ru.radiationx.anilibria.common.GradientBackgroundManager

internal data class TvAppBackgroundState(
    val enabled: Boolean = false,
    val baseColor: Color = Color.Transparent,
    val foregroundColor: Color = Color.Transparent,
    val foregroundAlpha: Float = 0f,
)

internal val LocalTvAppBackgroundState = staticCompositionLocalOf {
    TvAppBackgroundState()
}

@Composable
internal fun ProvideGradientBackground(
    manager: GradientBackgroundManager,
    content: @Composable () -> Unit,
) {
    val targetState by manager.backgroundState.collectAsState()
    val baseColor by animateColorAsState(
        targetValue = Color(targetState.baseColor),
        label = "tvBackgroundBaseColor",
    )
    val foregroundAlpha by animateFloatAsState(
        targetValue = if (targetState.foregroundVisible) 1f else 0f,
        label = "tvBackgroundForegroundAlpha",
    )

    CompositionLocalProvider(
        LocalTvAppBackgroundState provides TvAppBackgroundState(
            enabled = true,
            baseColor = baseColor,
            foregroundColor = Color(targetState.foregroundColor),
            foregroundAlpha = foregroundAlpha,
        ),
        content = content,
    )
}
