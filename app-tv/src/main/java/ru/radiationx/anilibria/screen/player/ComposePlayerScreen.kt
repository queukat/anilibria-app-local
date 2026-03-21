package ru.radiationx.anilibria.screen.player

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
    PlayPause,
    SeekBack,
    PreviousEpisode,
    NextEpisode,
    Quality,
    Speed,
    AspectRatio,
}

internal enum class PlayerOverlayPicker(
    @StringRes val titleRes: Int,
    private val returnTarget: PlayerOverlayFocusTarget,
) {
    Quality(
        titleRes = R.string.player_action_quality,
        returnTarget = PlayerOverlayFocusTarget.Quality,
    ),
    Speed(
        titleRes = R.string.player_action_speed,
        returnTarget = PlayerOverlayFocusTarget.Speed,
    ),
    AspectRatio(
        titleRes = R.string.player_action_aspect_ratio,
        returnTarget = PlayerOverlayFocusTarget.AspectRatio,
    ),
    ;

    fun focusTarget(): PlayerOverlayFocusTarget = returnTarget
}

private data class PlayerPickerOption(
    val id: String,
    val label: String,
    val selected: Boolean,
    val onSelected: () -> Unit,
)

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreenContent(
    player: Player?,
    title: String,
    subtitle: String,
    controlsVisible: Boolean,
    controlsFocusTarget: PlayerOverlayFocusTarget,
    controlsFocusToken: Int,
    activePicker: PlayerOverlayPicker?,
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
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onDismissPicker: (PlayerOverlayPicker) -> Unit,
    onQualitySelected: (PlayerQuality) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAspectRatioSelected: (PlayerAspectRatioMode) -> Unit,
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
    val playPauseRequester = remember { FocusRequester() }
    val seekBackRequester = remember { FocusRequester() }
    val previousRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val qualityRequester = remember { FocusRequester() }
    val speedRequester = remember { FocusRequester() }
    val aspectRatioRequester = remember { FocusRequester() }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester = when (target) {
        PlayerOverlayFocusTarget.Root -> rootRequester
        PlayerOverlayFocusTarget.PlayPause -> playPauseRequester
        PlayerOverlayFocusTarget.SeekBack -> seekBackRequester
        PlayerOverlayFocusTarget.PreviousEpisode -> previousRequester
        PlayerOverlayFocusTarget.NextEpisode -> nextRequester
        PlayerOverlayFocusTarget.Quality -> qualityRequester
        PlayerOverlayFocusTarget.Speed -> speedRequester
        PlayerOverlayFocusTarget.AspectRatio -> aspectRatioRequester
    }

    fun firstAvailable(vararg targets: PlayerOverlayFocusTarget): PlayerOverlayFocusTarget {
        return targets.firstOrNull { target ->
            when (target) {
                PlayerOverlayFocusTarget.Root -> false
                PlayerOverlayFocusTarget.PlayPause -> true
                PlayerOverlayFocusTarget.SeekBack -> true
                PlayerOverlayFocusTarget.PreviousEpisode -> canPrevious
                PlayerOverlayFocusTarget.NextEpisode -> canNext
                PlayerOverlayFocusTarget.Quality -> qualityControlsEnabled
                PlayerOverlayFocusTarget.Speed -> speedControlsEnabled
                PlayerOverlayFocusTarget.AspectRatio -> aspectRatioControlEnabled
            }
        } ?: PlayerOverlayFocusTarget.PlayPause
    }

    fun resolveFocusTarget(target: PlayerOverlayFocusTarget): PlayerOverlayFocusTarget = when (target) {
        PlayerOverlayFocusTarget.Root -> firstAvailable(
            PlayerOverlayFocusTarget.PlayPause,
            PlayerOverlayFocusTarget.SeekBack,
            PlayerOverlayFocusTarget.PreviousEpisode,
            PlayerOverlayFocusTarget.NextEpisode,
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.AspectRatio,
        )

        PlayerOverlayFocusTarget.PlayPause -> PlayerOverlayFocusTarget.PlayPause
        PlayerOverlayFocusTarget.SeekBack -> PlayerOverlayFocusTarget.SeekBack
        PlayerOverlayFocusTarget.PreviousEpisode -> firstAvailable(
            PlayerOverlayFocusTarget.PreviousEpisode,
            PlayerOverlayFocusTarget.SeekBack,
            PlayerOverlayFocusTarget.PlayPause,
            PlayerOverlayFocusTarget.NextEpisode,
        )

        PlayerOverlayFocusTarget.NextEpisode -> firstAvailable(
            PlayerOverlayFocusTarget.NextEpisode,
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.PlayPause,
        )

        PlayerOverlayFocusTarget.Quality -> firstAvailable(
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.PlayPause,
        )

        PlayerOverlayFocusTarget.Speed -> firstAvailable(
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.PlayPause,
        )

        PlayerOverlayFocusTarget.AspectRatio -> firstAvailable(
            PlayerOverlayFocusTarget.AspectRatio,
            PlayerOverlayFocusTarget.Speed,
            PlayerOverlayFocusTarget.Quality,
            PlayerOverlayFocusTarget.PlayPause,
        )
    }

    val pickerOptions = when (activePicker) {
        PlayerOverlayPicker.Quality -> availableQualities.map { quality ->
            PlayerPickerOption(
                id = "quality_${quality.name}",
                label = quality.asPlayerLabel(),
                selected = quality == selectedQuality,
                onSelected = { onQualitySelected(quality) },
            )
        }

        PlayerOverlayPicker.Speed -> availableSpeeds.map { speed ->
            PlayerPickerOption(
                id = "speed_$speed",
                label = speed.asPlayerLabel(),
                selected = kotlin.math.abs(speed - selectedSpeed) < 0.001f,
                onSelected = { onSpeedSelected(speed) },
            )
        }

        PlayerOverlayPicker.AspectRatio -> PlayerAspectRatioMode.entries.map { mode ->
            PlayerPickerOption(
                id = "aspect_${mode.name}",
                label = stringResource(mode.titleRes),
                selected = mode == aspectRatioMode,
                onSelected = { onAspectRatioSelected(mode) },
            )
        }

        null -> emptyList()
    }

    LaunchedEffect(controlsVisible, controlsFocusToken, controlsFocusTarget, activePicker) {
        if (!controlsVisible || activePicker != null) {
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

    LaunchedEffect(activePicker, pickerOptions) {
        if (activePicker != null && pickerOptions.isEmpty()) {
            onDismissPicker(activePicker)
        }
    }

    LaunchedEffect(
        controlsVisible,
        autoHideToken,
        isPlaying,
        isLoading,
        isBuffering,
        skipVisible,
        activePicker,
    ) {
        if (!controlsVisible || !isPlaying || isLoading || isBuffering || skipVisible || activePicker != null) {
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
                        registerInteraction()
                        if (activePicker != null) {
                            onDismissPicker(activePicker)
                        } else {
                            onBackRequested()
                        }
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
                            onShowControls(PlayerOverlayFocusTarget.SeekBack)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionRight -> {
                        if (!controlsVisible && !skipVisible) {
                            registerInteraction()
                            onSeekForward()
                            onShowControls(PlayerOverlayFocusTarget.PlayPause)
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
            Column(
                modifier = Modifier.fillMaxWidth(PlayerControlsRuntimeLayout.panelWidthFraction),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (activePicker != null && pickerOptions.isNotEmpty()) {
                    PlayerPickerMenu(
                        title = stringResource(activePicker.titleRes),
                        options = pickerOptions,
                        palette = palette,
                        onInteraction = ::registerInteraction,
                        modifier = Modifier.widthIn(
                            min = PlayerOverlayUiDefaults.PickerMinWidth,
                            max = PlayerOverlayUiDefaults.PickerMaxWidth,
                        ),
                    )
                }

                PlayerControlsPanel(
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    bufferedPositionMs = bufferedPositionMs,
                    qualityLabel = qualityLabel,
                    speedLabel = speedLabel,
                    aspectRatioMode = aspectRatioMode,
                    canPrevious = canPrevious,
                    canNext = canNext,
                    qualityEnabled = qualityControlsEnabled,
                    speedEnabled = speedControlsEnabled,
                    aspectRatioEnabled = aspectRatioControlEnabled,
                    palette = palette,
                    playPauseRequester = playPauseRequester,
                    seekBackRequester = seekBackRequester,
                    previousRequester = previousRequester,
                    nextRequester = nextRequester,
                    qualityRequester = qualityRequester,
                    speedRequester = speedRequester,
                    aspectRatioRequester = aspectRatioRequester,
                    onControlFocused = onControlFocused,
                    onInteraction = ::registerInteraction,
                    onTogglePlayback = onTogglePlayback,
                    onSeekBack = onSeekBack,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onQualityClick = onQualityClick,
                    onSpeedClick = onSpeedClick,
                    onAspectRatioClick = onAspectRatioClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { controlsPanelHeightPx = it.height },
                )
            }
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
    canPrevious: Boolean,
    canNext: Boolean,
    qualityEnabled: Boolean,
    speedEnabled: Boolean,
    aspectRatioEnabled: Boolean,
    palette: WatchingPalette,
    playPauseRequester: FocusRequester,
    seekBackRequester: FocusRequester,
    previousRequester: FocusRequester,
    nextRequester: FocusRequester,
    qualityRequester: FocusRequester,
    speedRequester: FocusRequester,
    aspectRatioRequester: FocusRequester,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onInteraction: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = PlayerControlsRuntimeLayout
    val playPauseButtonLayout = layout.button(PlayerControlButtonId.PlayPause)
    val seekBackButtonLayout = layout.button(PlayerControlButtonId.SeekBack)
    val previousButtonLayout = layout.button(PlayerControlButtonId.Previous)
    val nextButtonLayout = layout.button(PlayerControlButtonId.Next)
    val qualityButtonLayout = layout.button(PlayerControlButtonId.Quality)
    val speedButtonLayout = layout.button(PlayerControlButtonId.Speed)
    val aspectRatioButtonLayout = layout.button(PlayerControlButtonId.AspectRatio)

    val focusableButtonTargets = buildList {
        add(PlayerOverlayFocusTarget.PlayPause)
        add(PlayerOverlayFocusTarget.SeekBack)
        if (canPrevious) add(PlayerOverlayFocusTarget.PreviousEpisode)
        if (canNext) add(PlayerOverlayFocusTarget.NextEpisode)
        if (qualityEnabled) add(PlayerOverlayFocusTarget.Quality)
        if (speedEnabled) add(PlayerOverlayFocusTarget.Speed)
        if (aspectRatioEnabled) add(PlayerOverlayFocusTarget.AspectRatio)
    }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester = when (target) {
        PlayerOverlayFocusTarget.Root -> playPauseRequester
        PlayerOverlayFocusTarget.PlayPause -> playPauseRequester
        PlayerOverlayFocusTarget.SeekBack -> seekBackRequester
        PlayerOverlayFocusTarget.PreviousEpisode -> previousRequester
        PlayerOverlayFocusTarget.NextEpisode -> nextRequester
        PlayerOverlayFocusTarget.Quality -> qualityRequester
        PlayerOverlayFocusTarget.Speed -> speedRequester
        PlayerOverlayFocusTarget.AspectRatio -> aspectRatioRequester
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
        PlayerProgressDisplay(
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            palette = palette,
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
                onUp = { true },
                onDown = { true },
            )

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
                onUp = { true },
                onDown = { true },
            )

            PlayerActionButton(
                contentDescription = stringResource(R.string.player_action_previous),
                focusRequester = previousRequester,
                palette = palette,
                iconRes = R.drawable.ic_player_skip_previous,
                enabled = canPrevious,
                minWidth = previousButtonLayout.minWidth,
                horizontalPadding = previousButtonLayout.horizontalPadding,
                verticalPadding = previousButtonLayout.verticalPadding,
                iconSize = previousButtonLayout.iconSize,
                onFocused = {
                    onInteraction()
                    onControlFocused(PlayerOverlayFocusTarget.PreviousEpisode)
                },
                onClick = {
                    onInteraction()
                    onPreviousClick()
                },
                onLeft = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.PreviousEpisode, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.PreviousEpisode, 1)
                },
                onUp = { true },
                onDown = { true },
            )

            PlayerActionButton(
                contentDescription = stringResource(R.string.player_action_next),
                focusRequester = nextRequester,
                palette = palette,
                iconRes = R.drawable.ic_player_skip_next,
                enabled = canNext,
                minWidth = nextButtonLayout.minWidth,
                horizontalPadding = nextButtonLayout.horizontalPadding,
                verticalPadding = nextButtonLayout.verticalPadding,
                iconSize = nextButtonLayout.iconSize,
                onFocused = {
                    onInteraction()
                    onControlFocused(PlayerOverlayFocusTarget.NextEpisode)
                },
                onClick = {
                    onInteraction()
                    onNextClick()
                },
                onLeft = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.NextEpisode, -1)
                },
                onRight = {
                    onInteraction()
                    requestAdjacent(PlayerOverlayFocusTarget.NextEpisode, 1)
                },
                onUp = { true },
                onDown = { true },
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
                onUp = { true },
                onDown = { true },
            )

            PlayerActionButton(
                text = speedLabel,
                contentDescription = "${stringResource(R.string.player_action_speed)} $speedLabel",
                focusRequester = speedRequester,
                palette = palette,
                enabled = speedEnabled,
                minWidth = speedButtonLayout.minWidth,
                horizontalPadding = speedButtonLayout.horizontalPadding,
                verticalPadding = speedButtonLayout.verticalPadding,
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
                onUp = { true },
                onDown = { true },
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
                onRight = { true },
                onUp = { true },
                onDown = { true },
            )
        }
    }
}

@Composable
private fun PlayerProgressDisplay(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    palette: WatchingPalette,
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

    Box(
        modifier = modifier
            .playerPanelSurface(PlayerOverlayUiDefaults.progressDisplayStyle())
            .padding(PlayerOverlayUiDefaults.ProgressSurfacePadding),
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
private fun PlayerPickerMenu(
    title: String,
    options: List<PlayerPickerOption>,
    palette: WatchingPalette,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) {
        return
    }

    val optionIds = remember(options) { options.map(PlayerPickerOption::id) }
    val optionRequesters = remember(optionIds) { List(optionIds.size) { FocusRequester() } }
    val selectedIndex = remember(options) {
        options.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
    }

    LaunchedEffect(optionIds, selectedIndex) {
        requestWatchingFocusAfterAttach(optionRequesters.getOrNull(selectedIndex))
    }

    Column(
        modifier = modifier
            .playerPanelSurface(PlayerOverlayUiDefaults.pickerPanelStyle(palette))
            .padding(PlayerOverlayUiDefaults.PickerPanelPadding),
        verticalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.PickerSectionSpacing),
    ) {
        Text(
            text = title,
            color = palette.textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = PlayerOverlayUiDefaults.PickerHeaderBottomSpacing),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.PickerOptionSpacing),
        ) {
            options.forEachIndexed { index, option ->
                PlayerPickerOptionButton(
                    option = option,
                    focusRequester = optionRequesters[index],
                    palette = palette,
                    onInteraction = onInteraction,
                    onUp = {
                        onInteraction()
                        if (index <= 0) {
                            true
                        } else {
                            requestFocus(optionRequesters[index - 1])
                        }
                    },
                    onDown = {
                        onInteraction()
                        if (index >= optionRequesters.lastIndex) {
                            true
                        } else {
                            requestFocus(optionRequesters[index + 1])
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerPickerOptionButton(
    option: PlayerPickerOption,
    focusRequester: FocusRequester,
    palette: WatchingPalette,
    onInteraction: () -> Unit,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
) {
    val optionColors = PlayerOverlayUiDefaults.pickerOptionColors(
        palette = palette,
        selected = option.selected,
    )

    PlayerControlSurface(
        focusRequester = focusRequester,
        backgroundColor = optionColors.backgroundColor,
        focusedBackgroundColor = optionColors.focusedBackgroundColor,
        borderColor = optionColors.borderColor,
        selected = option.selected,
        selectedBorderColor = optionColors.borderColor,
        onClick = {
            onInteraction()
            option.onSelected()
        },
        onFocused = onInteraction,
        onLeft = { true },
        onUp = onUp,
        onRight = { true },
        onDown = onDown,
        modifier = Modifier.fillMaxWidth(),
        paddingValues = PlayerOverlayUiDefaults.PickerOptionPadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (option.selected) {
                            palette.accentColor
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (option.selected) {
                            palette.accentColor
                        } else {
                            Color.White.copy(alpha = 0.28f)
                        },
                        shape = CircleShape,
                    ),
            )
            Text(
                text = option.label,
                color = palette.textColor,
                fontSize = 16.sp,
                fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 22.sp,
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
                    tint = if (enabled) palette.textColor else palette.textColor.copy(alpha = 0.46f),
                    modifier = Modifier.size(iconSize),
                )
            }
            if (text != null) {
                Text(
                    text = text,
                    color = if (enabled) palette.textColor else palette.textColor.copy(alpha = 0.46f),
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
    val interactionSource = remember { MutableInteractionSource() }
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
private val PlayerPanelHorizontalInset = 20.dp
