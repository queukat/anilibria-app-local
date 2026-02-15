package ru.radiationx.anilibria.screen.details.description

import android.os.Bundle
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.fragment.FakeGuidedStepFragment
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.quill.inject
import ru.radiationx.shared.ktx.android.getExtraNotNull
import ru.radiationx.shared.ktx.android.putExtra

class DetailDescriptionGuidedFragment : FakeGuidedStepFragment() {

    companion object {
        private const val CLOSE_ACTION_ID = 0L

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

    private val guidedRouter by inject<GuidedRouter>()

    override fun onProvideTheme(): Int = R.style.AppTheme_Player_LeanbackWizard

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance = Guidance(
        getExtraNotNull(ARG_TITLE),
        getExtraNotNull(ARG_MESSAGE),
        null,
        null
    )

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        super.onCreateActions(actions, savedInstanceState)
        actions += GuidedAction.Builder(requireContext())
            .id(CLOSE_ACTION_ID)
            .title("Закрыть")
            .build()
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        super.onGuidedActionClicked(action)
        guidedRouter.close()
    }
}
