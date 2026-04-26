package ru.radiationx.anilibria.screen.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.entity.domain.types.EpisodeId

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
    availableEpisodes: List<PlayerViewModel.EpisodeOptionUiModel>,
    selectedEpisodeId: EpisodeId?,
    aspectRatioMode: PlayerAspectRatioMode,
    availableQualities: List<PlayerQuality>,
    availableSpeeds: List<Float>,
    canPrevious: Boolean,
    canNext: Boolean,
    skipsPart: PlayerSkipsPart?,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onShowControls: (PlayerOverlayFocusTarget?) -> Unit,
    onShowControlsFromQuickActions: () -> Unit,
    onAutoHideControls: () -> Unit,
    onQuickActionHandled: (PlayerQuickActionHandling) -> Unit,
    onBackRequested: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onDismissPicker: (PlayerOverlayPicker) -> Unit,
    onEpisodeSelected: (EpisodeId) -> Unit,
    onQualitySelected: (PlayerQuality) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onAspectRatioSelected: (PlayerAspectRatioMode) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val skipVisible = skipsPart?.visibleSkip != null
    val episodesControlEnabled = availableEpisodes.size > 1
    val qualityControlsEnabled = availableQualities.isNotEmpty()
    val speedControlsEnabled = availableSpeeds.isNotEmpty()
    val aspectRatioControlEnabled = PlayerAspectRatioMode.entries.size > 1
    val quickActionsControlsFocusToken = skipsPart?.controlsFocusTransferToken ?: 0
    var autoHideToken by remember { mutableIntStateOf(0) }
    var controlsPanelHeightPx by remember { mutableIntStateOf(0) }
    var handledQuickActionsControlsFocusToken by remember { mutableIntStateOf(0) }

    fun registerInteraction() {
        autoHideToken += 1
    }

    val rootRequester = remember { FocusRequester() }
    val progressRequester = remember { FocusRequester() }
    val playPauseRequester = remember { FocusRequester() }
    val seekBackRequester = remember { FocusRequester() }
    val seekForwardRequester = remember { FocusRequester() }
    val previousRequester = remember { FocusRequester() }
    val nextRequester = remember { FocusRequester() }
    val episodesRequester = remember { FocusRequester() }
    val qualityRequester = remember { FocusRequester() }
    val speedRequester = remember { FocusRequester() }
    val aspectRatioRequester = remember { FocusRequester() }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester =
        when (target) {
            PlayerOverlayFocusTarget.Root -> rootRequester
            PlayerOverlayFocusTarget.Progress -> progressRequester
            PlayerOverlayFocusTarget.PlayPause -> playPauseRequester
            PlayerOverlayFocusTarget.SeekBack -> seekBackRequester
            PlayerOverlayFocusTarget.SeekForward -> seekForwardRequester
            PlayerOverlayFocusTarget.PreviousEpisode -> previousRequester
            PlayerOverlayFocusTarget.NextEpisode -> nextRequester
            PlayerOverlayFocusTarget.Episodes -> episodesRequester
            PlayerOverlayFocusTarget.Quality -> qualityRequester
            PlayerOverlayFocusTarget.Speed -> speedRequester
            PlayerOverlayFocusTarget.AspectRatio -> aspectRatioRequester
        }

    fun firstAvailable(vararg targets: PlayerOverlayFocusTarget): PlayerOverlayFocusTarget {
        return targets.firstOrNull { target ->
            when (target) {
                PlayerOverlayFocusTarget.Root -> false
                PlayerOverlayFocusTarget.Progress -> true
                PlayerOverlayFocusTarget.PlayPause -> true
                PlayerOverlayFocusTarget.SeekBack -> true
                PlayerOverlayFocusTarget.SeekForward -> true
                PlayerOverlayFocusTarget.PreviousEpisode -> canPrevious
                PlayerOverlayFocusTarget.NextEpisode -> canNext
                PlayerOverlayFocusTarget.Episodes -> episodesControlEnabled
                PlayerOverlayFocusTarget.Quality -> qualityControlsEnabled
                PlayerOverlayFocusTarget.Speed -> speedControlsEnabled
                PlayerOverlayFocusTarget.AspectRatio -> aspectRatioControlEnabled
            }
        } ?: PlayerOverlayFocusTarget.PlayPause
    }

    fun resolveFocusTarget(target: PlayerOverlayFocusTarget): PlayerOverlayFocusTarget =
        when (target) {
            PlayerOverlayFocusTarget.Root ->
                firstAvailable(
                    PlayerOverlayFocusTarget.PlayPause,
                    PlayerOverlayFocusTarget.Progress,
                    PlayerOverlayFocusTarget.SeekBack,
                    PlayerOverlayFocusTarget.SeekForward,
                    PlayerOverlayFocusTarget.PreviousEpisode,
                    PlayerOverlayFocusTarget.NextEpisode,
                    PlayerOverlayFocusTarget.Episodes,
                    PlayerOverlayFocusTarget.Quality,
                    PlayerOverlayFocusTarget.Speed,
                    PlayerOverlayFocusTarget.AspectRatio,
                )

            PlayerOverlayFocusTarget.Progress -> PlayerOverlayFocusTarget.Progress
            PlayerOverlayFocusTarget.PlayPause -> PlayerOverlayFocusTarget.PlayPause
            PlayerOverlayFocusTarget.SeekBack -> PlayerOverlayFocusTarget.SeekBack
            PlayerOverlayFocusTarget.SeekForward -> PlayerOverlayFocusTarget.SeekForward
            PlayerOverlayFocusTarget.PreviousEpisode ->
                firstAvailable(
                    PlayerOverlayFocusTarget.PreviousEpisode,
                    PlayerOverlayFocusTarget.SeekForward,
                    PlayerOverlayFocusTarget.SeekBack,
                    PlayerOverlayFocusTarget.PlayPause,
                    PlayerOverlayFocusTarget.NextEpisode,
                )

            PlayerOverlayFocusTarget.NextEpisode ->
                firstAvailable(
                    PlayerOverlayFocusTarget.NextEpisode,
                    PlayerOverlayFocusTarget.Episodes,
                    PlayerOverlayFocusTarget.Quality,
                    PlayerOverlayFocusTarget.Speed,
                    PlayerOverlayFocusTarget.AspectRatio,
                    PlayerOverlayFocusTarget.PlayPause,
                )

            PlayerOverlayFocusTarget.Episodes ->
                firstAvailable(
                    PlayerOverlayFocusTarget.Episodes,
                    PlayerOverlayFocusTarget.Quality,
                    PlayerOverlayFocusTarget.Speed,
                    PlayerOverlayFocusTarget.AspectRatio,
                    PlayerOverlayFocusTarget.PlayPause,
                )

            PlayerOverlayFocusTarget.Quality ->
                firstAvailable(
                    PlayerOverlayFocusTarget.Quality,
                    PlayerOverlayFocusTarget.Episodes,
                    PlayerOverlayFocusTarget.Speed,
                    PlayerOverlayFocusTarget.AspectRatio,
                    PlayerOverlayFocusTarget.PlayPause,
                )

            PlayerOverlayFocusTarget.Speed ->
                firstAvailable(
                    PlayerOverlayFocusTarget.Speed,
                    PlayerOverlayFocusTarget.Episodes,
                    PlayerOverlayFocusTarget.Quality,
                    PlayerOverlayFocusTarget.AspectRatio,
                    PlayerOverlayFocusTarget.PlayPause,
                )

            PlayerOverlayFocusTarget.AspectRatio ->
                firstAvailable(
                    PlayerOverlayFocusTarget.AspectRatio,
                    PlayerOverlayFocusTarget.Speed,
                    PlayerOverlayFocusTarget.Episodes,
                    PlayerOverlayFocusTarget.Quality,
                    PlayerOverlayFocusTarget.PlayPause,
                )
        }

    val pickerOptions =
        when (activePicker) {
            PlayerOverlayPicker.Episodes ->
                availableEpisodes.map { episode ->
                    PlayerPickerOption(
                        id = "${episode.episodeId.releaseId.id}:${episode.episodeId.id}",
                        label = episode.label,
                        selected = episode.episodeId == selectedEpisodeId,
                        onSelected = { onEpisodeSelected(episode.episodeId) },
                    )
                }

            PlayerOverlayPicker.Quality ->
                availableQualities.map { quality ->
                    PlayerPickerOption(
                        id = "quality_${quality.name}",
                        label = quality.asPlayerLabel(),
                        selected = quality == selectedQuality,
                        onSelected = { onQualitySelected(quality) },
                    )
                }

            PlayerOverlayPicker.Speed ->
                availableSpeeds.map { speed ->
                    PlayerPickerOption(
                        id = "speed_$speed",
                        label = speed.asPlayerLabel(),
                        selected = kotlin.math.abs(speed - selectedSpeed) < 0.001f,
                        onSelected = { onSpeedSelected(speed) },
                    )
                }

            PlayerOverlayPicker.AspectRatio ->
                PlayerAspectRatioMode.entries.map { mode ->
                    PlayerPickerOption(
                        id = "aspect_${mode.name}",
                        label = stringResource(mode.titleRes),
                        selected = mode == aspectRatioMode,
                        onSelected = { onAspectRatioSelected(mode) },
                    )
                }

            null -> emptyList()
        }

    LaunchedEffect(
        controlsVisible,
        controlsFocusToken,
        controlsFocusTarget,
        activePicker,
        skipVisible,
        quickActionsControlsFocusToken,
    ) {
        val quickActionsTransferPending =
            quickActionsControlsFocusToken > handledQuickActionsControlsFocusToken
        if (!shouldRequestPlayerControlsFocus(
                controlsVisible = controlsVisible,
                hasActivePicker = activePicker != null,
                skipVisible = skipVisible,
                quickActionsTransferPending = quickActionsTransferPending,
            )
        ) {
            return@LaunchedEffect
        }
        requestWatchingFocusAfterAttach(requesterFor(resolveFocusTarget(controlsFocusTarget)))
        if (quickActionsTransferPending) {
            handledQuickActionsControlsFocusToken = quickActionsControlsFocusToken
        }
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
        modifier =
            Modifier
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
                        Key.Escape,
                        -> {
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
                        Key.NumPadEnter,
                        -> {
                            if (!controlsVisible && !skipVisible) {
                                registerInteraction()
                                onShowControls(PlayerOverlayFocusTarget.PlayPause)
                                true
                            } else {
                                false
                            }
                        }

                        Key.DirectionUp,
                        Key.DirectionDown,
                        -> {
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
                contentPadding =
                    PaddingValues(
                        horizontal = TvPlayerOverlayHorizontalPadding,
                        vertical = 24.dp,
                    ),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier =
                Modifier
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
                        modifier =
                            Modifier.widthIn(
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
                    progressRequester = progressRequester,
                    playPauseRequester = playPauseRequester,
                    seekBackRequester = seekBackRequester,
                    seekForwardRequester = seekForwardRequester,
                    previousRequester = previousRequester,
                    nextRequester = nextRequester,
                    episodesRequester = episodesRequester,
                    qualityRequester = qualityRequester,
                    speedRequester = speedRequester,
                    aspectRatioRequester = aspectRatioRequester,
                    onControlFocused = onControlFocused,
                    onInteraction = ::registerInteraction,
                    onTogglePlayback = onTogglePlayback,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    episodesEnabled = episodesControlEnabled,
                    onEpisodesClick = onEpisodesClick,
                    onQualityClick = onQualityClick,
                    onSpeedClick = onSpeedClick,
                    onAspectRatioClick = onAspectRatioClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { controlsPanelHeightPx = it.height },
                )
            }
        }

        PlayerSkipsOverlay(
            skipsPart = skipsPart,
            controlsVisible = controlsVisible,
            onInteraction = ::registerInteraction,
            onQuickActionHandled = onQuickActionHandled,
            onOpenControls = onShowControlsFromQuickActions,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = PlayerPanelHorizontalInset),
            bottomPadding =
                if (controlsVisible) {
                    with(LocalDensity.current) {
                        controlsPanelHeightPx.toDp() + 52.dp
                    }
                } else {
                    52.dp
                },
            panelWidthFraction = PlayerControlsRuntimeLayout.panelWidthFraction,
        )

        if (isLoading || isBuffering) {
            val loadingPanelStyle = PlayerOverlayUiDefaults.loadingPanelStyle()
            Box(
                modifier =
                    Modifier
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

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 4_500L
private val PlayerPanelHorizontalInset = 20.dp
