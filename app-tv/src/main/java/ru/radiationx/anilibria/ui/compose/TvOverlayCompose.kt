package ru.radiationx.anilibria.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocus
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach

internal data class TvOverlayChoiceItem(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val iconRes: Int? = null,
)

internal data class TvOverlayChoiceSection(
    val title: String? = null,
    val items: List<TvOverlayChoiceItem>,
)

internal val TvOverlayOuterPadding = TvUiDefaults.OverlayPanelPadding
private val TvOverlayPanelSpacing = 18.dp
private const val TV_OVERLAY_FOCUS_RETRY_DELAY_MS = 120L
private const val TV_OVERLAY_INITIAL_FOCUS_DELAY_MS = 120L

internal enum class TvOverlayVerticalMoveAction {
    MoveFocus,
    Consume,
}

internal data class TvOverlayActionButtonState(
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val loading: Boolean = false,
)

internal data class TvOverlayActionButtonFocus(
    val requester: FocusRequester = FocusRequester.Default,
    val upRequester: FocusRequester? = null,
    val downRequester: FocusRequester? = null,
)

internal data class TvOverlayTextFieldState(
    val value: String,
    val onValueChange: (String) -> Unit,
    val enabled: Boolean = true,
    val supportingText: String? = null,
    val isError: Boolean = false,
)

internal data class TvOverlayTextFieldFocus(
    val requester: FocusRequester = FocusRequester.Default,
    val upRequester: FocusRequester = FocusRequester.Default,
    val downRequester: FocusRequester = FocusRequester.Default,
)

internal data class TvOverlayTextFieldInput(
    val singleLine: Boolean = false,
    val minLines: Int = 1,
    val maxLines: Int = 3,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val visualTransformation: VisualTransformation = VisualTransformation.None,
)

internal fun resolveTvOverlayVerticalMoveAction(hasTarget: Boolean): TvOverlayVerticalMoveAction {
    return if (hasTarget) {
        TvOverlayVerticalMoveAction.MoveFocus
    } else {
        TvOverlayVerticalMoveAction.Consume
    }
}

private fun requestOverlayVerticalFocus(targetRequester: FocusRequester?): Boolean {
    return when (resolveTvOverlayVerticalMoveAction(targetRequester != null)) {
        TvOverlayVerticalMoveAction.MoveFocus -> requestWatchingFocus(targetRequester)
        TvOverlayVerticalMoveAction.Consume -> true
    }
}

@Composable
internal fun TvOverlayPanelSurface(
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = TvUiDefaults.OverlayPanelPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = TvUiDefaults.OverlayPanelShape,
        color = palette.surfaceColor.copy(alpha = 0.98f),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .border(
                        width = 1.dp,
                        color = palette.textColor.copy(alpha = 0.08f),
                        shape = TvUiDefaults.OverlayPanelShape,
                    )
                    .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(TvOverlayPanelSpacing),
            content = content,
        )
    }
}

@Composable
internal fun TvOverlayScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    panelMaxWidth: Dp = 760.dp,
    content: @Composable ColumnScope.(WatchingPalette) -> Unit,
) {
    val palette = rememberWatchingPalette()

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(TvUiDefaults.surfaceBackdropBrush(palette))
                .padding(TvOverlayOuterPadding),
    ) {
        TvOverlayPanelSurface(
            palette = palette,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = minOf(maxWidth - 24.dp, panelMaxWidth))
                    .heightIn(max = maxHeight - 24.dp)
                    .fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    color = palette.textColor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                subtitle?.takeIf { it.isNotBlank() }?.also {
                    Text(
                        text = it,
                        color = palette.secondaryTextColor,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    )
                }
            }
            content(palette)
        }
    }
}

@Composable
internal fun TvOverlayActionButton(
    text: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    focus: TvOverlayActionButtonFocus = TvOverlayActionButtonFocus(),
    state: TvOverlayActionButtonState = TvOverlayActionButtonState(),
    onClick: () -> Unit,
) {
    val interactiveEnabled = state.enabled && !state.loading
    val colors =
        if (state.destructive) {
            TvUiDefaults.accentActionColors(
                palette = palette,
                focusedBackgroundAlpha = 0.24f,
                borderAlpha = 0.9f,
            )
        } else {
            TvUiDefaults.chipActionColors(
                palette = palette,
                backgroundAlpha = 1f,
                borderAlpha = 0.75f,
            )
        }

    WatchingFocusableSurface(
        focusRequester = focus.requester,
        enabled = interactiveEnabled,
        backgroundColor = colors.backgroundColor,
        focusedBackgroundColor = colors.focusedBackgroundColor,
        borderColor = colors.borderColor,
        onClick = onClick,
        onUp = {
            requestOverlayVerticalFocus(focus.upRequester)
        },
        onDown = {
            requestOverlayVerticalFocus(focus.downRequester)
        },
        modifier = modifier,
        paddingValues = TvUiDefaults.ActionButtonPadding,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = palette.textColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                color = if (state.enabled) palette.textColor else palette.secondaryTextColor,
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
internal fun TvOverlayTextField(
    label: String,
    state: TvOverlayTextFieldState,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    focus: TvOverlayTextFieldFocus = TvOverlayTextFieldFocus(),
    input: TvOverlayTextFieldInput = TvOverlayTextFieldInput(),
) {
    var isFocused by remember { mutableStateOf(false) }
    val fieldShape = TvUiDefaults.OverlayPanelShape
    OutlinedTextField(
        value = state.value,
        onValueChange = state.onValueChange,
        enabled = state.enabled,
        label = { Text(text = label, fontSize = 15.sp) },
        supportingText =
            state.supportingText?.let {
                {
                    Text(
                        text = it,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            },
        singleLine = input.singleLine,
        minLines = input.minLines,
        maxLines = input.maxLines,
        isError = state.isError,
        keyboardOptions = input.keyboardOptions,
        visualTransformation = input.visualTransformation,
        shape = fieldShape,
        textStyle =
            TextStyle(
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = palette.surfaceColor.copy(alpha = 0.48f),
                unfocusedContainerColor = palette.surfaceColor.copy(alpha = 0.30f),
                disabledContainerColor = palette.surfaceColor.copy(alpha = 0.16f),
                focusedTextColor = palette.textColor,
                unfocusedTextColor = palette.textColor,
                disabledTextColor = palette.secondaryTextColor,
                focusedLabelColor = palette.secondaryTextColor,
                unfocusedLabelColor = palette.secondaryTextColor,
                focusedSupportingTextColor = palette.secondaryTextColor,
                unfocusedSupportingTextColor = palette.secondaryTextColor,
                errorSupportingTextColor = palette.accentColor,
                errorLabelColor = palette.accentColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = palette.textColor,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color =
                        when {
                            state.isError -> palette.accentColor.copy(alpha = 0.92f)
                            isFocused -> palette.textColor.copy(alpha = 0.78f)
                            else -> palette.textColor.copy(alpha = 0.16f)
                        },
                    shape = fieldShape,
                )
                .focusRequester(focus.requester)
                .focusProperties {
                    up = focus.upRequester
                    down = focus.downRequester
                }
                .onFocusChanged { isFocused = it.isFocused },
    )
}

@Composable
internal fun TvOverlayInfoBlock(
    text: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color =
                        if (accent) {
                            palette.accentColor.copy(alpha = 0.12f)
                        } else {
                            palette.surfaceColor.copy(alpha = 0.52f)
                        },
                    shape = TvUiDefaults.InfoSurfaceShape,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            color = if (accent) palette.textColor else palette.secondaryTextColor,
            fontSize = 16.sp,
            lineHeight = 23.sp,
        )
    }
}

@Composable
internal fun TvOverlayScrollableText(
    text: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester.Default,
    upRequester: FocusRequester = FocusRequester.Default,
    downRequester: FocusRequester = FocusRequester.Default,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableStateOf(0) }
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(palette.surfaceColor.copy(alpha = 0.42f), TvUiDefaults.InfoSurfaceShape)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color =
                        if (isFocused) {
                            palette.textColor.copy(alpha = 0.76f)
                        } else {
                            palette.textColor.copy(alpha = 0.08f)
                        },
                    shape = TvUiDefaults.InfoSurfaceShape,
                )
                .focusRequester(focusRequester)
                .focusProperties {
                    up = upRequester
                    down = downRequester
                }
                .onFocusChanged { isFocused = it.isFocused }
                .onSizeChanged { viewportHeightPx = it.height }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || viewportHeightPx <= 0) {
                        return@onPreviewKeyEvent false
                    }
                    val target =
                        when (event.key) {
                            Key.DirectionDown -> (scrollState.value + viewportHeightPx).coerceAtMost(scrollState.maxValue)
                            Key.DirectionUp -> (scrollState.value - viewportHeightPx).coerceAtLeast(0)
                            else -> return@onPreviewKeyEvent false
                        }
                    if (target == scrollState.value) {
                        false
                    } else {
                        scope.launch {
                            scrollState.animateScrollTo(target)
                        }
                        true
                    }
                }
                .focusable()
                .padding(18.dp),
    ) {
        Text(
            text = text,
            color = palette.secondaryTextColor,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
        )
    }
}

@Composable
internal fun TvOverlayChoiceList(
    sections: List<TvOverlayChoiceSection>,
    onItemClick: (TvOverlayChoiceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberWatchingPalette()
    val entries =
        remember(sections) {
            buildList {
                var nextChoiceIndex = 0
                sections.forEach { section ->
                    section.title
                        ?.takeIf { it.isNotBlank() }
                        ?.let(::Header)
                        ?.also(::add)
                    section.items.forEach { choice ->
                        add(Choice(choice = choice, choiceIndex = nextChoiceIndex))
                        nextChoiceIndex += 1
                    }
                }
            }
        }
    val choices = remember(entries) { entries.filterIsInstance<Choice>() }
    val choiceIds = remember(choices) { choices.map { it.choice.id } }
    val focusRequesters = remember(choiceIds) { List(choiceIds.size) { FocusRequester() } }
    val listState = rememberLazyListState()
    var focusedChoiceIndex by remember(choiceIds) { mutableIntStateOf(-1) }
    val selectedChoiceIndex =
        remember(choices) {
            choices.indexOfFirst { it.choice.selected }.takeIf { it >= 0 } ?: 0
        }
    val selectedChoiceId = choices.getOrNull(selectedChoiceIndex)?.choice?.id
    val selectedEntryIndex =
        remember(entries, selectedChoiceId) {
            entries.indexOfFirst { entry ->
                entry is Choice && entry.choice.id == selectedChoiceId
            }.takeIf { it >= 0 } ?: 0
        }

    LaunchedEffect(choiceIds, selectedEntryIndex) {
        if (choices.isEmpty()) {
            return@LaunchedEffect
        }
        listState.scrollToItem((selectedEntryIndex - 1).coerceAtLeast(0))
    }

    LaunchedEffect(choiceIds, selectedChoiceId, focusedChoiceIndex) {
        if (choices.isEmpty() || focusedChoiceIndex >= 0) {
            return@LaunchedEffect
        }
        val targetRequester = focusRequesters.getOrNull(selectedChoiceIndex) ?: return@LaunchedEffect
        delay(TV_OVERLAY_INITIAL_FOCUS_DELAY_MS)
        if (requestWatchingFocusAfterAttach(targetRequester)) {
            return@LaunchedEffect
        }
        delay(TV_OVERLAY_FOCUS_RETRY_DELAY_MS)
        if (focusedChoiceIndex < 0) {
            requestWatchingFocusAfterAttach(targetRequester, attempts = 12)
        }
    }

    if (choices.isEmpty()) {
        TvOverlayInfoBlock(
            text = "Список пока недоступен.",
            palette = palette,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = entries,
            key = { _, entry ->
                when (entry) {
                    is Choice -> "choice-${entry.choice.id}"
                    is Header -> "header-${entry.title}"
                }
            },
        ) { _, entry ->
            when (entry) {
                is Header -> {
                    Text(
                        text = entry.title,
                        color = palette.secondaryTextColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }

                is Choice -> {
                    TvOverlayChoiceButton(
                        choice = entry.choice,
                        palette = palette,
                        focusRequester = focusRequesters[entry.choiceIndex],
                        upRequester = focusRequesters.getOrNull(entry.choiceIndex - 1),
                        downRequester = focusRequesters.getOrNull(entry.choiceIndex + 1),
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusedChoiceIndex = entry.choiceIndex
                            } else if (focusedChoiceIndex == entry.choiceIndex) {
                                focusedChoiceIndex = -1
                            }
                        },
                        onClick = { onItemClick(entry.choice) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvOverlayChoiceButton(
    choice: TvOverlayChoiceItem,
    palette: WatchingPalette,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val backgroundColor = palette.surfaceColor.copy(alpha = 0.88f)
    val focusedBackgroundColor = palette.surfaceColor.copy(alpha = 0.98f)

    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = choice.enabled,
        backgroundColor = backgroundColor,
        focusedBackgroundColor = focusedBackgroundColor,
        borderColor = palette.textColor.copy(alpha = 0.78f),
        unfocusedBorderColor = palette.textColor.copy(alpha = 0.08f),
        selected = choice.selected,
        selectedBorderColor = palette.accentColor.copy(alpha = 0.68f),
        selectedBorderWidth = 1.dp,
        shape = TvUiDefaults.OverlayPanelShape,
        onClick = onClick,
        onFocusChanged = onFocusChanged,
        onUp = {
            requestOverlayVerticalFocus(upRequester)
        },
        onDown = {
            requestOverlayVerticalFocus(downRequester)
        },
        modifier = Modifier.fillMaxWidth(),
        paddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            choice.iconRes?.let { iconRes ->
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = palette.textColor,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = choice.title,
                    color = palette.textColor,
                    fontSize = 17.sp,
                    fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                choice.subtitle
                    ?.takeIf { it.isNotBlank() }
                    ?.let { subtitle ->
                        Text(
                            text = subtitle,
                            color = palette.secondaryTextColor,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                        )
                    }
            }
            TvSelectionIndicator(
                selected = choice.selected,
                palette = palette,
                size = TvUiDefaults.LARGE_SELECTION_INDICATOR_SIZE,
                inactiveBorderColor = palette.textColor.copy(alpha = 0.24f),
            )
        }
    }
}

private sealed interface TvOverlayListEntry

private data class Header(val title: String) : TvOverlayListEntry

private data class Choice(
    val choice: TvOverlayChoiceItem,
    val choiceIndex: Int,
) : TvOverlayListEntry
