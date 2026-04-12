package ru.radiationx.anilibria.screen.suggestions

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.watching.TvBottomContentInset
import ru.radiationx.anilibria.screen.watching.TvCardScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvPageHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvRowEndPadding
import ru.radiationx.anilibria.screen.watching.TvRowSpacing
import ru.radiationx.anilibria.screen.watching.TvSectionHeaderSpacing
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingFilterChip
import ru.radiationx.anilibria.screen.watching.WatchingMessageCard
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.WatchingPosterCard
import ru.radiationx.anilibria.screen.watching.defaultTvSectionTargetIndex
import ru.radiationx.anilibria.screen.watching.edgeAwareHorizontalTransformOrigin
import ru.radiationx.anilibria.screen.watching.findAdjacentTvSectionTarget
import ru.radiationx.anilibria.screen.watching.findTvSectionRestoreTarget
import ru.radiationx.anilibria.screen.watching.hasTvPosterContent
import ru.radiationx.anilibria.screen.watching.isTvStateOnlySection
import ru.radiationx.anilibria.screen.watching.launchKeepTvSectionItemVisible
import ru.radiationx.anilibria.screen.watching.launchTvSectionFocus
import ru.radiationx.anilibria.screen.watching.primaryTvStateItem
import ru.radiationx.anilibria.screen.watching.rememberTvDescriptionOverlayClearance
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.restoreTvSectionFocus
import ru.radiationx.anilibria.screen.watching.tvStateFocusIndex
import ru.radiationx.anilibria.ui.compose.DebouncedCardBackdropEffect
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvOverlayTextField
import ru.radiationx.anilibria.ui.compose.TvPageHeader
import ru.radiationx.anilibria.ui.compose.TvSectionHeader
import ru.radiationx.anilibria.ui.compose.tvAppBackground

internal data class SuggestionsSectionUiModel(
    val id: Long,
    val title: String,
    val items: List<CardItem>,
)

private const val SUGGESTIONS_QUERY_MIN_LENGTH = 3

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
    val sectionItems = remember(sections) { sections.map(SuggestionsSectionUiModel::items) }
    val sectionKeys =
        remember(sections) {
            sections.map { section ->
                section.id to section.items.map(CardItem::getId)
            }
        }
    val rowStates = remember(sectionKeys) { List(sections.size) { LazyListState() } }
    val sectionRequesters =
        remember(sectionKeys) {
            sections.map { section ->
                List(section.items.size) { FocusRequester() }
            }
        }
    var selectedItem by remember(sectionKeys) { mutableStateOf<LibriaCard?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemId by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastFocusArea by remember { mutableStateOf(SuggestionsFocusArea.Field) }
    val hasContent = remember(sectionKeys) { sections.any { section -> section.items.hasTvPosterContent() } }
    val descriptionOverlayClearance = rememberTvDescriptionOverlayClearance(hasContent = hasContent)

    fun requestTextFieldFocus(): Boolean {
        return requestWatchingFocus(searchRequester)
    }

    fun requestLastContentFocus(): Boolean {
        val restoreTarget =
            findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = lastFocusedSectionIndex,
                preferredItemIndex = lastFocusedItemIndex,
                preferredItemId = lastFocusedItemId,
                resolveTargetIndex = ::defaultTvSectionTargetIndex,
            ) ?: return false
        return launchTvSectionFocus(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = restoreTarget,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
        )
    }

    fun requestSectionFocus(
        currentSectionIndex: Int,
        direction: Int,
        preferredItemIndex: Int,
    ): Boolean {
        val target =
            findAdjacentTvSectionTarget(
                sections = sectionItems,
                currentSectionIndex = currentSectionIndex,
                direction = direction,
                preferredItemIndex = preferredItemIndex,
                resolveTargetIndex = ::defaultTvSectionTargetIndex,
            ) ?: return false
        return launchTvSectionFocus(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = target,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
        )
    }

    fun requestFirstSectionFocus(): Boolean {
        val target =
            findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = 0,
                preferredItemIndex = 0,
                resolveTargetIndex = ::defaultTvSectionTargetIndex,
            ) ?: return false
        return launchTvSectionFocus(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = target,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
        )
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
            val restoreTarget =
                findTvSectionRestoreTarget(
                    sections = sectionItems,
                    preferredSectionIndex = lastFocusedSectionIndex,
                    preferredItemIndex = lastFocusedItemIndex,
                    preferredItemId = lastFocusedItemId,
                    resolveTargetIndex = ::defaultTvSectionTargetIndex,
                )
            if (restoreTarget != null) {
                restoreTvSectionFocus(
                    verticalState = verticalState,
                    rowStates = rowStates,
                    sectionRequesters = sectionRequesters,
                    target = restoreTarget,
                    verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                )
            }
        }
    }

    DebouncedCardBackdropEffect(
        card = selectedItem,
        onCardSettled = { item -> onItemFocused(item) },
    )

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        val focused =
            when (lastFocusArea) {
                SuggestionsFocusArea.Results -> requestLastContentFocus()
                SuggestionsFocusArea.Voice -> voiceSearchAvailable && requestWatchingFocus(voiceRequester)
                SuggestionsFocusArea.Field -> false
            }
        if ((focused || requestTextFieldFocus())) {
            handledFocusToken = focusRequestToken
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .tvAppBackground(palette)
                .padding(horizontal = TvCardScreenHorizontalPadding, vertical = TvPageVerticalPadding),
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
                contentPadding =
                    PaddingValues(
                        top = 4.dp,
                        bottom =
                            if (hasContent) {
                                descriptionOverlayClearance.bottomInset
                            } else {
                                TvBottomContentInset
                            },
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
                            selectedItem = item as? LibriaCard
                            lastFocusedSectionIndex = sectionIndex
                            lastFocusedItemIndex = itemIndex
                            lastFocusedItemId = item.getId()
                            lastFocusArea = SuggestionsFocusArea.Results
                            launchKeepTvSectionItemVisible(
                                scope = scope,
                                verticalState = verticalState,
                                rowStates = rowStates,
                                sectionIndex = sectionIndex,
                                itemIndex = itemIndex,
                                verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                            )
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
            val description =
                item.toTvCardDescription { card ->
                    card.resolveDescription(context)
                }
            if (description.title.isNotBlank() || description.subtitle.isNotBlank()) {
                WatchingDescriptionBar(
                    title = description.title.toString(),
                    subtitle = description.subtitle.toString(),
                    palette = palette,
                    solidSurface = true,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .then(descriptionOverlayClearance.measureModifier),
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
    val helperText =
        if (value.length < SUGGESTIONS_QUERY_MIN_LENGTH) {
            "Введите минимум $SUGGESTIONS_QUERY_MIN_LENGTH символа, чтобы показать точные результаты."
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
            modifier =
                Modifier
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
    val stateItem = remember(items) { items.primaryTvStateItem() }
    val stateFocusIndex = remember(items) { items.tvStateFocusIndex() }
    val stateFocusItem =
        remember(items, stateFocusIndex) {
            stateFocusIndex?.let(items::getOrNull)
        }
    val stateActionItem = remember(items) { items.filterIsInstance<LinkCard>().firstOrNull() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TvSectionHeaderSpacing),
    ) {
        TvSectionHeader(
            title = title,
            palette = palette,
        )

        if (items.isTvStateOnlySection() && stateItem != null && stateFocusItem != null) {
            TvContentStatePanel(
                title =
                    when (stateItem) {
                        is LoadingCard -> stateItem.title.ifBlank { "Загрузка результатов" }
                        is LinkCard -> stateItem.title
                        is InfoCard -> stateItem.title
                        else -> title
                    },
                subtitle =
                    when (stateItem) {
                        is LoadingCard ->
                            stateItem.description.ifBlank {
                                if (stateItem.isError) {
                                    "Проверьте подключение и попробуйте ещё раз."
                                } else {
                                    "Результаты обновятся автоматически."
                                }
                            }
                        is LinkCard -> "Уточните запрос или повторите действие позже."
                        is InfoCard -> stateItem.subtitle
                        else -> ""
                    },
                palette = palette,
                accent = stateItem is LoadingCard && stateItem.isError,
                loading = stateItem is LoadingCard && !stateItem.isError,
                focusRequester =
                    if (stateActionItem == null) {
                        requesters.getOrNull(stateFocusIndex ?: -1)
                    } else {
                        null
                    },
                onFocused = {
                    onItemFocused(stateFocusIndex ?: 0, stateFocusItem)
                },
                onUp = { onUp(stateFocusIndex ?: 0) },
                onDown = { onDown(stateFocusIndex ?: 0) },
                action =
                    stateActionItem?.let { actionItem ->
                        {
                            TvContentStateActionButton(
                                text = actionItem.title,
                                palette = palette,
                                focusRequester =
                                    requesters.getOrNull(stateFocusIndex ?: -1)
                                        ?: FocusRequester.Default,
                                onClick = { onItemClick(actionItem) },
                                onFocused = {
                                    onItemFocused(stateFocusIndex ?: 0, actionItem)
                                },
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
                        is LibriaCard ->
                            WatchingPosterCard(
                                imageUrl = item.image,
                                palette = palette,
                                focusRequester = requesters[index],
                                scaleTransformOrigin =
                                    edgeAwareHorizontalTransformOrigin(
                                        index = index,
                                        lastIndex = items.lastIndex,
                                    ),
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(index, item) },
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )

                        is InfoCard ->
                            WatchingMessageCard(
                                title = item.title,
                                subtitle = item.subtitle,
                                palette = palette,
                                focusRequester = requesters[index],
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(index, item) },
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )

                        is LinkCard ->
                            WatchingMessageCard(
                                title = item.title,
                                subtitle = "Нажмите, чтобы выполнить действие",
                                palette = palette,
                                focusRequester = requesters[index],
                                onClick = { onItemClick(item) },
                                onFocused = { onItemFocused(index, item) },
                                onUp = { onUp(index) },
                                onDown = { onDown(index) },
                            )

                        is LoadingCard ->
                            WatchingMessageCard(
                                title = item.title.ifBlank { "Загрузка" },
                                subtitle =
                                    item.description.ifBlank {
                                        if (item.isError) "Нажмите, чтобы повторить попытку" else ""
                                    },
                                palette =
                                    palette.copy(
                                        accentColor =
                                            if (item.isError) {
                                                palette.accentColor
                                            } else {
                                                palette.textColor.copy(alpha = 0.4f)
                                            },
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
