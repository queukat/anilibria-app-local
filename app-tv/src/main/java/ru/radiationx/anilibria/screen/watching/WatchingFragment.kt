package ru.radiationx.anilibria.screen.watching

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
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.mainpages.MainShellContentFragment
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel

class WatchingFragment : Fragment(), MainShellContentFragment {

    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }
    private val watchingViewModel by quillParentViewModel<WatchingViewModel>()
    private val historyViewModel by quillParentViewModel<WatchingHistoryViewModel>()
    private val continueViewModel by quillParentViewModel<WatchingContinueViewModel>()
    private val recommendsViewModel by quillParentViewModel<WatchingRecommendsViewModel>()

    private var rowOrderState by mutableStateOf(
        listOf(
            WatchingViewModel.CONTINUE_ROW_ID,
            WatchingViewModel.HISTORY_ROW_ID,
            WatchingViewModel.RECOMMENDS_ROW_ID,
        )
    )
    private var continueTitleState by mutableStateOf("Продолжить просмотр")
    private var historyTitleState by mutableStateOf("История просмотров")
    private var recommendsTitleState by mutableStateOf("Рекомендации")
    private var continueCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var historyCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var recommendsCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var focusRequestToken by mutableIntStateOf(1)

    override var onRequestRailFocus: (() -> Boolean)? = null
    override var onContentMovedDown: (() -> Unit)? = null
    override var onContentMovedUp: (() -> Unit)? = null
    override var onRequestHeaderFocus: (() -> Boolean)? = null

    override fun requestContentFocus(): Boolean {
        val rootView = view ?: return false
        focusRequestToken++
        rootView.requestFocus()
        return true
    }

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
                WatchingScreen(
                    sections = buildSections(),
                    focusRequestToken = focusRequestToken,
                    onItemClick = ::handleItemClick,
                    onRequestRailFocus = { onRequestRailFocus?.invoke() == true },
                    onRequestHeaderFocus = { onRequestHeaderFocus?.invoke() == true },
                    onContentMovedDown = { onContentMovedDown?.invoke() },
                    onContentMovedUp = { onContentMovedUp?.invoke() },
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

        viewLifecycleOwner.lifecycle.addObserver(watchingViewModel)
        viewLifecycleOwner.lifecycle.addObserver(historyViewModel)
        viewLifecycleOwner.lifecycle.addObserver(continueViewModel)
        viewLifecycleOwner.lifecycle.addObserver(recommendsViewModel)

        subscribeTo(watchingViewModel.rowListData) { rowOrderState = it }
        subscribeTo(continueViewModel.rowTitle) { continueTitleState = it }
        subscribeTo(historyViewModel.rowTitle) { historyTitleState = it }
        subscribeTo(recommendsViewModel.rowTitle) { recommendsTitleState = it }
        subscribeTo(continueViewModel.cardsData) { continueCardsState = it }
        subscribeTo(historyViewModel.cardsData) { historyCardsState = it }
        subscribeTo(recommendsViewModel.cardsData) { recommendsCardsState = it }
    }

    private fun buildSections(): List<WatchingSectionUiModel> {
        return rowOrderState.mapNotNull { rowId ->
            when (rowId) {
                WatchingViewModel.CONTINUE_ROW_ID -> WatchingSectionUiModel(
                    id = rowId,
                    title = continueTitleState,
                    items = continueCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
                )

                WatchingViewModel.HISTORY_ROW_ID -> WatchingSectionUiModel(
                    id = rowId,
                    title = historyTitleState,
                    items = historyCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
                )

                WatchingViewModel.RECOMMENDS_ROW_ID -> WatchingSectionUiModel(
                    id = rowId,
                    title = recommendsTitleState,
                    items = recommendsCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
                )

                else -> null
            }
        }
    }

    private fun handleItemClick(
        rowId: Long,
        item: CardItem,
    ) {
        when (rowId) {
            WatchingViewModel.CONTINUE_ROW_ID -> dispatchItemClick(continueViewModel, item)
            WatchingViewModel.HISTORY_ROW_ID -> dispatchItemClick(historyViewModel, item)
            WatchingViewModel.RECOMMENDS_ROW_ID -> dispatchItemClick(recommendsViewModel, item)
        }
    }

    private fun dispatchItemClick(
        viewModel: ru.radiationx.anilibria.common.BaseCardsViewModel,
        item: CardItem,
    ) {
        when (item) {
            is LibriaCard -> viewModel.onLibriaCardClick(item)
            is LinkCard -> viewModel.onLinkCardClick()
            is LoadingCard -> viewModel.onLoadingCardClick()
        }
    }
}
