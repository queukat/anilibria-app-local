package ru.radiationx.anilibria.screen.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.github.terrakok.cicerone.Router
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.InfoCard
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.screen.details.other.DetailOtherViewModel
import ru.radiationx.anilibria.screen.main.MainSectionUiModel
import ru.radiationx.anilibria.ui.compose.ProvideGradientBackground
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.quill.QuillExtra
import ru.radiationx.quill.inject
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.getExtraNotNull
import ru.radiationx.shared.ktx.android.putExtra
import ru.radiationx.shared.ktx.android.subscribeTo

data class DetailExtra(
    val id: ReleaseId,
) : QuillExtra

class DetailFragment : Fragment() {
    companion object {
        private const val ARG_ID = "id"

        fun newInstance(releaseId: ReleaseId) =
            DetailFragment().putExtra {
                putParcelable(ARG_ID, releaseId)
            }
    }

    private val argExtra by lazy {
        DetailExtra(id = getExtraNotNull(ARG_ID))
    }

    private val detailsViewModel by viewModel<DetailsViewModel> { argExtra }
    private val router by inject<Router>()
    private val headerViewModel by viewModel<DetailHeaderViewModel> { argExtra }
    private val otherViewModel by viewModel<DetailOtherViewModel> { argExtra }
    private val relatedViewModel by viewModel<DetailRelatedViewModel> { argExtra }
    private val recommendsViewModel by viewModel<DetailRecommendsViewModel> { argExtra }
    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

    private var rowIdsState by mutableStateOf<List<Long>>(emptyList())
    private var detailsState by mutableStateOf<LibriaDetails?>(null)
    private var progressState by mutableStateOf(DetailsState(loadingProgress = true))
    private var relatedCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var relatedTitleState by mutableStateOf("")
    private var recommendsCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var recommendsTitleState by mutableStateOf("")
    private var headerFocusToken by mutableIntStateOf(1)
    private var isHeaderSelected by mutableStateOf(true)
    private var contentRestoreToken by mutableIntStateOf(0)
    private var restoreSectionIndex by mutableIntStateOf(0)
    private var restoreItemIndex by mutableIntStateOf(0)
    private var restoreItemId by mutableIntStateOf(Int.MIN_VALUE)
    private var hasRestoreTarget by mutableStateOf(false)
    private var wasHeaderLoading by mutableStateOf(true)
    private var restoreItemState by mutableStateOf<CardItem?>(null)
    private var allowContentSelectionCapture by mutableStateOf(false)
    private var pendingContentRestoreAfterLoad by mutableStateOf(false)
    private var headerFocusTargetState by mutableStateOf(ReleaseDetailsFocusTarget.StartAction)
    private var overlayState by mutableStateOf<DetailOverlayState?>(null)
    private var backPressedCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            onFocusChangeListener =
                View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && isHeaderSelected) {
                        requestHeaderFocus()
                    }
                }
            setContent {
                ProvideGradientBackground(backgroundManager) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DetailScreen(
                            headerUiState =
                                ReleaseDetailsRowUiState(
                                    details = detailsState,
                                    progressState = progressState,
                                    initialFocusToken = headerFocusToken,
                                    initialFocusTarget = headerFocusTargetState,
                                ),
                            headerCallbacks =
                                ReleaseDetailsCallbacks(
                                    continueClick = {
                                        headerFocusTargetState = ReleaseDetailsFocusTarget.Continue
                                        headerViewModel.onContinueClick()
                                    },
                                    playClick = {
                                        headerFocusTargetState = ReleaseDetailsFocusTarget.Play
                                        headerViewModel.onPlayClick()
                                    },
                                    favoriteClick = {
                                        headerFocusTargetState = ReleaseDetailsFocusTarget.Favorite
                                        headerViewModel.onFavoriteClick()
                                    },
                                    descriptionClick = {
                                        headerFocusTargetState = ReleaseDetailsFocusTarget.Description
                                        headerViewModel.onDescriptionClick()
                                    },
                                    otherClick = {
                                        headerFocusTargetState = ReleaseDetailsFocusTarget.Other
                                        headerViewModel.onOtherClick()
                                    },
                                ),
                            sections = buildSections(),
                            contentRestoreState =
                                DetailContentRestoreState(
                                    focusToken = contentRestoreToken,
                                    preferredSectionIndex = restoreSectionIndex,
                                    preferredItemIndex = restoreItemIndex,
                                    preferredItemId = restoreItemId,
                                ),
                            contentSelectionEnabled = allowContentSelectionCapture,
                            onRequestHeaderFocus = ::requestHeaderFocus,
                            onHeaderFocusSettled = {
                                allowContentSelectionCapture = true
                            },
                            onSectionItemClick = ::handleSectionItemClick,
                            onContentItemFocused = contentFocus@{ sectionIndex, itemIndex, item ->
                                if (!allowContentSelectionCapture) {
                                    return@contentFocus
                                }
                                hasRestoreTarget = true
                                restoreSectionIndex = sectionIndex
                                restoreItemIndex = itemIndex
                                restoreItemId = item.getId()
                                restoreItemState = item
                                isHeaderSelected = false
                            },
                            onBackdropItemFocused = { item -> backgroundManager.applyCard(item) },
                        )

                        overlayState?.let { currentOverlay ->
                            DetailOverlayHost(
                                overlayState = currentOverlay,
                                onDismiss = { dismissOverlay(requestHeaderFocus = true) },
                                onClearHistoryClick = otherViewModel::onClearClick,
                                onMarkAllViewedClick = otherViewModel::onMarkClick,
                                onEpisodeSelected = headerViewModel::onEpisodeSelected,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (overlayState != null) {
            return
        }
        if (hasRestoreTarget && !isHeaderSelected) {
            if (progressState.loadingProgress) {
                pendingContentRestoreAfterLoad = true
            } else {
                requestContentRestore()
            }
        } else {
            requestHeaderFocus()
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        backgroundManager.clearGradient()

        viewLifecycleOwner.lifecycle.addObserver(detailsViewModel)
        viewLifecycleOwner.lifecycle.addObserver(headerViewModel)
        viewLifecycleOwner.lifecycle.addObserver(otherViewModel)
        viewLifecycleOwner.lifecycle.addObserver(relatedViewModel)
        viewLifecycleOwner.lifecycle.addObserver(recommendsViewModel)
        backPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (overlayState != null) {
                        dismissOverlay(requestHeaderFocus = true)
                        return
                    }
                    if (!isHeaderSelected) {
                        requestHeaderFocus()
                        return
                    }
                    router.exit()
                }
            }.also {
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
            }

        relatedTitleState = relatedViewModel.defaultTitle
        recommendsTitleState = recommendsViewModel.defaultTitle

        subscribeTo(detailsViewModel.rowListData) {
            rowIdsState = it
        }
        subscribeTo(headerViewModel.releaseData) {
            detailsState = it
            if (isHeaderSelected && overlayState == null) {
                applyImage(it?.image.orEmpty())
            }
        }
        subscribeTo(headerViewModel.overlayState) {
            overlayState = it
        }
        subscribeTo(headerViewModel.progressState) {
            val loadingFinished = wasHeaderLoading && !it.loadingProgress
            progressState = it
            wasHeaderLoading = it.loadingProgress
            if (loadingFinished) {
                if (pendingContentRestoreAfterLoad && hasRestoreTarget && !isHeaderSelected) {
                    requestContentRestore()
                } else if (overlayState == null) {
                    requestHeaderFocus()
                }
            }
        }
        subscribeTo(otherViewModel.dismissEvents) {
            dismissOverlay(requestHeaderFocus = true)
        }
        subscribeTo(relatedViewModel.rowTitle) {
            relatedTitleState = it
        }
        subscribeTo(relatedViewModel.cardsData) {
            relatedCardsState = it
        }
        subscribeTo(recommendsViewModel.rowTitle) {
            recommendsTitleState = it
        }
        subscribeTo(recommendsViewModel.cardsData) {
            recommendsCardsState = it
        }
    }

    override fun onDestroyView() {
        backPressedCallback?.remove()
        backPressedCallback = null
        backgroundManager.clearGradient()
        super.onDestroyView()
    }

    private fun buildSections(): List<MainSectionUiModel> {
        return rowIdsState.mapNotNull { rowId ->
            when (rowId) {
                DetailsViewModel.RELATED_ROW_ID ->
                    MainSectionUiModel(
                        id = rowId,
                        title = relatedTitleState,
                        items = relatedCardsState,
                    )

                DetailsViewModel.RECOMMENDS_ROW_ID ->
                    MainSectionUiModel(
                        id = rowId,
                        title = recommendsTitleState,
                        items = recommendsCardsState,
                    )

                else -> null
            }
        }
    }

    private fun handleSectionItemClick(
        rowId: Long,
        item: CardItem,
    ) {
        val viewModel =
            when (rowId) {
                DetailsViewModel.RELATED_ROW_ID -> relatedViewModel
                DetailsViewModel.RECOMMENDS_ROW_ID -> recommendsViewModel
                else -> null
            } ?: return

        when (item) {
            is LibriaCard -> viewModel.onLibriaCardClick(item)
            is LinkCard -> viewModel.onLinkCardClick()
            is LoadingCard ->
                if (item.isError) {
                    viewModel.onLoadingCardClick()
                }
            is InfoCard -> Unit
        }
    }

    private fun requestHeaderFocus() {
        pendingContentRestoreAfterLoad = false
        allowContentSelectionCapture = false
        isHeaderSelected = true
        headerFocusToken++
        applyImage(detailsState?.image.orEmpty())
    }

    private fun requestContentRestore() {
        if (!hasRestoreTarget) {
            requestHeaderFocus()
            return
        }
        pendingContentRestoreAfterLoad = false
        allowContentSelectionCapture = true
        isHeaderSelected = false
        contentRestoreToken++
    }

    private fun applyImage(image: String) {
        backgroundManager.applyImage(
            image,
            colorSelector = { null },
        ) { originalColor ->
            val hslColor = FloatArray(3)
            ColorUtils.colorToHSL(originalColor, hslColor)
            hslColor[1] = (hslColor[1] + 0.05f).coerceAtMost(1.0f)
            hslColor[2] = (hslColor[2] + 0.05f).coerceAtMost(1.0f)
            ColorUtils.HSLToColor(hslColor)
        }
    }

    private fun dismissOverlay(requestHeaderFocus: Boolean) {
        headerViewModel.dismissOverlay()
        if (requestHeaderFocus) {
            requestHeaderFocus()
        }
    }
}
