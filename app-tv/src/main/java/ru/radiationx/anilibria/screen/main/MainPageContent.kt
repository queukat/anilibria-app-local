package ru.radiationx.anilibria.screen.main

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

internal class MainPageContent(
    private val fragment: Fragment,
    private val backgroundManager: GradientBackgroundManager,
) : MainShellPageContent {

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
    private var feedTitleState by mutableStateOf("Самое актуальное")
    private var favoritesTitleState by mutableStateOf("Обновления в избранном")
    private var scheduleTitleState by mutableStateOf("Ожидается сегодня")
    private var youtubeTitleState by mutableStateOf("Обновления на YouTube")
    private var feedCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var favoritesCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var scheduleCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var youtubeCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
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

        owner.collectStarted(mainViewModel.rowListData) { rowOrderState = it }
        owner.collectStarted(feedViewModel.rowTitle) { feedTitleState = it }
        owner.collectStarted(favoritesViewModel.rowTitle) { favoritesTitleState = it }
        owner.collectStarted(scheduleViewModel.rowTitle) { scheduleTitleState = it }
        owner.collectStarted(youtubeViewModel.rowTitle) { youtubeTitleState = it }
        owner.collectStarted(feedViewModel.cardsData) { feedCardsState = it }
        owner.collectStarted(favoritesViewModel.cardsData) { favoritesCardsState = it }
        owner.collectStarted(scheduleViewModel.cardsData) { scheduleCardsState = it }
        owner.collectStarted(youtubeViewModel.cardsData) { youtubeCardsState = it }
    }

    override fun onSelected() {
        visibilityRestoreToken++
        selectedItemState?.let(backgroundManager::applyCard) ?: backgroundManager.clearGradient()
    }

    override fun requestContentFocus(): Boolean {
        focusRequestToken++
        return buildSections().any { section -> section.items.isNotEmpty() }
    }

    @Composable
    override fun Render(callbacks: MainShellCallbacks) {
        MainScreen(
            sections = buildSections(),
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

    private fun buildSections(): List<MainSectionUiModel> {
        return rowOrderState.mapNotNull { rowId ->
            when (rowId) {
                MainViewModel.FEED_ROW_ID -> MainSectionUiModel(
                    id = rowId,
                    title = feedTitleState,
                    items = feedCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
                )

                MainViewModel.FAVORITE_ROW_ID -> MainSectionUiModel(
                    id = rowId,
                    title = favoritesTitleState,
                    items = favoritesCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
                )

                MainViewModel.SCHEDULE_ROW_ID -> MainSectionUiModel(
                    id = rowId,
                    title = scheduleTitleState,
                    items = scheduleCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
                )

                MainViewModel.YOUTUBE_ROW_ID -> MainSectionUiModel(
                    id = rowId,
                    title = youtubeTitleState,
                    items = youtubeCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
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
