package ru.radiationx.anilibria.screen.details.other

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.fragment.ComposeGuidedFragment
import ru.radiationx.anilibria.screen.details.DetailExtra
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButton
import ru.radiationx.anilibria.ui.compose.TvOverlayInfoBlock
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.getExtraNotNull
import ru.radiationx.shared.ktx.android.putExtra

class DetailOtherGuidedFragment : ComposeGuidedFragment() {

    companion object {
        private const val ARG_ID = "id"

        fun newInstance(releaseId: ReleaseId) = DetailOtherGuidedFragment().putExtra {
            putParcelable(ARG_ID, releaseId)
        }
    }

    private val viewModel by viewModel<DetailOtherViewModel> {
        DetailExtra(getExtraNotNull(ARG_ID))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycle.addObserver(viewModel)
    }

    @Composable
    override fun RenderContent() {
        val clearRequester = remember { FocusRequester() }
        val markRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            requestWatchingFocusAfterAttach(clearRequester)
        }

        TvOverlayScreen(
            title = "Дополнительные действия",
            subtitle = "Эти действия изменяют состояние просмотра для всего релиза.",
            panelMaxWidth = 700.dp,
        ) { palette ->
            TvOverlayInfoBlock(
                text = "Используйте их только если хотите быстро очистить прогресс " +
                    "или отметить весь релиз как просмотренный.",
                palette = palette,
            )
            TvOverlayActionButton(
                text = "Сбросить историю просмотров",
                palette = palette,
                focusRequester = clearRequester,
                downRequester = markRequester,
                destructive = true,
                onClick = viewModel::onClearClick,
                modifier = Modifier.fillMaxWidth(),
            )
            TvOverlayActionButton(
                text = "Отметить всё как просмотренные",
                palette = palette,
                focusRequester = markRequester,
                upRequester = clearRequester,
                onClick = viewModel::onMarkClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
