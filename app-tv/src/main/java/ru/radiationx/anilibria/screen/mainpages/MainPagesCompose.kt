package ru.radiationx.anilibria.screen.mainpages

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import ru.radiationx.anilibria.R
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.main.MainContentRestoreState
import ru.radiationx.anilibria.screen.main.MainScreen
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.data.entity.domain.types.ReleaseId

internal data class MainShellItem(
    val id: Long,
    val title: String,
)

@Composable
internal fun MainPagesRoot(
    items: List<MainShellItem>,
    selectedPageId: Long,
    hasUpdates: Boolean,
    headerVisible: Boolean,
    railExpanded: Boolean,
    headerFocusRequestToken: Int,
    railFocusRequestToken: Int,
    onContentContainerReady: (FragmentContainerView) -> Unit,
    onHeaderFocused: () -> Unit,
    onSearchClick: () -> Unit,
    onCatalogClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onPageFocused: (Long) -> Unit,
    onRequestHeaderFocus: () -> Boolean,
    onRequestContentFocus: () -> Boolean,
) {
    val headerHeight = dimensionResource(R.dimen.main_pages_header_height)
    val headerSpacing = dimensionResource(R.dimen.main_pages_header_spacing)
    val railWidth = dimensionResource(R.dimen.main_pages_rail_width)
    val shellTopOffset = if (headerVisible) headerHeight + headerSpacing else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.dark_windowBackground))
    ) {
        AndroidView(
            factory = { context ->
                FragmentContainerView(context).apply {
                    id = R.id.mainPagesContentContainer
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
            },
            update = { containerView ->
                containerView.post {
                    onContentContainerReady(containerView)
                }
            },
            modifier = Modifier
                .fillMaxSize(),
        )

        AnimatedVisibility(
            visible = headerVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(headerHeight),
        ) {
            MainPagesHeader(
                selectedPageTitle = MainPagesFragmentFactory.variant1.getValue(selectedPageId),
                hasUpdates = hasUpdates,
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
    headerFocusRequestToken: Int,
    onSearchClick: () -> Unit,
    onCatalogClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onRequestContentFocus: () -> Boolean,
    onFocused: () -> Unit,
) {
    val surfaceColor = colorResource(R.color.dark_colorPrimary)
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val actionBackground = colorResource(R.color.dark_release_day_btn).copy(alpha = 0.92f)
    val accentColor = colorResource(R.color.dark_colorAccent)

    val searchRequester = remember { FocusRequester() }
    val catalogRequester = remember { FocusRequester() }
    val updateRequester = remember { FocusRequester() }

    LaunchedEffect(headerFocusRequestToken, hasUpdates) {
        if (headerFocusRequestToken <= 0) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        requestFocusSafely(searchRequester)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor.copy(alpha = 0.96f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_anilibria_splash),
                    contentDescription = null,
                    modifier = Modifier.width(20.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "AniLibria",
                    color = secondaryTextColor,
                    fontSize = 12.sp,
                )
                Text(
                    text = selectedPageTitle,
                    color = textColor,
                    fontSize = 20.sp,
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
                    onFocused = onFocused,
                )
                HeaderActionButton(
                    text = "Каталог",
                    focusRequester = catalogRequester,
                    backgroundColor = actionBackground,
                    borderColor = textColor.copy(alpha = 0.72f),
                    textColor = textColor,
                    onClick = onCatalogClick,
                    onDown = onRequestContentFocus,
                    onFocused = onFocused,
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
                        onFocused = onFocused,
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
    val surfaceColor = colorResource(R.color.dark_colorPrimary)
    val accentColor = colorResource(R.color.dark_colorAccent)
    val textColor = colorResource(R.color.dark_textDefault)
    val secondaryTextColor = colorResource(R.color.dark_textSecond)
    val selectedIndex = items.indexOfFirst { it.id == selectedPageId }.coerceAtLeast(0)
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }

    val panelOffset by animateDpAsState(
        targetValue = if (expanded) 0.dp else -railWidth,
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
                .background(Color.Black.copy(alpha = 0.08f))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(x = panelOffset.roundToPx(), y = 0) }
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        surfaceColor.copy(alpha = 0.98f),
                        surfaceColor.copy(alpha = 0.94f),
                        surfaceColor.copy(alpha = 0.9f),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = textColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(40.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.26f),
                            accentColor.copy(alpha = 0.12f),
                            Color.Transparent,
                        )
                    )
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 26.dp)
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
                .padding(start = 20.dp, top = 28.dp, end = 52.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Разделы",
                color = secondaryTextColor,
                fontSize = 13.sp,
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
                fontSize = 12.sp,
                lineHeight = 16.sp,
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
        modifier = Modifier.width(156.dp),
        focusRequester = focusRequester,
        horizontalPadding = 16.dp,
        verticalPadding = 10.dp,
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
        focusedBackgroundColor = if (selected) selectedColor else secondaryTextColor.copy(alpha = 0.12f),
        textColor = textColor,
        borderColor = borderColor,
        onClick = onFocused,
        onFocused = onFocused,
        onUp = onUp,
        onRight = onRight,
        modifier = Modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        horizontalPadding = 16.dp,
        verticalPadding = 14.dp,
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
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .then(
                if (focusRequester != null && enabled) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(24.dp))
            .background(if (isFocused) focusedBackgroundColor else backgroundColor)
            .border(
                width = if (isFocused || selected) 2.dp else 1.dp,
                color = if (isFocused || selected) borderColor else textColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(24.dp),
            )
            .onFocusChanged {
                val nowFocused = it.isFocused
                isFocused = nowFocused
                if (nowFocused) {
                    onFocused?.invoke()
                }
            }
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> onLeft?.invoke() == true
                    Key.DirectionUp -> onUp?.invoke() == true
                    Key.DirectionRight -> onRight?.invoke() == true
                    Key.DirectionDown -> onDown?.invoke() == true
                    else -> false
                }
            }
            .then(if (enabled) Modifier.focusable() else Modifier)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = textColor,
            fontSize = 17.sp,
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
private fun MainPagesHomePreview() {
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
private fun MainPagesHomeRailPreview() {
    MainPagesPreviewScene(railExpanded = true)
}

@Composable
private fun MainPagesPreviewScene(railExpanded: Boolean) {
    val shellItems = remember {
        MainPagesFragmentFactory.ids.map { pageId ->
            MainShellItem(
                id = pageId,
                title = MainPagesFragmentFactory.variant1.getValue(pageId),
            )
        }
    }
    val selectedPageId = MainPagesFragmentFactory.ID_MAIN
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
                selectedPageTitle = MainPagesFragmentFactory.variant1.getValue(selectedPageId),
                hasUpdates = true,
                headerFocusRequestToken = 0,
                onSearchClick = {},
                onCatalogClick = {},
                onUpdateClick = {},
                onRequestContentFocus = { true },
                onFocused = {},
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
            id = 1L,
            title = "Самое актуальное",
            items = listOf(
                previewReleaseCard(1001, "Врата Штейна", "На неделе вышла 7 серия"),
                previewReleaseCard(1002, "Frieren", "Новый эпизод сегодня в 20:00"),
                previewReleaseCard(1003, "Провожающая в последний путь", "Перевод завершен"),
                previewReleaseCard(1004, "Blue Box", "Онгоинг • обновлено 2 часа назад"),
                LinkCard("Открыть весь список"),
            ),
        ),
        MainSectionUiModel(
            id = 2L,
            title = "Обновления в избранном",
            items = listOf(
                previewReleaseCard(2001, "Solo Leveling", "Добавлена 10 серия"),
                previewReleaseCard(2002, "Kaiju No. 8", "Вышла новая озвучка"),
                previewReleaseCard(2003, "Dandadan", "Новый релиз уже доступен"),
                previewReleaseCard(2004, "Re:Zero", "Следующая серия завтра"),
            ),
        ),
        MainSectionUiModel(
            id = 3L,
            title = "Ожидается сегодня",
            items = listOf(
                previewReleaseCard(3001, "Dr. Stone", "Премьера в 18:30"),
                previewReleaseCard(3002, "Wind Breaker", "Сегодня вечером"),
                previewReleaseCard(3003, "Made in Abyss", "Пока без точного времени"),
                InfoCard(
                    title = "Ещё несколько релизов позже вечером",
                    subtitle = "Откройте расписание, чтобы посмотреть весь список на сегодня.",
                ),
            ),
        ),
        MainSectionUiModel(
            id = 4L,
            title = "Обновления на YouTube",
            items = listOf(
                previewYoutubeCard(4001, "Итоги недели AniLibria"),
                previewYoutubeCard(4002, "Разбор сезона и ожидания"),
                previewYoutubeCard(4003, "Новости студий и лицензий"),
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
