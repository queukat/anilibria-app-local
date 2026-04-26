package ru.radiationx.anilibria.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.screen.watching.WatchingPalette

@Composable
internal fun PlayerProgressDisplay(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onInteraction: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val progressFraction =
        if (safeDuration > 0L) {
            currentPositionMs.coerceIn(0L, safeDuration).toFloat() / safeDuration.toFloat()
        } else {
            0f
        }
    val bufferedFraction =
        if (safeDuration > 0L) {
            bufferedPositionMs.coerceIn(0L, safeDuration).toFloat() / safeDuration.toFloat()
        } else {
            0f
        }

    val progressStyle = PlayerOverlayUiDefaults.progressDisplayStyle()

    PlayerControlSurface(
        focusRequester = focusRequester,
        backgroundColor = progressStyle.backgroundColor,
        focusedBackgroundColor = progressStyle.backgroundColor,
        borderColor = palette.accentColor,
        onClick = {
            onInteraction()
            onTogglePlayback()
        },
        shape = progressStyle.shape,
        onFocused = {
            onInteraction()
            onControlFocused(PlayerOverlayFocusTarget.Progress)
        },
        onLeft = {
            onInteraction()
            onSeekBack()
            true
        },
        onUp = { true },
        onRight = {
            onInteraction()
            onSeekForward()
            true
        },
        onDown = {
            onInteraction()
            onDown()
        },
        modifier = modifier,
        paddingValues = PlayerOverlayUiDefaults.ProgressSurfacePadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.ProgressContentSpacing),
        ) {
            Text(
                text = currentPositionMs.toPlaybackTime(),
                color = palette.textColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                modifier = Modifier.width(PlayerOverlayUiDefaults.ProgressTimeWidth),
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(PlayerOverlayUiDefaults.ProgressTrackHeight)
                        .clip(PlayerOverlayUiDefaults.ProgressBarShape)
                        .background(PlayerOverlayUiDefaults.progressTrackColor()),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                            .height(PlayerOverlayUiDefaults.ProgressBufferedTrackHeight)
                            .clip(PlayerOverlayUiDefaults.ProgressBarShape)
                            .background(PlayerOverlayUiDefaults.progressBufferedTrackColor()),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                            .height(PlayerOverlayUiDefaults.ProgressTrackHeight)
                            .clip(PlayerOverlayUiDefaults.ProgressBarShape)
                            .background(PlayerOverlayUiDefaults.progressFillBrush(palette)),
                )
            }
            Text(
                text = safeDuration.toPlaybackTime(),
                color = palette.textColor.copy(alpha = 0.84f),
                fontSize = 16.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.width(PlayerOverlayUiDefaults.ProgressTimeWidth),
            )
        }
    }
}

private fun Long.toPlaybackTime(): String {
    val safeSeconds = (this / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val hours = safeSeconds / SECONDS_PER_HOUR
    val minutes = (safeSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = safeSeconds % SECONDS_PER_MINUTE
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
