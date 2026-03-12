package ru.radiationx.anilibria.screen.player.end_season

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.screen.player.BasePlayerGuidedFragment
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceItem
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceList
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceSection
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.quill.viewModel

class EndSeasonGuidedFragment : BasePlayerGuidedFragment() {

    companion object {
        private const val REPLAY_EPISODE_ACTION_ID = 0L
        private const val REPLAY_SEASON_ACTION_ID = 1L
        private const val CLOSE_ACTION_ID = 2L
    }

    private val viewModel by viewModel<EndSeasonViewModel> { argExtra }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)
    }

    @Composable
    override fun RenderContent() {
        TvOverlayScreen(
            title = "Сезон завершён",
            subtitle = "Можно пересмотреть финальную серию, перезапустить сезон или закрыть плеер.",
            panelMaxWidth = 720.dp,
        ) { _ ->
            TvOverlayChoiceList(
                sections = listOf(
                    TvOverlayChoiceSection(
                        items = listOf(
                            TvOverlayChoiceItem(
                                id = REPLAY_EPISODE_ACTION_ID,
                                title = "Начать серию заново",
                            ),
                            TvOverlayChoiceItem(
                                id = REPLAY_SEASON_ACTION_ID,
                                title = "Начать с первой серии",
                            ),
                            TvOverlayChoiceItem(
                                id = CLOSE_ACTION_ID,
                                title = "Закрыть плеер",
                            ),
                        )
                    )
                ),
                onItemClick = { choice ->
                    when (choice.id) {
                        REPLAY_EPISODE_ACTION_ID -> viewModel.onReplayEpisodeClick()
                        REPLAY_SEASON_ACTION_ID -> viewModel.onReplaySeasonClick()
                        CLOSE_ACTION_ID -> viewModel.onCloseClick()
                    }
                },
            )
        }
    }
}
