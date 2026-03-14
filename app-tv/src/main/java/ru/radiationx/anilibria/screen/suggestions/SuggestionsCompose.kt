package ru.radiationx.anilibria.screen.suggestions

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingFilterChip
import ru.radiationx.anilibria.screen.watching.WatchingMessageCard
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.WatchingPosterCard
import ru.radiationx.anilibria.screen.watching.TvBottomContentInset
import ru.radiationx.anilibria.screen.watching.TvBottomDescriptionInset
import ru.radiationx.anilibria.screen.watching.TvPageHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvRowEndPadding
import ru.radiationx.anilibria.screen.watching.TvRowSpacing
import ru.radiationx.anilibria.screen.watching.TvScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvSectionHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.indexOfItemId
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import ru.radiationx.anilibria.ui.compose.TvPageHeader
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvSectionHeader
import ru.radiationx.anilibria.ui.compose.TvOverlayTextField

internal data class SuggestionsSectionUiModel(
    val id: Long,
    val title: String,
    val items: List<CardItem>,
)

private const val SuggestionsQueryMinLength = 3

@Composable
internal fun SuggestionsScreen(
    query: String,
    sections: List<SuggestionsSectionUiModel>,
    progressVisible: Boolean,
    voiceSearchAvailable: Boolean,
    focusRequestToken: Int,
    onQueryChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    onItemClick: (Long, CardItem) -> Unit,
    onItemFocused: (CardItem?) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val verticalState = remember { LazyListState() }
    val searchRequester = remember { FocusRequester() }
    val voiceRequester = remember { FocusRequester() }
    val sectionKeys = remember(sections) {
        sections.map { section ->
            section.id to section.items.map(CardItem::getId)
        }
    }
    val rowStates = remember(sectionKeys) { List(sections.size) { LazyListState() } }
    val sectionRequesters = remember(sectionKeys) {
        sections.map { section ->
            List(section.items.size) { FocusRequester() }
        }
    }
    var selectedItem by remember(sectionKeys) { mutableStateOf<CardItem?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemId by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastFocusArea by remember { mutableStateOf(SuggestionsFocusArea.Field) }
    val hasContent = remember(sectionKeys) { sections.any { it.items.isNotEmpty() } }

    fun isStateOnlyInfoSection(section: SuggestionsSectionUiModel): Boolean {
        return section.items.singleOrNull() is InfoCard
    }

    fun requestTextFieldFocus(): Boolean {
        return requestWatchingFocus(searchRequester)
    }

    fun targetInSection(
        sectionIndex: Int,
        preferredItemIndex: Int,
    ): Pair<Int, Int>? {
        val section = sections.getOrNull(sectionIndex)
        return if (section == null || isStateOnlyInfoSection(section)) {
            null
        } else {
            val requesters = sectionRequesters.getOrNull(sectionIndex).orEmpty()
            requesters
                .takeIf { it.isNotEmpty() }
                ?.let { sectionIndex to preferredItemIndex.coerceIn(0, it.lastIndex) }
        }
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

    fun requestLastContentFocus(): Boolean {
        val restoreTarget = findRestoreTarget(
            preferredSectionIndex = lastFocusedSectionIndex,
            preferredItemIndex = lastFocusedItemIndex,
            preferredItemId = lastFocusedItemId,
        ) ?: return false
        val targetSectionIndex = restoreTarget.first
        val targetItemIndex = restoreTarget.second
        scope.launch {
            verticalState.scrollItemIntoViewIfNeeded(targetSectionIndex)
            rowStates.getOrNull(targetSectionIndex)?.scrollItemIntoViewIfNeeded(targetItemIndex)
            requestWatchingFocusAfterAttach(
                sectionRequesters.getOrNull(targetSectionIndex)?.getOrNull(targetItemIndex)
            )
        }
        return true
    }

    fun requestSectionFocus(
        currentSectionIndex: Int,
        direction: Int,
        preferredItemIndex: Int,
    ): Boolean {
        var targetSectionIndex = currentSectionIndex + direction
        while (targetSectionIndex in sections.indices) {
            if (isStateOnlyInfoSection(sections[targetSectionIndex])) {
                targetSectionIndex += direction
                continue
            }
            val requesters = sectionRequesters.getOrNull(targetSectionIndex).orEmpty()
            if (requesters.isNotEmpty()) {
                val targetItemIndex = preferredItemIndex.coerceIn(0, requesters.lastIndex)
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

    fun requestFirstSectionFocus(): Boolean {
        val firstSectionIndex = sections.indexOfFirst { section ->
            section.items.isNotEmpty() && !isStateOnlyInfoSection(section)
        }
        if (firstSectionIndex < 0) {
            return false
        }
        scope.launch {
            verticalState.scrollItemIntoViewIfNeeded(firstSectionIndex)
            rowStates.getOrNull(firstSectionIndex)?.scrollItemIntoViewIfNeeded(0)
            requestWatchingFocusAfterAttach(sectionRequesters.getOrNull(firstSectionIndex)?.firstOrNull())
        }
        return true
    }

    fun keepItemVisible(sectionIndex: Int, itemIndex: Int) {
        scope.launch {
            verticalState.scrollItemIntoViewIfNeeded(sectionIndex)
            rowStates.getOrNull(sectionIndex)?.scrollItemIntoViewIfNeeded(itemIndex)
        }
    }

    LaunchedEffect(sectionKeys) {
        val visibleItems = sections.asSequence().flatMap { it.items.asSequence() }.toList()
        val selectedId = selectedItem?.getId()
        val stillVisible = selectedId != null && visibleItems.any { it.getId() == selectedId }
        if (!stillVisible) {
            selectedItem = null
        }
        if (lastFocusedItemId != Int.MIN_VALUE && visibleItems.none { it.getId() == lastFocusedItemId }) {
            lastFocusedItemId = Int.MIN_VALUE
        }
        if (selectedId != null && !stillVisible) {
            val restoreTarget = findRestoreTarget(
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
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

    LaunchedEffect(selectedItem) {
        onItemFocused(selectedItem)
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        val focused = when (lastFocusArea) {
            SuggestionsFocusArea.Results -> requestLastContentFocus()
            SuggestionsFocusArea.Voice -> voiceSearchAvailable && requestWatchingFocus(voiceRequester)
            SuggestionsFocusArea.Field -> false
        }
        if ((focused || requestTextFieldFocus())) {
            handledFocusToken = focusRequestToken
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.surfaceColor.copy(alpha = 0.18f),
                        Color.Transparent,
                    )
                )
            )
            .padding(horizontal = TvScreenHorizontalPadding, vertical = TvPageVerticalPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(TvPageHeaderSpacing),
        ) {
            TvPageHeader(
                title = "Поиск",
                subtitle = "Ищите по названию и переходите к релизам без лишних шагов.",
                palette = palette,
            )
            SuggestionsSearchField(
                value = query,
                palette = palette,
                progressVisible = progressVisible,
                voiceSearchAvailable = voiceSearchAvailable,
                focusRequester = searchRequester,
                voiceRequester = voiceRequester,
                onValueChange = onQueryChange,
                onVoiceSearchClick = onVoiceSearchClick,
                onFieldFocused = {
                    lastFocusArea = SuggestionsFocusArea.Field
                    selectedItem = null
                },
                onVoiceFocused = {
                    lastFocusArea = SuggestionsFocusArea.Voice
                    selectedItem = null
                },
                onDown = ::requestFirstSectionFocus,
            )

            LazyColumn(
                state = verticalState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TvSectionSpacing),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = if (hasContent) TvBottomDescriptionInset else TvBottomContentInset,
                ),
            ) {
                itemsIndexed(
                    items = sections,
                    key = { _, section -> section.id },
                ) { sectionIndex, section ->
                    SuggestionsSectionBlock(
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
                            lastFocusArea = SuggestionsFocusArea.Results
                            keepItemVisible(sectionIndex, itemIndex)
                        },
                        onUp = { itemIndex ->
                            if (sectionIndex == 0) {
                                requestTextFieldFocus()
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
        }

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

@Composable
private fun SuggestionsSearchField(
    value: String,
    palette: WatchingPalette,
    progressVisible: Boolean,
    voiceSearchAvailable: Boolean,
    focusRequester: FocusRequester,
    voiceRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    onFieldFocused: () -> Unit,
    onVoiceFocused: () -> Unit,
    onDown: () -> Boolean,
) {
    var isFocused by remember { mutableStateOf(false) }
    val helperText = if (value.length < SuggestionsQueryMinLength) {
        "Введите минимум $SuggestionsQueryMinLength символа, чтобы показать точные результаты."
    } else {
        "Результаты обновляются по мере ввода."
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TvOverlayTextField(
            label = "Запрос",
            value = value,
            onValueChange = onValueChange,
            palette = palette,
            focusRequester = focusRequester,
            singleLine = true,
            minLines = 1,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        onFieldFocused()
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionRight -> {
                            voiceSearchAvailable && requestWatchingFocus(voiceRequester)
                        }

                        Key.DirectionDown -> onDown()
                        else -> false
                    }
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = helperText,
                color = if (isFocused) palette.textColor else palette.secondaryTextColor,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (progressVisible) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = palette.accentColor,
                        trackColor = palette.textColor.copy(alpha = 0.16f),
                    )
                }
                if (voiceSearchAvailable) {
                    WatchingFilterChip(
                        text = "Голосом",
                        palette = palette,
                        focusRequester = voiceRequester,
                        minWidth = 124.dp,
                        emphasized = false,
                        onClick = onVoiceSearchClick,
                        onFocused = onVoiceFocused,
                        onLeft = { requestWatchingFocus(focusRequester) },
                        onDown = onDown,
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsSectionBlock(
    title: String,
    items: List<CardItem>,
    palette: WatchingPalette,
    rowState: LazyListState,
    requesters: List<FocusRequester>,
    onItemClick: (CardItem) -> Unit,
    onItemFocused: (Int, CardItem) -> Unit,
    onUp: (Int) -> Boolean,
    onDown: (Int) -> Boolean,
) {
    val stateInfoCard = items.singleOrNull() as? InfoCard

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TvSectionHeaderSpacing),
    ) {
        TvSectionHeader(
            title = title,
            palette = palette,
        )

        if (stateInfoCard != null) {
            TvContentStatePanel(
                title = stateInfoCard.title,
                subtitle = stateInfoCard.subtitle,
                palette = palette,
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
                        is LibriaCard -> WatchingPosterCard(
                            imageUrl = item.image,
                            palette = palette,
                            focusRequester = requesters[index],
                            onClick = { onItemClick(item) },
                            onFocused = { onItemFocused(index, item) },
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
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )

                        is LinkCard -> WatchingMessageCard(
                            title = item.title,
                            subtitle = "Нажмите, чтобы выполнить действие",
                            palette = palette,
                            focusRequester = requesters[index],
                            onClick = { onItemClick(item) },
                            onFocused = { onItemFocused(index, item) },
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
                            onUp = { onUp(index) },
                            onDown = { onDown(index) },
                        )
                    }
                }
            }
        }
    }
}

private enum class SuggestionsFocusArea {
    Field,
    Voice,
    Results,
}
