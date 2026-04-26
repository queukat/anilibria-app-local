package ru.radiationx.anilibria.screen.details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.R

internal enum class ReleaseDetailsFocusTarget {
    StartAction,
    Continue,
    Play,
    Favorite,
    Description,
    Other,
}

internal enum class ReleaseDetailsVerticalMoveAction {
    MoveFocus,
    Consume,
}

internal fun resolveReleaseDetailsVerticalMoveAction(hasTarget: Boolean): ReleaseDetailsVerticalMoveAction {
    return if (hasTarget) {
        ReleaseDetailsVerticalMoveAction.MoveFocus
    } else {
        ReleaseDetailsVerticalMoveAction.Consume
    }
}

internal fun requestReleaseDetailsFocusOrConsumeBoundary(focusRequester: FocusRequester?): Boolean {
    return when (resolveReleaseDetailsVerticalMoveAction(focusRequester != null)) {
        ReleaseDetailsVerticalMoveAction.MoveFocus -> {
            requestReleaseDetailsFocus(focusRequester)
            true
        }

        ReleaseDetailsVerticalMoveAction.Consume -> true
    }
}

internal fun requestReleaseDetailsFocus(focusRequester: FocusRequester?): Boolean {
    if (focusRequester == null) {
        return false
    }
    return runCatching {
        focusRequester.requestFocus()
        true
    }.getOrDefault(false)
}

@Composable
internal fun Modifier.tvScrollableFocus(
    scrollState: ScrollState,
    viewportHeightPx: Int,
    scrollStepPx: Int,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = colorResource(R.color.dark_textDefault).copy(alpha = 0.75f)

    return this
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) borderColor else Color.Transparent,
            shape = RoundedCornerShape(6.dp),
        )
        .focusRequester(focusRequester)
        .focusProperties {
            up = upRequester
            down = downRequester
        }
        .onFocusChanged { isFocused = it.isFocused }
        .tvScrollKeys(
            scrollState = scrollState,
            viewportHeightPx = viewportHeightPx,
            scrollStepPx = scrollStepPx,
        )
        .focusable()
}

@Composable
internal fun Modifier.tvScrollKeys(
    scrollState: ScrollState,
    viewportHeightPx: Int,
    scrollStepPx: Int = viewportHeightPx,
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || viewportHeightPx <= 0) {
            return@onPreviewKeyEvent false
        }
        val delta =
            when (event.key) {
                Key.DirectionDown -> scrollStepPx
                Key.DirectionUp -> -scrollStepPx
                else -> return@onPreviewKeyEvent false
            }
        val targetValue =
            (scrollState.value + delta)
                .coerceIn(0, scrollState.maxValue)
        if (targetValue == scrollState.value) {
            false
        } else {
            coroutineScope.launch {
                scrollState.animateScrollTo(targetValue)
            }
            true
        }
    }
}
