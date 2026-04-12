package ru.radiationx.anilibria.screen.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.ui.compose.ProvideGradientBackground
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class ScheduleFragment : Fragment() {
    private val viewModel by viewModel<ScheduleViewModel>()
    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

    private var rowsState by mutableStateOf<List<Pair<String, List<CardItem>>>>(emptyList())
    private var loadingState by mutableStateOf(true)
    private var focusRequestToken by mutableIntStateOf(1)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            onFocusChangeListener =
                View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        focusRequestToken++
                    }
                }
            setContent {
                ProvideGradientBackground(backgroundManager) {
                    ScheduleScreen(
                        sections = buildSections(),
                        loadingVisible = loadingState,
                        focusRequestToken = focusRequestToken,
                        onItemClick = { _, item -> handleItemClick(item) },
                        onItemFocused = { item ->
                            backgroundManager.applyCard(item)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        focusRequestToken++
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        backgroundManager.clearGradient()

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.scheduleRows) {
            rowsState = it
        }

        subscribeTo(viewModel.loadingState) {
            loadingState = it
        }
    }

    override fun onDestroyView() {
        backgroundManager.clearGradient()
        super.onDestroyView()
    }

    private fun buildSections(): List<MainSectionUiModel> {
        return rowsState.mapIndexed { index, row ->
            MainSectionUiModel(
                id = index.toLong(),
                title = row.first,
                items = row.second,
            )
        }
    }

    private fun handleItemClick(item: CardItem) {
        when (item) {
            is LibriaCard -> viewModel.onCardClick(item)
            is LinkCard -> viewModel.onRetryClick()
            is LoadingCard ->
                if (item.isError) {
                    viewModel.onRetryClick()
                }
            is InfoCard -> Unit
        }
    }
}
