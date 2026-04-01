package ru.radiationx.anilibria.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
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
import ru.radiationx.anilibria.screen.watching.TvCardScreenHorizontalPadding
import ru.radiationx.quill.getViewModel

internal class MainPageContent(
    private val fragment: Fragment,
    private val backgroundManager: GradientBackgroundManager,
) : MainShellPageContent {

    private companion object {
        val loadingItems = listOf<CardItem>(LoadingCard("Загрузка..."))
    }

    private val mainViewModel = fragment.getViewModel(MainViewModel::class)
    private val feedViewModel = fragment.getViewModel(MainFeedViewModel::class)
    private val scheduleViewModel = fragment.getViewModel(MainScheduleViewModel::class)
    private val favoritesViewModel = fragment.getViewModel(MainFavoritesViewModel::class)
    private val youtubeViewModel = fragment.getViewModel(MainYouTubeViewModel::class)

    private var rowOrderState by mutableStateOf(
        listOf(
            MainViewModel.FEED_ROW_ID,
            MainViewModel.SCHEDULE_ROW_ID,
            MainViewModel.YOUTUBE_ROW_ID,
        )
    )
    private val sectionStateById = linkedMapOf(
        MainViewModel.FEED_ROW_ID to MainSectionUiModel(
            id = MainViewModel.FEED_ROW_ID,
            title = MainSectionTitles.FEED,
            items = loadingItems,
        ),
        MainViewModel.FAVORITE_ROW_ID to MainSectionUiModel(
            id = MainViewModel.FAVORITE_ROW_ID,
            title = MainSectionTitles.FAVORITES,
            items = loadingItems,
        ),
        MainViewModel.SCHEDULE_ROW_ID to MainSectionUiModel(
            id = MainViewModel.SCHEDULE_ROW_ID,
            title = MainSectionTitles.SCHEDULE,
            items = loadingItems,
        ),
        MainViewModel.YOUTUBE_ROW_ID to MainSectionUiModel(
            id = MainViewModel.YOUTUBE_ROW_ID,
            title = MainSectionTitles.YOUTUBE,
            items = loadingItems,
        ),
    )
    private var sectionsState by mutableStateOf(buildOrderedSections(rowOrderState))
    private var focusRequestToken by mutableIntStateOf(0)
    private var visibilityRestoreToken by mutableIntStateOf(0)
    private var restoreSectionIndex by mutableIntStateOf(0)
    private var restoreItemIndex by mutableIntStateOf(0)
    private var restoreItemId by mutableIntStateOf(Int.MIN_VALUE)
    private var selectedItemState by mutableStateOf<CardItem?>(null)

    override fun bind(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(mainViewModel)
        owner.lifecycle.addObserver(feedViewModel)
        owner.lifecycle.addObserver(scheduleViewModel)
        owner.lifecycle.addObserver(favoritesViewModel)
        owner.lifecycle.addObserver(youtubeViewModel)

        owner.collectStarted(mainViewModel.rowListData, ::updateRowOrder)
        owner.collectStarted(feedViewModel.rowTitle) { updateSectionTitle(MainViewModel.FEED_ROW_ID, it) }
        owner.collectStarted(favoritesViewModel.rowTitle) { updateSectionTitle(MainViewModel.FAVORITE_ROW_ID, it) }
        owner.collectStarted(scheduleViewModel.rowTitle) { updateSectionTitle(MainViewModel.SCHEDULE_ROW_ID, it) }
        owner.collectStarted(youtubeViewModel.rowTitle) { updateSectionTitle(MainViewModel.YOUTUBE_ROW_ID, it) }
        owner.collectStarted(feedViewModel.cardsData) { updateSectionItems(MainViewModel.FEED_ROW_ID, it) }
        owner.collectStarted(favoritesViewModel.cardsData) { updateSectionItems(MainViewModel.FAVORITE_ROW_ID, it) }
        owner.collectStarted(scheduleViewModel.cardsData) { updateSectionItems(MainViewModel.SCHEDULE_ROW_ID, it) }
        owner.collectStarted(youtubeViewModel.cardsData) { updateSectionItems(MainViewModel.YOUTUBE_ROW_ID, it) }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvCardScreenHorizontalPadding),
        ) {
            MainScreen(
                sections = sectionsState,
                interactionsEnabled = callbacks.contentInteractionsEnabled,
                focusRequestToken = focusRequestToken,
                visibilityRestoreToken = visibilityRestoreToken,
                contentRestoreState = MainContentRestoreState(
                    preferredSectionIndex = restoreSectionIndex,
                    preferredItemIndex = restoreItemIndex,
                    preferredItemId = restoreItemId,
                ),
                onItemClick = ::handleItemClick,
                onRequestRailFocus = callbacks.onRequestRailFocus,
                onRequestHeaderFocus = callbacks.onRequestHeaderFocus,
                onContentMovedDown = callbacks.onContentMovedDown,
                onContentMovedUp = callbacks.onContentMovedUp,
                onItemFocused = { sectionIndex, itemIndex, item ->
                    restoreSectionIndex = sectionIndex
                    restoreItemIndex = itemIndex
                    restoreItemId = item.getId()
                    selectedItemState = item
                    backgroundManager.applyCard(item)
                },
            )
        }
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
            transform = { section ->
                section.copy(items = items.ifEmpty { loadingItems })
            },
        )
    }

    private fun updateSection(
        rowId: Long,
        transform: (MainSectionUiModel) -> MainSectionUiModel,
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
    ): List<MainSectionUiModel> {
        return rowIds.mapNotNull(sectionStateById::get)
    }

    private fun handleItemClick(
        rowId: Long,
        item: CardItem,
    ) {
        when (rowId) {
            MainViewModel.FEED_ROW_ID -> dispatchItemClick(feedViewModel, item)
            MainViewModel.FAVORITE_ROW_ID -> dispatchItemClick(favoritesViewModel, item)
            MainViewModel.SCHEDULE_ROW_ID -> dispatchItemClick(scheduleViewModel, item)
            MainViewModel.YOUTUBE_ROW_ID -> dispatchItemClick(youtubeViewModel, item)
        }
    }

    private fun dispatchItemClick(
        viewModel: BaseCardsViewModel,
        item: CardItem,
    ) {
        viewModel.onCardItemClick(item)
    }
}
