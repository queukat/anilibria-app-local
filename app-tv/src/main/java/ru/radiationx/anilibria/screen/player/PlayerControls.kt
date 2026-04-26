package ru.radiationx.anilibria.screen.player

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.WatchingPalette

@Composable
internal fun PlayerControlsPanel(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    qualityLabel: String,
    speedLabel: String,
    aspectRatioMode: PlayerAspectRatioMode,
    canPrevious: Boolean,
    canNext: Boolean,
    episodesEnabled: Boolean,
    qualityEnabled: Boolean,
    speedEnabled: Boolean,
    aspectRatioEnabled: Boolean,
    palette: WatchingPalette,
    progressRequester: FocusRequester,
    playPauseRequester: FocusRequester,
    seekBackRequester: FocusRequester,
    seekForwardRequester: FocusRequester,
    previousRequester: FocusRequester,
    nextRequester: FocusRequester,
    episodesRequester: FocusRequester,
    qualityRequester: FocusRequester,
    speedRequester: FocusRequester,
    aspectRatioRequester: FocusRequester,
    onControlFocused: (PlayerOverlayFocusTarget) -> Unit,
    onInteraction: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onEpisodesClick: () -> Unit,
    onQualityClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = PlayerControlsRuntimeLayout
    val playPauseButtonLayout = layout.button(PlayerControlButtonId.PlayPause)
    val seekBackButtonLayout = layout.button(PlayerControlButtonId.SeekBack)
    val seekForwardButtonLayout = layout.button(PlayerControlButtonId.SeekForward)
    val previousButtonLayout = layout.button(PlayerControlButtonId.Previous)
    val nextButtonLayout = layout.button(PlayerControlButtonId.Next)
    val episodesButtonLayout = layout.button(PlayerControlButtonId.Episodes)
    val qualityButtonLayout = layout.button(PlayerControlButtonId.Quality)
    val speedButtonLayout = layout.button(PlayerControlButtonId.Speed)
    val aspectRatioButtonLayout = layout.button(PlayerControlButtonId.AspectRatio)

    val focusableButtonTargets =
        buildList {
            add(PlayerOverlayFocusTarget.PlayPause)
            add(PlayerOverlayFocusTarget.SeekBack)
            add(PlayerOverlayFocusTarget.SeekForward)
            if (canPrevious) add(PlayerOverlayFocusTarget.PreviousEpisode)
            if (canNext) add(PlayerOverlayFocusTarget.NextEpisode)
            if (episodesEnabled) add(PlayerOverlayFocusTarget.Episodes)
            if (qualityEnabled) add(PlayerOverlayFocusTarget.Quality)
            if (speedEnabled) add(PlayerOverlayFocusTarget.Speed)
            if (aspectRatioEnabled) add(PlayerOverlayFocusTarget.AspectRatio)
        }

    fun requesterFor(target: PlayerOverlayFocusTarget): FocusRequester =
        when (target) {
            PlayerOverlayFocusTarget.Root -> playPauseRequester
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

    fun requestAdjacent(
        current: PlayerOverlayFocusTarget,
        step: Int,
    ): Boolean {
        val currentIndex = focusableButtonTargets.indexOf(current)
        if (currentIndex < 0) {
            return false
        }
        val nextTarget = focusableButtonTargets.getOrNull(currentIndex + step) ?: return false
        return requestFocus(requesterFor(nextTarget))
    }

    fun requestPrimaryControlFocus(): Boolean {
        val target = focusableButtonTargets.firstOrNull() ?: return false
        return requestFocus(requesterFor(target))
    }

    Column(
        modifier =
            modifier
                .playerPanelSurface(PlayerOverlayUiDefaults.controlsPanelStyle())
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
            focusRequester = progressRequester,
            onControlFocused = onControlFocused,
            onInteraction = onInteraction,
            onTogglePlayback = onTogglePlayback,
            onSeekBack = onSeekBack,
            onSeekForward = onSeekForward,
            onDown = ::requestPrimaryControlFocus,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    space = layout.primaryRowSpacing,
                    alignment = Alignment.CenterHorizontally,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerActionButton(
                contentDescription =
                    androidx.compose.ui.res.stringResource(
                        if (isPlaying) {
                            R.string.player_action_pause
                        } else {
                            R.string.player_action_play
                        },
                    ),
                focusRequester = playPauseRequester,
                palette = palette,
                iconRes =
                    if (isPlaying) {
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
                onDown = { true },
            )

            PlayerActionButton(
                contentDescription = androidx.compose.ui.res.stringResource(R.string.player_action_rewind),
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
                onDown = { true },
            )

            PlayerActionButton(
                contentDescription = androidx.compose.ui.res.stringResource(R.string.player_action_forward),
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
                onDown = { true },
            )

            PlayerActionButton(
                contentDescription = androidx.compose.ui.res.stringResource(R.string.player_action_previous),
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
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { true },
            )

            PlayerActionButton(
                contentDescription = androidx.compose.ui.res.stringResource(R.string.player_action_next),
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
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { true },
            )

            PlayerActionButton(
                text = androidx.compose.ui.res.stringResource(R.string.player_action_episodes_compact),
                contentDescription = androidx.compose.ui.res.stringResource(R.string.player_action_episodes),
                focusRequester = episodesRequester,
                palette = palette,
                enabled = episodesEnabled,
                minWidth = episodesButtonLayout.minWidth,
                horizontalPadding = episodesButtonLayout.horizontalPadding,
                verticalPadding = episodesButtonLayout.verticalPadding,
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
                onDown = { true },
            )

            PlayerActionButton(
                text = qualityLabel,
                contentDescription = "${androidx.compose.ui.res.stringResource(R.string.player_action_quality)} $qualityLabel",
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
                onDown = { true },
            )

            PlayerActionButton(
                text = speedLabel,
                contentDescription = "${androidx.compose.ui.res.stringResource(R.string.player_action_speed)} $speedLabel",
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
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { true },
            )

            PlayerActionButton(
                text = androidx.compose.ui.res.stringResource(aspectRatioMode.compactTitleRes),
                contentDescription =
                    buildString {
                        append(androidx.compose.ui.res.stringResource(R.string.player_action_aspect_ratio))
                        append(' ')
                        append(androidx.compose.ui.res.stringResource(aspectRatioMode.titleRes))
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
                onUp = {
                    onInteraction()
                    requestFocus(progressRequester)
                },
                onDown = { true },
            )
        }
    }
}

internal fun Modifier.playerPanelSurface(style: PlayerPanelSurfaceStyle): Modifier {
    return clip(style.shape)
        .background(style.backgroundColor)
        .border(
            width = style.borderWidth,
            color = style.borderColor,
            shape = style.shape,
        )
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
    val buttonColors =
        PlayerOverlayUiDefaults.actionButtonColors(
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
        modifier =
            modifier
                .width(minWidth)
                .semantics { this.contentDescription = contentDescription },
        paddingValues = PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    space =
                        if (text != null && iconRes != null) {
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
internal fun PlayerControlSurface(
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
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = if (isFocused) PlayerOverlayUiDefaults.PLAYER_FOCUS_SCALE else 1f
                    scaleY = if (isFocused) PlayerOverlayUiDefaults.PLAYER_FOCUS_SCALE else 1f
                    shadowElevation =
                        if (isFocused) {
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
                    color =
                        when {
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
                    when (resolvePlayerControlSurfaceKeyAction(canFocus, enabled, event.key, event.type)) {
                        PlayerControlSurfaceKeyAction.Consume -> true
                        PlayerControlSurfaceKeyAction.Click -> {
                            onClick()
                            true
                        }

                        PlayerControlSurfaceKeyAction.MoveLeft -> onLeft?.invoke() == true
                        PlayerControlSurfaceKeyAction.MoveUp -> onUp?.invoke() == true
                        PlayerControlSurfaceKeyAction.MoveRight -> onRight?.invoke() == true
                        PlayerControlSurfaceKeyAction.MoveDown -> onDown?.invoke() == true
                        PlayerControlSurfaceKeyAction.Ignore -> false
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
                    },
                )
                .then(if (canFocus) Modifier.focusable() else Modifier)
                .padding(paddingValues),
    ) {
        content()
    }
}
