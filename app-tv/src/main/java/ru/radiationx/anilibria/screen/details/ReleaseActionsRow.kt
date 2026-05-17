package ru.radiationx.anilibria.screen.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface

internal data class ReleaseActionColors(
    val textColor: Color,
    val backgroundColor: Color,
)

internal data class ReleaseActionsFocus(
    val upRequester: FocusRequester,
    val downRequester: FocusRequester?,
    val continueRequester: FocusRequester,
    val playRequester: FocusRequester,
    val favoriteRequester: FocusRequester,
    val otherRequester: FocusRequester,
)

internal data class ReleaseActionsCallbacks(
    val continueClick: () -> Unit,
    val playClick: () -> Unit,
    val favoriteClick: () -> Unit,
    val otherClick: () -> Unit,
)

private data class ReleaseActionButtonFocus(
    val requester: FocusRequester,
    val upRequester: FocusRequester,
    val downRequester: FocusRequester?,
)

private fun ReleaseActionsFocus.toButtonFocus(requester: FocusRequester): ReleaseActionButtonFocus {
    return ReleaseActionButtonFocus(
        requester = requester,
        upRequester = upRequester,
        downRequester = downRequester,
    )
}

@Composable
internal fun ActionsRow(
    details: LibriaDetails?,
    colors: ReleaseActionColors,
    focus: ReleaseActionsFocus,
    enabled: Boolean,
    callbacks: ReleaseActionsCallbacks,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (details?.hasViewed == true) {
            ActionChipButton(
                text = "Продолжить",
                colors = colors,
                focus = focus.toButtonFocus(focus.continueRequester),
                enabled = enabled,
                onClick = callbacks.continueClick,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        if (details?.hasEpisodes == true) {
            ActionChipButton(
                text = "Смотреть",
                colors = colors,
                focus = focus.toButtonFocus(focus.playRequester),
                enabled = enabled,
                onClick = callbacks.playClick,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        ActionChipButton(
            text =
                if (details?.isFavorite == true) {
                    "Убрать из избранного"
                } else {
                    "Добавить в избранное"
                },
            colors = colors,
            focus = focus.toButtonFocus(focus.favoriteRequester),
            enabled = enabled,
            onClick = callbacks.favoriteClick,
        )

        if (details?.let { it.hasEpisodes || it.hasViewed } == true) {
            Spacer(modifier = Modifier.width(16.dp))
            IconChipButton(
                iconRes = R.drawable.ic_more_vert,
                colors = colors,
                focus = focus.toButtonFocus(focus.otherRequester),
                enabled = enabled,
                onClick = callbacks.otherClick,
            )
        }
    }
}

@Composable
private fun ActionChipButton(
    text: String,
    colors: ReleaseActionColors,
    focus: ReleaseActionButtonFocus,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ReleaseActionSurface(
        colors = colors,
        focus = focus,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.heightIn(min = 54.dp),
        paddingValues =
            PaddingValues(
                horizontal = 22.dp,
                vertical = 15.dp,
            ),
    ) {
        Text(
            text = text,
            color = colors.textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun IconChipButton(
    iconRes: Int,
    colors: ReleaseActionColors,
    focus: ReleaseActionButtonFocus,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ReleaseActionSurface(
        colors = colors,
        focus = focus,
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.heightIn(min = 54.dp),
        paddingValues =
            PaddingValues(
                horizontal = 20.dp,
                vertical = 14.dp,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.textColor,
            )
        }
    }
}

@Composable
private fun ReleaseActionSurface(
    colors: ReleaseActionColors,
    focus: ReleaseActionButtonFocus,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    paddingValues: PaddingValues,
    content: @Composable () -> Unit,
) {
    WatchingFocusableSurface(
        focusRequester = focus.requester,
        enabled = enabled,
        backgroundColor = colors.backgroundColor.copy(alpha = 0.88f),
        focusedBackgroundColor = colors.backgroundColor,
        borderColor = colors.textColor.copy(alpha = 0.8f),
        onClick = onClick,
        onUp = {
            requestReleaseDetailsFocus(focus.upRequester)
        },
        onDown = {
            requestReleaseDetailsFocusOrConsumeBoundary(focus.downRequester)
        },
        modifier = modifier,
        paddingValues = paddingValues,
        content = content,
    )
}
