package ru.radiationx.anilibria.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButton
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButtonFocus
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButtonState
import ru.radiationx.anilibria.ui.compose.TvOverlayInfoBlock
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.anilibria.ui.compose.TvOverlayTextField
import ru.radiationx.anilibria.ui.compose.TvOverlayTextFieldFocus
import ru.radiationx.anilibria.ui.compose.TvOverlayTextFieldInput
import ru.radiationx.anilibria.ui.compose.TvOverlayTextFieldState
import ru.radiationx.data.entity.domain.auth.OtpInfo
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
internal fun AuthMenuOverlay(
    onCodeClick: () -> Unit,
    onClassicClick: () -> Unit,
    onSkipClick: () -> Unit,
) {
    TvOverlayScreen(
        title = "Вход на телевизоре",
        subtitle = (
            "Основной способ для TV — вход по коду. " +
                "Он быстрее и не требует вводить логин и пароль с пульта."
        ),
        panelMaxWidth = 680.dp,
    ) { palette ->
        val codeRequester = remember { FocusRequester() }
        val classicRequester = remember { FocusRequester() }
        val skipRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            requestWatchingFocusAfterAttach(codeRequester)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvOverlayInfoBlock(
                text = (
                    "Быстрый вход:\n" +
                        "1. Откройте AniLibria на телефоне или сайте.\n" +
                        "2. Выберите вход на устройстве.\n" +
                        "3. Подтвердите код на экране телевизора."
                ),
                palette = palette,
                accent = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Продолжить по коду",
                palette = palette,
                focus =
                    TvOverlayActionButtonFocus(
                        requester = codeRequester,
                        downRequester = classicRequester,
                    ),
                onClick = onCodeClick,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayInfoBlock(
                text = "Логин и пароль используйте только если не получается подтвердить вход по коду.",
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Войти логином и паролем",
                palette = palette,
                focus =
                    TvOverlayActionButtonFocus(
                        requester = classicRequester,
                        upRequester = codeRequester,
                        downRequester = skipRequester,
                    ),
                onClick = onClassicClick,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Пропустить сейчас",
                palette = palette,
                focus =
                    TvOverlayActionButtonFocus(
                        requester = skipRequester,
                        upRequester = classicRequester,
                    ),
                onClick = onSkipClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun AuthCredentialsOverlay(
    isLoading: Boolean,
    errorText: String,
    onSubmit: (String, String, String) -> Unit,
    onBackClick: () -> Unit,
) {
    TvOverlayScreen(
        title = "Ручной вход",
        subtitle = "Используйте этот вариант, только если вход по коду сейчас недоступен.",
        panelMaxWidth = 760.dp,
    ) { palette ->
        val loginRequester = remember { FocusRequester() }
        val passwordRequester = remember { FocusRequester() }
        val codeRequester = remember { FocusRequester() }
        val buttonRequester = remember { FocusRequester() }
        val backRequester = remember { FocusRequester() }

        var login by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var code by rememberSaveable { mutableStateOf("") }

        val loginValid = login.isNotBlank()
        val passwordValid = password.isNotBlank()
        val codeValid = code.isBlank() || code.all(Char::isDigit)
        val canSubmit = loginValid && passwordValid && codeValid && !isLoading

        LaunchedEffect(Unit) {
            requestWatchingFocusAfterAttach(loginRequester)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvOverlayInfoBlock(
                text = "Если рядом есть телефон или сайт AniLibria, для TV удобнее вернуться и войти по коду.",
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorText.isNotBlank()) {
                TvOverlayInfoBlock(
                    text = errorText,
                    palette = palette,
                    accent = true,
                )
            }
            TvOverlayTextField(
                label = "Логин или email",
                state =
                    TvOverlayTextFieldState(
                        value = login,
                        onValueChange = { login = it },
                        enabled = !isLoading,
                        supportingText = if (login.isEmpty()) "Введите логин или email" else null,
                    ),
                palette = palette,
                focus =
                    TvOverlayTextFieldFocus(
                        requester = loginRequester,
                        downRequester = passwordRequester,
                    ),
                input = TvOverlayTextFieldInput(singleLine = true),
            )
            TvOverlayTextField(
                label = "Пароль",
                state =
                    TvOverlayTextFieldState(
                        value = password,
                        onValueChange = { password = it },
                        enabled = !isLoading,
                        supportingText = if (password.isEmpty()) "Введите пароль" else null,
                    ),
                palette = palette,
                focus =
                    TvOverlayTextFieldFocus(
                        requester = passwordRequester,
                        upRequester = loginRequester,
                        downRequester = codeRequester,
                    ),
                input =
                    TvOverlayTextFieldInput(
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    ),
            )
            TvOverlayTextField(
                label = "2FA код, если включен",
                state =
                    TvOverlayTextFieldState(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit) },
                        enabled = !isLoading,
                        isError = code.isNotBlank() && !codeValid,
                        supportingText = "Оставьте пустым, если двухфакторная авторизация не настроена",
                    ),
                palette = palette,
                focus =
                    TvOverlayTextFieldFocus(
                        requester = codeRequester,
                        upRequester = passwordRequester,
                        downRequester = buttonRequester,
                    ),
                input =
                    TvOverlayTextFieldInput(
                        singleLine = true,
                        keyboardOptions =
                            androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    ),
            )
            TvOverlayActionButton(
                text = "Войти",
                palette = palette,
                focus =
                    TvOverlayActionButtonFocus(
                        requester = buttonRequester,
                        upRequester = codeRequester,
                        downRequester = backRequester,
                    ),
                state =
                    TvOverlayActionButtonState(
                        enabled = canSubmit,
                        loading = isLoading,
                    ),
                onClick = { onSubmit(login.trim(), password, code.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Выбрать другой способ входа",
                palette = palette,
                focus =
                    TvOverlayActionButtonFocus(
                        requester = backRequester,
                        upRequester = buttonRequester,
                    ),
                state = TvOverlayActionButtonState(enabled = !isLoading),
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun AuthOtpOverlay(
    otpInfo: OtpInfo?,
    state: AuthOtpState,
    onPrimaryClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val primaryTitle =
        when {
            otpInfo == null && state.progress -> "Получаем код"
            else ->
                when (state.buttonState) {
                    AuthOtpButtonState.COMPLETE -> "Проверить вход"
                    AuthOtpButtonState.EXPIRED -> "Показать новый код"
                    AuthOtpButtonState.REPEAT -> "Повторить запрос"
                }
        }
    val expiresAtLabel = otpInfo?.let(::formatOtpExpiration)

    TvOverlayScreen(
        title = if (otpInfo == null) "Получаем код" else "Вход по коду",
        subtitle = "Откройте AniLibria на телефоне или сайте, подтвердите вход и вернитесь на этот экран.",
        panelMaxWidth = 760.dp,
    ) { palette ->
        val buttonRequester = remember { FocusRequester() }
        val backRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            requestWatchingFocusAfterAttach(buttonRequester)
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.error.takeIf { it.isNotBlank() }?.also {
                TvOverlayInfoBlock(
                    text = it,
                    palette = palette,
                    accent = true,
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            color = palette.accentColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Код для входа",
                        color = palette.secondaryTextColor,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = otpInfo?.code ?: "......",
                        color = palette.textColor,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = expiresAtLabel ?: "Код появится через мгновение",
                        color = palette.secondaryTextColor,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            otpInfo?.description
                ?.takeIf { it.isNotBlank() }
                ?.also {
                    TvOverlayInfoBlock(
                        text = it,
                        palette = palette,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            TvOverlayActionButton(
                text = primaryTitle,
                palette = palette,
                focus =
                    TvOverlayActionButtonFocus(
                        requester = buttonRequester,
                        downRequester = backRequester,
                    ),
                state = TvOverlayActionButtonState(loading = state.progress),
                onClick = onPrimaryClick,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Выбрать другой способ входа",
                palette = palette,
                focus =
                    TvOverlayActionButtonFocus(
                        requester = backRequester,
                        upRequester = buttonRequester,
                    ),
                state = TvOverlayActionButtonState(enabled = !state.progress),
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth(),
            )
            if (otpInfo == null && state.error.isBlank()) {
                TvOverlayInfoBlock(
                    text = "Подготовим код и покажем его здесь. Затем подтвердите вход на другом устройстве.",
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun formatOtpExpiration(otpInfo: OtpInfo): String {
    val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(otpInfo.expiresAt)
    return "Код действует до $timeText"
}
