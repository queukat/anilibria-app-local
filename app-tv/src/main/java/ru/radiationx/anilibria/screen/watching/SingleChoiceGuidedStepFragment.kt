package ru.radiationx.anilibria.screen.watching

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction

class SingleChoiceGuidedStepFragment : GuidedStepSupportFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val optionsSize = requireArguments().getStringArrayList(ARG_OPTIONS)?.size ?: 0
        val selectedIndex = requireArguments().getInt(ARG_SELECTED_INDEX, -1)

        if (selectedIndex in 0 until optionsSize) {
            view.post {
                selectedActionPosition = selectedIndex
            }
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
            requireArguments().getString(ARG_TITLE).orEmpty(),
            null,
            null,
            null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val options = requireArguments().getStringArrayList(ARG_OPTIONS).orEmpty()
        val selectedIndex = requireArguments().getInt(ARG_SELECTED_INDEX, -1).coerceAtLeast(-1)

        options.forEachIndexed { index, title ->
            actions += GuidedAction.Builder(requireContext())
                .id(index.toLong())
                .title(title)
                .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                .checked(index == selectedIndex)
                .build()
        }
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val requestKey = requireArguments().getString(ARG_REQUEST_KEY).orEmpty()
        parentFragmentManager.setFragmentResult(
            requestKey,
            bundleOf(RESULT_INDEX to action.id.toInt())
        )
        parentFragmentManager.popBackStack()
    }

    companion object {
        const val RESULT_INDEX = "result_index"

        private const val ARG_TITLE = "arg_title"
        private const val ARG_REQUEST_KEY = "arg_request_key"
        private const val ARG_OPTIONS = "arg_options"
        private const val ARG_SELECTED_INDEX = "arg_selected_index"

        fun newInstance(
            title: String,
            requestKey: String,
            options: List<String>,
            selectedIndex: Int,
        ): SingleChoiceGuidedStepFragment {
            return SingleChoiceGuidedStepFragment().apply {
                arguments = bundleOf(
                    ARG_TITLE to title,
                    ARG_REQUEST_KEY to requestKey,
                    ARG_OPTIONS to ArrayList(options),
                    ARG_SELECTED_INDEX to selectedIndex
                )
            }
        }
    }
}
