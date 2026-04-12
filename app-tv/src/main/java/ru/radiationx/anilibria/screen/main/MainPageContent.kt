package ru.radiationx.anilibria.screen.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.TvStartupTrace
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.screen.mainpages.MainShellCallbacks
import ru.radiationx.anilibria.screen.mainpages.MainShellPageContent
import ru.radiationx.anilibria.screen.mainpages.MainShellPageSectionsState
import ru.radiationx.anilibria.screen.mainpages.collectStarted
import ru.radiationx.anilibria.screen.watching.TvCardScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvPosterCardWidth
import ru.radiationx.quill.getViewModel
import ru.radiationx.shared_app.imageloader.libriaImageLoader
import ru.radiationx.shared_app.imageloader.utils.toCacheKey
import kotlin.math.roundToInt

internal class MainPageContent(
    private val fragment: Fragment,
    private val backgroundManager: GradientBackgroundManager,
) : MainShellPageContent {
    private val mainViewModel = fragment.getViewModel(MainViewModel::class)
    private val feedViewModel = fragment.getViewModel(MainFeedViewModel::class)
    private val scheduleViewModel = fragment.getViewModel(MainScheduleViewModel::class)
    private val favoritesViewModel = fragment.getViewModel(MainFavoritesViewModel::class)
    private val youtubeViewModel = fragment.getViewModel(MainYouTubeViewModel::class)

    private val sectionsState =
        MainShellPageSectionsState(
            initialOrder =
                listOf(
                    MainViewModel.FEED_ROW_ID,
                    MainViewModel.SCHEDULE_ROW_ID,
                    MainViewModel.YOUTUBE_ROW_ID,
                ),
            initialSections =
                linkedMapOf(
                    MainViewModel.FEED_ROW_ID to
                        MainSectionUiModel(
                            id = MainViewModel.FEED_ROW_ID,
                            title = MainSectionTitles.FEED,
                            items = loadingItems,
                        ),
                    MainViewModel.FAVORITE_ROW_ID to
                        MainSectionUiModel(
                            id = MainViewModel.FAVORITE_ROW_ID,
                            title = MainSectionTitles.FAVORITES,
                            items = loadingItems,
                        ),
                    MainViewModel.SCHEDULE_ROW_ID to
                        MainSectionUiModel(
                            id = MainViewModel.SCHEDULE_ROW_ID,
                            title = MainSectionTitles.SCHEDULE,
                            items = loadingItems,
                        ),
                    MainViewModel.YOUTUBE_ROW_ID to
                        MainSectionUiModel(
                            id = MainViewModel.YOUTUBE_ROW_ID,
                            title = MainSectionTitles.YOUTUBE,
                            items = loadingItems,
                        ),
                ),
        )
    private var focusRequestToken by mutableIntStateOf(0)
    private var visibilityRestoreToken by mutableIntStateOf(0)
    private var restoreSectionIndex by mutableIntStateOf(0)
    private var restoreItemIndex by mutableIntStateOf(0)
    private var restoreItemId by mutableIntStateOf(Int.MIN_VALUE)
    private var selectedItemState by mutableStateOf<CardItem?>(null)
    private val prefetchedPosterUrls = linkedSetOf<String>()

    override fun bind(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(mainViewModel)
        owner.collectStarted(mainViewModel.rowListData) { rowIds ->
            sectionsState.updateRowOrder(rowIds)
        }
        bindSection(
            owner = owner,
            rowId = MainViewModel.FEED_ROW_ID,
            viewModel = feedViewModel,
            onItemsUpdated = ::prefetchMainFeedPosters,
        )
        bindSection(
            owner = owner,
            rowId = MainViewModel.FAVORITE_ROW_ID,
            viewModel = favoritesViewModel,
        )
        bindSection(
            owner = owner,
            rowId = MainViewModel.SCHEDULE_ROW_ID,
            viewModel = scheduleViewModel,
        )
        bindSection(
            owner = owner,
            rowId = MainViewModel.YOUTUBE_ROW_ID,
            viewModel = youtubeViewModel,
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = TvCardScreenHorizontalPadding),
        ) {
            MainScreen(
                sections = sectionsState.orderedSections,
                interactionsEnabled = callbacks.contentInteractionsEnabled,
                focusRequestToken = focusRequestToken,
                visibilityRestoreToken = visibilityRestoreToken,
                contentRestoreState =
                    MainContentRestoreState(
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
                },
                onBackdropItemFocused = { item -> backgroundManager.applyCard(item) },
            )
        }
    }

    private fun bindSection(
        owner: LifecycleOwner,
        rowId: Long,
        viewModel: BaseCardsViewModel,
        onItemsUpdated: (List<CardItem>) -> Unit = {},
    ) {
        owner.lifecycle.addObserver(viewModel)
        owner.collectStarted(viewModel.rowTitle) { title ->
            sectionsState.updateSection(rowId) { section ->
                section.copy(title = title)
            }
        }
        owner.collectStarted(viewModel.cardsData) { items ->
            if (items.any { it is LibriaCard }) {
                TvStartupTrace.markOnce("main_real_cards_visible")
            }
            onItemsUpdated(items)
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

    private fun prefetchMainFeedPosters(items: List<CardItem>) {
        val context = fragment.context ?: return
        val displayMetrics = context.resources.displayMetrics
        val posterWidthPx = (TvPosterCardWidth.value * displayMetrics.density).roundToInt()
        val posterHeightPx = (posterWidthPx / (130f / 185f)).roundToInt()
        val imageLoader = context.libriaImageLoader()

        items.asSequence()
            .filterIsInstance<LibriaCard>()
            .mapNotNull { card -> card.image.trim().takeIf { it.isNotEmpty() } }
            .distinct()
            .take(MAIN_FEED_PREFETCH_LIMIT)
            .forEach { imageUrl ->
                if (!prefetchedPosterUrls.add(imageUrl)) {
                    return@forEach
                }
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .diskCacheKey(imageUrl.toCacheKey())
                        .memoryCacheKey(imageUrl.toCacheKey())
                        .size(posterWidthPx, posterHeightPx)
                        .precision(Precision.INEXACT)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                )
            }
    }

    private companion object {
        val loadingItems = listOf<CardItem>(LoadingCard("Загрузка..."))
        const val MAIN_FEED_PREFETCH_LIMIT = 6
    }
}
