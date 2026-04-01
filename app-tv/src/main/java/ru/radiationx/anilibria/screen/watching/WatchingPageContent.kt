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

    private var rowOrderState by mutableStateOf(
        listOf(
            WatchingViewModel.CONTINUE_ROW_ID,
            WatchingViewModel.HISTORY_ROW_ID,
            WatchingViewModel.RECOMMENDS_ROW_ID,
        )
    )
    private val sectionStateById = linkedMapOf(
        WatchingViewModel.CONTINUE_ROW_ID to WatchingSectionUiModel(
            id = WatchingViewModel.CONTINUE_ROW_ID,
            title = "Продолжить просмотр",
            items = loadingItems,
        ),
        WatchingViewModel.HISTORY_ROW_ID to WatchingSectionUiModel(
            id = WatchingViewModel.HISTORY_ROW_ID,
            title = "История просмотров",
            items = loadingItems,
        ),
        WatchingViewModel.RECOMMENDS_ROW_ID to WatchingSectionUiModel(
            id = WatchingViewModel.RECOMMENDS_ROW_ID,
            title = "Рекомендации",
            items = loadingItems,
        ),
    )
    private var sectionsState by mutableStateOf(buildOrderedSections(rowOrderState))
    private var focusRequestToken by mutableIntStateOf(0)
    private var visibilityRestoreToken by mutableIntStateOf(0)
    private var selectedItemState by mutableStateOf<CardItem?>(null)

    override fun bind(owner: LifecycleOwner) {
        backgroundManager.clearGradient()

        owner.lifecycle.addObserver(watchingViewModel)
        owner.lifecycle.addObserver(historyViewModel)
        owner.lifecycle.addObserver(continueViewModel)
        owner.lifecycle.addObserver(recommendsViewModel)

        owner.collectStarted(watchingViewModel.rowListData, ::updateRowOrder)
        owner.collectStarted(continueViewModel.rowTitle) { updateSectionTitle(WatchingViewModel.CONTINUE_ROW_ID, it) }
        owner.collectStarted(historyViewModel.rowTitle) { updateSectionTitle(WatchingViewModel.HISTORY_ROW_ID, it) }
        owner.collectStarted(recommendsViewModel.rowTitle) { updateSectionTitle(WatchingViewModel.RECOMMENDS_ROW_ID, it) }
        owner.collectStarted(continueViewModel.cardsData) { updateSectionItems(WatchingViewModel.CONTINUE_ROW_ID, it) }
        owner.collectStarted(historyViewModel.cardsData) { updateSectionItems(WatchingViewModel.HISTORY_ROW_ID, it) }
        owner.collectStarted(recommendsViewModel.cardsData) { updateSectionItems(WatchingViewModel.RECOMMENDS_ROW_ID, it) }
    }

    override fun onSelected() {
        visibilityRestoreToken++
        selectedItemState?.let(backgroundManager::applyCard) ?: backgroundManager.clearGradient()
    }

    override fun requestContentFocus(): Boolean {
        focusRequestToken++
        return sectionsState.any { section -> section.items.isNotEmpty() }
    }

    @Composable
    override fun Render(callbacks: MainShellCallbacks) {
        WatchingScreen(
            sections = sectionsState,
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
                backgroundManager.applyCard(item)
            },
        )
    }

    private fun updateRowOrder(rowIds: List<Long>) {
        if (rowOrderState == rowIds) {
            return
        }
        rowOrderState = rowIds
        syncSectionsState()
    }

    private fun updateSectionTitle(
        rowId: Long,
        title: String,
    ) {
        updateSection(
            rowId = rowId,
            transform = { section -> section.copy(title = title) },
        )
    }

    private fun updateSectionItems(
        rowId: Long,
        items: List<CardItem>,
    ) {
        updateSection(
            rowId = rowId,
            transform = { section -> section.copy(items = items.ifEmpty { loadingItems }) },
        )
    }

    private fun updateSection(
        rowId: Long,
        transform: (WatchingSectionUiModel) -> WatchingSectionUiModel,
    ) {
        val current = sectionStateById[rowId] ?: return
        val updated = transform(current)
        if (updated == current) {
            return
        }
        sectionStateById[rowId] = updated
        syncSectionsState()
    }

    private fun syncSectionsState() {
        val orderedSections = buildOrderedSections(rowOrderState)
        if (sectionsState != orderedSections) {
            sectionsState = orderedSections
        }
    }

    private fun buildOrderedSections(
        rowIds: List<Long>,
    ): List<WatchingSectionUiModel> {
        return rowIds.mapNotNull(sectionStateById::get)
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
