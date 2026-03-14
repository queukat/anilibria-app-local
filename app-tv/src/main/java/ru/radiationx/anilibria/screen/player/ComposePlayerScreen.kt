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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
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
    Episodes,
}

private enum class PlayerInlinePicker {
    Quality,
    Speed,
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
    onEpisodesClick: () -> Unit,
) {
    val palette = rememberWatchingPalette()
    val skipVisible = skipsPart?.isVisible == true
    var autoHideToken by remember { mutableIntStateOf(0) }
    var controlsPanelHeightPx by remember { mutableIntStateOf(0) }
    var activePicker by remember { mutableStateOf<PlayerInlinePicker?>(null) }
    var pendingFocusRestoreTarget by remember { mutableStateOf<PlayerOverlayFocusTarget?>(null) }
    var rootSizePx by remember { mutableStateOf(IntSize.Zero) }
    var qualityButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var speedButtonBounds by remember { mutableStateOf<Rect?>(null) }

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
                        if (!controlsVisible) {
                            registerInteraction()
                            onShowControls(null)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionUp,
                    Key.DirectionDown -> {
                        if (!controlsVisible) {
                            registerInteraction()
                            onShowControls(null)
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
                canPrevious = canPrevious,
                canNext = canNext,
                qualityEnabled = availableQualities.isNotEmpty(),
                speedEnabled = availableSpeeds.isNotEmpty(),
                palette = palette,
                progressRequester = progressRequester,
                previousRequester = previousRequester,
                seekBackRequester = seekBackRequester,
                playPauseRequester = playPauseRequester,
                seekForwardRequester = seekForwardRequester,
                nextRequester = nextRequester,
                qualityRequester = qualityRequester,
                speedRequester = speedRequester,
                episodesRequester = episodesRequester,
                onQualityButtonPositioned = { qualityButtonBounds = it },
                onSpeedButtonPositioned = { speedButtonBounds = it },
                onControlFocused = onControlFocused,
                onInteraction = ::registerInteraction,
                onTogglePlayback = onTogglePlayback,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                isQualityPickerOpen = activePicker == PlayerInlinePicker.Quality,
                isSpeedPickerOpen = activePicker == PlayerInlinePicker.Speed,
                onOpenQualityPicker = {
                    openPicker(PlayerInlinePicker.Quality, PlayerOverlayFocusTarget.Quality)
                },
                onOpenSpeedPicker = {
                    openPicker(PlayerInlinePicker.Speed, PlayerOverlayFocusTarget.Speed)
                },
                onEpisodesClick = onEpisodesClick,
                modifier = Modifier
                    .fillMaxWidth()
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

                null -> Unit
            }
        }

        PlayerSkipsOverlay(
            skipsPart = skipsPart,
            onInteraction = ::registerInteraction,
            modifier = Modifier.align(Alignment.BottomEnd),
            bottomPadding = if (controlsVisible) {
                with(androidx.compose.ui.platform.LocalDensity.current) {
                    controlsPanelHeightPx.toDp() + 52.dp
                }
            } else {
                52.dp
            },
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
                        fontSize = 17.sp,
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
    qualityEnabled: Boolean,
    speedEnabled: Boolean,
    palette: WatchingPalette,
    progressRequester: FocusRequester,
    previousRequester: FocusRequester,
    seekBackRequester: FocusRequester,
    playPauseRequester: FocusRequester,
    seekForwardRequester: FocusRequester,
    nextRequester: FocusRequester,
    qualityRequester: FocusRequester,
    speedRequester: FocusRequester,
    episodesRequester: FocusRequester,
    onQualityButtonPositioned: (Rect) -> Unit,
    onSpeedButtonPositioned: (Rect) -> Unit,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onInteraction: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    isQualityPickerOpen: Boolean,
    isSpeedPickerOpen: Boolean,
    onOpenQualityPicker: () -> Unit,
    onOpenSpeedPicker: () -> Unit,
    onEpisodesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun requestSecondaryLeft(): Boolean {
        return requestFocus(episodesRequester)
    }

    fun requestSecondaryCenter(): Boolean {
        return when {
            speedEnabled -> requestFocus(speedRequester)
            qualityEnabled -> requestFocus(qualityRequester)
            else -> requestFocus(episodesRequester)
        }
    }

    fun requestSecondaryRight(): Boolean {
        return when {
            qualityEnabled -> requestFocus(qualityRequester)
            speedEnabled -> requestFocus(speedRequester)
            else -> requestFocus(episodesRequester)
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .border(
                width = 1.dp,
                color = palette.textColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp),
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
                .padding(horizontal = 18.dp, vertical = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerActionButton(
                    contentDescription = stringResource(R.string.player_action_previous),
                    focusRequester = previousRequester,
                    palette = palette,
                    iconRes = R.drawable.ic_player_skip_previous,
                    enabled = canPrevious,
                    minWidth = 72.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 10.dp,
                    iconSize = 22.dp,
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
                        requestFocus(episodesRequester)
                    },
                )
                PlayerActionButton(
                    text = "-10",
                    contentDescription = stringResource(R.string.player_action_rewind),
                    focusRequester = seekBackRequester,
                    palette = palette,
                    minWidth = 74.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
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
                        requestFocus(episodesRequester)
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
                    minWidth = 80.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    iconSize = 26.dp,
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
                    text = "+10",
                    contentDescription = stringResource(R.string.player_action_forward),
                    focusRequester = seekForwardRequester,
                    palette = palette,
                    minWidth = 74.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
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
                            true
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
                    minWidth = 72.dp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 10.dp,
                    iconSize = 22.dp,
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
                        true
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerActionButton(
                    text = stringResource(R.string.player_action_episodes_compact),
                    contentDescription = stringResource(R.string.player_action_episodes),
                    focusRequester = episodesRequester,
                    palette = palette,
                    iconRes = R.drawable.ic_playlist_play_black_24dp,
                    minWidth = 96.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    iconSize = 18.dp,
                    textFontSize = 16.sp,
                    onFocused = {
                        onInteraction()
                        onControlFocused(PlayerOverlayFocusTarget.Episodes)
                    },
                    onClick = {
                        onInteraction()
                        onEpisodesClick()
                    },
                    onLeft = { true },
                    onRight = {
                        onInteraction()
                        when {
                            speedEnabled -> requestFocus(speedRequester)
                            qualityEnabled -> requestFocus(qualityRequester)
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
                    minWidth = 96.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    iconSize = 18.dp,
                    textFontSize = 16.sp,
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
                        if (qualityEnabled) {
                            requestFocus(qualityRequester)
                        } else {
                            true
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
                    minWidth = 80.dp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    textFontSize = 16.sp,
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
                    onRight = { true },
                    onUp = {
                        onInteraction()
                        requestFocus(seekForwardRequester)
                    },
                    onDown = { true },
                    modifier = Modifier.onGloballyPositioned {
                        onQualityButtonPositioned(it.boundsInRoot())
                    },
                )
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

    WatchingFocusableSurface(
        focusRequester = focusRequester,
        backgroundColor = palette.surfaceColor.copy(alpha = 0.88f),
        focusedBackgroundColor = palette.surfaceColor,
        borderColor = palette.accentColor.copy(alpha = 0.92f),
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
        paddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    .height(7.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .height(7.dp)
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
    contentDescription: String,
    focusRequester: FocusRequester,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    text: String? = null,
    iconRes: Int? = null,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    minWidth: androidx.compose.ui.unit.Dp = 168.dp,
    horizontalPadding: androidx.compose.ui.unit.Dp = 18.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 14.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    textFontSize: TextUnit = 16.sp,
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
        modifier = modifier
            .widthIn(min = minWidth)
            .semantics { this.contentDescription = contentDescription },
        paddingValues = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (text != null && iconRes != null) 6.dp else 0.dp),
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
            .widthIn(min = 240.dp, max = 300.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surfaceColor.copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                color = palette.textColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                color = palette.secondaryTextColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        backgroundColor = if (selected) {
            palette.accentColor.copy(alpha = 0.14f)
        } else {
            palette.surfaceColor.copy(alpha = 0.68f)
        },
        focusedBackgroundColor = if (selected) {
            palette.accentColor.copy(alpha = 0.22f)
        } else {
            palette.surfaceColor
        },
        borderColor = if (selected) {
            palette.accentColor.copy(alpha = 0.92f)
        } else {
            palette.textColor.copy(alpha = 0.76f)
        },
        onClick = onClick,
        onLeft = {
            onLeft()
            true
        },
        onRight = { true },
        onUp = onUp,
        onDown = onDown,
        paddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (selected) palette.accentColor else Color.Transparent,
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) {
                            palette.accentColor
                        } else {
                            palette.textColor.copy(alpha = 0.22f)
                        },
                        shape = RoundedCornerShape(percent = 50),
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
    PlayerQuality.FULLHD -> "FHD"
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
