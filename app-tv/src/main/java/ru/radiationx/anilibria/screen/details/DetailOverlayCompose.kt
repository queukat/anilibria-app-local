package ru.radiationx.anilibria.screen.details

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButton
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceItem
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceList
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceSection
import ru.radiationx.anilibria.ui.compose.TvOverlayInfoBlock
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.anilibria.ui.compose.TvOverlayScrollableText
import ru.radiationx.data.entity.domain.types.EpisodeId

internal sealed interface DetailOverlayState {
    data class Description(
        val title: String,
        val message: String,
    ) : DetailOverlayState

    data object Other : DetailOverlayState

    data class EpisodePicker(
        val groups: List<Group>,
        val selectedActionId: Long,
    ) : DetailOverlayState {
        data class Group(
            val id: Long,
            val title: String,
            val actions: List<Action>,
        )

        data class Action(
            val id: Long,
            val episodeId: EpisodeId,
            val title: String,
            val description: String?,
        )
    }
}

@Composable
internal fun DetailOverlayHost(
    overlayState: DetailOverlayState,
    onDismiss: () -> Unit,
    onClearHistoryClick: () -> Unit,
    onMarkAllViewedClick: () -> Unit,
    onEpisodeSelected: (Long) -> Unit,
) {
    when (overlayState) {
        is DetailOverlayState.Description -> {
            val textRequester = remember { FocusRequester() }
            val closeRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                delay(DETAIL_OVERLAY_INITIAL_FOCUS_DELAY_MS)
                requestWatchingFocusAfterAttach(textRequester)
            }

            TvOverlayScreen(
                title = overlayState.title,
                subtitle = null,
                panelMaxWidth = 920.dp,
            ) { palette ->
                TvOverlayScrollableText(
                    text = overlayState.message,
                    palette = palette,
                    focusRequester = textRequester,
                    downRequester = closeRequester,
                    modifier = Modifier.heightIn(min = 180.dp, max = 420.dp),
                )
                TvOverlayActionButton(
                    text = "Закрыть",
                    palette = palette,
                    focusRequester = closeRequester,
                    upRequester = textRequester,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        DetailOverlayState.Other -> {
            val clearRequester = remember { FocusRequester() }
            val markRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                delay(DETAIL_OVERLAY_INITIAL_FOCUS_DELAY_MS)
                requestWatchingFocusAfterAttach(clearRequester)
            }

            TvOverlayScreen(
                title = "Дополнительные действия",
                subtitle = "Эти действия изменяют состояние просмотра для всего релиза.",
                panelMaxWidth = 700.dp,
            ) { palette ->
                TvOverlayInfoBlock(
                    text =
                        "Используйте их только если хотите быстро очистить прогресс " +
                            "или отметить весь релиз как просмотренный.",
                    palette = palette,
                )
                TvOverlayActionButton(
                    text = "Сбросить историю просмотров",
                    palette = palette,
                    focusRequester = clearRequester,
                    downRequester = markRequester,
                    destructive = true,
                    onClick = onClearHistoryClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                TvOverlayActionButton(
                    text = "Отметить всё как просмотренные",
                    palette = palette,
                    focusRequester = markRequester,
                    upRequester = clearRequester,
                    onClick = onMarkAllViewedClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is DetailOverlayState.EpisodePicker -> {
            TvOverlayScreen(
                title = "Список серий",
                subtitle = "Выберите эпизод, с которого нужно продолжить просмотр.",
                panelMaxWidth = 860.dp,
            ) { _ ->
                TvOverlayChoiceList(
                    sections =
                        overlayState.groups.map { group ->
                            TvOverlayChoiceSection(
                                title = if (overlayState.groups.size > 1) group.title else null,
                                items =
                                    group.actions.map { action ->
                                        TvOverlayChoiceItem(
                                            id = action.id,
                                            title = action.title,
                                            subtitle = action.description,
                                            selected = action.id == overlayState.selectedActionId,
                                        )
                                    },
                            )
                        },
                    onItemClick = { choice ->
                        onEpisodeSelected(choice.id)
                    },
                )
            }
        }
    }
}

private const val DETAIL_OVERLAY_INITIAL_FOCUS_DELAY_MS = 120L
