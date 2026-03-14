package ru.radiationx.anilibria.screen.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach

internal enum class PlayerOverlayFocusTarget {
    Root,
    Progress,
    Previous,
    PlayPause,
    Next,
    Quality,
    Speed,
    Episodes,
}

@Composable
internal fun PlayerScreenContent(
    player: Player?,
    title: String,
    subtitle: String,
    controlsVisible: Boolean,
    controlsFocusTarget: PlayerOverlayFocusTarget,
    controlsFocusToken: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    qualityLabel: String,
    speedLabel: String,
    canPrevious: Boolean,
    canNext: Boolean,
    skipsPart: PlayerSkipsPart?,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onShowControls: (PlayerOverlayFocusTarget) -> Unit,
    onAutoHideControls: () -> Unit,
    onBackRequested: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onEpisodesClick: () -> Unit,
) {
    val palette = rememberWatchingPalette()
    val skipVisible = skipsPart?.isVisible == true
    var autoHideToken by remember { mutableIntStateOf(0) }

    fun registerInteraction() {
        autoHideToken += 1
    }

    val rootRequester = remember { FocusRequester() }
    val progressRequester = remember { FocusRequester() }
    val previousRequester = remember { FocusRequester() }
    val playPauseRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val qualityRequester = remember { FocusRequester() }
    val speedRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester = when (target) {
        PlayerOverlayFocusTarget.Root -> rootRequester
        PlayerOverlayFocusTarget.Progress -> progressRequester
        PlayerOverlayFocusTarget.Previous -> previousRequester
        PlayerOverlayFocusTarget.PlayPause -> playPauseRequester
        PlayerOverlayFocusTarget.Next -> nextRequester
        PlayerOverlayFocusTarget.Quality -> qualityRequester
        PlayerOverlayFocusTarget.Speed -> speedRequester
        PlayerOverlayFocusTarget.Episodes -> episodesRequester
    }

    LaunchedEffect(controlsVisible, controlsFocusToken, controlsFocusTarget) {
        if (!controlsVisible) {
            return@LaunchedEffect
        }
        requestWatchingFocusAfterAttach(requesterFor(controlsFocusTarget))
    }

    LaunchedEffect(controlsVisible, skipVisible) {
        if (controlsVisible || skipVisible) {
            return@LaunchedEffect
        }
        requestWatchingFocusAfterAttach(rootRequester)
    }

    LaunchedEffect(
        controlsVisible,
        autoHideToken,
        isPlaying,
        isLoading,
        isBuffering,
        skipVisible,
    ) {
        if (!controlsVisible || !isPlaying || isLoading || isBuffering || skipVisible) {
            return@LaunchedEffect
        }
        delay(CONTROLS_AUTO_HIDE_DELAY_MS)
        onAutoHideControls()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Back,
                    Key.Escape -> {
                        onBackRequested()
                        true
                    }

                    Key.MediaPlayPause -> {
                        registerInteraction()
                        onShowControls(PlayerOverlayFocusTarget.PlayPause)
                        onTogglePlayback()
                        true
                    }

                    Key.MediaPlay -> {
                        if (!isPlaying) {
                            registerInteraction()
                            onShowControls(PlayerOverlayFocusTarget.PlayPause)
                            onTogglePlayback()
                            true
                        } else {
                            false
                        }
                    }

                    Key.MediaPause -> {
                        if (isPlaying) {
                            registerInteraction()
                            onShowControls(PlayerOverlayFocusTarget.PlayPause)
                            onTogglePlayback()
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        if (!controlsVisible) {
                            registerInteraction()
                            onShowControls(PlayerOverlayFocusTarget.PlayPause)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp,
                    Key.DirectionDown -> {
                        if (!controlsVisible) {
                            registerInteraction()
                            onShowControls(PlayerOverlayFocusTarget.PlayPause)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionLeft -> {
                        if (!controlsVisible) {
                            registerInteraction()
                            onSeekBack()
                            onShowControls(PlayerOverlayFocusTarget.Progress)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (!controlsVisible) {
                            registerInteraction()
                            onSeekForward()
                            onShowControls(PlayerOverlayFocusTarget.Progress)
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            update = { view ->
                view.player = player
            },
        )

        AnimatedVisibility(
            visible = controlsVisible || isLoading || isBuffering,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            WatchingDescriptionBar(
                title = title,
                subtitle = subtitle,
                palette = palette,
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 30.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControlsPanel(
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                qualityLabel = qualityLabel,
                speedLabel = speedLabel,
                canPrevious = canPrevious,
                canNext = canNext,
                palette = palette,
                progressRequester = progressRequester,
                previousRequester = previousRequester,
                playPauseRequester = playPauseRequester,
                nextRequester = nextRequester,
                qualityRequester = qualityRequester,
                speedRequester = speedRequester,
                episodesRequester = episodesRequester,
                onControlFocused = onControlFocused,
                onInteraction = ::registerInteraction,
                onTogglePlayback = onTogglePlayback,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onQualityClick = onQualityClick,
                onSpeedClick = onSpeedClick,
                onEpisodesClick = onEpisodesClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1320.dp),
            )
        }

        PlayerSkipsOverlay(
            skipsPart = skipsPart,
            onInteraction = ::registerInteraction,
            modifier = Modifier.align(Alignment.BottomEnd),
            bottomPadding = if (controlsVisible) 248.dp else 52.dp,
        )

        if (isLoading || isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .border(
                        width = 1.dp,
                        color = palette.textColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(22.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(
                        color = palette.textColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.player_loading),
                        color = palette.textColor,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsPanel(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    qualityLabel: String,
    speedLabel: String,
    canPrevious: Boolean,
    canNext: Boolean,
    palette: WatchingPalette,
    progressRequester: FocusRequester,
    previousRequester: FocusRequester,
    playPauseRequester: FocusRequester,
    nextRequester: FocusRequester,
    qualityRequester: FocusRequester,
    speedRequester: FocusRequester,
    episodesRequester: FocusRequester,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onInteraction: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.74f))
            .border(
                width = 1.dp,
                color = palette.textColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(28.dp),
            ),
    ) {
        PlayerProgressSurface(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            palette = palette,
            focusRequester = progressRequester,
            onFocused = {
                onInteraction()
                onControlFocused(PlayerOverlayFocusTarget.Progress)
            },
            onSeekBack = {
                onInteraction()
                onSeekBack()
            },
            onSeekForward = {
                onInteraction()
                onSeekForward()
            },
            onDown = {
                onInteraction()
                requestFocus(playPauseRequester)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 22.dp),
        )

        Column(
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerActionButton(
                    text = stringResource(R.string.player_action_previous),
                    focusRequester = previousRequester,
                    palette = palette,
                    enabled = canPrevious,
                    onFocused = {
                        onInteraction()
                        onControlFocused(PlayerOverlayFocusTarget.Previous)
                    },
                    onClick = {
                        onInteraction()
                        onPreviousClick()
                    },
                    onLeft = { true },
                    onRight = {
                        onInteraction()
                        requestFocus(playPauseRequester)
                    },
                    onUp = {
                        onInteraction()
                        requestFocus(progressRequester)
                    },
                    onDown = {
                        onInteraction()
                        requestFocus(qualityRequester)
                    },
                )
                PlayerActionButton(
                    text = stringResource(
                        if (isPlaying) {
                            R.string.player_action_pause
                        } else {
                            R.string.player_action_play
                        }
                    ),
                    focusRequester = playPauseRequester,
                    palette = palette,
                    emphasized = true,
                    onFocused = {
                        onInteraction()
                        onControlFocused(PlayerOverlayFocusTarget.PlayPause)
                    },
                    onClick = {
                        onInteraction()
                        onTogglePlayback()
                    },
                    onLeft = {
                        onInteraction()
                        if (canPrevious) {
                            requestFocus(previousRequester)
                        } else {
                            true
                        }
                    },
                    onRight = {
                        onInteraction()
                        if (canNext) {
                            requestFocus(nextRequester)
                        } else {
                            true
                        }
                    },
                    onUp = {
                        onInteraction()
                        requestFocus(progressRequester)
                    },
                    onDown = {
                        onInteraction()
                        requestFocus(speedRequester)
                    },
                )
                PlayerActionButton(
                    text = stringResource(R.string.player_action_next),
                    focusRequester = nextRequester,
                    palette = palette,
                    enabled = canNext,
                    onFocused = {
                        onInteraction()
                        onControlFocused(PlayerOverlayFocusTarget.Next)
                    },
                    onClick = {
                        onInteraction()
                        onNextClick()
                    },
                    onLeft = {
                        onInteraction()
                        requestFocus(playPauseRequester)
                    },
                    onRight = { true },
                    onUp = {
                        onInteraction()
                        requestFocus(progressRequester)
                    },
                    onDown = {
                        onInteraction()
                        requestFocus(episodesRequester)
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlayerActionButton(
                    text = "${stringResource(R.string.player_action_quality)} $qualityLabel",
                    focusRequester = qualityRequester,
                    palette = palette,
                    onFocused = {
                        onInteraction()
                        onControlFocused(PlayerOverlayFocusTarget.Quality)
                    },
                    onClick = {
                        onInteraction()
                        onQualityClick()
                    },
                    onLeft = { true },
                    onRight = {
                        onInteraction()
                        requestFocus(speedRequester)
                    },
                    onUp = {
                        onInteraction()
                        if (canPrevious) {
                            requestFocus(previousRequester)
                        } else {
                            requestFocus(playPauseRequester)
                        }
                    },
                    onDown = { true },
                )
                PlayerActionButton(
                    text = "${stringResource(R.string.player_action_speed)} $speedLabel",
                    focusRequester = speedRequester,
                    palette = palette,
                    onFocused = {
                        onInteraction()
                        onControlFocused(PlayerOverlayFocusTarget.Speed)
                    },
                    onClick = {
                        onInteraction()
                        onSpeedClick()
                    },
                    onLeft = {
                        onInteraction()
                        requestFocus(qualityRequester)
                    },
                    onRight = {
                        onInteraction()
                        requestFocus(episodesRequester)
                    },
                    onUp = {
                        onInteraction()
                        requestFocus(playPauseRequester)
                    },
                    onDown = { true },
                )
                PlayerActionButton(
                    text = stringResource(R.string.player_action_episodes),
                    focusRequester = episodesRequester,
                    palette = palette,
                    onFocused = {
                        onInteraction()
                        onControlFocused(PlayerOverlayFocusTarget.Episodes)
                    },
                    onClick = {
                        onInteraction()
                        onEpisodesClick()
                    },
                    onLeft = {
                        onInteraction()
                        requestFocus(speedRequester)
                    },
                    onRight = { true },
                    onUp = {
                        onInteraction()
                        if (canNext) {
                            requestFocus(nextRequester)
                        } else {
                            requestFocus(playPauseRequester)
                        }
                    },
                    onDown = { true },
                )
            }

            Text(
                text = stringResource(R.string.player_controls_hint),
                color = palette.secondaryTextColor.copy(alpha = 0.92f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PlayerProgressSurface(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val progressFraction = if (safeDuration > 0L) {
        currentPositionMs.coerceIn(0L, safeDuration).toFloat() / safeDuration.toFloat()
    } else {
        0f
    }
    val bufferedFraction = if (safeDuration > 0L) {
        bufferedPositionMs.coerceIn(0L, safeDuration).toFloat() / safeDuration.toFloat()
    } else {
        0f
    }

    WatchingFocusableSurface(
        focusRequester = focusRequester,
        backgroundColor = palette.surfaceColor.copy(alpha = 0.88f),
        focusedBackgroundColor = palette.surfaceColor,
        borderColor = palette.accentColor.copy(alpha = 0.92f),
        onClick = {},
        onFocused = onFocused,
        onLeft = {
            onSeekBack()
            true
        },
        onRight = {
            onSeekForward()
            true
        },
        onDown = onDown,
        modifier = modifier,
        paddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = currentPositionMs.toPlaybackTime(),
                    color = palette.textColor,
                    fontSize = 16.sp,
                )
                Text(
                    text = safeDuration.toPlaybackTime(),
                    color = palette.secondaryTextColor,
                    fontSize = 15.sp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .height(10.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    palette.accentColor.copy(alpha = 0.82f),
                                    palette.textColor.copy(alpha = 0.94f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun PlayerActionButton(
    text: String,
    focusRequester: FocusRequester,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = if (emphasized) {
            palette.accentColor.copy(alpha = 0.18f)
        } else {
            palette.chipColor.copy(alpha = 0.86f)
        },
        focusedBackgroundColor = if (emphasized) {
            palette.accentColor.copy(alpha = 0.30f)
        } else {
            palette.chipColor
        },
        borderColor = if (emphasized) {
            palette.accentColor.copy(alpha = 0.96f)
        } else {
            palette.textColor.copy(alpha = 0.74f)
        },
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onRight = onRight,
        onUp = onUp,
        onDown = onDown,
        modifier = modifier.widthIn(min = 168.dp),
        paddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) palette.textColor else palette.secondaryTextColor,
            fontSize = 15.sp,
        )
    }
}

private fun requestFocus(focusRequester: FocusRequester): Boolean {
    return runCatching {
        focusRequester.requestFocus()
        true
    }.getOrDefault(false)
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
private const val CONTROLS_AUTO_HIDE_DELAY_MS = 4_500L
