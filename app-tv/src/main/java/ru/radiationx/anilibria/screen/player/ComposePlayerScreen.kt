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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.TvPlayerOverlayBottomPadding
import ru.radiationx.anilibria.screen.watching.TvPlayerOverlayHorizontalPadding
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.data.entity.common.PlayerQuality
import kotlin.math.roundToInt

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

private enum class PlayerInlinePicker {
    Quality,
    Speed,
    AspectRatio,
}

private fun resolveQuickActionsEntryFocusTarget(
    qualityEnabled: Boolean,
    speedEnabled: Boolean,
): PlayerOverlayFocusTarget = when {
    qualityEnabled -> PlayerOverlayFocusTarget.Quality
    speedEnabled -> PlayerOverlayFocusTarget.Speed
    else -> PlayerOverlayFocusTarget.Episodes
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
    qualityLabel: String,
    speedLabel: String,
    aspectRatioMode: PlayerAspectRatioMode,
    availableQualities: List<PlayerQuality>,
    availableSpeeds: List<Float>,
    availableAspectRatios: List<PlayerAspectRatioMode>,
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
    val skipVisible = skipsPart?.isVisible == true
    val qualityControlsEnabled = availableQualities.isNotEmpty()
    val speedControlsEnabled = availableSpeeds.isNotEmpty()
    val aspectRatioControlEnabled = availableAspectRatios.size > 1
    var autoHideToken by remember { mutableIntStateOf(0) }
    var controlsPanelHeightPx by remember { mutableIntStateOf(0) }
    var activePicker by remember { mutableStateOf<PlayerInlinePicker?>(null) }
    var pendingFocusRestoreTarget by remember { mutableStateOf<PlayerOverlayFocusTarget?>(null) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var qualityButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var speedButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var aspectRatioButtonBounds by remember { mutableStateOf<Rect?>(null) }

    fun registerInteraction() {
        autoHideToken += 1
    }

    val rootRequester = remember { FocusRequester() }
    val progressRequester = remember { FocusRequester() }
    val previousRequester = remember { FocusRequester() }
    val seekBackRequester = remember { FocusRequester() }
    val playPauseRequester = remember { FocusRequester() }
    val seekForwardRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val qualityRequester = remember { FocusRequester() }
    val speedRequester = remember { FocusRequester() }
    val aspectRatioRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester = when (target) {
        PlayerOverlayFocusTarget.Root -> rootRequester
        PlayerOverlayFocusTarget.Progress -> progressRequester
        PlayerOverlayFocusTarget.Previous -> previousRequester
        PlayerOverlayFocusTarget.SeekBack -> seekBackRequester
        PlayerOverlayFocusTarget.PlayPause -> playPauseRequester
        PlayerOverlayFocusTarget.SeekForward -> seekForwardRequester
        PlayerOverlayFocusTarget.Next -> nextRequester
        PlayerOverlayFocusTarget.Quality -> qualityRequester
        PlayerOverlayFocusTarget.Speed -> speedRequester
        PlayerOverlayFocusTarget.AspectRatio -> aspectRatioRequester
        PlayerOverlayFocusTarget.Episodes -> episodesRequester
    }

    fun openPicker(picker: PlayerInlinePicker, target: PlayerOverlayFocusTarget) {
        registerInteraction()
        pendingFocusRestoreTarget = target
        activePicker = if (activePicker == picker) {
            null
        } else {
            picker
        }
    }

    fun closePicker(restoreTarget: PlayerOverlayFocusTarget? = pendingFocusRestoreTarget) {
        activePicker = null
        pendingFocusRestoreTarget = restoreTarget
    }

    LaunchedEffect(controlsVisible, controlsFocusToken, controlsFocusTarget, activePicker) {
        if (!controlsVisible || activePicker != null) {
            return@LaunchedEffect
        }
        requestWatchingFocusAfterAttach(requesterFor(controlsFocusTarget))
    }

    LaunchedEffect(activePicker, pendingFocusRestoreTarget) {
        if (activePicker != null) {
            return@LaunchedEffect
        }
        val target = pendingFocusRestoreTarget ?: return@LaunchedEffect
        requestWatchingFocusAfterAttach(requesterFor(target))
        pendingFocusRestoreTarget = null
    }

    LaunchedEffect(controlsVisible, skipVisible) {
        if (controlsVisible || skipVisible) {
            return@LaunchedEffect
        }
        activePicker = null
        pendingFocusRestoreTarget = null
        requestWatchingFocusAfterAttach(rootRequester)
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
            .onSizeChanged { rootSizePx = it }
            .focusRequester(rootRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Back,
                    Key.Escape -> {
                        if (activePicker != null) {
                            closePicker()
                        } else {
                            onBackRequested()
                        }
                        true
                    }

                    Key.MediaPlayPause -> {
                        registerInteraction()
                        onShowControls(null)
                        onTogglePlayback()
                        true
                    }

                    Key.MediaPlay -> {
                        if (!isPlaying) {
                            registerInteraction()
                            onShowControls(null)
                            onTogglePlayback()
                            true
                        } else {
                            false
                        }
                    }

                    Key.MediaPause -> {
                        if (isPlaying) {
                            registerInteraction()
                            onShowControls(null)
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
                            onShowControls(null)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp,
                    Key.DirectionDown -> {
                        if (!controlsVisible && !skipVisible) {
                            registerInteraction()
                            onShowControls(null)
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
                    start = TvPlayerOverlayHorizontalPadding,
                    end = TvPlayerOverlayHorizontalPadding,
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
                canPrevious = canPrevious,
                canNext = canNext,
                qualityEnabled = qualityControlsEnabled,
                speedEnabled = speedControlsEnabled,
                aspectRatioEnabled = aspectRatioControlEnabled,
                palette = palette,
                progressRequester = progressRequester,
                previousRequester = previousRequester,
                seekBackRequester = seekBackRequester,
                playPauseRequester = playPauseRequester,
                seekForwardRequester = seekForwardRequester,
                nextRequester = nextRequester,
                qualityRequester = qualityRequester,
                speedRequester = speedRequester,
                aspectRatioRequester = aspectRatioRequester,
                episodesRequester = episodesRequester,
                onQualityButtonPositioned = { qualityButtonBounds = it },
                onSpeedButtonPositioned = { speedButtonBounds = it },
                onAspectRatioButtonPositioned = { aspectRatioButtonBounds = it },
                onControlFocused = onControlFocused,
                onInteraction = ::registerInteraction,
                onTogglePlayback = onTogglePlayback,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                isQualityPickerOpen = activePicker == PlayerInlinePicker.Quality,
                isSpeedPickerOpen = activePicker == PlayerInlinePicker.Speed,
                isAspectRatioPickerOpen = false,
                onOpenQualityPicker = {
                    openPicker(PlayerInlinePicker.Quality, PlayerOverlayFocusTarget.Quality)
                },
                onOpenSpeedPicker = {
                    openPicker(PlayerInlinePicker.Speed, PlayerOverlayFocusTarget.Speed)
                },
                onOpenAspectRatioPicker = {
                    onAspectRatioSelected(aspectRatioMode.next())
                },
                onEpisodesClick = onEpisodesClick,
                modifier = Modifier
                    .fillMaxWidth(PlayerControlsLayoutPresets.Runtime.panelWidthFraction)
                    .onSizeChanged { controlsPanelHeightPx = it.height },
            )
        }

        if (controlsVisible) {
            val pickerBottomPadding = with(LocalDensity.current) {
                controlsPanelHeightPx.toDp() + TvPlayerOverlayBottomPadding + 12.dp
            }
            val pickerBottomPaddingPx = with(LocalDensity.current) { pickerBottomPadding.roundToPx() }
            val pickerHorizontalPaddingPx = with(LocalDensity.current) { TvPlayerOverlayHorizontalPadding.roundToPx() }
            val pickerTopPaddingPx = with(LocalDensity.current) { 24.dp.roundToPx() }
            val pickerGapPx = with(LocalDensity.current) { 12.dp.roundToPx() }
            when (activePicker) {
                PlayerInlinePicker.Quality -> {
                    var pickerSizePx by remember { mutableStateOf(IntSize.Zero) }
                    PlayerInlinePickerPanel(
                        title = stringResource(R.string.player_action_quality),
                        options = availableQualities.map { quality ->
                            PlayerInlinePickerOption(
                                title = quality.toPlayerPickerLabel(),
                                selected = qualityLabel == quality.toPlayerPickerLabel(),
                                onClick = {
                                    registerInteraction()
                                    closePicker(PlayerOverlayFocusTarget.Quality)
                                    onQualitySelected(quality)
                                },
                            )
                        },
                        palette = palette,
                        modifier = Modifier
                            .onSizeChanged { pickerSizePx = it }
                            .offset {
                                calculateAnchoredPickerOffset(
                                    rootSizePx = rootSizePx,
                                    pickerSizePx = pickerSizePx,
                                    anchorBounds = qualityButtonBounds,
                                    fallbackBottomPaddingPx = pickerBottomPaddingPx,
                                    horizontalPaddingPx = pickerHorizontalPaddingPx,
                                    topPaddingPx = pickerTopPaddingPx,
                                    gapPx = pickerGapPx,
                                )
                            }
                            .align(Alignment.TopStart)
                            .padding(
                                start = TvPlayerOverlayHorizontalPadding,
                                end = TvPlayerOverlayHorizontalPadding,
                            ),
                        onDismiss = {
                            registerInteraction()
                            closePicker(PlayerOverlayFocusTarget.Quality)
                        },
                    )
                }

                PlayerInlinePicker.Speed -> {
                    var pickerSizePx by remember { mutableStateOf(IntSize.Zero) }
                    PlayerInlinePickerPanel(
                        title = stringResource(R.string.player_action_speed),
                        options = availableSpeeds.map { speed ->
                            PlayerInlinePickerOption(
                                title = speed.toPlayerPickerLabel(),
                                selected = speedLabel == speed.toPlayerPickerLabel(),
                                onClick = {
                                    registerInteraction()
                                    closePicker(PlayerOverlayFocusTarget.Speed)
                                    onSpeedSelected(speed)
                                },
                            )
                        },
                        palette = palette,
                        modifier = Modifier
                            .onSizeChanged { pickerSizePx = it }
                            .offset {
                                calculateAnchoredPickerOffset(
                                    rootSizePx = rootSizePx,
                                    pickerSizePx = pickerSizePx,
                                    anchorBounds = speedButtonBounds,
                                    fallbackBottomPaddingPx = pickerBottomPaddingPx,
                                    horizontalPaddingPx = pickerHorizontalPaddingPx,
                                    topPaddingPx = pickerTopPaddingPx,
                                    gapPx = pickerGapPx,
                                )
                            }
                            .align(Alignment.TopStart)
                            .padding(
                                start = TvPlayerOverlayHorizontalPadding,
                                end = TvPlayerOverlayHorizontalPadding,
                            ),
                        onDismiss = {
                            registerInteraction()
                            closePicker(PlayerOverlayFocusTarget.Speed)
                        },
                    )
                }

                PlayerInlinePicker.AspectRatio -> {
                    var pickerSizePx by remember { mutableStateOf(IntSize.Zero) }
                    PlayerInlinePickerPanel(
                        title = stringResource(R.string.player_action_aspect_ratio),
                        options = availableAspectRatios.map { mode ->
                            PlayerInlinePickerOption(
                                title = stringResource(mode.titleRes),
                                selected = aspectRatioMode == mode,
                                onClick = {
                                    registerInteraction()
                                    closePicker(PlayerOverlayFocusTarget.AspectRatio)
                                    onAspectRatioSelected(mode)
                                },
                            )
                        },
                        palette = palette,
                        modifier = Modifier
                            .onSizeChanged { pickerSizePx = it }
                            .offset {
                                calculateAnchoredPickerOffset(
                                    rootSizePx = rootSizePx,
                                    pickerSizePx = pickerSizePx,
                                    anchorBounds = aspectRatioButtonBounds,
                                    fallbackBottomPaddingPx = pickerBottomPaddingPx,
                                    horizontalPaddingPx = pickerHorizontalPaddingPx,
                                    topPaddingPx = pickerTopPaddingPx,
                                    gapPx = pickerGapPx,
                                )
                            }
                            .align(Alignment.TopStart)
                            .padding(
                                start = TvPlayerOverlayHorizontalPadding,
                                end = TvPlayerOverlayHorizontalPadding,
                            ),
                        onDismiss = {
                            registerInteraction()
                            closePicker(PlayerOverlayFocusTarget.AspectRatio)
                        },
                    )
                }

                null -> Unit
            }
        }

        PlayerSkipsOverlay(
            skipsPart = skipsPart,
            onInteraction = ::registerInteraction,
            onOpenControls = {
                onShowControls(
                    resolveQuickActionsEntryFocusTarget(
                        qualityEnabled = qualityControlsEnabled,
                        speedEnabled = speedControlsEnabled,
                    )
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = TvPlayerOverlayHorizontalPadding),
            bottomPadding = if (controlsVisible) {
                with(LocalDensity.current) {
                    controlsPanelHeightPx.toDp() + 52.dp
                }
            } else {
                52.dp
            },
            panelWidthFraction = PlayerControlsLayoutPresets.Runtime.panelWidthFraction,
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
    progressRequester: FocusRequester,
    previousRequester: FocusRequester,
    seekBackRequester: FocusRequester,
    playPauseRequester: FocusRequester,
    seekForwardRequester: FocusRequester,
    nextRequester: FocusRequester,
    qualityRequester: FocusRequester,
    speedRequester: FocusRequester,
    aspectRatioRequester: FocusRequester,
    episodesRequester: FocusRequester,
    onQualityButtonPositioned: (Rect) -> Unit,
    onSpeedButtonPositioned: (Rect) -> Unit,
    onAspectRatioButtonPositioned: (Rect) -> Unit,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onInteraction: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    isQualityPickerOpen: Boolean,
    isSpeedPickerOpen: Boolean,
    isAspectRatioPickerOpen: Boolean,
    onOpenQualityPicker: () -> Unit,
    onOpenSpeedPicker: () -> Unit,
    onOpenAspectRatioPicker: () -> Unit,
    onEpisodesClick: () -> Unit,
    modifier: Modifier = Modifier,
    layoutSpec: PlayerControlsLayoutSpec = PlayerControlsLayoutPresets.Runtime,
) {
    val previousButtonLayout = layoutSpec.button(PlayerControlButtonId.Previous)
    val seekBackButtonLayout = layoutSpec.button(PlayerControlButtonId.SeekBack)
    val playPauseButtonLayout = layoutSpec.button(PlayerControlButtonId.PlayPause)
    val seekForwardButtonLayout = layoutSpec.button(PlayerControlButtonId.SeekForward)
    val nextButtonLayout = layoutSpec.button(PlayerControlButtonId.Next)
    val episodesButtonLayout = layoutSpec.button(PlayerControlButtonId.Episodes)
    val speedButtonLayout = layoutSpec.button(PlayerControlButtonId.Speed)
    val qualityButtonLayout = layoutSpec.button(PlayerControlButtonId.Quality)
    val aspectRatioButtonLayout = layoutSpec.button(PlayerControlButtonId.AspectRatio)

    fun requestSecondaryLeft(): Boolean {
        return requestFocus(episodesRequester)
    }

    fun requestSecondaryCenter(): Boolean {
        return when {
            speedEnabled -> requestFocus(speedRequester)
            qualityEnabled -> requestFocus(qualityRequester)
            aspectRatioEnabled -> requestFocus(aspectRatioRequester)
            else -> requestFocus(episodesRequester)
        }
    }

    fun requestSecondaryRight(): Boolean {
        return when {
            aspectRatioEnabled -> requestFocus(aspectRatioRequester)
            qualityEnabled -> requestFocus(qualityRequester)
            speedEnabled -> requestFocus(speedRequester)
            else -> requestFocus(episodesRequester)
        }
    }

    Column(
        modifier = modifier.playerPanelSurface(
            PlayerOverlayUiDefaults.controlsPanelStyle(palette)
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
            onClick = {
                onInteraction()
                onTogglePlayback()
            },
            onDown = {
                onInteraction()
                requestFocus(playPauseRequester)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = layoutSpec.progressHorizontalPadding,
                    vertical = layoutSpec.progressVerticalPadding,
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = layoutSpec.controlsHorizontalPadding,
                    end = layoutSpec.controlsHorizontalPadding,
                    bottom = layoutSpec.controlsBottomPadding,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.textColor.copy(alpha = 0.08f))
                    .align(Alignment.TopCenter),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = layoutSpec.rowsTopPadding),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(layoutSpec.primaryRowSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                            onControlFocused(PlayerOverlayFocusTarget.Previous)
                        },
                        onClick = {
                            onInteraction()
                            onPreviousClick()
                        },
                        onLeft = { true },
                        onRight = {
                            onInteraction()
                            requestFocus(seekBackRequester)
                        },
                        onUp = {
                            onInteraction()
                            requestFocus(progressRequester)
                        },
                        onDown = {
                            onInteraction()
                            requestSecondaryLeft()
                        },
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
                            if (canPrevious) {
                                requestFocus(previousRequester)
                            } else {
                                true
                            }
                        },
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
                            requestSecondaryLeft()
                        },
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
                            requestFocus(seekBackRequester)
                        },
                        onRight = {
                            onInteraction()
                            requestFocus(seekForwardRequester)
                        },
                        onUp = {
                            onInteraction()
                            requestFocus(progressRequester)
                        },
                        onDown = {
                            onInteraction()
                            requestSecondaryCenter()
                        },
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
                            requestFocus(playPauseRequester)
                        },
                        onRight = {
                            onInteraction()
                            if (canNext) {
                                requestFocus(nextRequester)
                            } else {
                                requestSecondaryRight()
                            }
                        },
                        onUp = {
                            onInteraction()
                            requestFocus(progressRequester)
                        },
                        onDown = {
                            onInteraction()
                            requestSecondaryRight()
                        },
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
                            onControlFocused(PlayerOverlayFocusTarget.Next)
                        },
                        onClick = {
                            onInteraction()
                            onNextClick()
                        },
                        onLeft = {
                            onInteraction()
                            requestFocus(seekForwardRequester)
                        },
                        onRight = {
                            onInteraction()
                            requestSecondaryRight()
                        },
                        onUp = {
                            onInteraction()
                            requestFocus(progressRequester)
                        },
                        onDown = {
                            onInteraction()
                            requestSecondaryRight()
                        },
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(layoutSpec.secondaryRowSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                            requestFocus(playPauseRequester)
                        },
                        onRight = {
                            onInteraction()
                            when {
                                speedEnabled -> requestFocus(speedRequester)
                                qualityEnabled -> requestFocus(qualityRequester)
                                aspectRatioEnabled -> requestFocus(aspectRatioRequester)
                                else -> true
                            }
                        },
                        onUp = {
                            onInteraction()
                            requestFocus(seekBackRequester)
                        },
                        onDown = { true },
                    )
                    PlayerActionButton(
                        text = speedLabel,
                        contentDescription = "${stringResource(R.string.player_action_speed)} $speedLabel",
                        focusRequester = speedRequester,
                        palette = palette,
                        iconRes = R.drawable.ic_play_speed,
                        enabled = speedEnabled,
                        emphasized = isSpeedPickerOpen,
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
                            onOpenSpeedPicker()
                        },
                        onLeft = {
                            onInteraction()
                            requestFocus(episodesRequester)
                        },
                        onRight = {
                            onInteraction()
                            when {
                                qualityEnabled -> requestFocus(qualityRequester)
                                aspectRatioEnabled -> requestFocus(aspectRatioRequester)
                                else -> true
                            }
                        },
                        onUp = {
                            onInteraction()
                            requestFocus(playPauseRequester)
                        },
                        onDown = { true },
                        modifier = Modifier.onGloballyPositioned {
                            onSpeedButtonPositioned(it.boundsInRoot())
                        },
                    )
                    PlayerActionButton(
                        text = qualityLabel,
                        contentDescription = "${stringResource(R.string.player_action_quality)} $qualityLabel",
                        focusRequester = qualityRequester,
                        palette = palette,
                        enabled = qualityEnabled,
                        emphasized = isQualityPickerOpen,
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
                            onOpenQualityPicker()
                        },
                        onLeft = {
                            onInteraction()
                            if (speedEnabled) {
                                requestFocus(speedRequester)
                            } else {
                                requestFocus(episodesRequester)
                            }
                        },
                        onRight = {
                            onInteraction()
                            if (aspectRatioEnabled) {
                                requestFocus(aspectRatioRequester)
                            } else {
                                true
                            }
                        },
                        onUp = {
                            onInteraction()
                            requestFocus(seekForwardRequester)
                        },
                        onDown = { true },
                        modifier = Modifier.onGloballyPositioned {
                            onQualityButtonPositioned(it.boundsInRoot())
                        },
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
                        emphasized = isAspectRatioPickerOpen,
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
                            onOpenAspectRatioPicker()
                        },
                        onLeft = {
                            onInteraction()
                            when {
                                qualityEnabled -> requestFocus(qualityRequester)
                                speedEnabled -> requestFocus(speedRequester)
                                else -> requestFocus(episodesRequester)
                            }
                        },
                        onRight = { true },
                        onUp = {
                            onInteraction()
                            requestFocus(seekForwardRequester)
                        },
                        onDown = { true },
                        modifier = Modifier.onGloballyPositioned {
                            onAspectRatioButtonPositioned(it.boundsInRoot())
                        },
                    )
                }
            }
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

    WatchingFocusableSurface(
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
        onDown = onDown,
        modifier = modifier,
        paddingValues = PlayerOverlayUiDefaults.ProgressSurfacePadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.ProgressContentSpacing)) {
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
    WatchingFocusableSurface(
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
                )
            }
        }
    }
}

private data class PlayerInlinePickerOption(
    val title: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun PlayerInlinePickerPanel(
    title: String,
    options: List<PlayerInlinePickerOption>,
    palette: WatchingPalette,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(options) { List(options.size) { FocusRequester() } }
    LaunchedEffect(options) {
        if (options.isEmpty()) {
            return@LaunchedEffect
        }
        val selectedIndex = options.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
        requestWatchingFocusAfterAttach(focusRequesters.getOrNull(selectedIndex))
    }

    Box(
        modifier = modifier
            .widthIn(
                min = PlayerOverlayUiDefaults.PickerMinWidth,
                max = PlayerOverlayUiDefaults.PickerMaxWidth,
            )
            .playerPanelSurface(PlayerOverlayUiDefaults.pickerPanelStyle(palette))
            .padding(PlayerOverlayUiDefaults.PickerPanelPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.PickerSectionSpacing)) {
            Text(
                text = title,
                color = palette.secondaryTextColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.PickerOptionSpacing)) {
                options.forEachIndexed { index, option ->
                    PlayerInlinePickerButton(
                        title = option.title,
                        selected = option.selected,
                        palette = palette,
                        focusRequester = focusRequesters[index],
                        onClick = option.onClick,
                        onLeft = onDismiss,
                        onUp = {
                            focusRequesters.getOrNull(index - 1)?.let(::requestFocus) ?: true
                        },
                        onDown = {
                            focusRequesters.getOrNull(index + 1)?.let(::requestFocus) ?: true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerInlinePickerButton(
    title: String,
    selected: Boolean,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onLeft: () -> Unit,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
) {
    val optionColors = PlayerOverlayUiDefaults.pickerOptionColors(
        palette = palette,
        selected = selected,
    )
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        backgroundColor = optionColors.backgroundColor,
        focusedBackgroundColor = optionColors.focusedBackgroundColor,
        borderColor = optionColors.borderColor,
        onClick = onClick,
        onLeft = {
            onLeft()
            true
        },
        onRight = { true },
        onUp = onUp,
        onDown = onDown,
        paddingValues = PlayerOverlayUiDefaults.CompactControlPadding,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.PickerIndicatorSpacing),
        ) {
            Box(
                modifier = Modifier
                    .size(PlayerOverlayUiDefaults.PickerIndicatorSize)
                    .background(
                        color = if (selected) palette.accentColor else Color.Transparent,
                        shape = PlayerOverlayUiDefaults.ProgressBarShape,
                    )
                    .border(
                        width = 1.dp,
                        color = PlayerOverlayUiDefaults.pickerIndicatorBorderColor(
                            palette = palette,
                            selected = selected,
                        ),
                        shape = PlayerOverlayUiDefaults.ProgressBarShape,
                    ),
            )
            Text(
                text = title,
                color = palette.textColor,
                fontSize = 16.sp,
            )
        }
    }
}

@Preview(
    name = "Player Controls Runtime",
    widthDp = 1280,
    heightDp = 720,
    showBackground = true,
    backgroundColor = 0xFF0B1118,
)
@Composable
private fun PlayerControlsRuntimePreview() {
    PlayerControlsLayoutPreviewScene(layoutSpec = PlayerControlsLayoutPresets.Runtime)
}

@Preview(
    name = "Player Controls Compact",
    widthDp = 1280,
    heightDp = 720,
    showBackground = true,
    backgroundColor = 0xFF0B1118,
)
@Composable
private fun PlayerControlsCompactPreview() {
    PlayerControlsLayoutPreviewScene(layoutSpec = PlayerControlsLayoutPresets.Compact)
}

@Preview(
    name = "Player Controls Balanced",
    widthDp = 1280,
    heightDp = 720,
    showBackground = true,
    backgroundColor = 0xFF0B1118,
)
@Composable
private fun PlayerControlsBalancedPreview() {
    PlayerControlsLayoutPreviewScene(layoutSpec = PlayerControlsLayoutPresets.Balanced)
}

@Composable
private fun PlayerControlsLayoutPreviewScene(
    layoutSpec: PlayerControlsLayoutSpec,
) {
    val palette = rememberWatchingPalette()
    val progressRequester = remember { FocusRequester() }
    val buttonRequesters = remember {
        PlayerControlButtonId.values().associateWith { FocusRequester() }
    }
    val skipRequester = remember { FocusRequester() }
    val watchRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF182332),
                        Color(0xFF0B1118),
                    ),
                ),
            )
            .padding(horizontal = 56.dp, vertical = 40.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        PlayerControlsPanel(
            isPlaying = true,
            currentPositionMs = 26 * MILLIS_PER_SECOND + 18_000L,
            durationMs = 24 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND + 12 * MILLIS_PER_SECOND,
            bufferedPositionMs = 7 * SECONDS_PER_MINUTE * MILLIS_PER_SECOND,
            qualityLabel = "1080",
            speedLabel = "1.25x",
            aspectRatioMode = PlayerAspectRatioMode.FIT,
            canPrevious = true,
            canNext = true,
            qualityEnabled = true,
            speedEnabled = true,
            aspectRatioEnabled = true,
            palette = palette,
            progressRequester = progressRequester,
            previousRequester = buttonRequesters.getValue(PlayerControlButtonId.Previous),
            seekBackRequester = buttonRequesters.getValue(PlayerControlButtonId.SeekBack),
            playPauseRequester = buttonRequesters.getValue(PlayerControlButtonId.PlayPause),
            seekForwardRequester = buttonRequesters.getValue(PlayerControlButtonId.SeekForward),
            nextRequester = buttonRequesters.getValue(PlayerControlButtonId.Next),
            qualityRequester = buttonRequesters.getValue(PlayerControlButtonId.Quality),
            speedRequester = buttonRequesters.getValue(PlayerControlButtonId.Speed),
            aspectRatioRequester = buttonRequesters.getValue(PlayerControlButtonId.AspectRatio),
            episodesRequester = buttonRequesters.getValue(PlayerControlButtonId.Episodes),
            onQualityButtonPositioned = {},
            onSpeedButtonPositioned = {},
            onAspectRatioButtonPositioned = {},
            onControlFocused = {},
            onInteraction = {},
            onTogglePlayback = {},
            onSeekBack = {},
            onSeekForward = {},
            onPreviousClick = {},
            onNextClick = {},
            isQualityPickerOpen = false,
            isSpeedPickerOpen = false,
            isAspectRatioPickerOpen = false,
            onOpenQualityPicker = {},
            onOpenSpeedPicker = {},
            onOpenAspectRatioPicker = {},
            onEpisodesClick = {},
            modifier = Modifier.fillMaxWidth(layoutSpec.panelWidthFraction),
            layoutSpec = layoutSpec,
        )
        PlayerSkipsButtonsRow(
            palette = palette,
            skipRequester = skipRequester,
            watchRequester = watchRequester,
            onInteraction = {},
            onOpenControls = {},
            onSkipClick = {},
            onWatchClick = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = TvPlayerOverlayHorizontalPadding),
            panelWidthFraction = layoutSpec.panelWidthFraction,
            bottomPadding = 92.dp,
        )
    }
}

private fun requestFocus(focusRequester: FocusRequester): Boolean {
    return runCatching {
        focusRequester.requestFocus()
        true
    }.getOrDefault(false)
}

private fun calculateAnchoredPickerOffset(
    rootSizePx: IntSize,
    pickerSizePx: IntSize,
    anchorBounds: Rect?,
    fallbackBottomPaddingPx: Int,
    horizontalPaddingPx: Int,
    topPaddingPx: Int,
    gapPx: Int,
): IntOffset {
    val pickerWidth = pickerSizePx.width
    val pickerHeight = pickerSizePx.height
    return if (rootSizePx == IntSize.Zero || pickerWidth == 0 || pickerHeight == 0) {
        IntOffset.Zero
    } else {
        val minX = horizontalPaddingPx
        val maxX = (rootSizePx.width - horizontalPaddingPx - pickerWidth).coerceAtLeast(minX)
        val fallbackX = ((rootSizePx.width - pickerWidth) / 2).coerceIn(minX, maxX)
        val fallbackY = (rootSizePx.height - fallbackBottomPaddingPx - pickerHeight)
            .coerceAtLeast(topPaddingPx)

        val anchor = anchorBounds
        if (anchor == null) {
            IntOffset(fallbackX, fallbackY)
        } else {
            val anchoredX = (anchor.center.x - pickerWidth / 2f).roundToInt().coerceIn(minX, maxX)
            val maxY = (rootSizePx.height - pickerHeight - topPaddingPx).coerceAtLeast(topPaddingPx)
            val anchoredY = (anchor.top - gapPx - pickerHeight).roundToInt().coerceIn(topPaddingPx, maxY)
            IntOffset(anchoredX, anchoredY)
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

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val CONTROLS_AUTO_HIDE_DELAY_MS = 4_500L
