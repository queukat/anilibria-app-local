package ru.radiationx.anilibria.screen.player.quality

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

class PlayerQualityGuidedFragment : BasePlayerGuidedFragment() {

    private val viewModel by viewModel<PlayerQualityViewModel> { argExtra }
    private var availableIdsState by mutableStateOf<List<Long>>(emptyList())
    private var selectedIdState by mutableLongStateOf(-1L)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.availableData) {
            availableIdsState = it
        }

        subscribeTo(viewModel.selectedData) { selectedId ->
            selectedIdState = selectedId
        }
    }

    @Composable
    override fun RenderContent() {
        TvOverlayScreen(
            title = "Качество воспроизведения",
            subtitle = "Выберите поток, который будет использовать плеер для текущего эпизода.",
            panelMaxWidth = 700.dp,
        ) { _ ->
            TvOverlayChoiceList(
                sections = listOf(
                    TvOverlayChoiceSection(
                        items = availableIdsState.mapNotNull(::mapChoiceOrNull),
                    )
                ),
                onItemClick = { choice ->
                    viewModel.applyQuality(choice.id)
                },
            )
        }
    }

    private fun mapChoiceOrNull(id: Long): TvOverlayChoiceItem? = when (id) {
        PlayerQualityViewModel.SD_ACTION_ID -> TvOverlayChoiceItem(
            id = id,
            title = "480p",
            selected = id == selectedIdState,
        )

        PlayerQualityViewModel.HD_ACTION_ID -> TvOverlayChoiceItem(
            id = id,
            title = "720p",
            selected = id == selectedIdState,
        )

        PlayerQualityViewModel.FULL_HD_ACTION_ID -> TvOverlayChoiceItem(
            id = id,
            title = "1080p",
            selected = id == selectedIdState,
        )

        else -> null
    }
}
