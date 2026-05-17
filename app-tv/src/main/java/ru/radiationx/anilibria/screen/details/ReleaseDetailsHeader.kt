package ru.radiationx.anilibria.screen.details

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.content.ContextCompat
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.screen.watching.TvDetailHorizontalPadding
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach

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

private const val BOTTOM_ARROW_ALPHA = 0.75f

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

    val startActionRequester =
        remember(details) {
            when {
                details?.hasViewed == true -> continueRequester
                details?.hasEpisodes == true -> playRequester
                details != null -> favoriteRequester
                else -> descriptionRequester
            }
        }
    val initialFocusRequester =
        remember(
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
        modifier =
            modifier
                .fillMaxSize()
                .background(colorResource(R.color.dark_colorPrimary)),
    ) {
        ConstraintLayout(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = horizontalPadding, end = horizontalPadding),
        ) {
            val leftContent = createRef()
            val imageCard = createRef()
            val actionsRow = createRef()
            val updateProgress = createRef()
            val bottomArrow = createRef()
            val loadingProgress = createRef()

            BoxWithConstraints(
                modifier =
                    Modifier.constrainAs(leftContent) {
                        start.linkTo(parent.start)
                        end.linkTo(imageCard.start, margin = horizontalPadding)
                        top.linkTo(parent.top, margin = topPadding)
                        bottom.linkTo(actionsRow.top, margin = 16.dp)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    },
            ) {
                val contentWidthPx = with(density) { maxWidth.roundToPx() }
                val ruTitle = details?.titleRu?.normalizeTitleText()
                val enTitle = details?.titleEn?.normalizeTitleText()
                val ruTitleStyle =
                    TextStyle(
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Normal,
                        color = textColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    )
                val enTitleStyle =
                    TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal,
                        color = textColor,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    )

                val ruMeasure =
                    rememberScrollableTextMeasure(
                        text = ruTitle,
                        style = ruTitleStyle,
                        widthPx = contentWidthPx,
                        maxVisibleLines = 2,
                    )
                val enMeasure =
                    rememberScrollableTextMeasure(
                        text = enTitle,
                        style = enTitleStyle,
                        widthPx = contentWidthPx,
                        maxVisibleLines = 2,
                    )

                val hasRuFocusStop = interactionsEnabled && !ruTitle.isNullOrBlank() && ruMeasure.isScrollable
                val hasEnFocusStop = interactionsEnabled && !enTitle.isNullOrBlank() && enMeasure.isScrollable

                Column(
                    modifier =
                        Modifier
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
                            focus =
                                ScrollableTitleFocus(
                                    requester = ruTitleRequester,
                                    upRequester = FocusRequester.Default,
                                    downRequester =
                                        when {
                                            hasEnFocusStop -> enTitleRequester
                                            else -> descriptionRequester
                                        },
                                ),
                            state =
                                ScrollableTitleState(
                                    enabled = interactionsEnabled,
                                    canFocus = hasRuFocusStop,
                                ),
                        )
                    }

                    if (!enTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ScrollableTitle(
                            text = enTitle,
                            style = enTitleStyle,
                            measure = enMeasure,
                            textColor = textColor,
                            focus =
                                ScrollableTitleFocus(
                                    requester = enTitleRequester,
                                    upRequester = if (hasRuFocusStop) ruTitleRequester else FocusRequester.Default,
                                    downRequester = descriptionRequester,
                                ),
                            state =
                                ScrollableTitleState(
                                    enabled = interactionsEnabled,
                                    canFocus = hasEnFocusStop,
                                ),
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
                        colors =
                            DescriptionCardColors(
                                textColor = textColor,
                                backgroundColor = cardBackground.copy(alpha = 0.86f),
                            ),
                        focus =
                            DescriptionCardFocus(
                                requester = descriptionRequester,
                                upRequester =
                                    when {
                                        hasEnFocusStop -> enTitleRequester
                                        hasRuFocusStop -> ruTitleRequester
                                        else -> FocusRequester.Default
                                    },
                                downRequester = startActionRequester,
                            ),
                        enabled = interactionsEnabled,
                        onClick = callbacks.descriptionClick,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    )
                }
            }

            ReleasePosterImage(
                imageUrl = details?.image.orEmpty(),
                backgroundColor = cardBackground,
                modifier =
                    Modifier
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
                colors =
                    ReleaseActionColors(
                        textColor = textColor,
                        backgroundColor = actionBackground,
                    ),
                focus =
                    ReleaseActionsFocus(
                        upRequester = descriptionRequester,
                        downRequester = actionsDownRequester,
                        continueRequester = continueRequester,
                        playRequester = playRequester,
                        favoriteRequester = favoriteRequester,
                        otherRequester = otherRequester,
                    ),
                enabled = interactionsEnabled,
                callbacks =
                    ReleaseActionsCallbacks(
                        continueClick = callbacks.continueClick,
                        playClick = callbacks.playClick,
                        favoriteClick = callbacks.favoriteClick,
                        otherClick = callbacks.otherClick,
                    ),
                modifier =
                    Modifier
                        .constrainAs(actionsRow) {
                            start.linkTo(parent.start)
                            bottom.linkTo(parent.bottom, margin = actionRowBottomPadding)
                        }
                        .alpha(if (progressState.loadingProgress) 0f else 1f),
            )

            if (progressState.updateProgress && !progressState.loadingProgress) {
                androidx.compose.material3.CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = textColor,
                    modifier =
                        Modifier
                            .size(24.dp)
                            .constrainAs(updateProgress) {
                                bottom.linkTo(actionsRow.top, margin = 12.dp)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            },
                )
            }

            if (showMoreHint) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.ic_wide_arrow_down),
                    contentDescription = null,
                    tint = textColor,
                    modifier =
                        Modifier
                            .alpha(BOTTOM_ARROW_ALPHA)
                            .constrainAs(bottomArrow) {
                                top.linkTo(actionsRow.bottom, margin = bottomHintTopSpacing)
                                bottom.linkTo(parent.bottom, margin = bottomHintBottomSpacing)
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                            },
                )
            }

            if (progressState.loadingProgress) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = textColor,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .constrainAs(loadingProgress) {
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom, margin = topPadding)
                            },
                )
            }
        }
    }
}

@Composable
private fun rememberThemeColor(
    @AttrRes attrRes: Int,
): androidx.compose.ui.graphics.Color {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(attrRes) {
        androidx.compose.ui.graphics.Color(context.resolveThemeColor(attrRes))
    }
}

private fun Context.resolveThemeColor(
    @AttrRes attrRes: Int,
): Int {
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
