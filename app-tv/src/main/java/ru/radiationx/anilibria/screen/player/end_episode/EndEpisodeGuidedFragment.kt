package ru.radiationx.anilibria.screen.player.end_episode

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

class EndEpisodeGuidedFragment : BasePlayerGuidedFragment() {

    companion object {
        private const val REPLAY_ACTION_ID = 0L
        private const val NEXT_ACTION_ID = 1L
    }

    private val viewModel by viewModel<EndEpisodeViewModel> { argExtra }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)
    }

    @Composable
    override fun RenderContent() {
        TvOverlayScreen(
            title = "Серия завершена",
            subtitle = "Можно пересмотреть эпизод или сразу перейти к следующему.",
            panelMaxWidth = 700.dp,
        ) { _ ->
            TvOverlayChoiceList(
                sections = listOf(
                    TvOverlayChoiceSection(
                        items = listOf(
                            TvOverlayChoiceItem(
                                id = REPLAY_ACTION_ID,
                                title = "Начать серию заново",
                            ),
                            TvOverlayChoiceItem(
                                id = NEXT_ACTION_ID,
                                title = "Включить следующую серию",
                            ),
                        )
                    )
                ),
                onItemClick = { choice ->
                    when (choice.id) {
                        REPLAY_ACTION_ID -> viewModel.onReplayClick()
                        NEXT_ACTION_ID -> viewModel.onNextClick()
                    }
                },
            )
        }
    }
}
