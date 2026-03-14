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
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text

internal data class SuggestionsSectionUiModel(
    val id: Long,
    val title: String,
    val items: List<CardItem>,
)

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
    var selectedItem by remember(sectionKeys) {
        mutableStateOf<CardItem?>(sections.asSequence().flatMap { it.items.asSequence() }.firstOrNull())
    }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }

    fun requestTextFieldFocus(): Boolean {
        return requestWatchingFocus(searchRequester)
    }

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
    ): Pair<Int, Int>? {
        val clampedSectionIndex = preferredSectionIndex.coerceIn(0, sections.lastIndex.coerceAtLeast(0))
        var target: Pair<Int, Int>? = null
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
                    verticalState.scrollToItem(targetSectionIndex)
                    rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
                    withFrameNanos { }
                    requestWatchingFocus(requesters.getOrNull(targetItemIndex))
                }
                return true
            }
            targetSectionIndex += direction
        }
        return false
    }

    fun requestFirstSectionFocus(): Boolean {
        val firstSectionIndex = sections.indexOfFirst { it.items.isNotEmpty() }
        if (firstSectionIndex < 0) {
            return false
        }
        scope.launch {
            verticalState.scrollToItem(firstSectionIndex)
            rowStates.getOrNull(firstSectionIndex)?.scrollToItem(0)
            withFrameNanos { }
            requestWatchingFocus(sectionRequesters.getOrNull(firstSectionIndex)?.firstOrNull())
        }
        return true
    }

    fun keepItemVisible(sectionIndex: Int, itemIndex: Int) {
        scope.launch {
            verticalState.scrollToItem(sectionIndex)
            rowStates.getOrNull(sectionIndex)?.scrollToItem(itemIndex)
        }
    }

    LaunchedEffect(sectionKeys) {
        val selectedId = selectedItem?.getId()
        val visibleItems = sections.asSequence().flatMap { it.items.asSequence() }.toList()
        val hadSelectedItem = selectedId != null
        val stillVisible = selectedId != null && visibleItems.any { it.getId() == selectedId }
        selectedItem = visibleItems.firstOrNull { it.getId() == selectedId } ?: visibleItems.firstOrNull()
        if (hadSelectedItem && !stillVisible) {
            val restoreTarget = findRestoreTarget(lastFocusedSectionIndex, lastFocusedItemIndex)
            if (restoreTarget != null) {
                val (targetSectionIndex, targetItemIndex) = restoreTarget
                verticalState.scrollToItem(targetSectionIndex)
                rowStates.getOrNull(targetSectionIndex)?.scrollToItem(targetItemIndex)
                withFrameNanos { }
                requestWatchingFocus(
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
        if (requestTextFieldFocus()) {
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SuggestionsSearchField(
                value = query,
                palette = palette,
                progressVisible = progressVisible,
                voiceSearchAvailable = voiceSearchAvailable,
                focusRequester = searchRequester,
                onValueChange = onQueryChange,
                onVoiceSearchClick = onVoiceSearchClick,
                onDown = ::requestFirstSectionFocus,
            )

            LazyColumn(
                state = verticalState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = if (selectedItem != null) 124.dp else 28.dp,
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
    onValueChange: (String) -> Unit,
    onVoiceSearchClick: () -> Unit,
    onDown: () -> Boolean,
) {
    var isFocused by remember { mutableStateOf(false) }
    val voiceRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Поиск",
            color = palette.textColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
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
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = palette.textColor,
                    fontSize = 18.sp,
                ),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Введите минимум 3 символа",
                        color = palette.secondaryTextColor,
                        fontSize = 16.sp,
                    )
                },
                trailingIcon = if (progressVisible) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = palette.accentColor,
                            trackColor = palette.textColor.copy(alpha = 0.16f),
                        )
                    }
                } else {
                    null
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accentColor,
                    unfocusedBorderColor = palette.textColor.copy(alpha = 0.2f),
                    focusedContainerColor = palette.surfaceColor.copy(alpha = 0.92f),
                    unfocusedContainerColor = palette.surfaceColor.copy(alpha = 0.88f),
                    focusedTextColor = palette.textColor,
                    unfocusedTextColor = palette.textColor,
                    cursorColor = palette.accentColor,
                ),
            )
            if (voiceSearchAvailable) {
                WatchingFilterChip(
                    text = "Голосом",
                    palette = palette,
                    focusRequester = voiceRequester,
                    minWidth = 132.dp,
                    emphasized = true,
                    onClick = onVoiceSearchClick,
                    onLeft = { requestWatchingFocus(focusRequester) },
                    onDown = onDown,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Text(
            text = if (value.length < 3) {
                "Пока запрос короче 3 символов, показываются рекомендации."
            } else {
                "Результаты обновляются по мере ввода."
            },
            color = if (isFocused) palette.textColor else palette.secondaryTextColor,
            fontSize = 13.sp,
        )
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                color = palette.textColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(palette.textColor.copy(alpha = 0.08f))
            )
        }

        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(end = 24.dp),
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
