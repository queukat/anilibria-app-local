package ru.radiationx.anilibria.screen.main

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
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.screen.mainpages.MainShellContentFragment
import ru.radiationx.shared.ktx.android.subscribeTo
import ru.radiationx.shared_app.di.quillParentViewModel

class MainFragment : Fragment(), MainShellContentFragment {

    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }
    private val mainViewModel by quillParentViewModel<MainViewModel>()
    private val feedViewModel by quillParentViewModel<MainFeedViewModel>()
    private val scheduleViewModel by quillParentViewModel<MainScheduleViewModel>()
    private val favoritesViewModel by quillParentViewModel<MainFavoritesViewModel>()
    private val youtubeViewModel by quillParentViewModel<MainYouTubeViewModel>()

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
    private var focusRequestToken by mutableIntStateOf(1)
    private var restoreSectionIndex by mutableIntStateOf(0)
    private var restoreItemIndex by mutableIntStateOf(0)
    private var restoreItemId by mutableIntStateOf(Int.MIN_VALUE)

    override var onRequestRailFocus: (() -> Boolean)? = null
    override var onContentMovedDown: (() -> Unit)? = null
    override var onContentMovedUp: (() -> Unit)? = null
    override var onRequestHeaderFocus: (() -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSectionIndex = savedInstanceState?.getInt(KEY_RESTORE_SECTION_INDEX) ?: 0
        restoreItemIndex = savedInstanceState?.getInt(KEY_RESTORE_ITEM_INDEX) ?: 0
        restoreItemId = savedInstanceState?.getInt(KEY_RESTORE_ITEM_ID) ?: Int.MIN_VALUE
    }

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
                MainScreen(
                    sections = buildSections(),
                    focusRequestToken = focusRequestToken,
                    contentRestoreState = MainContentRestoreState(
                        preferredSectionIndex = restoreSectionIndex,
                        preferredItemIndex = restoreItemIndex,
                        preferredItemId = restoreItemId,
                    ),
                    onItemClick = ::handleItemClick,
                    onRequestRailFocus = { onRequestRailFocus?.invoke() == true },
                    onRequestHeaderFocus = { onRequestHeaderFocus?.invoke() == true },
                    onContentMovedDown = { onContentMovedDown?.invoke() },
                    onContentMovedUp = { onContentMovedUp?.invoke() },
                    onItemFocused = { sectionIndex, itemIndex, item ->
                        restoreSectionIndex = sectionIndex
                        restoreItemIndex = itemIndex
                        restoreItemId = item.getId()
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

        viewLifecycleOwner.lifecycle.addObserver(mainViewModel)
        viewLifecycleOwner.lifecycle.addObserver(feedViewModel)
        viewLifecycleOwner.lifecycle.addObserver(scheduleViewModel)
        viewLifecycleOwner.lifecycle.addObserver(favoritesViewModel)
        viewLifecycleOwner.lifecycle.addObserver(youtubeViewModel)

        subscribeTo(mainViewModel.rowListData) { rowOrderState = it }
        subscribeTo(feedViewModel.rowTitle) { feedTitleState = it }
        subscribeTo(favoritesViewModel.rowTitle) { favoritesTitleState = it }
        subscribeTo(scheduleViewModel.rowTitle) { scheduleTitleState = it }
        subscribeTo(youtubeViewModel.rowTitle) { youtubeTitleState = it }
        subscribeTo(feedViewModel.cardsData) { feedCardsState = it }
        subscribeTo(favoritesViewModel.cardsData) { favoritesCardsState = it }
        subscribeTo(scheduleViewModel.cardsData) { scheduleCardsState = it }
        subscribeTo(youtubeViewModel.cardsData) { youtubeCardsState = it }
    }

    override fun onDestroyView() {
        backgroundManager.clearGradient()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_RESTORE_SECTION_INDEX, restoreSectionIndex)
        outState.putInt(KEY_RESTORE_ITEM_INDEX, restoreItemIndex)
        outState.putInt(KEY_RESTORE_ITEM_ID, restoreItemId)
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
        when (item) {
            is LibriaCard -> viewModel.onLibriaCardClick(item)
            is LinkCard -> viewModel.onLinkCardClick()
            is LoadingCard -> viewModel.onLoadingCardClick()
        }
    }

    private companion object {
        const val KEY_RESTORE_SECTION_INDEX = "restore_section_index"
        const val KEY_RESTORE_ITEM_INDEX = "restore_item_index"
        const val KEY_RESTORE_ITEM_ID = "restore_item_id"
    }
}
