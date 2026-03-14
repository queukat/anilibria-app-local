package ru.radiationx.anilibria.screen.watching

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.screen.mainpages.MainShellCallbacks
import ru.radiationx.anilibria.screen.mainpages.MainShellPageContent
import ru.radiationx.anilibria.screen.mainpages.collectStarted
import ru.radiationx.quill.getViewModel

internal class WatchingPageContent(
    private val fragment: Fragment,
    private val backgroundManager: GradientBackgroundManager,
) : MainShellPageContent {

    private val watchingViewModel = fragment.getViewModel(WatchingViewModel::class)
    private val historyViewModel = fragment.getViewModel(WatchingHistoryViewModel::class)
    private val continueViewModel = fragment.getViewModel(WatchingContinueViewModel::class)
    private val recommendsViewModel = fragment.getViewModel(WatchingRecommendsViewModel::class)

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
    private var focusRequestToken by mutableIntStateOf(0)
    private var visibilityRestoreToken by mutableIntStateOf(0)
    private var selectedItemState by mutableStateOf<CardItem?>(null)

    override fun bind(owner: LifecycleOwner) {
        backgroundManager.clearGradient()

        owner.lifecycle.addObserver(watchingViewModel)
        owner.lifecycle.addObserver(historyViewModel)
        owner.lifecycle.addObserver(continueViewModel)
        owner.lifecycle.addObserver(recommendsViewModel)

        owner.collectStarted(watchingViewModel.rowListData) { rowOrderState = it }
        owner.collectStarted(continueViewModel.rowTitle) { continueTitleState = it }
        owner.collectStarted(historyViewModel.rowTitle) { historyTitleState = it }
        owner.collectStarted(recommendsViewModel.rowTitle) { recommendsTitleState = it }
        owner.collectStarted(continueViewModel.cardsData) { continueCardsState = it }
        owner.collectStarted(historyViewModel.cardsData) { historyCardsState = it }
        owner.collectStarted(recommendsViewModel.cardsData) { recommendsCardsState = it }
    }

    override fun onSelected() {
        visibilityRestoreToken++
        val card = selectedItemState as? LibriaCard
        if (card != null) {
            backgroundManager.applyCard(card)
        } else {
            backgroundManager.clearGradient()
        }
    }

    override fun requestContentFocus(): Boolean {
        focusRequestToken++
        return buildSections().any { section -> section.items.isNotEmpty() }
    }

    @Composable
    override fun Render(callbacks: MainShellCallbacks) {
        WatchingScreen(
            sections = buildSections(),
            focusRequestToken = focusRequestToken,
            visibilityRestoreToken = visibilityRestoreToken,
            onItemClick = ::handleItemClick,
            onRequestRailFocus = callbacks.onRequestRailFocus,
            onRequestHeaderFocus = callbacks.onRequestHeaderFocus,
            onContentMovedDown = callbacks.onContentMovedDown,
            onContentMovedUp = callbacks.onContentMovedUp,
            onItemFocused = { _, _, item ->
                selectedItemState = item
                val card = item as? LibriaCard
                if (card != null) {
                    backgroundManager.applyCard(card)
                } else {
                    backgroundManager.clearGradient()
                }
            },
        )
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
        viewModel: BaseCardsViewModel,
        item: CardItem,
    ) {
        when (item) {
            is LibriaCard -> viewModel.onLibriaCardClick(item)
            is LinkCard -> viewModel.onLinkCardClick()
            is LoadingCard -> viewModel.onLoadingCardClick()
        }
    }
}
