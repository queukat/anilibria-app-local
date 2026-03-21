package ru.radiationx.anilibria.screen.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.TvPlayerOverlayBottomPadding
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.data.entity.domain.release.PlayerSkips

class PlayerSkipsPart(
    private val onSeek: (Long) -> Unit,
) {

    private var playerSkips: PlayerSkips? = null
    private val skippedList = mutableSetOf<PlayerSkips.Skip>()
    private var currentPosition = 0L

    var visibleSkip by mutableStateOf<PlayerSkips.Skip?>(null)
        private set

    var focusRequestToken by mutableIntStateOf(0)
        private set

    val isVisible: Boolean
        get() = visibleSkip != null

    fun setSkips(skips: PlayerSkips?) {
        playerSkips = skips
        skippedList.clear()
        visibleSkip = null
    }

    fun update(position: Long) {
        currentPosition = position
        autoCancel()

        val nextVisibleSkip = getCurrentSkip()
        if (nextVisibleSkip != null && nextVisibleSkip != visibleSkip) {
            focusRequestToken += 1
        }
        visibleSkip = nextVisibleSkip
    }

    fun skipCurrent() {
        visibleSkip?.also {
            skippedList.add(it)
            onSeek(it.end)
        }
        dismissCurrent()
    }

    fun cancelCurrent() {
        visibleSkip?.also(skippedList::add)
        dismissCurrent()
    }

    private fun dismissCurrent() {
        visibleSkip = null
    }

    private fun getCurrentSkip(): PlayerSkips.Skip? {
        return playerSkips?.opening?.takeIf(::canDisplaySkip)
            ?: playerSkips?.ending?.takeIf(::canDisplaySkip)
    }

    private fun canDisplaySkip(skip: PlayerSkips.Skip): Boolean {
        return skip !in skippedList &&
            currentPosition >= skip.start &&
            currentPosition <= skip.end
    }

    private fun autoCancel() {
        playerSkips?.opening
            ?.takeIf { it !in skippedList && it.end < currentPosition }
            ?.also(skippedList::add)
        playerSkips?.ending
            ?.takeIf { it !in skippedList && it.end < currentPosition }
            ?.also(skippedList::add)
    }
}

@Composable
internal fun PlayerSkipsOverlay(
    skipsPart: PlayerSkipsPart?,
    onInteraction: () -> Unit,
    onOpenControls: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = TvPlayerOverlayBottomPadding,
    panelWidthFraction: Float = 1f,
) {
    val visibleSkip = skipsPart?.visibleSkip
    val visible = visibleSkip != null
    val palette = rememberWatchingPalette()
    val animatedBottomPadding by animateDpAsState(
        targetValue = bottomPadding,
        label = "playerSkipsBottomPadding",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val skipRequester = remember { FocusRequester() }
        val watchRequester = remember { FocusRequester() }

        LaunchedEffect(skipsPart?.focusRequestToken, visibleSkip) {
            if (visible) {
                requestWatchingFocusAfterAttach(skipRequester)
            }
        }

        PlayerSkipsButtonsRow(
            palette = palette,
            skipRequester = skipRequester,
            watchRequester = watchRequester,
            onInteraction = onInteraction,
            onOpenControls = onOpenControls,
            onSkipClick = { skipsPart?.skipCurrent() },
            onWatchClick = { skipsPart?.cancelCurrent() },
            panelWidthFraction = panelWidthFraction,
            bottomPadding = animatedBottomPadding,
        )
    }
}

@Composable
internal fun PlayerSkipsButtonsRow(
    palette: WatchingPalette,
    skipRequester: FocusRequester,
    watchRequester: FocusRequester,
    onInteraction: () -> Unit,
    onOpenControls: () -> Unit,
    onSkipClick: () -> Unit,
    onWatchClick: () -> Unit,
    modifier: Modifier = Modifier,
    panelWidthFraction: Float = 1f,
    bottomPadding: Dp = 0.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(panelWidthFraction)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PlayerOverlayUiDefaults.QuickActionsRowHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.QuickActionsRowSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val skipColors = PlayerOverlayUiDefaults.quickActionColors(
                palette = palette,
                emphasized = true,
            )
            val watchColors = PlayerOverlayUiDefaults.quickActionColors(
                palette = palette,
                emphasized = false,
            )

            WatchingFocusableSurface(
                focusRequester = skipRequester,
                backgroundColor = skipColors.backgroundColor,
                focusedBackgroundColor = skipColors.focusedBackgroundColor,
                borderColor = skipColors.borderColor,
                focusedScale = PlayerOverlayUiDefaults.PlayerFocusScale,
                focusedShadowElevation = PlayerOverlayUiDefaults.PlayerFocusShadowElevation,
                onClick = {
                    onInteraction()
                    onSkipClick()
                },
                onFocused = onInteraction,
                onLeft = { true },
                onRight = {
                    onInteraction()
                    runCatching { watchRequester.requestFocus() }.isSuccess
                },
                onUp = {
                    onInteraction()
                    onOpenControls()
                    true
                },
                onDown = { true },
                paddingValues = PlayerOverlayUiDefaults.CompactControlPadding,
            ) {
                Text(
                    text = stringResource(R.string.player_skip),
                    color = palette.textColor,
                )
            }

            WatchingFocusableSurface(
                focusRequester = watchRequester,
                backgroundColor = watchColors.backgroundColor,
                focusedBackgroundColor = watchColors.focusedBackgroundColor,
                borderColor = watchColors.borderColor,
                focusedScale = PlayerOverlayUiDefaults.PlayerFocusScale,
                focusedShadowElevation = PlayerOverlayUiDefaults.PlayerFocusShadowElevation,
                onClick = {
                    onInteraction()
                    onWatchClick()
                },
                onFocused = onInteraction,
                onLeft = {
                    onInteraction()
                    runCatching { skipRequester.requestFocus() }.isSuccess
                },
                onRight = { true },
                onUp = {
                    onInteraction()
                    onOpenControls()
                    true
                },
                onDown = { true },
                paddingValues = PlayerOverlayUiDefaults.CompactControlPadding,
            ) {
                Text(
                    text = stringResource(R.string.player_watch),
                    color = palette.textColor,
                )
            }
        }
    }
}
