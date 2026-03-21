package ru.radiationx.anilibria.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingMessageCard
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.WatchingPosterCard
import ru.radiationx.anilibria.screen.watching.TvBottomContentInset
import ru.radiationx.anilibria.screen.watching.TvBottomDescriptionInset
import ru.radiationx.anilibria.screen.watching.TvDescriptionBarPadding
import ru.radiationx.anilibria.screen.watching.TvPosterCardWidth
import ru.radiationx.anilibria.screen.watching.TvRowEndPadding
import ru.radiationx.anilibria.screen.watching.TvRowSpacing
import ru.radiationx.anilibria.screen.watching.TvRowsScreenVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvCardScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvSectionHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.hasTvPosterContent
import ru.radiationx.anilibria.screen.watching.indexOfItemId
import ru.radiationx.anilibria.screen.watching.isTvStateOnlySection
import ru.radiationx.anilibria.screen.watching.primaryTvStateItem
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded
import ru.radiationx.anilibria.screen.watching.tvStateFocusIndex
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvSectionHeader
import ru.radiationx.anilibria.ui.compose.tvAppBackground

internal data class MainSectionUiModel(
    val id: Long,
    val title: String,
    val items: List<CardItem>,
)

@Stable
internal data class MainContentRestoreState(
    val preferredSectionIndex: Int = 0,
    val preferredItemIndex: Int = 0,
    val preferredItemId: Int = Int.MIN_VALUE,
)

private const val DESCRIPTION_REFRESH_INTERVAL_MS = 60_000L

@Composable
internal fun MainScreen(
    sections: List<MainSectionUiModel>,
    focusRequestToken: Int,
    visibilityRestoreToken: Int,
    contentRestoreState: MainContentRestoreState,
    onItemClick: (Long, CardItem) -> Unit,
    onRequestRailFocus: () -> Boolean,
    onRequestHeaderFocus: () -> Boolean,
    onContentMovedDown: () -> Unit,
    onContentMovedUp: () -> Unit,
    onItemFocused: (Int, Int, CardItem) -> Unit,
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
    val rowStates = remember(sectionKeys) {
        List(sections.size) { LazyListState() }
    }
    val sectionRequesters = remember(sectionKeys) {
        sections.map { section ->
            List(section.items.size) { androidx.compose.ui.focus.FocusRequester() }
        }
    }
    var selectedItem by remember(sectionKeys) { mutableStateOf<LibriaCard?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var descriptionTick by remember { mutableIntStateOf(0) }
    val hasContent = remember(sectionKeys) { sections.any { section -> section.items.hasTvPosterContent() } }

    fun targetInSection(
        sectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val items = sections.getOrNull(sectionIndex)?.items.orEmpty()
        val requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty()
        val targetItemIndex = requesters.takeIf { it.isNotEmpty() }?.let {
            if (items.hasTvPosterContent()) {
                preferredItemIndex.coerceIn(0, it.lastIndex)
            } else {
                items.tvStateFocusIndex()
            }
        }
        return targetItemIndex?.let { sectionIndex to it.coerceIn(0, requesters.lastIndex) }
    }

    fun targetByItemId(preferredItemId: Int): Pair<Int, Int>? {
        var target: Pair<Int, Int>? = null
        sections.forEachIndexed { sectionIndex, section ->
            if (target == null) {
                val itemIndex = section.items.indexOfItemId(preferredItemId)
                if (itemIndex != null) {
                    target = sectionIndex to itemIndex
                }
            }
        }
        return target
    }

    fun findRestoreTarget(
        preferredSectionIndex: Int,
        preferredItemIndex: Int,
        preferredItemId: Int = Int.MIN_VALUE,
    ): Pair<Int, Int>? {
        var target = if (preferredItemId != Int.MIN_VALUE) {
            targetByItemId(preferredItemId)
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
                if (direction > 0) {
                    onContentMovedDown()
                }
                scope.launch {
                    verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
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
            verticalState.scrollItemIntoViewIfNeeded(sectionIndex)
            rowStates.getOrNull(sectionIndex)?.scrollItemIntoViewIfNeeded(itemIndex)
        }
    }

    LaunchedEffect(sectionKeys) {
        val selectedId = selectedItem?.getId()
        val visibleCards = sections.asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<LibriaCard>()
            .toList()
        val preferredItemId = contentRestoreState.preferredItemId
        val hadFocusedItem = preferredItemId != Int.MIN_VALUE
        val stillVisible = sections.asSequence()
            .flatMap { it.items.asSequence() }
            .any { it.getId() == preferredItemId }
        selectedItem = visibleCards.firstOrNull { it.getId() == selectedId }
            ?: visibleCards.firstOrNull { it.getId() == preferredItemId }
        if (hadFocusedItem && !stillVisible) {
            val restoreTarget = findRestoreTarget(
                preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                preferredItemIndex = contentRestoreState.preferredItemIndex,
            )
            if (restoreTarget != null) {
                val (targetSectionIndex, targetItemIndex) = restoreTarget
                verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
                rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
                requestWatchingFocusAfterAttach(
                    sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
                )
            }
        }
    }

    LaunchedEffect(visibilityRestoreToken, sectionKeys) {
        if (visibilityRestoreToken <= 0 || contentRestoreState.preferredItemId == Int.MIN_VALUE) {
            return@LaunchedEffect
        }
        val restoreTarget = findRestoreTarget(
            preferredSectionIndex = contentRestoreState.preferredSectionIndex,
            preferredItemIndex = contentRestoreState.preferredItemIndex,
            preferredItemId = contentRestoreState.preferredItemId,
        ) ?: return@LaunchedEffect
        val (targetSectionIndex, targetItemIndex) = restoreTarget
        selectedItem = sections.getOrNull(targetSectionIndex)
            ?.items
            ?.getOrNull(targetItemIndex) as? LibriaCard
        verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
    }

    LaunchedEffect(selectedItem?.getId()) {
        if (selectedItem == null) {
            return@LaunchedEffect
        }
        while (isActive) {
            delay(DESCRIPTION_REFRESH_INTERVAL_MS)
            descriptionTick++
        }
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        val restoreTarget = if (contentRestoreState.preferredItemId != Int.MIN_VALUE) {
            findRestoreTarget(
                preferredSectionIndex = contentRestoreState.preferredSectionIndex,
                preferredItemIndex = contentRestoreState.preferredItemIndex,
                preferredItemId = contentRestoreState.preferredItemId,
            )
        } else {
            null
        }
        val (targetSectionIndex, targetItemIndex) = restoreTarget ?: run {
            val firstSectionIndex = sections.indexOfFirst { section ->
                section.items.hasTvPosterContent() || section.items.tvStateFocusIndex() != null
            }
            if (firstSectionIndex < 0) {
                return@LaunchedEffect
            }
            val firstTargetIndex = if (sections[firstSectionIndex].items.hasTvPosterContent()) {
                0
            } else {
                sections[firstSectionIndex].items.tvStateFocusIndex() ?: 0
            }
            firstSectionIndex to firstTargetIndex
        }
        verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
        rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
        if (
            requestWatchingFocusAfterAttach(
                sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
            )
        ) {
            handledFocusToken = focusRequestToken
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvAppBackground(palette)
            .padding(horizontal = TvCardScreenHorizontalPadding, vertical = TvRowsScreenVerticalPadding),
    ) {
        LazyColumn(
            state = verticalState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
            contentPadding = PaddingValues(
                top = 6.dp,
                bottom = if (hasContent) TvBottomDescriptionInset else TvBottomContentInset,
            ),
        ) {
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
                        selectedItem = item as? LibriaCard
                        keepItemVisible(sectionIndex, itemIndex)
                        onItemFocused(sectionIndex, itemIndex, item)
                    },
                    onLeftEdge = onRequestRailFocus,
                    onUp = { itemIndex ->
                        if (sectionIndex == 0) {
                            onContentMovedUp()
                            onRequestHeaderFocus()
                        } else {
                            requestSectionFocus(sectionIndex, -1, itemIndex)
                        }
                    },
                    onDown = { itemIndex ->
                        requestSectionFocus(sectionIndex, 1, itemIndex)
                    },
                )
            }
        }

        selectedItem?.let { item ->
            val description = remember(item, descriptionTick, context) {
                item.toTvCardDescription { card ->
                    card.resolveDescription(context)
                }
            }
            if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
                WatchingDescriptionBar(
                    title = description.title.toString(),
                    subtitle = description.subtitle.toString(),
                    palette = palette,
                    contentPadding = TvDescriptionBarPadding,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
internal fun MainSectionBlock(
    title: String,
    items: List<CardItem>,
    palette: WatchingPalette,
    rowState: LazyListState,
    requesters: List<androidx.compose.ui.focus.FocusRequester>,
    onItemClick: (CardItem) -> Unit,
    onItemFocused: (Int, CardItem) -> Unit,
    onLeftEdge: () -> Boolean,
    onUp: (Int) -> Boolean,
    onDown: (Int) -> Boolean,
    modifier: Modifier = Modifier,
    posterFocusedBackgroundColor: Color = palette.accentColor.copy(alpha = 0.12f),
    posterBorderColor: Color = palette.accentColor.copy(alpha = 0.92f),
    posterFocusedBorderWidth: androidx.compose.ui.unit.Dp = 2.dp,
    posterUnfocusedBorderWidth: androidx.compose.ui.unit.Dp = 1.dp,
) {
    val stateItem = remember(items) { items.primaryTvStateItem() }
    val stateFocusIndex = remember(items) { items.tvStateFocusIndex() }
    val stateFocusItem = remember(items, stateFocusIndex) {
        stateFocusIndex?.let(items::getOrNull)
    }
    val stateActionLabel = remember(stateFocusItem) {
        when (stateFocusItem) {
            is LinkCard -> stateFocusItem.title
            is LoadingCard -> if (stateFocusItem.isError) "Повторить" else null
            else -> null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TvSectionHeaderSpacing),
    ) {
        TvSectionHeader(
            title = title,
            palette = palette,
        )

        if (items.isTvStateOnlySection() && stateItem != null && stateFocusItem != null) {
            TvContentStatePanel(
                title = when (stateItem) {
                    is LoadingCard -> stateItem.title.ifBlank { "Загрузка" }
                    is LinkCard -> stateItem.title
                    is InfoCard -> stateItem.title
                    else -> title
                },
                subtitle = when (stateItem) {
                    is LoadingCard -> stateItem.description.ifBlank {
                        if (stateItem.isError) {
                            "Проверьте подключение и повторите попытку."
                        } else {
                            "Раздел обновится автоматически."
                        }
                    }
                    is LinkCard -> "Откройте полный раздел и продолжайте навигацию оттуда."
                    is InfoCard -> stateItem.subtitle
                    else -> ""
                },
                palette = palette,
                accent = stateItem is LoadingCard && stateItem.isError,
                loading = stateItem is LoadingCard && !stateItem.isError,
                focusRequester = if (stateActionLabel == null) {
                    requesters.getOrNull(stateFocusIndex ?: -1)
                } else {
                    null
                },
                onFocused = {
                    onItemFocused(stateFocusIndex ?: 0, stateFocusItem)
                },
                onLeft = onLeftEdge,
                onUp = { onUp(stateFocusIndex ?: 0) },
                onDown = { onDown(stateFocusIndex ?: 0) },
                action = stateActionLabel?.let { actionLabel ->
                    {
                        TvContentStateActionButton(
                            text = actionLabel,
                            palette = palette,
                            focusRequester = requesters.getOrNull(stateFocusIndex ?: -1)
                                ?: androidx.compose.ui.focus.FocusRequester.Default,
                            onClick = { onItemClick(stateFocusItem) },
                            onFocused = {
                                onItemFocused(stateFocusIndex ?: 0, stateFocusItem)
                            },
                            onLeft = onLeftEdge,
                            onUp = { onUp(stateFocusIndex ?: 0) },
                            onDown = { onDown(stateFocusIndex ?: 0) },
                        )
                    }
                },
            )
        } else {
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TvRowSpacing),
                contentPadding = PaddingValues(end = TvRowEndPadding),
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.getId() },
                ) { index, item ->
                    when (item) {
                        is LibriaCard -> {
                            val isYoutube = item.type is LibriaCard.Type.Youtube
                            WatchingPosterCard(
                                imageUrl = item.image,
                                palette = palette,
                                focusRequester = requesters[index],
                                cardWidth = if (isYoutube) 346.dp else TvPosterCardWidth,
                                contentAspectRatio = if (isYoutube) 330f / 185f else 130f / 185f,
                                focusedBackgroundColor = posterFocusedBackgroundColor,
                                focusedBorderColor = posterBorderColor,
                                focusedBorderWidth = posterFocusedBorderWidth,
                                unfocusedBorderWidth = posterUnfocusedBorderWidth,
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(index, item) },
                                onLeft = if (index == 0) onLeftEdge else null,
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )
                        }

                        is LinkCard -> WatchingMessageCard(
                            title = item.title,
                            subtitle = "Нажмите, чтобы выполнить действие",
                            palette = palette,
                            focusRequester = requesters[index],
                            onClick = { onItemClick(item) },
                            onFocused = { onItemFocused(index, item) },
                            onLeft = if (index == 0) onLeftEdge else null,
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )

                        is LoadingCard -> WatchingMessageCard(
                            title = item.title.ifBlank { "Загрузка" },
                            subtitle = item.description.ifBlank {
                                if (item.isError) "Нажмите, чтобы повторить попытку" else ""
                            },
                            palette = palette.copy(
                                accentColor = if (item.isError) {
                                    palette.accentColor
                                } else {
                                    palette.textColor.copy(alpha = 0.4f)
                                }
                            ),
                            focusRequester = requesters[index],
                            loading = !item.isError,
                            onClick = { onItemClick(item) },
                            onFocused = { onItemFocused(index, item) },
                            onLeft = if (index == 0) onLeftEdge else null,
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )

                        is InfoCard -> WatchingMessageCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            palette = palette,
                            focusRequester = requesters[index],
                            onClick = { onItemClick(item) },
                            onFocused = { onItemFocused(index, item) },
                            onLeft = if (index == 0) onLeftEdge else null,
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )
                    }
                }
            }
        }
    }
}
