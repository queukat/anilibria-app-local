package ru.radiationx.anilibria.screen.mainpages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.anilibria.common.TvStartupTrace
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.screen.main.MainPageContent
import ru.radiationx.anilibria.screen.profile.ProfilePageContent
import ru.radiationx.anilibria.screen.watching.WatchingFavoritesPageContent
import ru.radiationx.anilibria.screen.watching.WatchingPageContent
import ru.radiationx.anilibria.ui.compose.ProvideGradientBackground
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class MainPagesFragment : Fragment() {

    private val backgroundManager by lazy(LazyThreadSafetyMode.NONE) {
        GradientBackgroundManager(requireActivity())
    }
    private val viewModel by viewModel<MainPagesViewModel>()

    private val pageContentFactories by lazy(LazyThreadSafetyMode.NONE) {
        mapOf<Long, () -> MainShellPageContent>(
            MainPagesSpec.ID_MAIN to { MainPageContent(this, backgroundManager) },
            MainPagesSpec.ID_MY to { WatchingPageContent(this, backgroundManager) },
            MainPagesSpec.ID_FAVORITES to { WatchingFavoritesPageContent(this, backgroundManager) },
            MainPagesSpec.ID_PROFILE to { ProfilePageContent(this, backgroundManager) },
        )
    }
    private val pageContents = mutableMapOf<Long, MainShellPageContent>()
    private val boundPageIds = mutableSetOf<Long>()

    private var selectedPageId by mutableLongStateOf(MainPagesSpec.ids.first())
    private var hasUpdates by mutableStateOf(false)
    private var isRailExpanded by mutableStateOf(false)
    private var railFocusRequestToken by mutableIntStateOf(0)
    private var headerFocusRequestToken by mutableIntStateOf(0)
    private var isHeaderVisible by mutableStateOf(true)
    private var preferredHeaderAction by mutableStateOf(MainHeaderAction.Search)

    private var listenerHostView: View? = null
    private var initialFocusRunnable: Runnable? = null
    private var pendingContentFocusRunnable: Runnable? = null
    private var backPressedCallback: OnBackPressedCallback? = null

    private val shellItems by lazy(LazyThreadSafetyMode.NONE) {
        MainPagesSpec.ids.map { pageId ->
            MainShellItem(
                id = pageId,
                title = MainPagesSpec.titles.getValue(pageId),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPageId = savedInstanceState?.getLong(KEY_SELECTED_PAGE_ID)
            ?: MainPagesSpec.ids.first()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ensurePageContent(selectedPageId)
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ProvideGradientBackground(backgroundManager) {
                    MainPagesRoot(
                        items = shellItems,
                        selectedPageId = selectedPageId,
                        hasUpdates = hasUpdates,
                        headerVisible = isHeaderVisible,
                        railExpanded = isRailExpanded,
                        preferredHeaderAction = preferredHeaderAction,
                        headerFocusRequestToken = headerFocusRequestToken,
                        railFocusRequestToken = railFocusRequestToken,
                        onHeaderFocused = { action ->
                            preferredHeaderAction = action
                            isRailExpanded = false
                            applyHeaderVisibility(true)
                        },
                        onSearchClick = viewModel::onSearchClick,
                        onCatalogClick = viewModel::onCatalogClick,
                        onUpdateClick = viewModel::onAppUpdateClick,
                        onPageFocused = ::showPageFromShell,
                        onRequestHeaderFocus = ::requestHeaderFocus,
                        onRequestContentFocus = ::moveFocusToContent,
                    ) {
                        MainPagesContentHost(
                            selectedPageId = selectedPageId,
                            resolvePageContent = ::requirePageContent,
                            callbacks = buildShellCallbacks(),
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TvStartupTrace.markOnce("main_pages_view_created")

        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        bindPageContentIfNeeded(selectedPageId)

        listenerHostView = view
        installBackHandler()
        applyHeaderVisibility(true)

        subscribeTo(viewModel.hasUpdatesData) {
            hasUpdates = it
        }

        currentPageContent()?.onSelected()

        initialFocusRunnable = object : Runnable {
            override fun run() {
                if (isRailExpanded) {
                    return
                }
                if (!requestCurrentContentFocus()) {
                    listenerHostView?.post(this)
                    return
                }
                isRailExpanded = false
            }
        }
        view.post(initialFocusRunnable)
    }

    override fun onResume() {
        super.onResume()
        currentPageContent()?.onSelected()
        val hostView = listenerHostView ?: return
        hostView.post {
            if (!isRailExpanded) {
                requestCurrentContentFocus()
            }
        }
    }

    override fun onDestroyView() {
        initialFocusRunnable?.also { runnable ->
            listenerHostView?.removeCallbacks(runnable)
        }
        pendingContentFocusRunnable?.also { runnable ->
            listenerHostView?.removeCallbacks(runnable)
        }
        backPressedCallback?.remove()
        listenerHostView = null
        initialFocusRunnable = null
        pendingContentFocusRunnable = null
        backPressedCallback = null
        boundPageIds.clear()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_SELECTED_PAGE_ID, selectedPageId)
    }

    private fun showPageFromShell(pageId: Long) {
        cancelPendingContentFocusRequests()
        isRailExpanded = true
        applyHeaderVisibility(true)
        showPage(pageId)
    }

    private fun showPage(pageId: Long) {
        ensurePageContent(pageId)
        bindPageContentIfNeeded(pageId)
        selectedPageId = pageId
        currentPageContent()?.onSelected()
    }

    private fun currentPageContent() = pageContents[selectedPageId]

    private fun ensurePageContent(pageId: Long): MainShellPageContent {
        return pageContents.getOrPut(pageId) {
            pageContentFactories.getValue(pageId).invoke()
        }
    }

    private fun requirePageContent(pageId: Long): MainShellPageContent {
        return pageContents[pageId] ?: error("Page content $pageId must be created before render")
    }

    private fun bindPageContentIfNeeded(pageId: Long) {
        if (boundPageIds.add(pageId)) {
            ensurePageContent(pageId).bind(viewLifecycleOwner)
        }
    }

    private fun moveFocusToContent(): Boolean {
        val hostView = listenerHostView ?: return false
        cancelPendingContentFocusRequests()
        isRailExpanded = false
        applyHeaderVisibility(false)
        pendingContentFocusRunnable = Runnable {
            if (requestCurrentContentFocus()) {
                pendingContentFocusRunnable = null
                return@Runnable
            }
            hostView.post {
                if (!requestCurrentContentFocus()) {
                    applyHeaderVisibility(true)
                    requestHeaderFocus()
                } else {
                    pendingContentFocusRunnable = null
                }
            }
        }
        hostView.post(pendingContentFocusRunnable)
        return true
    }

    private fun requestRailFocus(): Boolean {
        cancelPendingContentFocusRequests()
        applyHeaderVisibility(true)
        isRailExpanded = true
        railFocusRequestToken++
        return true
    }

    private fun requestCurrentContentFocus(): Boolean {
        val focused = currentPageContent()?.requestContentFocus() == true
        if (focused && !isRailExpanded) {
            applyHeaderVisibility(false)
        }
        return focused
    }

    private fun installBackHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPageContent()?.onBackPressed() == true) {
                    return
                }
                if (!isRailExpanded) {
                    requestRailFocus()
                    return
                }
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }.also {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
        }
    }

    private fun applyHeaderVisibility(visible: Boolean) {
        isHeaderVisible = visible
    }

    private fun requestHeaderFocus(): Boolean {
        cancelPendingContentFocusRequests()
        applyHeaderVisibility(true)
        headerFocusRequestToken++
        return true
    }

    private fun cancelPendingContentFocusRequests() {
        val hostView = listenerHostView ?: return
        initialFocusRunnable?.also(hostView::removeCallbacks)
        pendingContentFocusRunnable?.also(hostView::removeCallbacks)
        pendingContentFocusRunnable = null
    }

    private fun buildShellCallbacks(): MainShellCallbacks {
        return MainShellCallbacks(
            onRequestRailFocus = ::requestRailFocus,
            onContentMovedDown = {
                if (!isRailExpanded) {
                    applyHeaderVisibility(false)
                }
            },
            onContentMovedUp = {
                applyHeaderVisibility(true)
            },
            onRequestHeaderFocus = ::requestHeaderFocus,
            contentInteractionsEnabled = !isRailExpanded,
        )
    }

    private companion object {
        const val KEY_SELECTED_PAGE_ID = "selected_page_id"
    }
}

@Composable
private fun MainPagesContentHost(
    selectedPageId: Long,
    resolvePageContent: (Long) -> MainShellPageContent,
    callbacks: MainShellCallbacks,
) {
    val stateHolder = rememberSaveableStateHolder()
    val selectedPage = remember(selectedPageId) { resolvePageContent(selectedPageId) }

    Box(modifier = Modifier.fillMaxSize()) {
        stateHolder.SaveableStateProvider(selectedPageId) {
            selectedPage.Render(callbacks)
        }
    }
}
