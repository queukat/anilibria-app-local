package ru.radiationx.anilibria.screen.mainpages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.main.MainContentRestoreState
import ru.radiationx.anilibria.screen.main.MainScreen
import ru.radiationx.anilibria.screen.main.MainSectionTitles
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
import ru.radiationx.anilibria.screen.watching.requestWatchingFocusAfterAttach
import ru.radiationx.anilibria.ui.compose.TvShellDefaults
import ru.radiationx.anilibria.ui.compose.TvUiDefaults
import ru.radiationx.anilibria.ui.compose.tvAppBackground
import ru.radiationx.data.entity.domain.types.ReleaseId

internal data class MainShellItem(
    val id: Long,
    val title: String,
)

internal enum class MainHeaderAction {
    Search,
    Catalog,
    Update,
}

internal data class MainPagesRootState(
    val items: List<MainShellItem>,
    val selectedPageId: Long,
    val hasUpdates: Boolean,
    val headerVisible: Boolean,
    val railExpanded: Boolean,
)

internal data class MainPagesRootFocus(
    val preferredHeaderAction: MainHeaderAction,
    val headerFocusRequestToken: Int,
    val railFocusRequestToken: Int,
)

internal data class MainPagesRootCallbacks(
    val onHeaderFocused: (MainHeaderAction) -> Unit,
    val onSearchClick: () -> Unit,
    val onCatalogClick: () -> Unit,
    val onUpdateClick: () -> Unit,
    val onPageFocused: (Long) -> Unit,
    val onRequestHeaderFocus: () -> Boolean,
    val onRequestContentFocus: () -> Boolean,
)

internal data class MainPagesHeaderState(
    val selectedPageTitle: String,
    val hasUpdates: Boolean,
    val preferredAction: MainHeaderAction,
    val focusRequestToken: Int,
)

internal data class MainPagesHeaderCallbacks(
    val onSearchClick: () -> Unit,
    val onCatalogClick: () -> Unit,
    val onUpdateClick: () -> Unit,
    val onRequestContentFocus: () -> Boolean,
    val onFocused: (MainHeaderAction) -> Unit,
)

internal data class MainPagesRailState(
    val items: List<MainShellItem>,
    val selectedPageId: Long,
    val expanded: Boolean,
    val railWidth: Dp,
    val focusRequestToken: Int,
)

internal data class MainPagesRailCallbacks(
    val onPageFocused: (Long) -> Unit,
    val onRequestHeaderFocus: () -> Boolean,
    val onRequestContentFocus: () -> Boolean,
)

private data class MainPagesRailColors(
    val accentColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
)

private data class ShellButtonColors(
    val backgroundColor: Color,
    val textColor: Color,
    val borderColor: Color,
    val focusedBackgroundColor: Color = backgroundColor,
)

private data class ShellButtonCallbacks(
    val onClick: () -> Unit,
    val onFocused: (() -> Unit)? = null,
    val onLeft: (() -> Boolean)? = null,
    val onUp: (() -> Boolean)? = null,
    val onRight: (() -> Boolean)? = null,
    val onDown: (() -> Boolean)? = null,
)

private data class ShellButtonStyle(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val modifier: Modifier = Modifier,
    val minWidth: Dp = TvShellDefaults.DefaultButtonMinWidth,
    val selected: Boolean = false,
    val textAlign: TextAlign = TextAlign.Start,
)

@Composable
internal fun MainPagesRoot(
    state: MainPagesRootState,
    focus: MainPagesRootFocus,
    callbacks: MainPagesRootCallbacks,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = rememberWatchingPalette()
    val headerHeight = TvShellDefaults.HeaderHeight
    val headerSpacing = TvShellDefaults.HeaderSpacing
    val railWidth = TvShellDefaults.RailWidth
    val shellTopOffset = if (state.headerVisible) headerHeight + headerSpacing else 0.dp
    val contentTopOffset by animateDpAsState(
        targetValue = shellTopOffset,
        label = "mainPagesContentOffset",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .tvAppBackground(palette, glowAlpha = TvUiDefaults.APP_BACKGROUND_GLOW_ALPHA),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = contentTopOffset),
        ) {
            content()
        }

        AnimatedVisibility(
            visible = state.headerVisible,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(headerHeight),
        ) {
            MainPagesHeader(
                state =
                    MainPagesHeaderState(
                        selectedPageTitle = MainPagesSpec.titles.getValue(state.selectedPageId),
                        hasUpdates = state.hasUpdates,
                        preferredAction = focus.preferredHeaderAction,
                        focusRequestToken = focus.headerFocusRequestToken,
                    ),
                callbacks =
                    MainPagesHeaderCallbacks(
                        onSearchClick = callbacks.onSearchClick,
                        onCatalogClick = callbacks.onCatalogClick,
                        onUpdateClick = callbacks.onUpdateClick,
                        onRequestContentFocus = callbacks.onRequestContentFocus,
                        onFocused = callbacks.onHeaderFocused,
                    ),
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight()
                    .width(railWidth)
                    .clipToBounds()
                    .padding(top = shellTopOffset),
        ) {
            MainPagesShell(
                state =
                    MainPagesRailState(
                        items = state.items,
                        selectedPageId = state.selectedPageId,
                        expanded = state.railExpanded,
                        railWidth = railWidth,
                        focusRequestToken = focus.railFocusRequestToken,
                    ),
                callbacks =
                    MainPagesRailCallbacks(
                        onPageFocused = callbacks.onPageFocused,
                        onRequestHeaderFocus = callbacks.onRequestHeaderFocus,
                        onRequestContentFocus = callbacks.onRequestContentFocus,
                    ),
            )
        }
    }
}

@Composable
internal fun MainPagesHeader(
    state: MainPagesHeaderState,
    callbacks: MainPagesHeaderCallbacks,
) {
    val palette = rememberWatchingPalette()
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val actionBackground =
        colorResource(R.color.dark_release_day_btn).copy(alpha = TvShellDefaults.ACTION_BACKGROUND_ALPHA)
    val accentColor = colorResource(R.color.dark_colorAccent)

    val searchRequester = remember { FocusRequester() }
    val catalogRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }

    LaunchedEffect(state.focusRequestToken, state.hasUpdates, state.preferredAction) {
        if (state.focusRequestToken <= 0) {
            return@LaunchedEffect
        }
        val preferredRequester =
            when (state.preferredAction) {
                MainHeaderAction.Search -> searchRequester
                MainHeaderAction.Catalog -> catalogRequester
                MainHeaderAction.Update -> if (state.hasUpdates) updateRequester else catalogRequester
            }
        requestWatchingFocusAfterAttach(
            requester = preferredRequester,
            attempts = 12,
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(TvUiDefaults.shellHeaderBrush(palette)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(TvShellDefaults.HeaderPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvShellDefaults.HeaderContentSpacing),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(TvShellDefaults.HeaderLogoShape)
                        .background(accentColor.copy(alpha = TvShellDefaults.LOGO_BACKGROUND_ALPHA))
                        .border(
                            width = TvUiDefaults.UNFOCUSED_BORDER_WIDTH,
                            color = accentColor.copy(alpha = TvShellDefaults.LOGO_BORDER_ALPHA),
                            shape = TvShellDefaults.HeaderLogoShape,
                        )
                        .padding(TvShellDefaults.HeaderLogoPadding),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_anilibria_splash),
                    contentDescription = null,
                    modifier = Modifier.width(TvShellDefaults.HeaderLogoWidth),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TvShellDefaults.HeaderTitleSpacing),
            ) {
                Text(
                    text = "AniLibria TV",
                    color = secondaryTextColor,
                    fontSize = TvShellDefaults.HeaderEyebrowFontSize,
                )
                Text(
                    text = state.selectedPageTitle,
                    color = textColor,
                    fontSize = TvShellDefaults.HeaderTitleFontSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(TvShellDefaults.HeaderActionSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderActionButton(
                    text = "Поиск",
                    focusRequester = searchRequester,
                    colors =
                        ShellButtonColors(
                            backgroundColor = actionBackground.copy(alpha = TvShellDefaults.SEARCH_BACKGROUND_ALPHA),
                            borderColor = textColor.copy(alpha = TvShellDefaults.ACTION_BORDER_ALPHA),
                            textColor = textColor,
                        ),
                    callbacks =
                        ShellButtonCallbacks(
                            onClick = callbacks.onSearchClick,
                            onDown = callbacks.onRequestContentFocus,
                            onFocused = { callbacks.onFocused(MainHeaderAction.Search) },
                        ),
                )
                HeaderActionButton(
                    text = "Каталог",
                    focusRequester = catalogRequester,
                    colors =
                        ShellButtonColors(
                            backgroundColor = actionBackground,
                            borderColor = textColor.copy(alpha = TvShellDefaults.ACTION_BORDER_ALPHA),
                            textColor = textColor,
                        ),
                    callbacks =
                        ShellButtonCallbacks(
                            onClick = callbacks.onCatalogClick,
                            onDown = callbacks.onRequestContentFocus,
                            onFocused = { callbacks.onFocused(MainHeaderAction.Catalog) },
                        ),
                )
                if (state.hasUpdates) {
                    HeaderActionButton(
                        text = "Обновление",
                        focusRequester = updateRequester,
                        colors =
                            ShellButtonColors(
                                backgroundColor = accentColor.copy(alpha = TvShellDefaults.UPDATE_BACKGROUND_ALPHA),
                                borderColor = accentColor.copy(alpha = TvShellDefaults.UPDATE_BORDER_ALPHA),
                                textColor = textColor,
                            ),
                        callbacks =
                            ShellButtonCallbacks(
                                onClick = callbacks.onUpdateClick,
                                onDown = callbacks.onRequestContentFocus,
                                onFocused = { callbacks.onFocused(MainHeaderAction.Update) },
                            ),
                    )
                }
            }
        }

        HorizontalDivider(
            color = textColor.copy(alpha = TvShellDefaults.DIVIDER_ALPHA),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun MainPagesShell(
    state: MainPagesRailState,
    callbacks: MainPagesRailCallbacks,
) {
    val palette = rememberWatchingPalette()
    val surfaceColor = colorResource(R.color.dark_colorPrimary)
    val accentColor = colorResource(R.color.dark_colorAccent)
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val selectedIndex = state.items.indexOfFirst { it.id == state.selectedPageId }.coerceAtLeast(0)
    val requesters = remember(state.items.size) { List(state.items.size) { FocusRequester() } }

    val panelOffset by animateDpAsState(
        targetValue = if (state.expanded) 0.dp else -(state.railWidth + TvShellDefaults.RailCollapsedOvershoot),
        label = "mainPagesRailOffset",
    )

    LaunchedEffect(state.focusRequestToken, state.expanded, state.selectedPageId) {
        if (!state.expanded) {
            return@LaunchedEffect
        }
        requestWatchingFocusAfterAttach(
            requester = requesters.getOrNull(selectedIndex),
            attempts = 12,
        )
    }

    if (state.expanded) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(surfaceColor.copy(alpha = TvShellDefaults.RAIL_BACKDROP_ALPHA)),
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .offset { IntOffset(x = panelOffset.roundToPx(), y = 0) }
                .clip(TvShellDefaults.RailShape)
                .background(TvUiDefaults.shellRailBrush(palette))
                .border(
                    width = TvUiDefaults.UNFOCUSED_BORDER_WIDTH,
                    color = textColor.copy(alpha = TvShellDefaults.DIVIDER_ALPHA),
                    shape = TvShellDefaults.RailShape,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(TvShellDefaults.RailStripeWidth)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        accentColor.copy(alpha = TvShellDefaults.RAIL_STRIPE_STRONG_ALPHA),
                                        accentColor.copy(alpha = TvShellDefaults.RAIL_STRIPE_SOFT_ALPHA),
                                        Color.Transparent,
                                    ),
                            ),
                    ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(top = TvShellDefaults.RailStripeTopPadding)
                        .width(TvShellDefaults.RailStripeMarkerWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TvShellDefaults.RailStripeSpacing),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_anilibria_splash),
                    contentDescription = null,
                    modifier = Modifier.width(TvShellDefaults.RailStripeIconWidth),
                )
                Box(
                    modifier =
                        Modifier
                            .width(TvShellDefaults.RailStripeLineWidth)
                            .weight(1f)
                            .background(textColor.copy(alpha = TvShellDefaults.RAIL_LINE_ALPHA)),
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(TvShellDefaults.RailPadding),
            verticalArrangement = Arrangement.spacedBy(TvShellDefaults.RailContentSpacing),
        ) {
            Text(
                text = "Разделы",
                color = secondaryTextColor,
                fontSize = TvShellDefaults.RailTitleFontSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = TvShellDefaults.RailTitleStartPadding),
            )

            Spacer(modifier = Modifier.height(TvShellDefaults.RailSectionTitleSpacer))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(TvShellDefaults.RailButtonSpacing),
            ) {
                MainPagesRailButtons(
                    state = state,
                    requesters = requesters,
                    colors =
                        MainPagesRailColors(
                            accentColor = accentColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                        ),
                    callbacks = callbacks,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Влево: навигация\nВправо: контент",
                color = secondaryTextColor,
                fontSize = TvShellDefaults.RailHintFontSize,
                lineHeight = TvShellDefaults.RailHintLineHeight,
                modifier = Modifier.padding(start = TvShellDefaults.RailHintStartPadding),
            )
        }
    }
}

@Composable
private fun MainPagesRailButtons(
    state: MainPagesRailState,
    requesters: List<FocusRequester>,
    colors: MainPagesRailColors,
    callbacks: MainPagesRailCallbacks,
) {
    state.items.forEachIndexed { index, item ->
        val selected = item.id == state.selectedPageId
        val selectedColor = colors.accentColor.copy(alpha = TvShellDefaults.RAIL_SELECTED_ALPHA)
        val onFocused = { callbacks.onPageFocused(item.id) }
        RailPageButton(
            text = item.title,
            enabled = state.expanded,
            selected = selected,
            focusRequester = requesters[index],
            colors =
                ShellButtonColors(
                    backgroundColor = if (selected) selectedColor else Color.Transparent,
                    focusedBackgroundColor =
                        if (selected) {
                            selectedColor
                        } else {
                            colors.secondaryTextColor.copy(alpha = TvShellDefaults.RAIL_FOCUSED_ALPHA)
                        },
                    textColor = colors.textColor,
                    borderColor = colors.accentColor.copy(alpha = TvShellDefaults.RAIL_SELECTED_BORDER_ALPHA),
                ),
            callbacks =
                ShellButtonCallbacks(
                    onClick = onFocused,
                    onFocused = onFocused,
                    onUp = if (index == 0) callbacks.onRequestHeaderFocus else null,
                    onRight = callbacks.onRequestContentFocus,
                ),
        )
    }
}

@Composable
private fun HeaderActionButton(
    text: String,
    focusRequester: FocusRequester,
    colors: ShellButtonColors,
    callbacks: ShellButtonCallbacks,
) {
    ShellFocusableButton(
        text = text,
        enabled = true,
        colors =
            colors.copy(
                focusedBackgroundColor =
                    colors.backgroundColor.copy(alpha = TvUiDefaults.SOLID_SURFACE_ALPHA),
            ),
        callbacks = callbacks,
        focusRequester = focusRequester,
        style =
            ShellButtonStyle(
                horizontalPadding = TvShellDefaults.HeaderActionHorizontalPadding,
                verticalPadding = TvShellDefaults.HeaderActionVerticalPadding,
                modifier = Modifier.width(TvShellDefaults.HeaderActionWidth),
                minWidth = TvShellDefaults.HeaderActionWidth,
                textAlign = TextAlign.Center,
            ),
    )
}

@Composable
private fun RailPageButton(
    text: String,
    enabled: Boolean,
    selected: Boolean,
    focusRequester: FocusRequester,
    colors: ShellButtonColors,
    callbacks: ShellButtonCallbacks,
) {
    ShellFocusableButton(
        text = text,
        enabled = enabled,
        colors = colors,
        callbacks = callbacks,
        focusRequester = focusRequester,
        style =
            ShellButtonStyle(
                horizontalPadding = TvShellDefaults.RailButtonHorizontalPadding,
                verticalPadding = TvShellDefaults.RailButtonVerticalPadding,
                modifier = Modifier.fillMaxWidth(),
                minWidth = 0.dp,
                selected = selected,
            ),
    )
}

@Composable
private fun ShellFocusableButton(
    text: String,
    enabled: Boolean,
    colors: ShellButtonColors,
    callbacks: ShellButtonCallbacks,
    style: ShellButtonStyle,
    focusRequester: FocusRequester? = null,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester ?: FocusRequester.Default,
        enabled = enabled,
        backgroundColor = colors.backgroundColor,
        focusedBackgroundColor = colors.focusedBackgroundColor,
        borderColor = colors.borderColor,
        onClick = callbacks.onClick,
        modifier = style.modifier.widthIn(min = style.minWidth),
        selected = style.selected,
        selectedBorderColor = colors.borderColor,
        selectedBorderWidth = TvUiDefaults.FOCUSED_BORDER_WIDTH,
        onFocused = callbacks.onFocused,
        onLeft = callbacks.onLeft,
        onUp = callbacks.onUp,
        onRight = callbacks.onRight,
        onDown = callbacks.onDown,
        paddingValues =
            PaddingValues(
                horizontal = style.horizontalPadding,
                vertical = style.verticalPadding,
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = colors.textColor,
            fontSize =
                if (style.textAlign == TextAlign.Center) {
                    TvShellDefaults.HeaderActionFontSize
                } else {
                    TvShellDefaults.RailButtonFontSize
                },
            fontWeight = if (style.selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = style.textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(
    name = "TV Home",
    widthDp = 1280,
    heightDp = 720,
    showBackground = true,
    backgroundColor = 0xFF0E131A,
)
@Composable
internal fun MainPagesHomePreview() {
    MainPagesPreviewScene(railExpanded = false)
}

@Preview(
    name = "TV Home Rail",
    widthDp = 1280,
    heightDp = 720,
    showBackground = true,
    backgroundColor = 0xFF0E131A,
)
@Composable
internal fun MainPagesHomeRailPreview() {
    MainPagesPreviewScene(railExpanded = true)
}

@Composable
private fun MainPagesPreviewScene(railExpanded: Boolean) {
    val shellItems =
        remember {
            MainPagesSpec.ids.map { pageId ->
                MainShellItem(
                    id = pageId,
                    title = MainPagesSpec.titles.getValue(pageId),
                )
            }
        }
    val selectedPageId = MainPagesSpec.ID_MAIN
    val headerHeight = TvShellDefaults.HeaderHeight
    val headerSpacing = TvShellDefaults.HeaderSpacing
    val railWidth = TvShellDefaults.RailWidth
    val shellTopOffset = headerHeight + headerSpacing

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(colorResource(R.color.dark_windowBackground)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = shellTopOffset),
        ) {
            MainScreen(
                sections = previewMainSections(),
                focusRequestToken = 0,
                visibilityRestoreToken = 0,
                contentRestoreState =
                    MainContentRestoreState(
                        preferredSectionIndex = 0,
                        preferredItemIndex = 1,
                        preferredItemKey = "release:$PREVIEW_RELEASE_ID_2",
                    ),
                onItemClick = { _, _ -> },
                onRequestRailFocus = { true },
                onRequestHeaderFocus = { true },
                onContentMovedDown = {},
                onContentMovedUp = {},
                onItemFocused = { _, _, _ -> },
                onBackdropItemFocused = { _ -> },
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(headerHeight),
        ) {
            MainPagesHeader(
                state =
                    MainPagesHeaderState(
                        selectedPageTitle = MainPagesSpec.titles.getValue(selectedPageId),
                        hasUpdates = true,
                        preferredAction = MainHeaderAction.Search,
                        focusRequestToken = 0,
                    ),
                callbacks =
                    MainPagesHeaderCallbacks(
                        onSearchClick = {},
                        onCatalogClick = {},
                        onUpdateClick = {},
                        onRequestContentFocus = { true },
                        onFocused = { _ -> },
                    ),
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight()
                    .width(railWidth)
                    .padding(top = shellTopOffset),
        ) {
            MainPagesShell(
                state =
                    MainPagesRailState(
                        items = shellItems,
                        selectedPageId = selectedPageId,
                        expanded = railExpanded,
                        railWidth = railWidth,
                        focusRequestToken = 0,
                    ),
                callbacks =
                    MainPagesRailCallbacks(
                        onPageFocused = {},
                        onRequestHeaderFocus = { true },
                        onRequestContentFocus = { true },
                    ),
            )
        }
    }
}

private fun previewMainSections(): List<MainSectionUiModel> {
    return listOf(
        MainSectionUiModel(
            id = PREVIEW_MAIN_SECTION_ID,
            title = MainSectionTitles.FEED,
            items =
                listOf(
                    previewReleaseCard(PREVIEW_RELEASE_ID_1, "Врата Штейна", "На неделе вышла 7 серия"),
                    previewReleaseCard(PREVIEW_RELEASE_ID_2, "Frieren", "Новый эпизод сегодня в 20:00"),
                    previewReleaseCard(PREVIEW_RELEASE_ID_3, "Провожающая в последний путь", "Перевод завершен"),
                    previewReleaseCard(PREVIEW_RELEASE_ID_4, "Blue Box", "Онгоинг • обновлено 2 часа назад"),
                    LinkCard("Открыть весь список"),
                ),
        ),
        MainSectionUiModel(
            id = PREVIEW_FAVORITES_SECTION_ID,
            title = MainSectionTitles.FAVORITES,
            items =
                listOf(
                    previewReleaseCard(PREVIEW_FAVORITE_ID_1, "Solo Leveling", "Добавлена 10 серия"),
                    previewReleaseCard(PREVIEW_FAVORITE_ID_2, "Kaiju No. 8", "Вышла новая озвучка"),
                    previewReleaseCard(PREVIEW_FAVORITE_ID_3, "Dandadan", "Новый релиз уже доступен"),
                    previewReleaseCard(PREVIEW_FAVORITE_ID_4, "Re:Zero", "Следующая серия завтра"),
                ),
        ),
        MainSectionUiModel(
            id = PREVIEW_SCHEDULE_SECTION_ID,
            title = MainSectionTitles.SCHEDULE,
            items =
                listOf(
                    previewReleaseCard(PREVIEW_SCHEDULE_ID_1, "Dr. Stone", "Премьера в 18:30"),
                    previewReleaseCard(PREVIEW_SCHEDULE_ID_2, "Wind Breaker", "Сегодня вечером"),
                    previewReleaseCard(PREVIEW_SCHEDULE_ID_3, "Made in Abyss", "Пока без точного времени"),
                    InfoCard(
                        title = "Ещё несколько релизов позже вечером",
                        subtitle = "Откройте расписание, чтобы посмотреть весь список на сегодня.",
                    ),
                ),
        ),
        MainSectionUiModel(
            id = PREVIEW_YOUTUBE_SECTION_ID,
            title = MainSectionTitles.YOUTUBE,
            items =
                listOf(
                    previewYoutubeCard(PREVIEW_YOUTUBE_ID_1, "Итоги недели AniLibria"),
                    previewYoutubeCard(PREVIEW_YOUTUBE_ID_2, "Разбор сезона и ожидания"),
                    previewYoutubeCard(PREVIEW_YOUTUBE_ID_3, "Новости студий и лицензий"),
                    LoadingCard("Следующий ролик уже подгружается"),
                ),
        ),
    )
}

private fun previewReleaseCard(
    id: Int,
    title: String,
    description: String,
): LibriaCard {
    return LibriaCard(
        title = title,
        description = description,
        image = "",
        type = LibriaCard.Type.Release(ReleaseId(id)),
    )
}

private fun previewYoutubeCard(
    id: Int,
    title: String,
): LibriaCard {
    return LibriaCard(
        title = title,
        description = "YouTube • подборка редакции",
        image = "",
        type = LibriaCard.Type.Youtube("https://youtube.com/watch?v=preview-$id"),
    )
}

private const val PREVIEW_MAIN_SECTION_ID = 1L
private const val PREVIEW_FAVORITES_SECTION_ID = 2L
private const val PREVIEW_SCHEDULE_SECTION_ID = 3L
private const val PREVIEW_YOUTUBE_SECTION_ID = 4L

private const val PREVIEW_RELEASE_ID_1 = 1001
private const val PREVIEW_RELEASE_ID_2 = 1002
private const val PREVIEW_RELEASE_ID_3 = 1003
private const val PREVIEW_RELEASE_ID_4 = 1004
private const val PREVIEW_FAVORITE_ID_1 = 2001
private const val PREVIEW_FAVORITE_ID_2 = 2002
private const val PREVIEW_FAVORITE_ID_3 = 2003
private const val PREVIEW_FAVORITE_ID_4 = 2004
private const val PREVIEW_SCHEDULE_ID_1 = 3001
private const val PREVIEW_SCHEDULE_ID_2 = 3002
private const val PREVIEW_SCHEDULE_ID_3 = 3003
private const val PREVIEW_YOUTUBE_ID_1 = 4001
private const val PREVIEW_YOUTUBE_ID_2 = 4002
private const val PREVIEW_YOUTUBE_ID_3 = 4003
