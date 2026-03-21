package ru.radiationx.anilibria.screen.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButton
import ru.radiationx.anilibria.ui.compose.TvOverlayInfoBlock
import ru.radiationx.anilibria.ui.compose.TvOverlayPanelSurface
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import ru.radiationx.data.entity.common.ConfigScreenState

private const val CONFIG_PANEL_MAX_WIDTH = 840

@Composable
internal fun ConfigScreenContent(
    screenState: ConfigScreenState?,
    isCompleting: Boolean,
    onRepeatClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val palette = rememberWatchingPalette()
    var showControls by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showControls = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvAppBackground(palette, glowAlpha = 0.22f)
            .padding(horizontal = TvScreenHorizontalPadding, vertical = TvPageVerticalPadding),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .widthIn(max = CONFIG_PANEL_MAX_WIDTH.dp),
        ) {
            TvOverlayPanelSurface(
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_anilibria_splash),
                            contentDescription = null,
                            modifier = Modifier.size(76.dp),
                        )
                        Text(
                            text = stringResource(R.string.config_logo_name),
                            color = palette.textColor,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        ConfigStatusContent(
                            screenState = screenState,
                            isCompleting = isCompleting,
                            palette = palette,
                            onRepeatClick = onRepeatClick,
                            onSkipClick = onSkipClick,
                            onNextClick = onNextClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigStatusContent(
    screenState: ConfigScreenState?,
    isCompleting: Boolean,
    palette: ru.radiationx.anilibria.screen.watching.WatchingPalette,
    onRepeatClick: () -> Unit,
    onSkipClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val state = screenState
    val showActions = state?.needRefresh == true && !isCompleting
    val actionState = state?.takeIf { showActions }
    val titleText = when {
        isCompleting -> "Завершаем настройку"
        showActions -> "Нужна ручная проверка"
        else -> "Проверяем конфигурацию"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = titleText,
            color = palette.textColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (actionState != null) {
            TvOverlayInfoBlock(
                text = actionState.status,
                palette = palette,
                accent = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            state?.status?.takeIf { it.isNotBlank() }?.also {
                TvOverlayInfoBlock(
                    text = it,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CircularProgressIndicator(
                color = palette.textColor,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = if (isCompleting) {
                    "Сохраняем результат и подготавливаем следующий экран."
                } else {
                    "Подождите немного: если понадобится ручное действие, кнопки появятся ниже."
                },
                color = palette.secondaryTextColor,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            )
        }

        if (actionState != null) {
            val repeatRequester = remember { FocusRequester() }
            val skipRequester = remember { FocusRequester() }
            val nextRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                requestWatchingFocusAfterAttach(repeatRequester)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvOverlayActionButton(
                    text = stringResource(R.string.config_action_repeat),
                    palette = palette,
                    focusRequester = repeatRequester,
                    onClick = onRepeatClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                TvOverlayActionButton(
                    text = stringResource(R.string.config_action_skip),
                    palette = palette,
                    focusRequester = skipRequester,
                    onClick = onSkipClick,
                    modifier = Modifier.fillMaxWidth(),
                    upRequester = repeatRequester,
                    downRequester = nextRequester,
                )
                TvOverlayActionButton(
                    text = stringResource(
                        if (actionState.hasNext) {
                            R.string.config_action_next
                        } else {
                            R.string.config_action_restart
                        }
                    ),
                    palette = palette,
                    focusRequester = nextRequester,
                    onClick = onNextClick,
                    modifier = Modifier.fillMaxWidth(),
                    upRequester = skipRequester,
                )
            }
        }
    }
}
