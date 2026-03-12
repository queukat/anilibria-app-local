package ru.radiationx.anilibria.screen.player

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.data.entity.domain.release.PlayerSkips

class PlayerSkipsPart(
    private val parent: FrameLayout,
    private val onSeek: (Long) -> Unit,
    private val onSkipShow: () -> Unit,
    private val onSkipHide: () -> Unit,
) {

    private var playerSkips: PlayerSkips? = null
    private val skippedList = mutableSetOf<PlayerSkips.Skip>()
    private var currentPosition = 0L
    private var isSkipVisible by mutableStateOf(false)
    private var hasOverlayFocus by mutableStateOf(false)
    private var focusRequestToken by mutableIntStateOf(0)

    private val overlayView = ComposeView(parent.context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            PlayerSkipsOverlay(
                visible = isSkipVisible,
                focusRequestToken = focusRequestToken,
                onSkip = {
                    getCurrentSkip()?.also {
                        onSeek(it.end)
                    }
                    cancelSkip()
                },
                onCancel = ::cancelSkip,
                onFocusChanged = { hasOverlayFocus = it },
            )
        }
    }

    init {
        parent.addView(overlayView)
    }

    fun setSkips(skips: PlayerSkips?) {
        playerSkips = skips
        skippedList.clear()
        if (skips == null) {
            hideSkipOverlay()
        }
    }

    /**
     * Вызывается периодически (например, playerGlue?.playbackListener?.onUpdateProgress())
     */
    fun update(position: Long) {
        currentPosition = position
        autoCancel()

        val hasSkip = getCurrentSkip() != null
        if (hasSkip && !hasOverlayFocus) {
            focusRequestToken++
        }
        if (hasSkip) {
            showSkipOverlay()
        } else {
            hideSkipOverlay()
        }
    }

    private fun getCurrentSkip(): PlayerSkips.Skip? {
        return playerSkips?.opening?.takeIf { checkSkip(it) }
            ?: playerSkips?.ending?.takeIf { checkSkip(it) }
    }

    private fun checkSkip(skip: PlayerSkips.Skip): Boolean {
        return !skippedList.contains(skip) &&
            currentPosition >= skip.start &&
            currentPosition <= skip.end
    }

    private fun autoCancel() {
        val opening = playerSkips?.opening
        val ending = playerSkips?.ending

        if (opening != null && opening !in skippedList && opening.end < currentPosition) {
            skippedList.add(opening)
        }
        if (ending != null && ending !in skippedList && ending.end < currentPosition) {
            skippedList.add(ending)
        }
    }

    private fun cancelSkip() {
        getCurrentSkip()?.also { skippedList.add(it) }
        hideSkipOverlay()
    }

    private fun showSkipOverlay() {
        if (isSkipVisible) {
            return
        }
        isSkipVisible = true
        onSkipShow.invoke()
    }

    private fun hideSkipOverlay() {
        if (!isSkipVisible) {
            return
        }
        isSkipVisible = false
        hasOverlayFocus = false
        onSkipHide.invoke()
    }
}

@Composable
private fun PlayerSkipsOverlay(
    visible: Boolean,
    focusRequestToken: Int,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    if (!visible) {
        LaunchedEffect(Unit) {
            onFocusChanged(false)
        }
        return
    }

    val skipRequester = remember { FocusRequester() }
    var skipFocused by remember { mutableStateOf(false) }
    var cancelFocused by remember { mutableStateOf(false) }

    LaunchedEffect(skipFocused, cancelFocused) {
        onFocusChanged(skipFocused || cancelFocused)
    }

    LaunchedEffect(visible, focusRequestToken) {
        if (!visible || focusRequestToken <= 0) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        skipRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerSkipsButton(
                text = stringResource(R.string.player_skip),
                focusRequester = skipRequester,
                onClick = onSkip,
                onFocusedChanged = { skipFocused = it },
            )
            PlayerSkipsButton(
                text = stringResource(R.string.player_watch),
                onClick = onCancel,
                onFocusedChanged = { cancelFocused = it },
            )
        }
    }
}

@Composable
private fun PlayerSkipsButton(
    text: String,
    onClick: () -> Unit,
    onFocusedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val backgroundColor = colorResource(R.color.dark_release_day_btn).copy(alpha = 0.94f)
    val accentColor = colorResource(R.color.dark_colorAccent)
    val textColor = colorResource(R.color.dark_textDefault)
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .background(
                color = if (isFocused) backgroundColor else backgroundColor.copy(alpha = 0.82f),
                shape = RoundedCornerShape(26.dp),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) accentColor.copy(alpha = 0.92f) else textColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(26.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusedChanged(it.isFocused)
            }
            .focusable()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
