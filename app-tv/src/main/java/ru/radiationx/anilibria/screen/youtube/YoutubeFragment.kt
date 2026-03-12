package ru.radiationx.anilibria.screen.youtube

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
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class YoutubeFragment : Fragment() {

    private val viewModel by viewModel<YouTubeViewModel>()
    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

    private var cardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
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
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focusRequestToken++
                }
            }
            setContent {
                YoutubeScreen(
                    cards = cardsState,
                    focusRequestToken = focusRequestToken,
                    onItemClick = ::handleItemClick,
                    onItemFocused = { item ->
                        backgroundManager.applyCard(item)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        focusRequestToken++
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backgroundManager.clearGradient()

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        subscribeTo(viewModel.cardsData) {
            cardsState = it.ifEmpty {
                listOf(
                    InfoCard(
                        title = "Пока пусто",
                        subtitle = "Для этой подборки ещё нет доступных роликов",
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        backgroundManager.clearGradient()
        super.onDestroyView()
    }

    private fun handleItemClick(item: CardItem) {
        when (item) {
            is LibriaCard -> viewModel.onLibriaCardClick(item)
            is LinkCard -> viewModel.onLinkCardClick()
            is LoadingCard -> viewModel.onLoadingCardClick()
            is InfoCard -> Unit
        }
    }
}
