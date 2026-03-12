package ru.radiationx.anilibria.screen.details.description

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.common.fragment.ComposeGuidedFragment
import ru.radiationx.anilibria.ui.compose.TvOverlayActionButton
import ru.radiationx.anilibria.ui.compose.TvOverlayScreen
import ru.radiationx.anilibria.ui.compose.TvOverlayScrollableText
import ru.radiationx.shared.ktx.android.getExtraNotNull
import ru.radiationx.shared.ktx.android.putExtra

class DetailDescriptionGuidedFragment : ComposeGuidedFragment() {

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_MESSAGE = "arg_message"

        fun newInstance(
            title: String,
            message: String,
        ) = DetailDescriptionGuidedFragment().putExtra {
            putString(ARG_TITLE, title)
            putString(ARG_MESSAGE, message)
        }
    }

    @Composable
    override fun RenderContent() {
        val closeRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            closeRequester.requestFocus()
        }

        TvOverlayScreen(
            title = getExtraNotNull(ARG_TITLE),
            subtitle = null,
            panelMaxWidth = 920.dp,
        ) { palette ->
            TvOverlayScrollableText(
                text = getExtraNotNull(ARG_MESSAGE),
                palette = palette,
                modifier = androidx.compose.ui.Modifier.heightIn(min = 180.dp, max = 420.dp),
            )
            TvOverlayActionButton(
                text = "Закрыть",
                palette = palette,
                focusRequester = closeRequester,
                onClick = { guidedRouter.close() },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            )
        }
    }
}
