package ru.radiationx.anilibria.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
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
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.84f),
                        palette.surfaceColor.copy(alpha = 0.96f),
                    )
                )
            )
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.surfaceColor.copy(alpha = 0.98f),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = minOf(maxWidth - 24.dp, panelMaxWidth))
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
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
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
                content(palette)
            }
        }
    }
}

@Composable
internal fun TvOverlayActionButton(
    text: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester.Default,
    upRequester: FocusRequester = FocusRequester.Default,
    downRequester: FocusRequester = FocusRequester.Default,
    enabled: Boolean = true,
    destructive: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = if (destructive) {
        palette.accentColor.copy(alpha = 0.18f)
    } else {
        androidx.compose.ui.res.colorResource(R.color.dark_release_day_btn)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) palette.textColor.copy(alpha = 0.75f) else Color.Transparent,
                shape = RoundedCornerShape(24.dp),
            )
            .focusRequester(focusRequester)
            .focusProperties {
                up = upRequester
                down = downRequester
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                enabled = enabled && !loading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .focusable(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = palette.textColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                color = if (enabled) palette.textColor else palette.secondaryTextColor,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
internal fun TvOverlayTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester.Default,
    upRequester: FocusRequester = FocusRequester.Default,
    downRequester: FocusRequester = FocusRequester.Default,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = false,
        minLines = 1,
        maxLines = 3,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.surfaceColor.copy(alpha = 0.40f),
            unfocusedContainerColor = palette.surfaceColor.copy(alpha = 0.24f),
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
            focusedIndicatorColor = if (isFocused) palette.textColor.copy(alpha = 0.7f) else palette.secondaryTextColor,
            unfocusedIndicatorColor = palette.textColor.copy(alpha = 0.2f),
            errorIndicatorColor = palette.accentColor,
            cursorColor = palette.textColor,
        ),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties {
                up = upRequester
                down = downRequester
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
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (accent) {
                    palette.accentColor.copy(alpha = 0.12f)
                } else {
                    palette.surfaceColor.copy(alpha = 0.52f)
                },
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            color = if (accent) palette.textColor else palette.secondaryTextColor,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
internal fun TvOverlayScrollableText(
    text: String,
    palette: WatchingPalette,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surfaceColor.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Text(
            text = text,
            color = palette.secondaryTextColor,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier
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
    val entries = remember(sections) {
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
    val focusRequesters = remember(choices) { List(choices.size) { FocusRequester() } }

    LaunchedEffect(choices) {
        val selectedIndex = choices.indexOfFirst { it.choice.selected }.takeIf { it >= 0 } ?: 0
        requestWatchingFocusAfterAttach(focusRequesters.getOrNull(selectedIndex))
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }

                is Choice -> {
                    TvOverlayChoiceButton(
                        choice = entry.choice,
                        palette = palette,
                        focusRequester = focusRequesters[entry.choiceIndex],
                        upRequester = focusRequesters.getOrNull(entry.choiceIndex - 1)
                            ?: FocusRequester.Default,
                        downRequester = focusRequesters.getOrNull(entry.choiceIndex + 1)
                            ?: FocusRequester.Default,
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
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val backgroundColor = if (choice.selected) {
        palette.accentColor.copy(alpha = 0.16f)
    } else {
        palette.surfaceColor.copy(alpha = 0.64f)
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> palette.textColor.copy(alpha = 0.78f)
                    choice.selected -> palette.accentColor.copy(alpha = 0.68f)
                    else -> palette.textColor.copy(alpha = 0.08f)
                },
                shape = RoundedCornerShape(24.dp),
            )
            .focusRequester(focusRequester)
            .focusProperties {
                up = upRequester
                down = downRequester
            }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                enabled = choice.enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .focusable(enabled = choice.enabled),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
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
                    fontSize = 16.sp,
                    fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                choice.subtitle
                    ?.takeIf { it.isNotBlank() }
                    ?.let { subtitle ->
                        Text(
                            text = subtitle,
                            color = palette.secondaryTextColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        color = if (choice.selected) palette.accentColor else Color.Transparent,
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .border(
                        width = 1.dp,
                        color = if (choice.selected) {
                            palette.accentColor
                        } else {
                            palette.textColor.copy(alpha = 0.24f)
                        },
                        shape = RoundedCornerShape(percent = 50),
                    ),
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
