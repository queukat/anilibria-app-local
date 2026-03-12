package ru.radiationx.anilibria.screen.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.data.entity.common.ConfigScreenState
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class ConfigFragment : Fragment() {

    private val viewModel: ConfiguringViewModel by viewModel()

    private var screenState by mutableStateOf<ConfigScreenState?>(null)
    private var completingState by mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ConfigScreenContent(
                    screenState = screenState,
                    isCompleting = completingState,
                    onRepeatClick = viewModel::repeatCheck,
                    onSkipClick = viewModel::skipCheck,
                    onNextClick = viewModel::nextCheck,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        viewModel.startConfiguring()

        subscribeTo(viewModel.screenStateData) {
            screenState = it
        }
        subscribeTo(viewModel.completeEvent) {
            completingState = true
        }
    }
}
