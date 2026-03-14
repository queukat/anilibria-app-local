package ru.radiationx.anilibria.screen.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.TvPlayerOverlayBottomPadding
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
        if (nextVisibleSkip != null && visibleSkip == null) {
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
    modifier: Modifier = Modifier,
    bottomPadding: Dp = TvPlayerOverlayBottomPadding,
) {
    val visible = skipsPart?.isVisible == true
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
        val cancelRequester = remember { FocusRequester() }

        androidx.compose.runtime.LaunchedEffect(skipsPart?.focusRequestToken, visible) {
            if (visible) {
                requestWatchingFocusAfterAttach(skipRequester)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 28.dp, bottom = animatedBottomPadding),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WatchingFocusableSurface(
                    focusRequester = skipRequester,
                    backgroundColor = palette.accentColor.copy(alpha = 0.20f),
                    focusedBackgroundColor = palette.accentColor.copy(alpha = 0.30f),
                    borderColor = palette.accentColor.copy(alpha = 0.96f),
                    onClick = {
                        onInteraction()
                        skipsPart?.skipCurrent()
                    },
                    onFocused = onInteraction,
                    onLeft = { true },
                    onRight = {
                        onInteraction()
                        runCatching { cancelRequester.requestFocus() }.isSuccess
                    },
                    paddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.player_skip),
                        color = palette.textColor,
                    )
                }

                WatchingFocusableSurface(
                    focusRequester = cancelRequester,
                    backgroundColor = palette.surfaceColor.copy(alpha = 0.90f),
                    focusedBackgroundColor = palette.surfaceColor,
                    borderColor = palette.textColor.copy(alpha = 0.76f),
                    onClick = {
                        onInteraction()
                        skipsPart?.cancelCurrent()
                    },
                    onFocused = onInteraction,
                    onLeft = {
                        onInteraction()
                        runCatching { skipRequester.requestFocus() }.isSuccess
                    },
                    onRight = { true },
                    paddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.player_watch),
                        color = palette.textColor,
                    )
                }
            }
        }
    }
}
