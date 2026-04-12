package ru.radiationx.anilibria.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceItem
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceList
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceSection
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen

internal enum class PlayerCompletionOverlay {
    EpisodeComplete,
    SeasonComplete,
}

private const val REPLAY_EPISODE_ACTION_ID = 0L
private const val NEXT_EPISODE_ACTION_ID = 1L
private const val REPLAY_SEASON_ACTION_ID = 2L
private const val CLOSE_PLAYER_ACTION_ID = 3L

@Composable
internal fun PlayerCompletionOverlayHost(
    overlay: PlayerCompletionOverlay,
    onReplayEpisodeClick: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    onReplaySeasonClick: () -> Unit,
    onClosePlayerClick: () -> Unit,
) {
    TvOverlayScreen(
        title =
            when (overlay) {
                PlayerCompletionOverlay.EpisodeComplete -> "Серия завершена"
                PlayerCompletionOverlay.SeasonComplete -> "Сезон завершён"
            },
        subtitle =
            when (overlay) {
                PlayerCompletionOverlay.EpisodeComplete ->
                    "Можно пересмотреть эпизод или сразу перейти к следующему."

                PlayerCompletionOverlay.SeasonComplete ->
                    "Можно пересмотреть финальную серию, перезапустить сезон или закрыть плеер."
            },
        panelMaxWidth =
            when (overlay) {
                PlayerCompletionOverlay.EpisodeComplete -> 700.dp
                PlayerCompletionOverlay.SeasonComplete -> 720.dp
            },
    ) { _ ->
        TvOverlayChoiceList(
            sections =
                listOf(
                    TvOverlayChoiceSection(
                        items =
                            when (overlay) {
                                PlayerCompletionOverlay.EpisodeComplete ->
                                    listOf(
                                        TvOverlayChoiceItem(
                                            id = REPLAY_EPISODE_ACTION_ID,
                                            title = "Начать серию заново",
                                        ),
                                        TvOverlayChoiceItem(
                                            id = NEXT_EPISODE_ACTION_ID,
                                            title = "Включить следующую серию",
                                            selected = true,
                                        ),
                                    )

                                PlayerCompletionOverlay.SeasonComplete ->
                                    listOf(
                                        TvOverlayChoiceItem(
                                            id = REPLAY_EPISODE_ACTION_ID,
                                            title = "Начать серию заново",
                                        ),
                                        TvOverlayChoiceItem(
                                            id = REPLAY_SEASON_ACTION_ID,
                                            title = "Начать с первой серии",
                                        ),
                                        TvOverlayChoiceItem(
                                            id = CLOSE_PLAYER_ACTION_ID,
                                            title = "Закрыть плеер",
                                            selected = true,
                                        ),
                                    )
                            },
                    ),
                ),
            onItemClick = { choice ->
                when (choice.id) {
                    REPLAY_EPISODE_ACTION_ID -> onReplayEpisodeClick()
                    NEXT_EPISODE_ACTION_ID -> onNextEpisodeClick()
                    REPLAY_SEASON_ACTION_ID -> onReplaySeasonClick()
                    CLOSE_PLAYER_ACTION_ID -> onClosePlayerClick()
                }
            },
        )
    }
}
