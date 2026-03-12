package ru.radiationx.anilibria.screen.player.episodes

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.screen.player.BasePlayerGuidedFragment
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceItem
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceList
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceSection
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class PlayerEpisodesGuidedFragment : BasePlayerGuidedFragment() {

    private val viewModel by viewModel<PlayerEpisodesViewModel> { argExtra }
    private var groupsState by mutableStateOf<List<PlayerEpisodesViewModel.Group>>(emptyList())
    private var selectedActionIdState by mutableLongStateOf(-1L)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.episodesData) {
            groupsState = it
        }

        subscribeTo(viewModel.selectedActionId) { actionId ->
            selectedActionIdState = actionId
        }
    }

    @Composable
    override fun RenderContent() {
        TvOverlayScreen(
            title = "Список серий",
            subtitle = "Выберите эпизод, с которого нужно продолжить просмотр.",
            panelMaxWidth = 860.dp,
        ) { _ ->
            TvOverlayChoiceList(
                sections = groupsState.map { group ->
                    TvOverlayChoiceSection(
                        title = if (groupsState.size > 1) group.title else null,
                        items = group.actions.map { action ->
                            TvOverlayChoiceItem(
                                id = action.id,
                                title = action.title,
                                subtitle = action.description,
                                selected = action.id == selectedActionIdState,
                            )
                        }
                    )
                },
                onItemClick = { choice ->
                    viewModel.applyEpisode(choice.id)
                },
            )
        }
    }
}
