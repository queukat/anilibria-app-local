package ru.radiationx.anilibria.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.watching.WatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.screen.watching.scrollItemIntoViewIfNeeded

@Composable
internal fun PlayerPickerMenu(
    title: String,
    options: List<PlayerPickerOption>,
    palette: WatchingPalette,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) {
        return
    }

    val optionIds = remember(options) { options.map(PlayerPickerOption::id) }
    val optionRequesters = remember(optionIds) { List(optionIds.size) { FocusRequester() } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedIndex =
        remember(options) {
            options.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0
        }

    fun requestOptionFocus(targetIndex: Int): Boolean {
        if (options.isEmpty()) {
            return false
        }
        val clampedIndex = targetIndex.coerceIn(0, options.lastIndex)
        scope.launch {
            listState.scrollItemIntoViewIfNeeded(clampedIndex)
            requestWatchingFocusAfterAttach(optionRequesters.getOrNull(clampedIndex))
        }
        return true
    }

    LaunchedEffect(optionIds, selectedIndex) {
        listState.scrollItemIntoViewIfNeeded(selectedIndex)
        requestWatchingFocusAfterAttach(optionRequesters.getOrNull(selectedIndex))
    }

    Column(
        modifier =
            modifier
                .playerPanelSurface(PlayerOverlayUiDefaults.pickerPanelStyle())
                .padding(PlayerOverlayUiDefaults.PickerPanelPadding),
        verticalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.PickerSectionSpacing),
    ) {
        Text(
            text = title,
            color = palette.textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = PlayerOverlayUiDefaults.PickerHeaderBottomSpacing),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(PlayerOverlayUiDefaults.PickerOptionSpacing),
        ) {
            itemsIndexed(
                items = options,
                key = { _, option -> option.id },
            ) { index, option ->
                PlayerPickerOptionButton(
                    option = option,
                    focusRequester = optionRequesters[index],
                    palette = palette,
                    onInteraction = onInteraction,
                    onUp = {
                        onInteraction()
                        if (index <= 0) {
                            true
                        } else {
                            requestOptionFocus(index - 1)
                        }
                    },
                    onDown = {
                        onInteraction()
                        if (index >= optionRequesters.lastIndex) {
                            true
                        } else {
                            requestOptionFocus(index + 1)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayerPickerOptionButton(
    option: PlayerPickerOption,
    focusRequester: FocusRequester,
    palette: WatchingPalette,
    onInteraction: () -> Unit,
    onUp: () -> Boolean,
    onDown: () -> Boolean,
) {
    val optionColors =
        PlayerOverlayUiDefaults.pickerOptionColors(
            palette = palette,
            selected = option.selected,
        )

    PlayerControlSurface(
        focusRequester = focusRequester,
        backgroundColor = optionColors.backgroundColor,
        focusedBackgroundColor = optionColors.focusedBackgroundColor,
        borderColor = optionColors.borderColor,
        selected = option.selected,
        selectedBorderColor = optionColors.borderColor,
        onClick = {
            onInteraction()
            option.onSelected()
        },
        onFocused = onInteraction,
        onLeft = { true },
        onUp = onUp,
        onRight = { true },
        onDown = onDown,
        modifier = Modifier.fillMaxWidth(),
        paddingValues = PlayerOverlayUiDefaults.PickerOptionPadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (option.selected) {
                                palette.accentColor
                            } else {
                                Color.Transparent
                            },
                        )
                        .border(
                            width = 1.dp,
                            color =
                                if (option.selected) {
                                    palette.accentColor
                                } else {
                                    Color.White.copy(alpha = 0.28f)
                                },
                            shape = CircleShape,
                        ),
            )
            Text(
                text = option.label,
                color = palette.textColor,
                fontSize = 16.sp,
                fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 22.sp,
            )
        }
    }
}
