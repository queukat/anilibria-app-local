package ru.radiationx.anilibria.screen.details

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.LibriaDetails
import kotlin.math.roundToInt

@Stable
internal data class ScrollableTextMeasure(
    val viewportHeight: Dp,
    val viewportHeightPx: Int,
    val scrollStepPx: Int,
    val isScrollable: Boolean,
)

@Composable
internal fun MetadataRow(
    details: LibriaDetails,
    textColor: Color,
    secondaryTextColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = details.extra,
            color = textColor,
            fontSize = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (details.hasFullHd) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                painter = painterResource(R.drawable.ic_quality_full_hd_base),
                contentDescription = null,
                tint = textColor,
            )
        }
        if (details.favoriteCount != "0") {
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = details.favoriteCount,
                    color = secondaryTextColor,
                    fontSize = 17.sp,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter =
                        painterResource(
                            if (details.isFavorite) {
                                R.drawable.ic_details_favorite_filled
                            } else {
                                R.drawable.ic_details_favorite
                            },
                        ),
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun AnnounceChip(
    text: String,
    textColor: Color,
    backgroundColor: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor.copy(alpha = 0.18f))
                .border(
                    width = 1.dp,
                    color = backgroundColor.copy(alpha = 0.34f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun ScrollableTitle(
    text: String,
    style: TextStyle,
    measure: ScrollableTextMeasure,
    textColor: Color,
    focusRequester: FocusRequester,
    enabled: Boolean,
    canFocus: Boolean,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
) {
    key(text) {
        val scrollState = rememberScrollState()
        val viewportHeight = measure.viewportHeight
        val modifier =
            if (enabled && canFocus) {
                Modifier
                    .fillMaxWidth()
                    .height(viewportHeight)
                    .tvScrollableFocus(
                        scrollState = scrollState,
                        viewportHeightPx = measure.viewportHeightPx,
                        scrollStepPx = measure.scrollStepPx,
                        focusRequester = focusRequester,
                        upRequester = upRequester,
                        downRequester = downRequester,
                    )
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(viewportHeight)
            }

        Box(modifier = modifier.clipToBounds()) {
            Text(
                text = text,
                style = style,
                color = textColor,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = if (measure.isScrollable) 10.dp else 0.dp)
                        .verticalScroll(
                            state = scrollState,
                            enabled = enabled && measure.isScrollable,
                        ),
            )
            VerticalScrollIndicator(
                scrollState = scrollState,
                viewportHeightPx = measure.viewportHeightPx,
                color = textColor,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp),
            )
        }
    }
}

@Composable
internal fun DescriptionCard(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    focusRequester: FocusRequester,
    enabled: Boolean,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    key(text) {
        val scrollState = rememberScrollState()
        var viewportHeightPx by remember { mutableIntStateOf(0) }
        var isFocused by remember { mutableStateOf(false) }
        val interactiveModifier =
            if (enabled) {
                Modifier
                    .focusRequester(focusRequester)
                    .focusProperties {
                        up = upRequester
                        down = downRequester
                    }
                    .onFocusChanged { isFocused = it.isFocused }
                    .onSizeChanged { viewportHeightPx = it.height }
                    .tvScrollKeys(
                        scrollState = scrollState,
                        viewportHeightPx = viewportHeightPx,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                    .focusable()
            } else {
                Modifier
            }

        Box(
            modifier =
                modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(backgroundColor)
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color =
                            if (isFocused) {
                                textColor.copy(alpha = 0.75f)
                            } else {
                                textColor.copy(alpha = 0.08f)
                            },
                        shape = RoundedCornerShape(18.dp),
                    )
                    .onSizeChanged { viewportHeightPx = it.height }
                    .then(interactiveModifier)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(
                text = text,
                color = textColor.copy(alpha = 0.9f),
                fontSize = 17.sp,
                lineHeight = 26.sp,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(end = if (scrollState.maxValue > 0) 10.dp else 0.dp)
                        .verticalScroll(scrollState, enabled = enabled),
            )
            VerticalScrollIndicator(
                scrollState = scrollState,
                viewportHeightPx = viewportHeightPx,
                color = textColor,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 12.dp, horizontal = 2.dp),
                minThumbHeight = 18.dp,
            )
        }
    }
}

@Composable
internal fun VerticalScrollIndicator(
    scrollState: ScrollState,
    viewportHeightPx: Int,
    color: Color,
    modifier: Modifier = Modifier,
    minThumbHeight: Dp = 14.dp,
) {
    if (viewportHeightPx <= 0 || scrollState.maxValue <= 0) {
        return
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val minThumbHeightPx = with(density) { minThumbHeight.roundToPx() }
    val contentHeightPx = viewportHeightPx + scrollState.maxValue
    val thumbHeightPx =
        ((viewportHeightPx.toFloat() / contentHeightPx) * viewportHeightPx)
            .roundToInt()
            .coerceAtLeast(minThumbHeightPx)
            .coerceAtMost(viewportHeightPx)
    val thumbOffsetPx by remember(scrollState, viewportHeightPx, thumbHeightPx) {
        derivedStateOf {
            if (scrollState.maxValue == 0) {
                0
            } else {
                (((scrollState.value.toFloat() / scrollState.maxValue) * (viewportHeightPx - thumbHeightPx)))
                    .roundToInt()
            }
        }
    }

    Box(
        modifier =
            modifier
                .width(3.dp)
                .height(with(density) { viewportHeightPx.toDp() })
                .clip(RoundedCornerShape(percent = 50))
                .background(color.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { thumbHeightPx.toDp() })
                    .offset { IntOffset(x = 0, y = thumbOffsetPx) }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color.copy(alpha = 0.52f)),
        )
    }
}

@Composable
internal fun rememberScrollableTextMeasure(
    text: String?,
    style: TextStyle,
    widthPx: Int,
    maxVisibleLines: Int,
): ScrollableTextMeasure {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(text, style, widthPx, maxVisibleLines, density.density, density.fontScale) {
        if (text.isNullOrBlank() || widthPx <= 0) {
            ScrollableTextMeasure(
                viewportHeight = 0.dp,
                viewportHeightPx = 0,
                scrollStepPx = 0,
                isScrollable = false,
            )
        } else {
            val layoutResult =
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = style,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = Int.MAX_VALUE,
                    constraints = Constraints(maxWidth = widthPx),
                )
            val visibleLines = layoutResult.lineCount.coerceAtMost(maxVisibleLines)
            val viewportHeightPx =
                if (visibleLines == 0) {
                    0
                } else {
                    (layoutResult.getLineBottom(visibleLines - 1) - layoutResult.getLineTop(0))
                        .roundToInt()
                }
            val scrollStepPx =
                if (layoutResult.lineCount == 0) {
                    0
                } else {
                    (layoutResult.getLineBottom(0) - layoutResult.getLineTop(0))
                        .roundToInt()
                        .coerceAtLeast(1)
                }
            ScrollableTextMeasure(
                viewportHeight = with(density) { viewportHeightPx.toDp() },
                viewportHeightPx = viewportHeightPx,
                scrollStepPx = scrollStepPx,
                isScrollable = layoutResult.lineCount > maxVisibleLines,
            )
        }
    }
}

internal fun String.normalizeTitleText(): String {
    return replace("\r", " ")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
