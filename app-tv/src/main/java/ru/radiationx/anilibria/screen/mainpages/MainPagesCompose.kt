package ru.radiationx.anilibria.screen.mainpages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.main.MainContentRestoreState
import ru.radiationx.anilibria.screen.main.MainScreen
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.screen.watching.WatchingFocusableSurface
import ru.radiationx.anilibria.screen.watching.rememberWatchingPalette
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

@Composable
internal fun MainPagesRoot(
    items: List<MainShellItem>,
    selectedPageId: Long,
    hasUpdates: Boolean,
    headerVisible: Boolean,
    railExpanded: Boolean,
    preferredHeaderAction: MainHeaderAction,
    headerFocusRequestToken: Int,
    railFocusRequestToken: Int,
    onHeaderFocused: (MainHeaderAction) -> Unit,
    onSearchClick: () -> Unit,
    onCatalogClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onPageFocused: (Long) -> Unit,
    onRequestHeaderFocus: () -> Boolean,
    onRequestContentFocus: () -> Boolean,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val palette = rememberWatchingPalette()
    val headerHeight = dimensionResource(R.dimen.main_pages_header_height)
    val headerSpacing = dimensionResource(R.dimen.main_pages_header_spacing)
    val railWidth = dimensionResource(R.dimen.main_pages_rail_width)
    val shellTopOffset = if (headerVisible) headerHeight + headerSpacing else 0.dp
    val contentTopOffset by animateDpAsState(
        targetValue = shellTopOffset,
        label = "mainPagesContentOffset",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .tvAppBackground(palette, glowAlpha = 0.22f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentTopOffset),
        ) {
            content()
        }

        AnimatedVisibility(
            visible = headerVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(headerHeight),
        ) {
            MainPagesHeader(
                selectedPageTitle = MainPagesSpec.titles.getValue(selectedPageId),
                hasUpdates = hasUpdates,
                preferredAction = preferredHeaderAction,
                headerFocusRequestToken = headerFocusRequestToken,
                onSearchClick = onSearchClick,
                onCatalogClick = onCatalogClick,
                onUpdateClick = onUpdateClick,
                onRequestContentFocus = onRequestContentFocus,
                onFocused = onHeaderFocused,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(railWidth)
                .clipToBounds()
                .padding(top = shellTopOffset),
        ) {
            MainPagesShell(
                items = items,
                selectedPageId = selectedPageId,
                expanded = railExpanded,
                railWidth = railWidth,
                railFocusRequestToken = railFocusRequestToken,
                onPageFocused = onPageFocused,
                onRequestHeaderFocus = onRequestHeaderFocus,
                onRequestContentFocus = onRequestContentFocus,
            )
        }
    }
}

@Composable
internal fun MainPagesHeader(
    selectedPageTitle: String,
    hasUpdates: Boolean,
    preferredAction: MainHeaderAction,
    headerFocusRequestToken: Int,
    onSearchClick: () -> Unit,
    onCatalogClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onRequestContentFocus: () -> Boolean,
    onFocused: (MainHeaderAction) -> Unit,
) {
    val palette = rememberWatchingPalette()
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val actionBackground = colorResource(R.color.dark_release_day_btn).copy(alpha = 0.92f)
    val accentColor = colorResource(R.color.dark_colorAccent)

    val searchRequester = remember { FocusRequester() }
    val catalogRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }

    LaunchedEffect(headerFocusRequestToken, hasUpdates, preferredAction) {
        if (headerFocusRequestToken <= 0) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        val preferredRequester = when (preferredAction) {
            MainHeaderAction.Search -> searchRequester
            MainHeaderAction.Catalog -> catalogRequester
            MainHeaderAction.Update -> if (hasUpdates) updateRequester else catalogRequester
        }
        requestFocusSafely(preferredRequester)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvUiDefaults.shellHeaderBrush(palette))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(TvUiDefaults.ShellHeaderPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(accentColor.copy(alpha = 0.20f))
                    .border(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.32f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_anilibria_splash),
                    contentDescription = null,
                    modifier = Modifier.width(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "AniLibria TV",
                    color = secondaryTextColor,
                    fontSize = 13.sp,
                )
                Text(
                    text = selectedPageTitle,
                    color = textColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderActionButton(
                    text = "Поиск",
                    focusRequester = searchRequester,
                    backgroundColor = actionBackground.copy(alpha = 0.72f),
                    borderColor = textColor.copy(alpha = 0.72f),
                    textColor = textColor,
                    onClick = onSearchClick,
                    onDown = onRequestContentFocus,
                    onFocused = { onFocused(MainHeaderAction.Search) },
                )
                HeaderActionButton(
                    text = "Каталог",
                    focusRequester = catalogRequester,
                    backgroundColor = actionBackground,
                    borderColor = textColor.copy(alpha = 0.72f),
                    textColor = textColor,
                    onClick = onCatalogClick,
                    onDown = onRequestContentFocus,
                    onFocused = { onFocused(MainHeaderAction.Catalog) },
                )
                if (hasUpdates) {
                    HeaderActionButton(
                        text = "Обновление",
                        focusRequester = updateRequester,
                        backgroundColor = accentColor.copy(alpha = 0.24f),
                        borderColor = accentColor.copy(alpha = 0.82f),
                        textColor = textColor,
                        onClick = onUpdateClick,
                        onDown = onRequestContentFocus,
                        onFocused = { onFocused(MainHeaderAction.Update) },
                    )
                }
            }
        }

        HorizontalDivider(
            color = textColor.copy(alpha = 0.08f),
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun MainPagesShell(
    items: List<MainShellItem>,
    selectedPageId: Long,
    expanded: Boolean,
    railWidth: Dp,
    railFocusRequestToken: Int,
    onPageFocused: (Long) -> Unit,
    onRequestHeaderFocus: () -> Boolean,
    onRequestContentFocus: () -> Boolean,
) {
    val palette = rememberWatchingPalette()
    val surfaceColor = colorResource(R.color.dark_colorPrimary)
    val accentColor = colorResource(R.color.dark_colorAccent)
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val selectedIndex = items.indexOfFirst { it.id == selectedPageId }.coerceAtLeast(0)
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }

    val panelOffset by animateDpAsState(
        targetValue = if (expanded) 0.dp else -(railWidth + 12.dp),
        label = "mainPagesRailOffset",
    )

    LaunchedEffect(railFocusRequestToken, expanded) {
        if (expanded && railFocusRequestToken > 0) {
            withFrameNanos { }
            requestFocusSafely(requesters.getOrNull(selectedIndex))
        }
    }

    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor.copy(alpha = 0.16f))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = panelOffset.roundToPx(), y = 0) }
            .clip(TvUiDefaults.ShellRailShape)
            .background(TvUiDefaults.shellRailBrush(palette))
            .border(
                width = 1.dp,
                color = textColor.copy(alpha = 0.08f),
                shape = TvUiDefaults.ShellRailShape,
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(42.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.28f),
                            accentColor.copy(alpha = 0.14f),
                            Color.Transparent,
                        )
                    )
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .width(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_anilibria_splash),
                    contentDescription = null,
                    modifier = Modifier.width(18.dp),
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(textColor.copy(alpha = 0.28f))
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(TvUiDefaults.ShellRailPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Разделы",
                color = secondaryTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEachIndexed { index, item ->
                    RailPageButton(
                        text = item.title,
                        enabled = expanded,
                        selected = item.id == selectedPageId,
                        focusRequester = requesters[index],
                        selectedColor = accentColor.copy(alpha = 0.22f),
                        backgroundColor = Color.Transparent,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor,
                        borderColor = accentColor.copy(alpha = 0.85f),
                        onFocused = { onPageFocused(item.id) },
                        onUp = if (index == 0) onRequestHeaderFocus else null,
                        onRight = onRequestContentFocus,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Влево: навигация\nВправо: контент",
                color = secondaryTextColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}


@Composable
private fun HeaderActionButton(
    text: String,
    focusRequester: FocusRequester,
    backgroundColor: Color,
    textColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    onDown: () -> Boolean,
    onFocused: () -> Unit,
) {
    ShellFocusableButton(
        text = text,
        enabled = true,
        backgroundColor = backgroundColor,
        focusedBackgroundColor = backgroundColor.copy(alpha = 1f),
        textColor = textColor,
        borderColor = borderColor,
        onClick = onClick,
        onDown = onDown,
        onFocused = onFocused,
        modifier = Modifier,
        focusRequester = focusRequester,
        horizontalPadding = 18.dp,
        verticalPadding = 11.dp,
        minWidth = 160.dp,
        textAlign = TextAlign.Center,
    )
}

private fun requestFocusSafely(focusRequester: FocusRequester?): Boolean {
    if (focusRequester == null) {
        return false
    }
    return runCatching {
        focusRequester.requestFocus()
        true
    }.getOrDefault(false)
}

@Composable
private fun RailPageButton(
    text: String,
    enabled: Boolean,
    selected: Boolean,
    focusRequester: FocusRequester,
    selectedColor: Color,
    backgroundColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    borderColor: Color,
    onFocused: () -> Unit,
    onUp: (() -> Boolean)?,
    onRight: () -> Boolean,
) {
    ShellFocusableButton(
        text = text,
        enabled = enabled,
        backgroundColor = if (selected) selectedColor else backgroundColor,
        focusedBackgroundColor = if (selected) {
            selectedColor
        } else {
            secondaryTextColor.copy(alpha = 0.18f)
        },
        textColor = textColor,
        borderColor = borderColor,
        onClick = onFocused,
        onFocused = onFocused,
        onUp = onUp,
        onRight = onRight,
        modifier = Modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        horizontalPadding = 18.dp,
        verticalPadding = 15.dp,
        minWidth = 0.dp,
        selected = selected,
    )
}

@Composable
private fun ShellFocusableButton(
    text: String,
    enabled: Boolean,
    backgroundColor: Color,
    focusedBackgroundColor: Color,
    textColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    minWidth: Dp = 120.dp,
    selected: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
) {
    WatchingFocusableSurface(
        focusRequester = focusRequester ?: FocusRequester.Default,
        enabled = enabled,
        backgroundColor = backgroundColor,
        focusedBackgroundColor = focusedBackgroundColor,
        borderColor = borderColor,
        onClick = onClick,
        modifier = modifier.widthIn(min = minWidth),
        selected = selected,
        selectedBorderColor = borderColor,
        selectedBorderWidth = TvUiDefaults.FocusedBorderWidth,
        onFocused = onFocused,
        onLeft = onLeft,
        onUp = onUp,
        onRight = onRight,
        onDown = onDown,
        paddingValues = androidx.compose.foundation.layout.PaddingValues(
            horizontal = horizontalPadding,
            vertical = verticalPadding,
        ),
        ) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                color = textColor,
                fontSize = 18.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = textAlign,
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
    val shellItems = remember {
        MainPagesSpec.ids.map { pageId ->
            MainShellItem(
                id = pageId,
                title = MainPagesSpec.titles.getValue(pageId),
            )
        }
    }
    val selectedPageId = MainPagesSpec.ID_MAIN
    // Preview should not depend on generated R.dimen fields because layoutlib can lag behind resource stubs.
    val headerHeight = 96.dp
    val headerSpacing = 10.dp
    val railWidth = 264.dp
    val shellTopOffset = headerHeight + headerSpacing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.dark_windowBackground))
    ) {
        MainScreen(
            sections = previewMainSections(),
            focusRequestToken = 0,
            visibilityRestoreToken = 0,
            contentRestoreState = MainContentRestoreState(
                preferredSectionIndex = 0,
                preferredItemIndex = 1,
                preferredItemId = 1002,
            ),
            onItemClick = { _, _ -> },
            onRequestRailFocus = { true },
            onRequestHeaderFocus = { true },
            onContentMovedDown = {},
            onContentMovedUp = {},
            onItemFocused = { _, _, _ -> },
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(headerHeight),
        ) {
            MainPagesHeader(
                selectedPageTitle = MainPagesSpec.titles.getValue(selectedPageId),
                hasUpdates = true,
                preferredAction = MainHeaderAction.Search,
                headerFocusRequestToken = 0,
                onSearchClick = {},
                onCatalogClick = {},
                onUpdateClick = {},
                onRequestContentFocus = { true },
                onFocused = { _ -> },
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(railWidth)
                .padding(top = shellTopOffset),
        ) {
            MainPagesShell(
                items = shellItems,
                selectedPageId = selectedPageId,
                expanded = railExpanded,
                railWidth = railWidth,
                railFocusRequestToken = 0,
                onPageFocused = {},
                onRequestHeaderFocus = { true },
                onRequestContentFocus = { true },
            )
        }
    }
}

private fun previewMainSections(): List<MainSectionUiModel> {
    return listOf(
        MainSectionUiModel(
            id = PREVIEW_MAIN_SECTION_ID,
            title = "Самое актуальное",
            items = listOf(
                previewReleaseCard(PREVIEW_RELEASE_ID_1, "Врата Штейна", "На неделе вышла 7 серия"),
                previewReleaseCard(PREVIEW_RELEASE_ID_2, "Frieren", "Новый эпизод сегодня в 20:00"),
                previewReleaseCard(PREVIEW_RELEASE_ID_3, "Провожающая в последний путь", "Перевод завершен"),
                previewReleaseCard(PREVIEW_RELEASE_ID_4, "Blue Box", "Онгоинг • обновлено 2 часа назад"),
                LinkCard("Открыть весь список"),
            ),
        ),
        MainSectionUiModel(
            id = PREVIEW_FAVORITES_SECTION_ID,
            title = "Обновления в избранном",
            items = listOf(
                previewReleaseCard(PREVIEW_FAVORITE_ID_1, "Solo Leveling", "Добавлена 10 серия"),
                previewReleaseCard(PREVIEW_FAVORITE_ID_2, "Kaiju No. 8", "Вышла новая озвучка"),
                previewReleaseCard(PREVIEW_FAVORITE_ID_3, "Dandadan", "Новый релиз уже доступен"),
                previewReleaseCard(PREVIEW_FAVORITE_ID_4, "Re:Zero", "Следующая серия завтра"),
            ),
        ),
        MainSectionUiModel(
            id = PREVIEW_SCHEDULE_SECTION_ID,
            title = "Ожидается сегодня",
            items = listOf(
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
            title = "Обновления на YouTube",
            items = listOf(
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
