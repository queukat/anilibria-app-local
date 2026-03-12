package ru.radiationx.anilibria.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.screen.auth.otp.AuthOtpViewModel
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButton
import ru.radiationx.anilibria.ui.compose.TvOverlayInfoBlock
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.anilibria.ui.compose.TvOverlayScrollableText
import ru.radiationx.anilibria.ui.compose.TvOverlayTextField
import ru.radiationx.data.entity.domain.auth.OtpInfo

@Composable
internal fun AuthMenuOverlay(
    onCodeClick: () -> Unit,
    onClassicClick: () -> Unit,
    onSkipClick: () -> Unit,
) {
    TvOverlayScreen(
        title = "Авторизация",
        subtitle = "Войдите в свой аккаунт удобным способом. Для регистрации используйте полную версию сайта.",
        panelMaxWidth = 680.dp,
    ) { palette ->
        val codeRequester = remember { FocusRequester() }
        val classicRequester = remember { FocusRequester() }
        val skipRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            requestWatchingFocusAfterAttach(codeRequester)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TvOverlayActionButton(
                text = "Войти по коду",
                palette = palette,
                focusRequester = codeRequester,
                downRequester = classicRequester,
                onClick = onCodeClick,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayInfoBlock(
                text = "Используйте мобильное приложение или сайт для подтверждения входа.",
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Ввести логин или email",
                palette = palette,
                focusRequester = classicRequester,
                upRequester = codeRequester,
                downRequester = skipRequester,
                onClick = onClassicClick,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Пропустить",
                palette = palette,
                focusRequester = skipRequester,
                upRequester = classicRequester,
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
) {
    TvOverlayScreen(
        title = "Авторизация",
        subtitle = "Логин и пароль обязательны. Код двухфакторной авторизации нужен только если вы её включали.",
        panelMaxWidth = 760.dp,
    ) { palette ->
        val loginRequester = remember { FocusRequester() }
        val passwordRequester = remember { FocusRequester() }
        val codeRequester = remember { FocusRequester() }
        val buttonRequester = remember { FocusRequester() }

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
            if (errorText.isNotBlank()) {
                TvOverlayInfoBlock(
                    text = errorText,
                    palette = palette,
                    accent = true,
                )
            }
            TvOverlayTextField(
                label = "Логин или email",
                value = login,
                onValueChange = { login = it },
                palette = palette,
                focusRequester = loginRequester,
                downRequester = passwordRequester,
                enabled = !isLoading,
                supportingText = if (login.isEmpty()) "Введите логин или email" else null,
            )
            TvOverlayTextField(
                label = "Пароль",
                value = password,
                onValueChange = { password = it },
                palette = palette,
                focusRequester = passwordRequester,
                upRequester = loginRequester,
                downRequester = codeRequester,
                enabled = !isLoading,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (password.isEmpty()) "Введите пароль" else null,
            )
            TvOverlayTextField(
                label = "Код двухфакторной авторизации",
                value = code,
                onValueChange = { code = it.filter(Char::isDigit) },
                palette = palette,
                focusRequester = codeRequester,
                upRequester = passwordRequester,
                downRequester = buttonRequester,
                enabled = !isLoading,
                isError = code.isNotBlank() && !codeValid,
                supportingText = "Оставьте пустым, если двухфакторная авторизация не настроена",
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            TvOverlayActionButton(
                text = "Войти",
                palette = palette,
                focusRequester = buttonRequester,
                upRequester = codeRequester,
                enabled = canSubmit,
                loading = isLoading,
                onClick = { onSubmit(login.trim(), password, code.trim()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun AuthOtpOverlay(
    otpInfo: OtpInfo?,
    state: AuthOtpViewModel.State,
    onPrimaryClick: () -> Unit,
) {
    val primaryTitle = when (state.buttonState) {
        AuthOtpViewModel.ButtonState.COMPLETE -> "Готово"
        AuthOtpViewModel.ButtonState.EXPIRED -> "Показать новый код"
        AuthOtpViewModel.ButtonState.REPEAT -> "Повторить"
    }

    TvOverlayScreen(
        title = otpInfo?.code?.let { "Код: $it" } ?: "Запрашивается код",
        subtitle = otpInfo?.description ?: "Запросите код в приложении или на сайте и подтвердите вход.",
        panelMaxWidth = 760.dp,
    ) { palette ->
        val buttonRequester = remember { FocusRequester() }

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
            otpInfo?.takeIf { it.description.isNotBlank() }?.also {
                TvOverlayScrollableText(
                    text = it.description,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TvOverlayActionButton(
                text = primaryTitle,
                palette = palette,
                focusRequester = buttonRequester,
                loading = state.progress,
                onClick = onPrimaryClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
