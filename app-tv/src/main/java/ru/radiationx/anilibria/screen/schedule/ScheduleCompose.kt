package ru.radiationx.anilibria.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.common.toTvCardDescription
import ru.radiationx.anilibria.screen.main.MainSectionBlock
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.screen.watching.TvBottomContentInset
import ru.radiationx.anilibria.screen.watching.TvCardScreenHorizontalPadding
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvSectionSpacing
import ru.radiationx.anilibria.screen.watching.WatchingDescriptionBar
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.clampedTvSectionTargetIndex
import ru.radiationx.anilibria.screen.watching.findAdjacentTvSectionTarget
import ru.radiationx.anilibria.screen.watching.findTvSectionRestoreTarget
import ru.radiationx.anilibria.screen.watching.launchKeepTvSectionItemVisible
import ru.radiationx.anilibria.screen.watching.launchTvSectionFocus
import ru.radiationx.anilibria.screen.watching.rememberTvDescriptionOverlayClearance
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.resolveTvSectionTargetInSection
import ru.radiationx.anilibria.screen.watching.restoreTvSectionFocus
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded
import ru.radiationx.anilibria.ui.compose.DebouncedCardBackdropEffect
import ru.radiationx.anilibria.ui.compose.TvContentStateActionButton
import ru.radiationx.anilibria.ui.compose.TvContentStatePanel
import ru.radiationx.anilibria.ui.compose.TvPageHeader
import ru.radiationx.anilibria.ui.compose.TvUiDefaults
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import ru.radiationx.shared.ktx.asDayName
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

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
    val todaySectionTitle = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK).asDayName() }
    val timezoneLabel = remember { buildScheduleTimezoneLabel(TimeZone.getDefault()) }
    val sectionItems = remember(sections) { sections.map(MainSectionUiModel::items) }
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
    val dayChipRequesters = remember(sectionKeys) { List(sections.size) { FocusRequester() } }
    val statePanelRequester = remember { FocusRequester() }
    val stateActionRequester = remember { FocusRequester() }
    var selectedItem by remember(sectionKeys) { mutableStateOf<CardItem?>(null) }
    var handledFocusToken by remember { mutableIntStateOf(0) }
    var lastFocusedSectionIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemIndex by remember { mutableIntStateOf(0) }
    var lastFocusedItemId by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val nonContentItems =
        remember(sectionKeys) {
            sections.flatMap { section -> section.items }.filter { it !is LibriaCard }
        }
    val stateCard =
        remember(nonContentItems) {
            nonContentItems.firstOrNull { it is LoadingCard && it.isError }
                ?: nonContentItems.firstOrNull { it is LoadingCard }
                ?: nonContentItems.firstOrNull { it is InfoCard }
                ?: nonContentItems.firstOrNull()
        }
    val stateActionCard =
        remember(nonContentItems) {
            nonContentItems.filterIsInstance<LinkCard>().firstOrNull()
        }
    val showStatePanel =
        sections.isEmpty() || (
            sections.isNotEmpty() &&
                sections.all { section ->
                    section.items.none { it is LibriaCard }
                }
        )
    val showQuickDayJump = sections.size > 1 && !showStatePanel
    val initialSectionIndex =
        remember(sectionKeys, todaySectionTitle) {
            sections.indexOfFirst { section -> section.title == todaySectionTitle }
                .takeIf { it >= 0 }
                ?: 0
        }
    var highlightedSectionIndex by remember(sectionKeys, initialSectionIndex) {
        mutableIntStateOf(initialSectionIndex)
    }
    var pendingDayChipJump by remember(sectionKeys) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val hasContent =
        remember(sectionKeys, showStatePanel) {
            sections.any { section -> section.items.any { it is LibriaCard } } && !showStatePanel
        }
    val descriptionOverlayClearance = rememberTvDescriptionOverlayClearance(hasContent = hasContent)

    fun requestStatePanelFocus(): Boolean {
        if (!showStatePanel) {
            return false
        }
        scope.launch {
            val targetRequester =
                if (stateActionCard != null) {
                    stateActionRequester
                } else {
                    statePanelRequester
                }
            requestWatchingFocusAfterAttach(targetRequester)
        }
        return true
    }

    fun sectionListIndex(sectionIndex: Int): Int = sectionIndex + if (showQuickDayJump) 2 else 1

    fun requestSectionItemFocus(
        sectionIndex: Int,
        preferredItemIndex: Int = 0,
    ): Boolean {
        val target =
            resolveTvSectionTargetInSection(
                sections = sectionItems,
                sectionIndex = sectionIndex,
                preferredItemIndex = preferredItemIndex,
                resolveTargetIndex = ::clampedTvSectionTargetIndex,
            ) ?: return false
        highlightedSectionIndex = sectionIndex
        return launchTvSectionFocus(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = target,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
            sectionListIndex = ::sectionListIndex,
        )
    }

    fun requestDayChipFocus(sectionIndex: Int = highlightedSectionIndex): Boolean {
        if (!showQuickDayJump || sections.isEmpty()) {
            return false
        }
        val clampedSectionIndex = sectionIndex.coerceIn(0, sections.lastIndex)
        highlightedSectionIndex = clampedSectionIndex
        scope.launch {
            verticalState.scrollItemIntoViewIfNeeded(1)
            requestWatchingFocusAfterAttach(dayChipRequesters.getOrNull(clampedSectionIndex))
        }
        return true
    }

    fun previewSection(sectionIndex: Int) {
        highlightedSectionIndex = sectionIndex.coerceIn(0, sections.lastIndex)
        launchKeepTvSectionItemVisible(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionIndex = highlightedSectionIndex,
            itemIndex = 0,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
            sectionListIndex = ::sectionListIndex,
        )
    }

    fun requestSectionFocus(
        currentSectionIndex: Int,
        direction: Int,
        preferredItemIndex: Int,
    ): Boolean {
        val chipFocusHandled = direction < 0 && currentSectionIndex <= 0 && showQuickDayJump
        if (chipFocusHandled) return requestDayChipFocus(currentSectionIndex)

        val target =
            findAdjacentTvSectionTarget(
                sections = sectionItems,
                currentSectionIndex = currentSectionIndex,
                direction = direction,
                preferredItemIndex = preferredItemIndex,
                resolveTargetIndex = ::clampedTvSectionTargetIndex,
            ) ?: return false
        highlightedSectionIndex = target.sectionIndex
        return launchTvSectionFocus(
            scope = scope,
            verticalState = verticalState,
            rowStates = rowStates,
            sectionRequesters = sectionRequesters,
            target = target,
            verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
            sectionListIndex = ::sectionListIndex,
        )
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
            val restoreTarget =
                findTvSectionRestoreTarget(
                    sections = sectionItems,
                    preferredSectionIndex = lastFocusedSectionIndex,
                    preferredItemIndex = lastFocusedItemIndex,
                    preferredItemId = lastFocusedItemId,
                    resolveTargetIndex = ::clampedTvSectionTargetIndex,
                )
            if (restoreTarget != null) {
                highlightedSectionIndex = restoreTarget.sectionIndex
                restoreTvSectionFocus(
                    verticalState = verticalState,
                    rowStates = rowStates,
                    sectionRequesters = sectionRequesters,
                    target = restoreTarget,
                    verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                    sectionListIndex = ::sectionListIndex,
                )
            }
        }
    }

    DebouncedCardBackdropEffect(
        card = selectedItem,
        onCardSettled = { item -> onItemFocused(item) },
    )

    LaunchedEffect(pendingDayChipJump, sectionKeys) {
        val jump = pendingDayChipJump ?: return@LaunchedEffect
        delay(SCHEDULE_DAY_CHIP_FOCUS_DELAY_MS)
        requestSectionItemFocus(
            sectionIndex = jump.first,
            preferredItemIndex = jump.second,
        )
        pendingDayChipJump = null
    }

    LaunchedEffect(focusRequestToken, sectionKeys) {
        if (focusRequestToken <= handledFocusToken) {
            return@LaunchedEffect
        }
        if (showStatePanel) {
            if (requestStatePanelFocus()) {
                handledFocusToken = focusRequestToken
            }
            return@LaunchedEffect
        }
        val restoreTarget =
            if (lastFocusedItemId != Int.MIN_VALUE) {
                findTvSectionRestoreTarget(
                    sections = sectionItems,
                    preferredSectionIndex = lastFocusedSectionIndex,
                    preferredItemIndex = lastFocusedItemIndex,
                    preferredItemId = lastFocusedItemId,
                    resolveTargetIndex = ::clampedTvSectionTargetIndex,
                )
            } else {
                null
            } ?: findTvSectionRestoreTarget(
                sections = sectionItems,
                preferredSectionIndex = initialSectionIndex,
                preferredItemIndex = 0,
                resolveTargetIndex = ::clampedTvSectionTargetIndex,
            )
                ?: return@LaunchedEffect
        highlightedSectionIndex = restoreTarget.sectionIndex
        if (restoreTvSectionFocus(
                verticalState = verticalState,
                rowStates = rowStates,
                sectionRequesters = sectionRequesters,
                target = restoreTarget,
                verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                sectionListIndex = ::sectionListIndex,
            )
        ) {
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
            item(key = "schedule-header") {
                TvPageHeader(
                    title = "Расписание",
                    subtitle = "Свежие и ближайшие релизы по времени устройства: $timezoneLabel.",
                    palette = palette,
                )
            }

            if (showQuickDayJump) {
                item(key = "schedule-day-jump") {
                    ScheduleDayJumpRow(
                        sections = sections,
                        highlightedSectionIndex = highlightedSectionIndex,
                        todaySectionTitle = todaySectionTitle,
                        palette = palette,
                        requesters = dayChipRequesters,
                        onChipFocused = { sectionIndex ->
                            highlightedSectionIndex = sectionIndex
                        },
                        onChipClick = { sectionIndex ->
                            val preferredItemIndex =
                                if (sectionIndex == lastFocusedSectionIndex) {
                                    lastFocusedItemIndex
                                } else {
                                    0
                                }
                            previewSection(sectionIndex)
                            pendingDayChipJump = sectionIndex to preferredItemIndex
                        },
                        onChipDown = { sectionIndex ->
                            val preferredItemIndex =
                                if (sectionIndex == lastFocusedSectionIndex) {
                                    lastFocusedItemIndex
                                } else {
                                    0
                                }
                            requestSectionItemFocus(sectionIndex, preferredItemIndex)
                        },
                    )
                }
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
                            stateSubtitle =
                                item.description.ifBlank {
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
                            stateTitle =
                                if (loadingVisible) {
                                    "Загружаем расписание"
                                } else {
                                    "На ближайшие дни пока пусто"
                                }
                            stateSubtitle =
                                if (loadingVisible) {
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
                        focusRequester = if (stateActionCard == null) statePanelRequester else null,
                        onUp = { false },
                        onDown = { true },
                        action =
                            stateActionCard?.let { actionCard ->
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
                            highlightedSectionIndex = sectionIndex
                            launchKeepTvSectionItemVisible(
                                scope = scope,
                                verticalState = verticalState,
                                rowStates = rowStates,
                                sectionIndex = sectionIndex,
                                itemIndex = itemIndex,
                                verticalBottomClearancePx = descriptionOverlayClearance.bottomClearancePx,
                                sectionListIndex = ::sectionListIndex,
                            )
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
}

@Composable
private fun ScheduleDayJumpRow(
    sections: List<MainSectionUiModel>,
    highlightedSectionIndex: Int,
    todaySectionTitle: String,
    palette: WatchingPalette,
    requesters: List<FocusRequester>,
    onChipFocused: (Int) -> Unit,
    onChipClick: (Int) -> Unit,
    onChipDown: (Int) -> Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text =
                sections.firstOrNull { it.title == todaySectionTitle }
                    ?.let { "Быстрый переход по дням. Сегодня: ${it.title}" }
                    ?: "Быстрый переход по дням",
            color = palette.secondaryTextColor,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            sections.forEachIndexed { index, section ->
                ScheduleDayChip(
                    title = section.title,
                    selected = index == highlightedSectionIndex,
                    today = section.title == todaySectionTitle,
                    palette = palette,
                    focusRequester = requesters.getOrNull(index) ?: FocusRequester.Default,
                    onFocused = { onChipFocused(index) },
                    onClick = { onChipClick(index) },
                    onLeft = { requestWatchingFocus(requesters.getOrNull(index - 1)) },
                    onRight = { requestWatchingFocus(requesters.getOrNull(index + 1)) },
                    onDown = { onChipDown(index) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleDayChip(
    title: String,
    selected: Boolean,
    today: Boolean,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
    onDown: () -> Boolean,
) {
    val colors =
        when {
            selected ->
                TvUiDefaults.accentActionColors(
                    palette = palette,
                    backgroundAlpha = 0.16f,
                    focusedBackgroundAlpha = 0.24f,
                    borderAlpha = 0.92f,
                )

            else ->
                TvUiDefaults.chipActionColors(
                    palette = palette,
                    backgroundAlpha = 0.82f,
                    borderAlpha = if (today) 0.78f else 0.22f,
                )
        }
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        backgroundColor = colors.backgroundColor,
        focusedBackgroundColor = colors.focusedBackgroundColor,
        borderColor = colors.borderColor,
        onClick = onClick,
        onFocused = onFocused,
        onLeft = onLeft,
        onRight = onRight,
        onDown = onDown,
        paddingValues = TvUiDefaults.CompactActionButtonPadding,
    ) {
        Text(
            text = title,
            color = palette.textColor,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

private fun buildScheduleTimezoneLabel(timeZone: TimeZone): String {
    val offsetMinutes = timeZone.getOffset(System.currentTimeMillis()) / MILLIS_IN_MINUTE
    val sign = if (offsetMinutes >= 0) "+" else "-"
    val absoluteMinutes = abs(offsetMinutes)
    val hours = absoluteMinutes / MINUTES_IN_HOUR
    val minutes = absoluteMinutes % MINUTES_IN_HOUR
    val offsetText =
        if (minutes == 0) {
            "GMT$sign$hours"
        } else {
            "GMT$sign$hours:${minutes.toString().padStart(2, '0')}"
        }
    return "$offsetText (${timeZone.id})"
}

private const val MILLIS_IN_MINUTE = 60_000
private const val MINUTES_IN_HOUR = 60
private const val SCHEDULE_DAY_CHIP_FOCUS_DELAY_MS = 120L
