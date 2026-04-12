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
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.screen.mainpages.MainShellCallbacks
import ru.radiationx.anilibria.screen.mainpages.MainShellPageContent
import ru.radiationx.anilibria.screen.mainpages.MainShellPageSectionsState
import ru.radiationx.anilibria.screen.mainpages.collectStarted
import ru.radiationx.quill.getViewModel

internal class WatchingPageContent(
    private val fragment: Fragment,
    private val backgroundManager: GradientBackgroundManager,
) : MainShellPageContent {
    private companion object {
        val loadingItems = listOf<CardItem>(LoadingCard("Загрузка..."))
    }

    private val watchingViewModel = fragment.getViewModel(WatchingViewModel::class)
    private val historyViewModel = fragment.getViewModel(WatchingHistoryViewModel::class)
    private val continueViewModel = fragment.getViewModel(WatchingContinueViewModel::class)
    private val recommendsViewModel = fragment.getViewModel(WatchingRecommendsViewModel::class)

    private val sectionsState =
        MainShellPageSectionsState(
            initialOrder =
                listOf(
                    WatchingViewModel.CONTINUE_ROW_ID,
                    WatchingViewModel.HISTORY_ROW_ID,
                    WatchingViewModel.RECOMMENDS_ROW_ID,
                ),
            initialSections =
                linkedMapOf(
                    WatchingViewModel.CONTINUE_ROW_ID to
                        WatchingSectionUiModel(
                            id = WatchingViewModel.CONTINUE_ROW_ID,
                            title = "Продолжить просмотр",
                            items = loadingItems,
                        ),
                    WatchingViewModel.HISTORY_ROW_ID to
                        WatchingSectionUiModel(
                            id = WatchingViewModel.HISTORY_ROW_ID,
                            title = "История просмотров",
                            items = loadingItems,
                        ),
                    WatchingViewModel.RECOMMENDS_ROW_ID to
                        WatchingSectionUiModel(
                            id = WatchingViewModel.RECOMMENDS_ROW_ID,
                            title = "Рекомендации",
                            items = loadingItems,
                        ),
                ),
        )
    private var focusRequestToken by mutableIntStateOf(0)
    private var visibilityRestoreToken by mutableIntStateOf(0)
    private var selectedItemState by mutableStateOf<CardItem?>(null)

    override fun bind(owner: LifecycleOwner) {
        backgroundManager.clearGradient()

        owner.lifecycle.addObserver(watchingViewModel)
        owner.collectStarted(watchingViewModel.rowListData) { rowIds ->
            sectionsState.updateRowOrder(rowIds)
        }
        bindSection(
            owner = owner,
            rowId = WatchingViewModel.CONTINUE_ROW_ID,
            viewModel = continueViewModel,
        )
        bindSection(
            owner = owner,
            rowId = WatchingViewModel.HISTORY_ROW_ID,
            viewModel = historyViewModel,
        )
        bindSection(
            owner = owner,
            rowId = WatchingViewModel.RECOMMENDS_ROW_ID,
            viewModel = recommendsViewModel,
        )
    }

    override fun onSelected() {
        visibilityRestoreToken++
        selectedItemState?.let(backgroundManager::applyCard) ?: backgroundManager.clearGradient()
    }

    override fun requestContentFocus(): Boolean {
        focusRequestToken++
        return sectionsState.orderedSections.any { section -> section.items.isNotEmpty() }
    }

    @Composable
    override fun Render(callbacks: MainShellCallbacks) {
        WatchingScreen(
            sections = sectionsState.orderedSections,
            interactionsEnabled = callbacks.contentInteractionsEnabled,
            focusRequestToken = focusRequestToken,
            visibilityRestoreToken = visibilityRestoreToken,
            onItemClick = ::handleItemClick,
            onRequestRailFocus = callbacks.onRequestRailFocus,
            onRequestHeaderFocus = callbacks.onRequestHeaderFocus,
            onContentMovedDown = callbacks.onContentMovedDown,
            onContentMovedUp = callbacks.onContentMovedUp,
            onItemFocused = { _, _, item ->
                selectedItemState = item
            },
            onBackdropItemFocused = { item -> backgroundManager.applyCard(item) },
        )
    }

    private fun bindSection(
        owner: LifecycleOwner,
        rowId: Long,
        viewModel: BaseCardsViewModel,
    ) {
        owner.lifecycle.addObserver(viewModel)
        owner.collectStarted(viewModel.rowTitle) { title ->
            sectionsState.updateSection(rowId) { section ->
                section.copy(title = title)
            }
        }
        owner.collectStarted(viewModel.cardsData) { items ->
            sectionsState.updateSection(rowId) { section ->
                section.copy(items = items.ifEmpty { loadingItems })
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
        viewModel.onCardItemClick(item)
    }
}
