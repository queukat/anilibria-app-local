package ru.radiationx.anilibria.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.main.MainSectionBlock
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.TvBottomContentInset
import ru.radiationx.anilibria.screen.watching.TvBottomDescriptionInset
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.indexOfItemId
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvPageHeader

@Composable
internal fun ScheduleScreen(
    sections: List<MainSectionUiModel>,
    loadingVisible: Boolean,
    focusRequestToken: Int,
    onItemClick: (Long, CardItem) -> Unit,
    onItemFocused: (CardItem?) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalState = remember { LazyListState() }
    val sectionKeys = remember(sections) {
        sections.map { section ->
            section.id to section.items.map(CardItem::getId)
        }
    }
    val rowStates = remember(sectionKeys) { List(sections.size) { LazyListState() } }
    val sectionRequesters = remember(sectionKeys) {
        sections.map { section ->
            List(section.items.size) { androidx.compose.ui.focus.FocusRequester() }
        }
    }
    val stateActionRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var selectedItem by remember(sectionKeys) { mutableStateOf<CardItem?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemId by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val nonContentItems = remember(sectionKeys) {
        sections.flatMap { section -> section.items }.filter { it !is LibriaCard }
    }
    val stateCard = remember(nonContentItems) {
        nonContentItems.firstOrNull { it is LoadingCard && it.isError }
            ?: nonContentItems.firstOrNull { it is LoadingCard }
            ?: nonContentItems.firstOrNull { it is InfoCard }
            ?: nonContentItems.firstOrNull()
    }
    val stateActionCard = remember(nonContentItems) {
        nonContentItems.filterIsInstance<LinkCard>().firstOrNull()
    }
    val showStatePanel = sections.isEmpty() || (
        sections.isNotEmpty() && sections.all { section ->
            section.items.none { it is LibriaCard }
        }
    )
    val hasContent = remember(sectionKeys, showStatePanel) {
        sections.any { section -> section.items.any { it is LibriaCard } } && !showStatePanel
    }

    fun requestStateActionFocus(): Boolean {
        if (!showStatePanel || stateActionCard == null) {
            return false
        }
        scope.launch {
            requestWatchingFocusAfterAttach(stateActionRequester)
        }
        return true
    }

    fun sectionListIndex(sectionIndex: Int): Int = sectionIndex + 1

    fun targetInSection(
        sectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty()
        return requesters
            .takeIf { it.isNotEmpty() }
            ?.let { sectionIndex to preferredItemIndex.coerceIn(0, it.lastIndex) }
    }

    fun findRestoreTarget(
        preferredSectionIndex: Int,
        preferredItemIndex: Int,
        preferredItemId: Int = Int.MIN_VALUE,
    ): Pair<Int, Int>? {
        var target = if (preferredItemId != Int.MIN_VALUE) {
            var targetById: Pair<Int, Int>? = null
            sections.forEachIndexed { sectionIndex, section ->
                if (targetById == null) {
                    val itemIndex = section.items.indexOfItemId(preferredItemId)
                    if (itemIndex != null) {
                        targetById = sectionIndex to itemIndex
                    }
                }
            }
            targetById
        } else {
            null
        }
        val clampedSectionIndex = preferredSectionIndex.coerceIn(0, sections.lastIndex.coerceAtLeast(0))
        for (offset in 0..sections.size) {
            if (target == null) {
                target = targetInSection(
                    sectionIndex = clampedSectionIndex + offset,
                    preferredItemIndex = preferredItemIndex,
                )
            }
            if (target == null && offset > 0) {
                target = targetInSection(
                    sectionIndex = clampedSectionIndex - offset,
                    preferredItemIndex = preferredItemIndex,
                )
            }
        }
        return target
    }

    fun requestSectionFocus(
        currentSectionIndex: Int,
        direction: Int,
        preferredItemIndex: Int,
    ): Boolean {
        var targetSectionIndex = currentSectionIndex + direction
        while (targetSectionIndex in sections.indices) {
            val requesters = sectionRequesters.getOrNull(targetSectionIndex).orEmpty()
            if (requesters.isNotEmpty()) {
                val targetItemIndex = preferredItemIndex.coerceIn(0, requesters.lastIndex)
                scope.launch {
                    verticalState.scrollItemIntoViewIfNeeded(sectionListIndex(targetSectionIndex))
                    rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
                    requestWatchingFocusAfterAttach(requesters.getOrNull(targetItemIndex))
                }
                return true
            }
            targetSectionIndex += direction
        }
        return false
    }

    fun keepItemVisible(sectionIndex: Int, itemIndex: Int) {
        scope.launch {
            verticalState.scrollItemIntoViewIfNeeded(sectionListIndex(sectionIndex))
            rowStates.getOrNull(sectionIndex)?.scrollItemIntoViewIfNeeded(itemIndex)
        }
    }

    LaunchedEffect(sectionKeys) {
        val selectedId = selectedItem?.getId()
        val visibleItems = sections.asSequence().flatMap { it.items.asSequence() }.toList()
        val hadSelectedItem = selectedId != null
        val stillVisible = selectedId != null && visibleItems.any { it.getId() == selectedId }
        if (!stillVisible) {
            selectedItem = null
        }
        if (hadSelectedItem && !stillVisible) {
            val restoreTarget = findRestoreTarget(
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
            )
            if (restoreTarget != null) {
                val (targetSectionIndex, targetItemIndex) = restoreTarget
                verticalState.scrollItemIntoViewIfNeeded(sectionListIndex(targetSectionIndex))
                rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
                requestWatchingFocusAfterAttach(
                    sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
                )
            }
        }
    }

    LaunchedEffect(selectedItem) {
        onItemFocused(selectedItem)
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        if (showStatePanel) {
            if (requestStateActionFocus()) {
                handledFocusToken = focusRequestToken
            }
            return@LaunchedEffect
        }
        val restoreTarget = if (lastFocusedItemId != Int.MIN_VALUE) {
            findRestoreTarget(
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
            )
        } else {
            null
        }
        val (targetSectionIndex, targetItemIndex) = restoreTarget ?: run {
            val firstSectionIndex = sections.indexOfFirst { it.items.isNotEmpty() }
            if (firstSectionIndex < 0) {
                return@LaunchedEffect
            }
            firstSectionIndex to 0
        }
        verticalState.scrollItemIntoViewIfNeeded(sectionListIndex(targetSectionIndex))
        rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
        if (requestWatchingFocusAfterAttach(
                sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
            )
        ) {
            handledFocusToken = focusRequestToken
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceColor.copy(alpha = 0.16f),
                        Color.Transparent,
                    )
                )
            )
            .padding(horizontal = TvScreenHorizontalPadding, vertical = TvPageVerticalPadding),
    ) {
        LazyColumn(
            state = verticalState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = if (hasContent) TvBottomDescriptionInset else TvBottomContentInset,
            ),
        ) {
            item(key = "schedule-header") {
                TvPageHeader(
                    title = "Расписание",
                    subtitle = "Свежие и ближайшие релизы по дням недели",
                    palette = palette,
                )
            }

            if (showStatePanel) {
                item(key = "schedule-state") {
                    val stateTitle: String
                    val stateSubtitle: String
                    val stateAccent: Boolean
                    val stateLoading: Boolean
                    when (val item = stateCard) {
                        is LoadingCard -> {
                            stateTitle = item.title.ifBlank { "Загружаем расписание" }
                            stateSubtitle = item.description.ifBlank {
                                if (item.isError) {
                                    "Проверьте подключение и попробуйте снова."
                                } else {
                                    "Подождите немного, экран обновится автоматически."
                                }
                            }
                            stateAccent = item.isError
                            stateLoading = !item.isError
                        }

                        is InfoCard -> {
                            stateTitle = item.title
                            stateSubtitle = item.subtitle
                            stateAccent = false
                            stateLoading = false
                        }

                        is LinkCard -> {
                            stateTitle = "Расписание временно недоступно"
                            stateSubtitle = "Попробуйте обновить экран ещё раз."
                            stateAccent = false
                            stateLoading = false
                        }

                        else -> {
                            stateTitle = if (loadingVisible) {
                                "Загружаем расписание"
                            } else {
                                "На ближайшие дни пока пусто"
                            }
                            stateSubtitle = if (loadingVisible) {
                                "Подождите немного, экран обновится автоматически."
                            } else {
                                "Проверьте экран позже: новые релизы появятся здесь, как только расписание обновится."
                            }
                            stateAccent = false
                            stateLoading = loadingVisible
                        }
                    }
                    TvContentStatePanel(
                        title = stateTitle,
                        subtitle = stateSubtitle,
                        palette = palette,
                        accent = stateAccent,
                        loading = stateLoading,
                        action = stateActionCard?.let { actionCard ->
                            {
                                TvContentStateActionButton(
                                    text = actionCard.title,
                                    palette = palette,
                                    focusRequester = stateActionRequester,
                                    onClick = { onItemClick(-1L, actionCard) },
                                    onUp = { false },
                                    onDown = { true },
                                )
                            }
                        },
                    )
                }
            }

            if (!showStatePanel) {
                itemsIndexed(
                    items = sections,
                    key = { _, section -> section.id },
                ) { sectionIndex, section ->
                    MainSectionBlock(
                        title = section.title,
                        items = section.items,
                        palette = palette,
                        rowState = rowStates.getOrNull(sectionIndex) ?: LazyListState(),
                        requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty(),
                        onItemClick = { item -> onItemClick(section.id, item) },
                        onItemFocused = { itemIndex, item ->
                            selectedItem = item
                            lastFocusedSectionIndex = sectionIndex
                            lastFocusedItemIndex = itemIndex
                            lastFocusedItemId = item.getId()
                            keepItemVisible(sectionIndex, itemIndex)
                        },
                        onLeftEdge = { false },
                        onUp = { itemIndex -> requestSectionFocus(sectionIndex, -1, itemIndex) },
                        onDown = { itemIndex -> requestSectionFocus(sectionIndex, 1, itemIndex) },
                    )
                }
            }
        }

        if (!showStatePanel) {
            selectedItem?.let { item ->
                val description = item.toTvCardDescription { card ->
                    card.resolveDescription(context)
                }
                if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
                    WatchingDescriptionBar(
                        title = description.title.toString(),
                        subtitle = description.subtitle.toString(),
                        palette = palette,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
