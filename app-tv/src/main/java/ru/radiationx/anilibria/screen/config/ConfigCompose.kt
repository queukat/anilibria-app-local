package ru.radiationx.anilibria.screen.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButton
import ru.radiationx.anilibria.ui.compose.TvOverlayInfoBlock
import ru.radiationx.data.entity.common.ConfigScreenState

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceColor.copy(alpha = 0.24f),
                        Color.Black,
                    )
                )
            )
            .padding(horizontal = 36.dp, vertical = 32.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_anilibria_splash),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp),
                )
                Text(
                    text = stringResource(R.string.config_logo_name),
                    color = palette.textColor,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.62f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val state = screenState
                    val showActions = state?.needRefresh == true && !isCompleting
                    val titleText = when {
                        isCompleting -> "Завершаем настройку"
                        showActions -> "Нужна ручная проверка"
                        else -> "Проверяем конфигурацию"
                    }
                    Text(
                        text = titleText,
                        color = palette.textColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (showActions) {
                        TvOverlayInfoBlock(
                            text = state.status,
                            palette = palette,
                            accent = true,
                        )
                    } else {
                        state?.status?.takeIf { it.isNotBlank() }?.also {
                            TvOverlayInfoBlock(
                                text = it,
                                palette = palette,
                            )
                        }
                        CircularProgressIndicator(
                            color = palette.textColor,
                            modifier = Modifier.size(48.dp),
                        )
                    }

                    if (showActions) {
                        val repeatRequester = remember { FocusRequester() }
                        val skipRequester = remember { FocusRequester() }
                        val nextRequester = remember { FocusRequester() }

                        LaunchedEffect(Unit) {
                            repeatRequester.requestFocus()
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
                                    if (state.hasNext) {
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
        }
    }
}
