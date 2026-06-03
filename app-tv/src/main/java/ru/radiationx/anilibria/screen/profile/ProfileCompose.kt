package ru.radiationx.anilibria.screen.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.TvPageContentPadding
import ru.radiationx.anilibria.screen.watching.TvPageHeaderSpacing
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvAsyncImage
import ru.radiationx.anilibria.ui.compose.TvAsyncImageOptions
import ru.radiationx.anilibria.ui.compose.TvFocusableSurfaceColors
import ru.radiationx.anilibria.ui.compose.TvPageHeader
import ru.radiationx.anilibria.ui.compose.TvTextActionButton
import ru.radiationx.anilibria.ui.compose.TvUiDefaults
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import ru.radiationx.anilibria.ui.compose.tvPanelSurface
import ru.radiationx.data.entity.domain.other.ProfileItem

private const val PROFILE_PANEL_WIDTH_FRACTION = 0.62f

@Composable
internal fun ProfileScreen(
    profile: ProfileItem?,
    interactionsEnabled: Boolean = true,
    focusRequestToken: Int,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onRequestRailFocus: () -> Boolean,
    onRequestHeaderFocus: () -> Boolean,
) {
    val palette = rememberWatchingPalette()
    val accentColor = colorResource(R.color.dark_colorAccent)
    val primaryButtonRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequestToken, interactionsEnabled) {
        if (interactionsEnabled && focusRequestToken > 0) {
            requestWatchingFocusAfterAttach(primaryButtonRequester)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .tvAppBackground(palette)
                .padding(TvPageContentPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(TvPageHeaderSpacing),
        ) {
            TvPageHeader(
                title = "Профиль",
                subtitle =
                    if (profile != null) {
                        "Управляйте аккаунтом на этом устройстве и быстро выходите из профиля без лишних шагов."
                    } else {
                        "Подключите аккаунт, чтобы синхронизировать историю, избранное " +
                            "и продолжение просмотра между устройствами."
                    },
                palette = palette,
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(PROFILE_PANEL_WIDTH_FRACTION)
                            .widthIn(min = 480.dp, max = 760.dp)
                            .tvPanelSurface(TvUiDefaults.profilePanelStyle(palette))
                            .padding(TvUiDefaults.ProfilePanelPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfileAvatar(
                        avatarUrl = profile?.avatarUrl,
                        accentColor = accentColor,
                        backgroundColor = palette.surfaceColor,
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = profile?.nick ?: "Гость",
                        color = palette.textColor,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text =
                            if (profile != null) {
                                "Аккаунт подключен. Можно выйти из профиля на этом устройстве."
                            } else {
                                "Подключите аккаунт, чтобы продолжать просмотр между устройствами и не терять избранное."
                            },
                        color = palette.secondaryTextColor,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    TvTextActionButton(
                        text = if (profile != null) "Выйти" else "Авторизоваться",
                        palette = palette,
                        focusRequester = primaryButtonRequester,
                        onClick = if (profile != null) onSignOutClick else onSignInClick,
                        enabled = interactionsEnabled,
                        minWidth = 240.dp,
                        colors =
                            TvFocusableSurfaceColors(
                                backgroundColor =
                                    palette.chipColor.copy(alpha = TvUiDefaults.SUBTLE_SURFACE_ALPHA),
                                focusedBackgroundColor =
                                    palette.chipColor.copy(alpha = TvUiDefaults.SUBTLE_SURFACE_ALPHA),
                                borderColor = accentColor.copy(alpha = 0.9f),
                            ),
                        paddingValues = TvUiDefaults.ActionButtonPadding,
                        fontSize = 18.sp,
                        onLeft = onRequestRailFocus,
                        onUp = onRequestHeaderFocus,
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
        modifier =
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(backgroundColor.copy(alpha = TvUiDefaults.TRANSLUCENT_CONTROL_ALPHA))
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
            TvAsyncImage(
                imageUrl = avatarUrl,
                modifier = Modifier.fillMaxSize(),
                options =
                    TvAsyncImageOptions(
                        contentScale = ContentScale.Crop,
                        placeholderRes = R.drawable.ic_anilibria_splash,
                    ),
            )
        }
    }
}
