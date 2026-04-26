package ru.radiationx.anilibria.screen.details

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
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

@Composable
internal fun ActionsRow(
    details: LibriaDetails?,
    textColor: Color,
    backgroundColor: Color,
    focusUpRequester: FocusRequester,
    focusDownRequester: FocusRequester?,
    continueRequester: FocusRequester,
    playRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    otherRequester: FocusRequester,
    enabled: Boolean,
    onContinueClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOtherClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (details?.hasViewed == true) {
            ActionChipButton(
                text = "Продолжить",
                textColor = textColor,
                backgroundColor = backgroundColor,
                focusRequester = continueRequester,
                upRequester = focusUpRequester,
                downRequester = focusDownRequester,
                enabled = enabled,
                onClick = onContinueClick,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        if (details?.hasEpisodes == true) {
            ActionChipButton(
                text = "Смотреть",
                textColor = textColor,
                backgroundColor = backgroundColor,
                focusRequester = playRequester,
                upRequester = focusUpRequester,
                downRequester = focusDownRequester,
                enabled = enabled,
                onClick = onPlayClick,
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
            textColor = textColor,
            backgroundColor = backgroundColor,
            focusRequester = favoriteRequester,
            upRequester = focusUpRequester,
            downRequester = focusDownRequester,
            enabled = enabled,
            onClick = onFavoriteClick,
        )

        if (details?.let { it.hasEpisodes || it.hasViewed } == true) {
            Spacer(modifier = Modifier.width(16.dp))
            IconChipButton(
                iconRes = R.drawable.ic_more_vert,
                contentColor = textColor,
                backgroundColor = backgroundColor,
                focusRequester = otherRequester,
                upRequester = focusUpRequester,
                downRequester = focusDownRequester,
                enabled = enabled,
                onClick = onOtherClick,
            )
        }
    }
}

@Composable
private fun ActionChipButton(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = backgroundColor.copy(alpha = 0.88f),
        focusedBackgroundColor = backgroundColor,
        borderColor = textColor.copy(alpha = 0.8f),
        onClick = onClick,
        onUp = {
            requestReleaseDetailsFocus(upRequester)
        },
        onDown = {
            requestReleaseDetailsFocusOrConsumeBoundary(downRequester)
        },
        modifier = Modifier.heightIn(min = 54.dp),
        paddingValues =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = 22.dp,
                vertical = 15.dp,
            ),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun IconChipButton(
    iconRes: Int,
    contentColor: Color,
    backgroundColor: Color,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = backgroundColor.copy(alpha = 0.88f),
        focusedBackgroundColor = backgroundColor,
        borderColor = contentColor.copy(alpha = 0.8f),
        onClick = onClick,
        onUp = {
            requestReleaseDetailsFocus(upRequester)
        },
        onDown = {
            requestReleaseDetailsFocusOrConsumeBoundary(downRequester)
        },
        modifier = Modifier.heightIn(min = 54.dp),
        paddingValues =
            androidx.compose.foundation.layout.PaddingValues(
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
                tint = contentColor,
            )
        }
    }
}
