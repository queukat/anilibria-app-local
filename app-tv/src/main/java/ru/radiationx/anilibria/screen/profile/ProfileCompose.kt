package ru.radiationx.anilibria.screen.profile

import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.TvPageHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvPageHeader
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.shared_app.imageloader.showImageUrl

private const val PROFILE_PANEL_WIDTH_FRACTION = 0.42f

@Composable
internal fun ProfileScreen(
    profile: ProfileItem?,
    focusRequestToken: Int,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onRequestRailFocus: () -> Boolean,
    onRequestHeaderFocus: () -> Boolean,
) {
    val surfaceColor = colorResource(R.color.dark_colorPrimary)
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val accentColor = colorResource(R.color.dark_colorAccent)
    val actionBackground = colorResource(R.color.dark_release_day_btn).copy(alpha = 0.94f)
    val palette = remember(surfaceColor, textColor, secondaryTextColor, accentColor, actionBackground) {
        WatchingPalette(
            surfaceColor = surfaceColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            accentColor = accentColor,
            chipColor = actionBackground,
        )
    }
    val primaryButtonRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequestToken) {
        if (focusRequestToken > 0) {
            requestWatchingFocusAfterAttach(primaryButtonRequester)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        surfaceColor.copy(alpha = 0.18f),
                        Color.Transparent,
                    )
                )
            )
            .padding(horizontal = TvScreenHorizontalPadding, vertical = TvPageVerticalPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(TvPageHeaderSpacing),
        ) {
            TvPageHeader(
                title = "Профиль",
                subtitle = if (profile != null) {
                    "Управляйте аккаунтом и быстрыми действиями для этого устройства."
                } else {
                    "Войдите, чтобы синхронизировать историю, избранное и персональные данные."
                },
                palette = palette,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth(PROFILE_PANEL_WIDTH_FRACTION)
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(surfaceColor.copy(alpha = 0.92f))
                    .border(
                        width = 1.dp,
                        color = textColor.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(32.dp),
                    )
                    .padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(
                    avatarUrl = profile?.avatarUrl,
                    accentColor = accentColor,
                    backgroundColor = surfaceColor,
                )

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = profile?.nick ?: "Гость",
                    color = textColor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (profile != null) {
                        "Аккаунт подключен. Можно выйти из профиля на этом устройстве."
                    } else {
                        "Подключите аккаунт, чтобы продолжать просмотр между устройствами и не терять избранное."
                    },
                    color = secondaryTextColor,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(28.dp))

                WatchingFocusableSurface(
                    focusRequester = primaryButtonRequester,
                    backgroundColor = actionBackground,
                    focusedBackgroundColor = actionBackground,
                    borderColor = accentColor.copy(alpha = 0.9f),
                    onClick = if (profile != null) onSignOutClick else onSignInClick,
                    onLeft = onRequestRailFocus,
                    onUp = onRequestHeaderFocus,
                    modifier = Modifier.widthIn(min = 240.dp),
                    paddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = if (profile != null) "Выйти" else "Авторизоваться",
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    accentColor: Color,
    backgroundColor: Color,
) {
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(CircleShape)
            .background(backgroundColor.copy(alpha = 0.92f))
            .border(
                width = 2.dp,
                color = accentColor.copy(alpha = 0.55f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.ic_anilibria_splash),
                contentDescription = null,
                modifier = Modifier.size(62.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            AndroidView(
                factory = { context ->
                    AppCompatImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.showImageUrl(avatarUrl)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
