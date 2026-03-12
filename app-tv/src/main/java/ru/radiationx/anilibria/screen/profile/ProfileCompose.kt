package ru.radiationx.anilibria.screen.profile

import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ru.radiationx.anilibria.R
import ru.radiationx.data.entity.domain.other.ProfileItem
import ru.radiationx.shared_app.imageloader.showImageUrl
import android.widget.ImageView

@Composable
internal fun ProfileScreen(
    profile: ProfileItem?,
    focusRequestToken: Int,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    val surfaceColor = colorResource(R.color.dark_colorPrimary)
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val accentColor = colorResource(R.color.dark_colorAccent)
    val actionBackground = colorResource(R.color.dark_release_day_btn).copy(alpha = 0.94f)
    val primaryButtonRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequestToken, profile?.id) {
        if (focusRequestToken > 0) {
            primaryButtonRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 48.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
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
                    "Авторизуйтесь, чтобы синхронизировать историю, избранное и персональные данные."
                },
                color = secondaryTextColor,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            ProfileActionButton(
                text = if (profile != null) "Выйти" else "Авторизоваться",
                focusRequester = primaryButtonRequester,
                backgroundColor = actionBackground,
                borderColor = accentColor.copy(alpha = 0.9f),
                textColor = textColor,
                onClick = if (profile != null) onSignOutClick else onSignInClick,
            )
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

@Composable
private fun ProfileActionButton(
    text: String,
    focusRequester: FocusRequester,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) borderColor else textColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(26.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
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
