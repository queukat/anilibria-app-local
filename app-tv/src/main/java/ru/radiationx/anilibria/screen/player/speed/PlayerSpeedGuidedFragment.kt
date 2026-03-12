package ru.radiationx.anilibria.screen.player.speed

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

class PlayerSpeedGuidedFragment : BasePlayerGuidedFragment() {

    private val viewModel by viewModel<PlayerSpeedViewModel>()
    private var speedState by mutableStateOf(PlayerSpeedViewModel.SpeedState())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.speedState) {
            speedState = it
        }
    }

    @Composable
    override fun RenderContent() {
        TvOverlayScreen(
            title = "Скорость воспроизведения",
            subtitle = "Настройка применяется сразу и сохраняется для следующих запусков.",
            panelMaxWidth = 700.dp,
        ) { _ ->
            TvOverlayChoiceList(
                sections = listOf(
                    TvOverlayChoiceSection(
                        items = speedState.speeds.mapIndexed { index, speed ->
                            TvOverlayChoiceItem(
                                id = index.toLong(),
                                title = speed.toTitle(),
                                selected = index == speedState.selectedIndex,
                            )
                        }
                    )
                ),
                onItemClick = { choice ->
                    viewModel.applySpeed(choice.id.toInt())
                },
            )
        }
    }

    private fun Float.toTitle(): String {
        return if (this == 1.0f) {
            "Нормальная"
        } else {
            "${this}x"
        }
    }
}
