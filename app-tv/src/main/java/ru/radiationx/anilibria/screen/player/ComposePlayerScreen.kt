package ru.radiationx.anilibria.screen.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.TvPlayerOverlayBottomPadding
import ru.radiationx.anilibria.screen.watching.TvPlayerOverlayHorizontalPadding
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.data.entity.common.PlayerQuality

internal enum class PlayerOverlayFocusTarget {
    Root,
    Progress,
    Previous,
    SeekBack,
    PlayPause,
    SeekForward,
    Next,
    Quality,
    Speed,
    AspectRatio,
    Episodes,
}

@OptIn(UnstableApi::class)
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
    selectedQuality: PlayerQuality,
    qualityLabel: String,
    selectedSpeed: Float,
    speedLabel: String,
    aspectRatioMode: PlayerAspectRatioMode,
    availableQualities: List<PlayerQuality>,
    availableSpeeds: List<Float>,
    canPrevious: Boolean,
    canNext: Boolean,
    skipsPart: PlayerSkipsPart?,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onShowControls: (PlayerOverlayFocusTarget?) -> Unit,
    onAutoHideControls: () -> Unit,
    onBackRequested: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onQualitySelected: (PlayerQuality) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAspectRatioSelected: (PlayerAspectRatioMode) -> Unit,
    onEpisodesClick: () -> Unit,
) {
    val palette = rememberWatchingPalette()
    val skipVisible = skipsPart?.visibleSkip != null
    val qualityControlsEnabled = availableQualities.isNotEmpty()
    val speedControlsEnabled = availableSpeeds.isNotEmpty()
    val aspectRatioControlEnabled = PlayerAspectRatioMode.entries.size > 1
    var autoHideToken by remember { mutableIntStateOf(0) }
    var controlsPanelHeightPx by remember { mutableIntStateOf(0) }

    fun registerInteraction() {
        autoHideToken += 1
    }

    val rootRequester = remember { FocusRequester() }
    val progressRequester = remember { FocusRequester() }
    val seekBackRequester = remember { FocusRequester() }
    val playPauseRequester = remember { FocusRequester() }
    val seekForwardRequester = remember { FocusRequester() }
    val qualityRequester = remember { FocusRequester() }
    val speedRequester = remember { FocusRequester() }
    val aspectRatioRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester = when (target) {
        PlayerOverlayFocusTarget.Root -> rootRequester
        PlayerOverlayFocusTarget.Progress -> progressRequester
        PlayerOverlayFocusTarget.Previous -> seekBackRequester
        PlayerOverlayFocusTarget.SeekBack -> seekBackRequester
        PlayerOverlayFocusTarget.PlayPause -> playPauseRequester
        PlayerOverlayFocusTarget.SeekForward -> seekForwardRequester
        PlayerOverlayFocusTarget.Next -> speedRequester
        PlayerOverlayFocusTarget.Quality -> qualityRequester
        PlayerOverlayFocusTarget.Speed -> speedRequester
        PlayerOverlayFocusTarget.AspectRatio -> aspectRatioRequester
        PlayerOverlayFocusTarget.Episodes -> episodesRequester
    }

    fun firstAvailable(vararg targets: PlayerOverlayFocusTarget): PlayerOverlayFocusTarget {
        return targets.firstOrNull { target ->
            when (target) {
                PlayerOverlayFocusTarget.Root -> false
                PlayerOverlayFocusTarget.Progress -> true
                PlayerOverlayFocusTarget.Previous -> false
                PlayerOverlayFocusTarget.SeekBack -> true
                PlayerOverlayFocusTarget.PlayPause -> true
                PlayerOverlayFocusTarget.SeekForward -> true
                PlayerOverlayFocusTarget.Next -> false
                PlayerOverlayFocusTarget.Episodes -> true
                PlayerOverlayFocusTarget.Quality -> qualityControlsEnabled
                PlayerOverlayFocusTarget.Speed -> speedControlsEnabled
                PlayerOverlayFocusTarget.AspectRatio -> aspectRatioControlEnabled
            }
        } ?: PlayerOverlayFocusTarget.PlayPause
    }

    fun resolveFocusTarget(target: PlayerOverlayFocusTarget): PlayerOverlayFocusTarget = when (target) {
        PlayerOverlayFocusTarget.Root -> firstAvailable(PlayerOverlayFocusTarget.PlayPause)
        PlayerOverlayFocusTarget.Progress -> PlayerOverlayFocusTarget.Progress
        PlayerOverlayFocusTarget.Previous -> firstAvailable(
            PlayerOverlayFocusTarget.SeekBack,
            PlayerOverlayFocusTarget.PlayPause,
        )

        PlayerOverlayFocusTarget.SeekBack -> PlayerOverlayFocusTarget.SeekBack
        PlayerOverlayFocusTarget.PlayPause -> PlayerOverlayFocusTarget.PlayPause
        PlayerOverlayFocusTarget.SeekForward -> PlayerOverlayFocusTarget.SeekForward
        PlayerOverlayFocusTarget.Next -> firstAvailable(
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.Episodes,
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.PlayPause,
        )

        PlayerOverlayFocusTarget.Episodes -> PlayerOverlayFocusTarget.Episodes
        PlayerOverlayFocusTarget.Quality -> firstAvailable(
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.Episodes,
            PlayerOverlayFocusTarget.PlayPause,
        )

        PlayerOverlayFocusTarget.Speed -> firstAvailable(
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.Episodes,
            PlayerOverlayFocusTarget.PlayPause,
        )

        PlayerOverlayFocusTarget.AspectRatio -> firstAvailable(
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.Episodes,
            PlayerOverlayFocusTarget.PlayPause,
        )
    }

    LaunchedEffect(controlsVisible, controlsFocusToken, controlsFocusTarget) {
        if (!controlsVisible) {
            return@LaunchedEffect
        }
        requestWatchingFocusAfterAttach(requesterFor(resolveFocusTarget(controlsFocusTarget)))
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
                        if (!controlsVisible && !skipVisible) {
                            registerInteraction()
                            onShowControls(PlayerOverlayFocusTarget.PlayPause)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp,
                    Key.DirectionDown -> {
                        if (!controlsVisible && !skipVisible) {
                            registerInteraction()
                            onShowControls(PlayerOverlayFocusTarget.PlayPause)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionLeft -> {
                        if (!controlsVisible && !skipVisible) {
                            registerInteraction()
                            onSeekBack()
                            onShowControls(PlayerOverlayFocusTarget.Progress)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (!controlsVisible && !skipVisible) {
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
                view.resizeMode = aspectRatioMode.resizeMode
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
                contentPadding = PaddingValues(
                    horizontal = TvPlayerOverlayHorizontalPadding,
                    vertical = 24.dp,
                ),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = PlayerPanelHorizontalInset,
                    end = PlayerPanelHorizontalInset,
                    bottom = TvPlayerOverlayBottomPadding,
                ),
        ) {
            PlayerControlsPanel(
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                qualityLabel = qualityLabel,
                speedLabel = speedLabel,
                aspectRatioMode = aspectRatioMode,
                qualityEnabled = qualityControlsEnabled,
                speedEnabled = speedControlsEnabled,
                aspectRatioEnabled = aspectRatioControlEnabled,
                palette = palette,
                progressRequester = progressRequester,
                seekBackRequester = seekBackRequester,
                playPauseRequester = playPauseRequester,
                seekForwardRequester = seekForwardRequester,
                qualityRequester = qualityRequester,
                speedRequester = speedRequester,
                aspectRatioRequester = aspectRatioRequester,
                episodesRequester = episodesRequester,
                onControlFocused = onControlFocused,
                onInteraction = ::registerInteraction,
                onTogglePlayback = onTogglePlayback,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onQualityClick = {
                    registerInteraction()
                    availableQualities.nextAfter(selectedQuality)?.let(onQualitySelected)
                },
                onSpeedClick = {
                    registerInteraction()
                    availableSpeeds.nextAfter(selectedSpeed)?.let(onSpeedSelected)
                },
                onAspectRatioClick = {
                    registerInteraction()
                    onAspectRatioSelected(aspectRatioMode.next())
                },
                onEpisodesClick = onEpisodesClick,
                modifier = Modifier
                    .fillMaxWidth(PlayerControlsRuntimeLayout.panelWidthFraction)
                    .onSizeChanged { controlsPanelHeightPx = it.height },
            )
        }

        PlayerSkipsOverlay(
            skipsPart = skipsPart,
            onInteraction = ::registerInteraction,
            onOpenControls = {
                onShowControls(PlayerOverlayFocusTarget.PlayPause)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = PlayerPanelHorizontalInset),
            bottomPadding = if (controlsVisible) {
                with(LocalDensity.current) {
                    controlsPanelHeightPx.toDp() + 52.dp
                }
            } else {
                52.dp
            },
            panelWidthFraction = PlayerControlsRuntimeLayout.panelWidthFraction,
        )

        if (isLoading || isBuffering) {
            val loadingPanelStyle = PlayerOverlayUiDefaults.loadingPanelStyle(palette)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .playerPanelSurface(loadingPanelStyle),
            ) {
                Row(
                    modifier = Modifier.padding(PlayerOverlayUiDefaults.LoadingPanelPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(
                        color = palette.textColor,
                        strokeWidth = PlayerOverlayUiDefaults.LoadingIndicatorStrokeWidth,
                        modifier = Modifier.size(PlayerOverlayUiDefaults.LoadingIndicatorSize),
                    )
                    Text(
                        text = stringResource(R.string.player_loading),
                        color = palette.textColor,
                        fontSize = 17.sp,
                    )
                }
            }
        }
    }
}

private fun Modifier.playerPanelSurface(
    style: PlayerPanelSurfaceStyle,
): Modifier {
    return clip(style.shape)
        .background(style.backgroundColor)
        .border(
            width = style.borderWidth,
            color = style.borderColor,
            shape = style.shape,
        )
}

@Composable
private fun PlayerControlsPanel(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    qualityLabel: String,
    speedLabel: String,
    aspectRatioMode: PlayerAspectRatioMode,
    qualityEnabled: Boolean,
    speedEnabled: Boolean,
    aspectRatioEnabled: Boolean,
    palette: WatchingPalette,
    progressRequester: FocusRequester,
    seekBackRequester: FocusRequester,
    playPauseRequester: FocusRequester,
    seekForwardRequester: FocusRequester,
    qualityRequester: FocusRequester,
    speedRequester: FocusRequester,
    aspectRatioRequester: FocusRequester,
    episodesRequester: FocusRequester,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onInteraction: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = PlayerControlsRuntimeLayout
    val seekBackButtonLayout = layout.button(PlayerControlButtonId.SeekBack)
    val playPauseButtonLayout = layout.button(PlayerControlButtonId.PlayPause)
    val seekForwardButtonLayout = layout.button(PlayerControlButtonId.SeekForward)
    val speedButtonLayout = layout.button(PlayerControlButtonId.Speed)
    val episodesButtonLayout = layout.button(PlayerControlButtonId.Episodes)
    val qualityButtonLayout = layout.button(PlayerControlButtonId.Quality)
    val aspectRatioButtonLayout = layout.button(PlayerControlButtonId.AspectRatio)
    val focusableButtonTargets = mutableListOf<PlayerOverlayFocusTarget>().apply {
        add(PlayerOverlayFocusTarget.SeekBack)
        add(PlayerOverlayFocusTarget.PlayPause)
        add(PlayerOverlayFocusTarget.SeekForward)
        if (speedEnabled) add(PlayerOverlayFocusTarget.Speed)
        add(PlayerOverlayFocusTarget.Episodes)
        if (qualityEnabled) add(PlayerOverlayFocusTarget.Quality)
        if (aspectRatioEnabled) add(PlayerOverlayFocusTarget.AspectRatio)
    }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester = when (target) {
        PlayerOverlayFocusTarget.Previous -> seekBackRequester
        PlayerOverlayFocusTarget.SeekBack -> seekBackRequester
        PlayerOverlayFocusTarget.PlayPause -> playPauseRequester
        PlayerOverlayFocusTarget.SeekForward -> seekForwardRequester
        PlayerOverlayFocusTarget.Next -> speedRequester
        PlayerOverlayFocusTarget.Speed -> speedRequester
        PlayerOverlayFocusTarget.Episodes -> episodesRequester
        PlayerOverlayFocusTarget.Quality -> qualityRequester
        PlayerOverlayFocusTarget.AspectRatio -> aspectRatioRequester
        PlayerOverlayFocusTarget.Progress -> progressRequester
        PlayerOverlayFocusTarget.Root -> playPauseRequester
    }

    fun requestAdjacent(current: PlayerOverlayFocusTarget, step: Int): Boolean {
        val currentIndex = focusableButtonTargets.indexOf(current)
        if (currentIndex < 0) {
            return false
        }
        val nextTarget = focusableButtonTargets.getOrNull(currentIndex + step) ?: return false
        return requestFocus(requesterFor(nextTarget))
    }

    Column(
        modifier = modifier
            .playerPanelSurface(PlayerOverlayUiDefaults.controlsPanelStyle(palette))
            .padding(
                start = layout.controlsHorizontalPadding,
                end = layout.controlsHorizontalPadding,
                top = layout.rowsTopPadding,
                bottom = layout.controlsBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(layout.rowsVerticalSpacing),
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
            onClick = onInteraction,
            onDown = {
                onInteraction()
                requestFocus(playPauseRequester)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                space = layout.primaryRowSpacing,
                alignment = Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerActionButton(
                contentDescription = stringResource(R.string.player_action_rewind),
                focusRequester = seekBackRequester,
                palette = palette,
                iconRes = R.drawable.ic_player_seek_back,
                minWidth = seekBackButtonLayout.minWidth,
                horizontalPadding = seekBackButtonLayout.horizontalPadding,
                verticalPadding = seekBackButtonLayout.verticalPadding,
                iconSize = seekBackButtonLayout.iconSize,
                onFocused = {
                    onInteraction()
                    onControlFocused(PlayerOverlayFocusTarget.SeekBack)
                },
                onClick = {
                    onInteraction()
                    onSeekBack()
                },
                onLeft = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.SeekBack, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.SeekBack, 1)
                },
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { false },
            )
            PlayerActionButton(
                contentDescription = stringResource(
                    if (isPlaying) {
                        R.string.player_action_pause
                    } else {
                        R.string.player_action_play
                    }
                ),
                focusRequester = playPauseRequester,
                palette = palette,
                iconRes = if (isPlaying) {
                    R.drawable.ic_player_pause
                } else {
                    R.drawable.ic_player_play
                },
                emphasized = true,
                minWidth = playPauseButtonLayout.minWidth,
                horizontalPadding = playPauseButtonLayout.horizontalPadding,
                verticalPadding = playPauseButtonLayout.verticalPadding,
                iconSize = playPauseButtonLayout.iconSize,
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
                    requestAdjacent(PlayerOverlayFocusTarget.PlayPause, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.PlayPause, 1)
                },
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { false },
            )
            PlayerActionButton(
                contentDescription = stringResource(R.string.player_action_forward),
                focusRequester = seekForwardRequester,
                palette = palette,
                iconRes = R.drawable.ic_player_seek_forward,
                minWidth = seekForwardButtonLayout.minWidth,
                horizontalPadding = seekForwardButtonLayout.horizontalPadding,
                verticalPadding = seekForwardButtonLayout.verticalPadding,
                iconSize = seekForwardButtonLayout.iconSize,
                onFocused = {
                    onInteraction()
                    onControlFocused(PlayerOverlayFocusTarget.SeekForward)
                },
                onClick = {
                    onInteraction()
                    onSeekForward()
                },
                onLeft = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.SeekForward, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.SeekForward, 1)
                },
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { false },
            )
            PlayerActionButton(
                text = speedLabel,
                contentDescription = "${stringResource(R.string.player_action_speed)} $speedLabel",
                focusRequester = speedRequester,
                palette = palette,
                iconRes = R.drawable.ic_play_speed,
                enabled = speedEnabled,
                minWidth = speedButtonLayout.minWidth,
                horizontalPadding = speedButtonLayout.horizontalPadding,
                verticalPadding = speedButtonLayout.verticalPadding,
                iconSize = speedButtonLayout.iconSize,
                textFontSize = speedButtonLayout.textFontSize,
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
                    requestAdjacent(PlayerOverlayFocusTarget.Speed, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.Speed, 1)
                },
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { false },
            )
            PlayerActionButton(
                text = stringResource(R.string.player_action_episodes_compact),
                contentDescription = stringResource(R.string.player_action_episodes),
                focusRequester = episodesRequester,
                palette = palette,
                iconRes = R.drawable.ic_playlist_play_black_24dp,
                minWidth = episodesButtonLayout.minWidth,
                horizontalPadding = episodesButtonLayout.horizontalPadding,
                verticalPadding = episodesButtonLayout.verticalPadding,
                iconSize = episodesButtonLayout.iconSize,
                textFontSize = episodesButtonLayout.textFontSize,
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
                    requestAdjacent(PlayerOverlayFocusTarget.Episodes, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.Episodes, 1)
                },
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { false },
            )
            PlayerActionButton(
                text = qualityLabel,
                contentDescription = "${stringResource(R.string.player_action_quality)} $qualityLabel",
                focusRequester = qualityRequester,
                palette = palette,
                enabled = qualityEnabled,
                minWidth = qualityButtonLayout.minWidth,
                horizontalPadding = qualityButtonLayout.horizontalPadding,
                verticalPadding = qualityButtonLayout.verticalPadding,
                textFontSize = qualityButtonLayout.textFontSize,
                onFocused = {
                    onInteraction()
                    onControlFocused(PlayerOverlayFocusTarget.Quality)
                },
                onClick = {
                    onInteraction()
                    onQualityClick()
                },
                onLeft = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.Quality, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.Quality, 1)
                },
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { false },
            )
            PlayerActionButton(
                text = stringResource(aspectRatioMode.compactTitleRes),
                contentDescription = buildString {
                    append(stringResource(R.string.player_action_aspect_ratio))
                    append(' ')
                    append(stringResource(aspectRatioMode.titleRes))
                },
                focusRequester = aspectRatioRequester,
                palette = palette,
                enabled = aspectRatioEnabled,
                minWidth = aspectRatioButtonLayout.minWidth,
                horizontalPadding = aspectRatioButtonLayout.horizontalPadding,
                verticalPadding = aspectRatioButtonLayout.verticalPadding,
                textFontSize = aspectRatioButtonLayout.textFontSize,
                onFocused = {
                    onInteraction()
                    onControlFocused(PlayerOverlayFocusTarget.AspectRatio)
                },
                onClick = {
                    onInteraction()
                    onAspectRatioClick()
                },
                onLeft = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.AspectRatio, -1)
                },
                onRight = { false },
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { false },
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
    onClick: () -> Unit,
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
    val surfaceColors = PlayerOverlayUiDefaults.progressSurfaceColors(palette)

    PlayerControlSurface(
        focusRequester = focusRequester,
        backgroundColor = surfaceColors.backgroundColor,
        focusedBackgroundColor = surfaceColors.focusedBackgroundColor,
        borderColor = surfaceColors.borderColor,
        onClick = onClick,
        onFocused = onFocused,
        onLeft = {
            onSeekBack()
            true
        },
        onRight = {
            onSeekForward()
            true
        },
        onUp = { false },
        onDown = onDown,
        modifier = modifier,
        shape = PlayerOverlayUiDefaults.ProgressSurfaceShape,
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
                modifier = Modifier
                    .weight(1f)
                    .height(PlayerOverlayUiDefaults.ProgressTrackHeight)
                    .clip(PlayerOverlayUiDefaults.ProgressBarShape)
                    .background(PlayerOverlayUiDefaults.progressTrackColor()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                        .height(PlayerOverlayUiDefaults.ProgressBufferedTrackHeight)
                        .clip(PlayerOverlayUiDefaults.ProgressBarShape)
                        .background(PlayerOverlayUiDefaults.progressBufferedTrackColor()),
                )
                Box(
                    modifier = Modifier
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

@Composable
private fun PlayerActionButton(
    contentDescription: String,
    focusRequester: FocusRequester,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    text: String? = null,
    iconRes: Int? = null,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 168.dp,
    horizontalPadding: Dp = 18.dp,
    verticalPadding: Dp = 14.dp,
    iconSize: Dp = 24.dp,
    textFontSize: TextUnit = 16.sp,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
) {
    val buttonColors = PlayerOverlayUiDefaults.actionButtonColors(
        palette = palette,
        emphasized = emphasized,
    )
    PlayerControlSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = buttonColors.backgroundColor,
        focusedBackgroundColor = buttonColors.focusedBackgroundColor,
        borderColor = buttonColors.borderColor,
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onRight = onRight,
        onUp = onUp,
        onDown = onDown,
        modifier = modifier
            .width(minWidth)
            .semantics { this.contentDescription = contentDescription },
        paddingValues = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                space = if (text != null && iconRes != null) {
                    PlayerOverlayUiDefaults.ActionButtonContentSpacing
                } else {
                    0.dp
                },
                alignment = Alignment.CenterHorizontally,
            ),
        ) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = if (enabled) palette.textColor else palette.secondaryTextColor,
                    modifier = Modifier.size(iconSize),
                )
            }
            if (text != null) {
                Text(
                    text = text,
                    color = if (enabled) palette.textColor else palette.secondaryTextColor,
                    fontSize = textFontSize,
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayerControlSurface(
    focusRequester: FocusRequester,
    backgroundColor: Color,
    focusedBackgroundColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    selectedBorderColor: Color = borderColor,
    shape: Shape = PlayerOverlayUiDefaults.ControlsPanelShape,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    paddingValues: PaddingValues,
    content: @Composable () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val canFocus = enabled

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (isFocused) PlayerOverlayUiDefaults.PlayerFocusScale else 1f
                scaleY = if (isFocused) PlayerOverlayUiDefaults.PlayerFocusScale else 1f
                shadowElevation = if (isFocused) {
                    PlayerOverlayUiDefaults.PlayerFocusShadowElevation.toPx()
                } else {
                    0f
                }
                this.shape = shape
                this.clip = false
            }
            .clip(shape)
            .background(if (isFocused) focusedBackgroundColor else backgroundColor)
            .border(
                width = if (isFocused || selected) 2.dp else 1.dp,
                color = when {
                    isFocused -> borderColor
                    selected -> selectedBorderColor
                    else -> Color.Transparent
                },
                shape = shape,
            )
            .then(if (canFocus) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
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
                        onClick()
                        true
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
                    Modifier.pointerInput(onClick) {
                        detectTapGestures(onTap = { onClick() })
                    }
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

private fun PlayerQuality.toPlayerPickerLabel(): String = when (this) {
    PlayerQuality.SD -> "SD"
    PlayerQuality.HD -> "HD"
    PlayerQuality.FULLHD -> "1080"
}

private fun Float.toPlayerPickerLabel(): String {
    return if (this == 1.0f) {
        "1x"
    } else {
        "${this}x"
    }
}

private fun List<PlayerQuality>.nextAfter(current: PlayerQuality): PlayerQuality? {
    if (isEmpty()) {
        return null
    }
    val currentIndex = indexOf(current)
    val nextIndex = if (currentIndex >= 0) {
        (currentIndex + 1) % size
    } else {
        0
    }
    return getOrNull(nextIndex)
}

private fun List<Float>.nextAfter(current: Float): Float? {
    if (isEmpty()) {
        return null
    }
    val currentIndex = indexOfFirst { kotlin.math.abs(it - current) < 0.001f }
    val nextIndex = if (currentIndex >= 0) {
        (currentIndex + 1) % size
    } else {
        0
    }
    return getOrNull(nextIndex)
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val CONTROLS_AUTO_HIDE_DELAY_MS = 4_500L
private val PlayerPanelHorizontalInset = 20.dp
