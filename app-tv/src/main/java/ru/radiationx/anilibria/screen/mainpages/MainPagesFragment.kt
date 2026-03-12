package ru.radiationx.anilibria.screen.mainpages

import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.FragmentContainerView
import androidx.leanback.widget.BaseGridView
import ru.radiationx.anilibria.R
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo
import java.util.ArrayDeque

class MainPagesFragment : Fragment() {

    private val fragmentFactory by lazy { MainPagesFragmentFactory() }
    private val viewModel by viewModel<MainPagesViewModel>()

    private var selectedPageId by mutableLongStateOf(MainPagesFragmentFactory.ids.first())
    private var hasUpdates by mutableStateOf(false)
    private var isRailExpanded by mutableStateOf(false)
    private var railFocusRequestToken by mutableIntStateOf(0)
    private var headerFocusRequestToken by mutableIntStateOf(0)
    private var isHeaderVisible by mutableStateOf(true)

    private var globalFocusListener: ViewTreeObserver.OnGlobalFocusChangeListener? = null
    private var unhandledKeyListener: ViewCompat.OnUnhandledKeyEventListenerCompat? = null
    private var contentHostView: View? = null
    private var listenerHostView: View? = null
    private var listenerViewTreeObserver: ViewTreeObserver? = null
    private var initialFocusRunnable: Runnable? = null
    private var pendingContentFocusRunnable: Runnable? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var shouldRestorePage = false
    private var hasAttachedPage = false

    private val shellItems by lazy(LazyThreadSafetyMode.NONE) {
        MainPagesFragmentFactory.ids.map { pageId ->
            MainShellItem(
                id = pageId,
                title = MainPagesFragmentFactory.variant1.getValue(pageId),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPageId = savedInstanceState?.getLong(KEY_SELECTED_PAGE_ID)
            ?: MainPagesFragmentFactory.ids.first()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MainPagesRoot(
                    items = shellItems,
                    selectedPageId = selectedPageId,
                    hasUpdates = hasUpdates,
                    headerVisible = isHeaderVisible,
                    railExpanded = isRailExpanded,
                    headerFocusRequestToken = headerFocusRequestToken,
                    railFocusRequestToken = railFocusRequestToken,
                    onContentContainerReady = ::onContentContainerReady,
                    onHeaderFocused = {
                        isRailExpanded = false
                        applyHeaderVisibility(true)
                    },
                    onSearchClick = viewModel::onSearchClick,
                    onCatalogClick = viewModel::onCatalogClick,
                    onUpdateClick = viewModel::onAppUpdateClick,
                    onPageFocused = ::showPageFromShell,
                    onRequestHeaderFocus = ::requestHeaderFocus,
                    onRequestContentFocus = ::moveFocusToContent,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)

        listenerHostView = view
        listenerViewTreeObserver = view.viewTreeObserver
        shouldRestorePage = savedInstanceState != null
        hasAttachedPage = false

        installShellFocusBridges()
        installBackHandler()
        applyHeaderVisibility(true)

        subscribeTo(viewModel.hasUpdatesData) {
            hasUpdates = it
        }

        maybeAttachCurrentPage()

        initialFocusRunnable = object : Runnable {
            override fun run() {
                if (!requestCurrentContentFocus()) {
                    listenerHostView?.post(this)
                    return
                }
                isRailExpanded = false
            }
        }
        view.post(initialFocusRunnable)
    }

    override fun onDestroyView() {
        initialFocusRunnable?.also { runnable ->
            listenerHostView?.removeCallbacks(runnable)
        }
        pendingContentFocusRunnable?.also { runnable ->
            listenerHostView?.removeCallbacks(runnable)
        }
        backPressedCallback?.remove()
        globalFocusListener?.also {
            listenerViewTreeObserver
                ?.takeIf(ViewTreeObserver::isAlive)
                ?.removeOnGlobalFocusChangeListener(it)
        }
        unhandledKeyListener?.also {
            listenerHostView?.also { hostView ->
                ViewCompat.removeOnUnhandledKeyEventListener(hostView, it)
            }
        }
        globalFocusListener = null
        unhandledKeyListener = null
        contentHostView = null
        listenerHostView = null
        listenerViewTreeObserver = null
        initialFocusRunnable = null
        pendingContentFocusRunnable = null
        backPressedCallback = null
        shouldRestorePage = false
        hasAttachedPage = false
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_SELECTED_PAGE_ID, selectedPageId)
    }

    private fun onContentContainerReady(containerView: FragmentContainerView) {
        if (contentHostView === containerView) {
            return
        }
        contentHostView = containerView
        maybeAttachCurrentPage()
    }

    private fun maybeAttachCurrentPage() {
        if (contentHostView == null || childFragmentManager.isStateSaved) {
            return
        }
        when {
            shouldRestorePage -> {
                shouldRestorePage = false
                hasAttachedPage = true
                syncVisiblePage(selectedPageId)
            }

            !hasAttachedPage -> {
                hasAttachedPage = true
                showPage(selectedPageId)
            }
        }
    }

    private fun showPageFromShell(pageId: Long) {
        isRailExpanded = true
        applyHeaderVisibility(true)
        showPage(pageId)
    }

    private fun showPage(pageId: Long) {
        selectedPageId = pageId
        val contentViewId = contentHostView?.id ?: return
        if (childFragmentManager.isStateSaved) {
            return
        }
        val targetTag = pageTag(pageId)
        val targetFragment = childFragmentManager.findFragmentByTag(targetTag)
            ?: fragmentFactory.getFragmentById(pageId)
        prepareShellContent(targetFragment)

        childFragmentManager.commitNow {
            setReorderingAllowed(true)
            childFragmentManager.fragments
                .filter { it.id == contentViewId && it !== targetFragment }
                .forEach { hide(it) }

            if (targetFragment.isAdded) {
                show(targetFragment)
            } else {
                add(contentViewId, targetFragment, targetTag)
            }
            setPrimaryNavigationFragment(targetFragment)
        }
    }

    private fun syncVisiblePage(pageId: Long) {
        selectedPageId = pageId
        val contentViewId = contentHostView?.id ?: return
        if (childFragmentManager.isStateSaved) {
            return
        }
        val targetTag = pageTag(pageId)
        val targetFragment = childFragmentManager.findFragmentByTag(targetTag)
            ?: fragmentFactory.getFragmentById(pageId)
        prepareShellContent(targetFragment)

        childFragmentManager.commitNow {
            setReorderingAllowed(true)
            childFragmentManager.fragments
                .filter { it.id == contentViewId && it !== targetFragment }
                .forEach { hide(it) }

            if (targetFragment.isAdded) {
                show(targetFragment)
            } else {
                add(contentViewId, targetFragment, targetTag)
            }
            setPrimaryNavigationFragment(targetFragment)
        }
    }

    private fun currentPageFragment() = childFragmentManager.findFragmentByTag(pageTag(selectedPageId))

    private fun moveFocusToContent(): Boolean {
        val hostView = listenerHostView ?: return false
        isRailExpanded = false
        applyHeaderVisibility(false)
        pendingContentFocusRunnable?.also(hostView::removeCallbacks)
        pendingContentFocusRunnable = Runnable {
            if (requestCurrentContentFocus()) {
                return@Runnable
            }
            hostView.post {
                if (!requestCurrentContentFocus()) {
                    applyHeaderVisibility(true)
                    requestHeaderFocus()
                }
            }
        }
        hostView.post(pendingContentFocusRunnable)
        return true
    }

    private fun requestRailFocus(): Boolean {
        applyHeaderVisibility(true)
        isRailExpanded = true
        railFocusRequestToken++
        return true
    }

    private fun requestCurrentContentFocus(): Boolean {
        childFragmentManager.executePendingTransactions()
        val shellFragment = currentPageFragment() as? MainShellContentFragment
        if (shellFragment?.requestContentFocus() == true) {
            return true
        }
        val fragmentView = currentPageFragment()?.view ?: return false
        val preferredTarget = findPreferredContentFocusTarget(fragmentView)
        return when {
            preferredTarget?.requestFocus() == true -> true
            fragmentView.requestFocus() -> true
            else -> requestFocusInChildren(fragmentView)
        }
    }

    private fun installShellFocusBridges() {
        val hostView = listenerHostView ?: return
        val viewTreeObserver = listenerViewTreeObserver ?: return

        globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            val contentView = contentHostView ?: return@OnGlobalFocusChangeListener
            if (newFocus != null && isDescendant(contentView, newFocus)) {
                isRailExpanded = false
            }
        }.also {
            viewTreeObserver.addOnGlobalFocusChangeListener(it)
        }

        unhandledKeyListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
            if (
                event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                shouldOpenRailFromLeftKey()
            ) {
                return@OnUnhandledKeyEventListenerCompat requestRailFocus()
            }
            false
        }.also {
            ViewCompat.addOnUnhandledKeyEventListener(hostView, it)
        }
    }

    private fun installBackHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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

    private fun isDescendant(parent: View, target: View): Boolean {
        var current: View? = target
        while (current != null) {
            if (current === parent) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    private fun requestFocusInChildren(view: View): Boolean {
        if (view.requestFocus()) {
            return true
        }
        if (view !is ViewGroup) {
            return false
        }
        for (index in 0 until view.childCount) {
            if (requestFocusInChildren(view.getChildAt(index))) {
                return true
            }
        }
        return false
    }

    private fun findPreferredContentFocusTarget(root: View): View? {
        root.findFocus()
            ?.takeIf { isDescendant(root, it) && it !is ComposeView }
            ?.also { return it }

        val queue = ArrayDeque<View>()
        queue.add(root)
        var fallback: View? = null

        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view.visibility != View.VISIBLE || !view.isShown) {
                continue
            }
            if (view is BaseGridView && view.isFocusable) {
                return view
            }
            if (fallback == null && view.isFocusable && view !is ComposeView) {
                fallback = view
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.addLast(view.getChildAt(index))
                }
            }
        }

        return fallback
    }

    private fun shouldOpenRailFromLeftKey(): Boolean {
        if (isRailExpanded) {
            return false
        }
        val contentView = contentHostView ?: return false
        val focusedView = listenerHostView?.findFocus() ?: return false
        if (!isDescendant(contentView, focusedView)) {
            return false
        }
        return isFocusedAtContentLeftEdge(focusedView, contentView)
    }

    private fun isFocusedAtContentLeftEdge(
        focusedView: View,
        contentView: View,
    ): Boolean {
        findOwningGridView(focusedView, contentView)?.let { gridView ->
            val selectedPosition = gridView.selectedPosition
            if (selectedPosition >= 0) {
                return !gridView.hasPreviousViewInSameRow(selectedPosition)
            }
        }

        val focusedRect = Rect()
        val contentRect = Rect()
        if (
            !focusedView.getGlobalVisibleRect(focusedRect) ||
            !contentView.getGlobalVisibleRect(contentRect)
        ) {
            return false
        }
        val thresholdPx = (resources.displayMetrics.density * 32).toInt()
        return focusedRect.left <= contentRect.left + thresholdPx
    }

    private fun findOwningGridView(
        focusedView: View,
        contentView: View,
    ): BaseGridView? {
        var current: View? = focusedView
        while (current != null) {
            if (current is BaseGridView) {
                return current
            }
            if (current === contentView) {
                break
            }
            current = current.parent as? View
        }
        return null
    }

    private fun pageTag(pageId: Long) = "main-page-$pageId"

    private fun requestHeaderFocus(): Boolean {
        applyHeaderVisibility(true)
        headerFocusRequestToken++
        return true
    }

    private fun prepareShellContent(fragment: Fragment) {
        if (fragment is MainShellContentFragment) {
            fragment.onRequestRailFocus = ::requestRailFocus
            fragment.onContentMovedDown = {
                applyHeaderVisibility(false)
            }
            fragment.onContentMovedUp = {
                applyHeaderVisibility(true)
            }
            fragment.onRequestHeaderFocus = ::requestHeaderFocus
        }
    }

    private companion object {
        const val KEY_SELECTED_PAGE_ID = "selected_page_id"
    }
}
