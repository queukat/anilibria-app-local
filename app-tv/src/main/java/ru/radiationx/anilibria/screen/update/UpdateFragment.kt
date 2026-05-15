package ru.radiationx.anilibria.screen.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.ui.compose.ProvideGradientBackground
import ru.radiationx.data.entity.domain.updater.UpdateData
import ru.radiationx.quill.inject
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo

class UpdateFragment : Fragment() {
    private val backgroundManager by inject<GradientBackgroundManager>()
    private val viewModel by viewModel<UpdateViewModel>()

    private var updateDataState by mutableStateOf<UpdateData?>(null)
    private var initialLoadingState by mutableStateOf(true)
    private var downloadVisibleState by mutableStateOf(false)
    private var downloadProgressState by mutableIntStateOf(0)
    private var sourceChooserVisibleState by mutableStateOf(false)
    private var focusRequestToken by mutableIntStateOf(1)
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
                    if (hasFocus) {
                        focusRequestToken++
                    }
                }
            setContent {
                ProvideGradientBackground(backgroundManager) {
                    UpdateScreen(
                        state =
                            UpdateScreenState(
                                updateData = updateDataState,
                                isInitialLoading = initialLoadingState,
                                isDownloading = downloadVisibleState,
                                downloadProgress = downloadProgressState,
                                isSourceChooserVisible = sourceChooserVisibleState,
                                focusRequestToken = focusRequestToken,
                            ),
                        onActionClick = viewModel::onActionClick,
                        onSourceSelected = viewModel::onSourceSelected,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        focusRequestToken++
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        backgroundManager.clearGradient()
        backPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (sourceChooserVisibleState) {
                        viewModel.dismissSourceChooser()
                        return
                    }
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }.also {
                requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, it)
            }

        subscribeTo(viewModel.updateData) {
            updateDataState = it
        }

        subscribeTo(viewModel.downloadProgressShowState) {
            downloadVisibleState = it
            if (!it) {
                focusRequestToken++
            }
        }

        subscribeTo(viewModel.downloadProgressData) {
            downloadProgressState = it
        }

        subscribeTo(viewModel.sourceChooserVisible) {
            sourceChooserVisibleState = it
            if (!it) {
                focusRequestToken++
            }
        }

        subscribeTo(viewModel.progressState) {
            initialLoadingState = it
            if (!it) {
                focusRequestToken++
            }
        }

        subscribeTo(viewModel.errorMessages) { message ->
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        backPressedCallback?.remove()
        backPressedCallback = null
        super.onDestroyView()
    }
}
