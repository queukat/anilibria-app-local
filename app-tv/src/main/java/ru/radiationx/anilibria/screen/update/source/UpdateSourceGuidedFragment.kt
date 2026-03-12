package ru.radiationx.anilibria.screen.update.source

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.fragment.ComposeGuidedFragment
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceItem
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceList
import ru.radiationx.anilibria.ui.compose.TvOverlayChoiceSection
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.data.entity.domain.updater.UpdateData
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class UpdateSourceGuidedFragment : ComposeGuidedFragment() {

    private val viewModel by viewModel<UpdateSourceViewModel>()

    private var sourcesState by mutableStateOf<List<UpdateData.UpdateLink>>(emptyList())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.sourcesData) {
            sourcesState = it
        }
    }

    @Composable
    override fun RenderContent() {
        TvOverlayScreen(
            title = "Источник обновления",
            subtitle = "Выберите, откуда загрузить новую версию приложения.",
            panelMaxWidth = 680.dp,
        ) { _ ->
            TvOverlayChoiceList(
                sections = listOf(
                    TvOverlayChoiceSection(
                        items = sourcesState.mapIndexed { index, source ->
                            TvOverlayChoiceItem(
                                id = index.toLong(),
                                title = source.name,
                            )
                        }
                    )
                ),
                onItemClick = { choice ->
                    viewModel.onLinkClick(choice.id.toInt())
                },
            )
        }
    }
}
