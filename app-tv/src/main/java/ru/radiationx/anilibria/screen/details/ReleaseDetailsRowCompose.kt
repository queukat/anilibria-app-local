package ru.radiationx.anilibria.screen.details

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.screen.watching.TvPageVerticalPadding
import ru.radiationx.anilibria.screen.watching.TvDetailHorizontalPadding
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.shared_app.imageloader.loadImageBitmap
import kotlin.math.roundToInt

internal enum class ReleaseDetailsFocusTarget {
    StartAction,
    Continue,
    Play,
    Favorite,
    Description,
    Other,
}

@Stable
internal data class ReleaseDetailsRowUiState(
    val details: LibriaDetails? = null,
    val progressState: DetailsState = DetailsState(loadingProgress = true),
    val initialFocusToken: Int = 0,
    val initialFocusTarget: ReleaseDetailsFocusTarget = ReleaseDetailsFocusTarget.StartAction,
)

@Stable
internal data class ReleaseDetailsCallbacks(
    val continueClick: () -> Unit,
    val playClick: () -> Unit,
    val favoriteClick: () -> Unit,
    val descriptionClick: () -> Unit,
    val otherClick: () -> Unit,
)

internal enum class ReleaseDetailsVerticalMoveAction {
    MoveFocus,
    Consume,
}

internal fun resolveReleaseDetailsVerticalMoveAction(hasTarget: Boolean): ReleaseDetailsVerticalMoveAction {
    return if (hasTarget) {
        ReleaseDetailsVerticalMoveAction.MoveFocus
    } else {
        ReleaseDetailsVerticalMoveAction.Consume
    }
}

internal fun requestReleaseDetailsFocusOrConsumeBoundary(focusRequester: FocusRequester?): Boolean {
    return when (resolveReleaseDetailsVerticalMoveAction(focusRequester != null)) {
        ReleaseDetailsVerticalMoveAction.MoveFocus -> {
            requestFocus(focusRequester)
            true
        }
        ReleaseDetailsVerticalMoveAction.Consume -> true
    }
}

private const val BOTTOM_ARROW_ALPHA = 0.75f

private data class ScrollableTextMeasure(
    val viewportHeight: Dp,
    val viewportHeightPx: Int,
    val scrollStepPx: Int,
    val isScrollable: Boolean,
)

@Composable
internal fun ReleaseDetailsRowContent(
    uiState: ReleaseDetailsRowUiState,
    callbacks: ReleaseDetailsCallbacks,
    modifier: Modifier = Modifier,
    showMoreHint: Boolean = false,
    actionsDownRequester: FocusRequester? = null,
    onInitialHeaderFocusApplied: () -> Unit = {},
) {
    val details = uiState.details
    val progressState = uiState.progressState
    val density = LocalDensity.current
    val interactionsEnabled = !progressState.loadingProgress

    val horizontalPadding = TvDetailHorizontalPadding
    val topPadding = horizontalPadding
    val actionRowBottomPadding = topPadding + 20.dp
    val bottomHintTopSpacing = 4.dp
    val bottomHintBottomSpacing = 4.dp

    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val cardBackground = colorResource(R.color.dark_cardBackground)
    val actionBackground = colorResource(R.color.dark_release_day_btn)
    val accentColor = rememberThemeColor(android.R.attr.colorControlHighlight)

    val ruTitleRequester = remember { FocusRequester() }
    val enTitleRequester = remember { FocusRequester() }
    val descriptionRequester = remember { FocusRequester() }
    val continueRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val favoriteRequester = remember { FocusRequester() }
    val otherRequester = remember { FocusRequester() }

    val startActionRequester = remember(details) {
        when {
            details?.hasViewed == true -> continueRequester
            details?.hasEpisodes == true -> playRequester
            details != null -> favoriteRequester
            else -> descriptionRequester
        }
    }
    val initialFocusRequester = remember(
        details,
        uiState.initialFocusTarget,
    ) {
        fun fallback(): FocusRequester = startActionRequester
        when (uiState.initialFocusTarget) {
            ReleaseDetailsFocusTarget.StartAction -> fallback()
            ReleaseDetailsFocusTarget.Continue -> {
                if (details?.hasViewed == true) continueRequester else fallback()
            }
            ReleaseDetailsFocusTarget.Play -> {
                if (details?.hasEpisodes == true) playRequester else fallback()
            }
            ReleaseDetailsFocusTarget.Favorite -> favoriteRequester
            ReleaseDetailsFocusTarget.Description -> descriptionRequester
            ReleaseDetailsFocusTarget.Other -> {
                if (details?.let { it.hasEpisodes || it.hasViewed } == true) {
                    otherRequester
                } else {
                    fallback()
                }
            }
        }
    }

    LaunchedEffect(uiState.initialFocusToken, progressState.loadingProgress) {
        if (uiState.initialFocusToken == 0 || progressState.loadingProgress) {
            return@LaunchedEffect
        }
        if (requestWatchingFocusAfterAttach(initialFocusRequester)) {
            onInitialHeaderFocusApplied()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.dark_colorPrimary))
    ) {
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = horizontalPadding, end = horizontalPadding)
        ) {
            val leftContent = createRef()
            val imageCard = createRef()
            val actionsRow = createRef()
            val updateProgress = createRef()
            val bottomArrow = createRef()
            val loadingProgress = createRef()

            BoxWithConstraints(
                modifier = Modifier.constrainAs(leftContent) {
                    start.linkTo(parent.start)
                    end.linkTo(imageCard.start, margin = horizontalPadding)
                    top.linkTo(parent.top, margin = topPadding)
                    bottom.linkTo(actionsRow.top, margin = 16.dp)
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                }
            ) {
                val contentWidthPx = with(density) { maxWidth.roundToPx() }
                val ruTitle = details?.titleRu?.normalizeTitleText()
                val enTitle = details?.titleEn?.normalizeTitleText()
                val ruTitleStyle = TextStyle(
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Normal,
                    color = textColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                )
                val enTitleStyle = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                    color = textColor,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                )

                val ruMeasure = rememberScrollableTextMeasure(
                    text = ruTitle,
                    style = ruTitleStyle,
                    widthPx = contentWidthPx,
                    maxVisibleLines = 2,
                )
                val enMeasure = rememberScrollableTextMeasure(
                    text = enTitle,
                    style = enTitleStyle,
                    widthPx = contentWidthPx,
                    maxVisibleLines = 2,
                )

                val hasRuFocusStop = interactionsEnabled && !ruTitle.isNullOrBlank() && ruMeasure.isScrollable
                val hasEnFocusStop = interactionsEnabled && !enTitle.isNullOrBlank() && enMeasure.isScrollable

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup(),
                    verticalArrangement = Arrangement.Top,
                ) {
                    if (!ruTitle.isNullOrBlank()) {
                        ScrollableTitle(
                            text = ruTitle,
                            style = ruTitleStyle,
                            measure = ruMeasure,
                            textColor = textColor,
                            focusRequester = ruTitleRequester,
                            enabled = interactionsEnabled,
                            canFocus = hasRuFocusStop,
                            upRequester = FocusRequester.Default,
                            downRequester = when {
                                hasEnFocusStop -> enTitleRequester
                                else -> descriptionRequester
                            },
                        )
                    }

                    if (!enTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ScrollableTitle(
                            text = enTitle,
                            style = enTitleStyle,
                            measure = enMeasure,
                            textColor = textColor,
                            focusRequester = enTitleRequester,
                            enabled = interactionsEnabled,
                            canFocus = hasEnFocusStop,
                            upRequester = if (hasRuFocusStop) ruTitleRequester else FocusRequester.Default,
                            downRequester = descriptionRequester,
                        )
                    }

                    details?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        MetadataRow(
                            details = it,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                        )

                        if (it.announce.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AnnounceChip(
                                text = it.announce,
                                textColor = textColor,
                                backgroundColor = accentColor,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    DescriptionCard(
                        text = details?.description.orEmpty(),
                        textColor = textColor,
                        backgroundColor = cardBackground.copy(alpha = 0.86f),
                        focusRequester = descriptionRequester,
                        enabled = interactionsEnabled,
                        upRequester = when {
                            hasEnFocusStop -> enTitleRequester
                            hasRuFocusStop -> ruTitleRequester
                            else -> FocusRequester.Default
                        },
                        downRequester = startActionRequester,
                        onClick = callbacks.descriptionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            ReleasePosterImage(
                imageUrl = details?.image.orEmpty(),
                backgroundColor = cardBackground,
                modifier = Modifier
                    .constrainAs(imageCard) {
                        end.linkTo(parent.end)
                        top.linkTo(parent.top, margin = topPadding)
                        bottom.linkTo(actionsRow.top, margin = 16.dp)
                        height = Dimension.fillToConstraints
                        width = Dimension.ratio("260:370")
                    }
                    .alpha(if (progressState.loadingProgress) 0f else 1f),
            )

            ActionsRow(
                details = details,
                textColor = textColor,
                backgroundColor = actionBackground,
                focusUpRequester = descriptionRequester,
                focusDownRequester = actionsDownRequester,
                continueRequester = continueRequester,
                playRequester = playRequester,
                favoriteRequester = favoriteRequester,
                otherRequester = otherRequester,
                enabled = interactionsEnabled,
                onContinueClick = callbacks.continueClick,
                onPlayClick = callbacks.playClick,
                onFavoriteClick = callbacks.favoriteClick,
                onOtherClick = callbacks.otherClick,
                modifier = Modifier
                    .constrainAs(actionsRow) {
                        start.linkTo(parent.start)
                        bottom.linkTo(parent.bottom, margin = actionRowBottomPadding)
                    }
                    .alpha(if (progressState.loadingProgress) 0f else 1f),
            )

            if (progressState.updateProgress && !progressState.loadingProgress) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = textColor,
                    modifier = Modifier
                        .size(24.dp)
                        .constrainAs(updateProgress) {
                            bottom.linkTo(actionsRow.top, margin = 12.dp)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                )
            }

            if (showMoreHint) {
                Icon(
                    painter = painterResource(R.drawable.ic_wide_arrow_down),
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier
                        .alpha(BOTTOM_ARROW_ALPHA)
                        .constrainAs(bottomArrow) {
                            top.linkTo(actionsRow.bottom, margin = bottomHintTopSpacing)
                            bottom.linkTo(parent.bottom, margin = bottomHintBottomSpacing)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                )
            }

            if (progressState.loadingProgress) {
                CircularProgressIndicator(
                    color = textColor,
                    modifier = Modifier
                        .size(56.dp)
                        .constrainAs(loadingProgress) {
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom, margin = topPadding)
                        }
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
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
                    painter = painterResource(
                        if (details.isFavorite) {
                            R.drawable.ic_details_favorite_filled
                        } else {
                            R.drawable.ic_details_favorite
                        }
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
private fun AnnounceChip(
    text: String,
    textColor: Color,
    backgroundColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor.copy(alpha = 0.18f))
            .border(
                width = 1.dp,
                color = backgroundColor.copy(alpha = 0.34f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
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
private fun ScrollableTitle(
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
    androidx.compose.runtime.key(text) {
        val scrollState = rememberScrollState()
        val viewportHeight = measure.viewportHeight
        val modifier = if (enabled && canFocus) {
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
                modifier = Modifier
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
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun DescriptionCard(
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
    androidx.compose.runtime.key(text) {
        val scrollState = rememberScrollState()
        var viewportHeightPx by remember { mutableIntStateOf(0) }
        var isFocused by remember { mutableStateOf(false) }
        val interactiveModifier = if (enabled) {
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
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(backgroundColor)
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) {
                        textColor.copy(alpha = 0.75f)
                    } else {
                        textColor.copy(alpha = 0.08f)
                    },
                    shape = RoundedCornerShape(18.dp),
                )
                .onSizeChanged { viewportHeightPx = it.height }
                .then(interactiveModifier)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = text,
                color = textColor.copy(alpha = 0.9f),
                fontSize = 17.sp,
                lineHeight = 26.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = if (scrollState.maxValue > 0) 10.dp else 0.dp)
                    .verticalScroll(scrollState, enabled = enabled),
            )
            VerticalScrollIndicator(
                scrollState = scrollState,
                viewportHeightPx = viewportHeightPx,
                color = textColor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(vertical = 12.dp, horizontal = 2.dp),
                minThumbHeight = 18.dp,
            )
        }
    }
}

@Composable
private fun ActionsRow(
    details: LibriaDetails?,
    textColor: Color,
    backgroundColor: Color,
    focusUpRequester: FocusRequester,
    focusDownRequester: FocusRequester?,
    continueRequester: FocusRequester,
    playRequester: FocusRequester,
    favoriteRequester: FocusRequester,
    otherRequester: FocusRequester,
    enabled: Boolean,
    onContinueClick: () -> Unit,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onOtherClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (details?.hasViewed == true) {
            ActionChipButton(
                text = "Продолжить",
                textColor = textColor,
                backgroundColor = backgroundColor,
                focusRequester = continueRequester,
                upRequester = focusUpRequester,
                downRequester = focusDownRequester,
                enabled = enabled,
                onClick = onContinueClick,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        if (details?.hasEpisodes == true) {
            ActionChipButton(
                text = "Смотреть",
                textColor = textColor,
                backgroundColor = backgroundColor,
                focusRequester = playRequester,
                upRequester = focusUpRequester,
                downRequester = focusDownRequester,
                enabled = enabled,
                onClick = onPlayClick,
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        ActionChipButton(
            text = if (details?.isFavorite == true) {
                "Убрать из избранного"
            } else {
                "Добавить в избранное"
            },
            textColor = textColor,
            backgroundColor = backgroundColor,
            focusRequester = favoriteRequester,
            upRequester = focusUpRequester,
            downRequester = focusDownRequester,
            enabled = enabled,
            onClick = onFavoriteClick,
        )

        if (details?.let { it.hasEpisodes || it.hasViewed } == true) {
            Spacer(modifier = Modifier.width(16.dp))
            IconChipButton(
                iconRes = R.drawable.ic_more_vert,
                contentColor = textColor,
                backgroundColor = backgroundColor,
                focusRequester = otherRequester,
                upRequester = focusUpRequester,
                downRequester = focusDownRequester,
                enabled = enabled,
                onClick = onOtherClick,
            )
        }
    }
}

@Composable
private fun ActionChipButton(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = backgroundColor.copy(alpha = 0.88f),
        focusedBackgroundColor = backgroundColor,
        borderColor = textColor.copy(alpha = 0.8f),
        onClick = onClick,
        onUp = {
            requestFocus(upRequester)
        },
        onDown = {
            requestReleaseDetailsFocusOrConsumeBoundary(downRequester)
        },
        modifier = Modifier.heightIn(min = 54.dp),
        paddingValues = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 22.dp,
            vertical = 15.dp,
        ),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun IconChipButton(
    iconRes: Int,
    contentColor: Color,
    backgroundColor: Color,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester,
        enabled = enabled,
        backgroundColor = backgroundColor.copy(alpha = 0.88f),
        focusedBackgroundColor = backgroundColor,
        borderColor = contentColor.copy(alpha = 0.8f),
        onClick = onClick,
        onUp = {
            requestFocus(upRequester)
        },
        onDown = {
            requestReleaseDetailsFocusOrConsumeBoundary(downRequester)
        },
        modifier = Modifier.heightIn(min = 54.dp),
        paddingValues = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 14.dp,
        ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun ReleasePosterImage(
    imageUrl: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = imageUrl,
    ) {
        value = if (imageUrl.isBlank()) {
            null
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.loadImageBitmap(imageUrl).asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun VerticalScrollIndicator(
    scrollState: androidx.compose.foundation.ScrollState,
    viewportHeightPx: Int,
    color: Color,
    modifier: Modifier = Modifier,
    minThumbHeight: Dp = 14.dp,
) {
    if (viewportHeightPx <= 0 || scrollState.maxValue <= 0) {
        return
    }

    val density = LocalDensity.current
    val minThumbHeightPx = with(density) { minThumbHeight.roundToPx() }
    val contentHeightPx = viewportHeightPx + scrollState.maxValue
    val thumbHeightPx = ((viewportHeightPx.toFloat() / contentHeightPx) * viewportHeightPx)
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
        modifier = modifier
            .width(3.dp)
            .height(with(density) { viewportHeightPx.toDp() })
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { thumbHeightPx.toDp() })
                .offset { IntOffset(x = 0, y = thumbOffsetPx) }
                .clip(RoundedCornerShape(percent = 50))
                .background(color.copy(alpha = 0.52f)),
        )
    }
}

@Composable
private fun rememberScrollableTextMeasure(
    text: String?,
    style: TextStyle,
    widthPx: Int,
    maxVisibleLines: Int,
): ScrollableTextMeasure {
    val density = LocalDensity.current
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
            val layoutResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                overflow = TextOverflow.Clip,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                constraints = Constraints(maxWidth = widthPx),
            )
            val visibleLines = layoutResult.lineCount.coerceAtMost(maxVisibleLines)
            val viewportHeightPx = if (visibleLines == 0) {
                0
            } else {
                (layoutResult.getLineBottom(visibleLines - 1) - layoutResult.getLineTop(0))
                    .roundToInt()
            }
            val scrollStepPx = if (layoutResult.lineCount == 0) {
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

@Composable
private fun Modifier.tvScrollableFocus(
    scrollState: androidx.compose.foundation.ScrollState,
    viewportHeightPx: Int,
    scrollStepPx: Int,
    focusRequester: FocusRequester,
    upRequester: FocusRequester,
    downRequester: FocusRequester,
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = colorResource(R.color.dark_textDefault).copy(alpha = 0.75f)

    return this
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) borderColor else Color.Transparent,
            shape = RoundedCornerShape(6.dp),
        )
        .focusRequester(focusRequester)
        .focusProperties {
            up = upRequester
            down = downRequester
        }
        .onFocusChanged { isFocused = it.isFocused }
        .tvScrollKeys(
            scrollState = scrollState,
            viewportHeightPx = viewportHeightPx,
            scrollStepPx = scrollStepPx,
        )
        .focusable()
}

@Composable
private fun Modifier.tvScrollKeys(
    scrollState: androidx.compose.foundation.ScrollState,
    viewportHeightPx: Int,
    scrollStepPx: Int = viewportHeightPx,
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || viewportHeightPx <= 0) {
            return@onPreviewKeyEvent false
        }
        val delta = when (event.key) {
            Key.DirectionDown -> scrollStepPx
            Key.DirectionUp -> -scrollStepPx
            else -> return@onPreviewKeyEvent false
        }
        val targetValue = (scrollState.value + delta)
            .coerceIn(0, scrollState.maxValue)
        if (targetValue == scrollState.value) {
            false
        } else {
            coroutineScope.launch {
                scrollState.animateScrollTo(targetValue)
            }
            true
        }
    }
}

@Composable
private fun rememberThemeColor(@AttrRes attrRes: Int): Color {
    val context = LocalContext.current
    return remember(attrRes) {
        Color(context.resolveThemeColor(attrRes))
    }
}

private fun Context.resolveThemeColor(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    check(theme.resolveAttribute(attrRes, typedValue, true)) {
        "Attribute $attrRes is not defined in the current theme"
    }
    return if (typedValue.resourceId != 0) {
        ContextCompat.getColor(this, typedValue.resourceId)
    } else {
        typedValue.data
    }
}

private fun requestFocus(focusRequester: FocusRequester?): Boolean {
    if (focusRequester == null) {
        return false
    }
    return runCatching {
        focusRequester.requestFocus()
        true
    }.getOrDefault(false)
}

private fun String.normalizeTitleText(): String {
    return replace("\r", " ")
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
