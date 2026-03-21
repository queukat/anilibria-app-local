package ru.radiationx.anilibria.screen.suggestions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.GradientBackgroundManager
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.extension.applyCard
import ru.radiationx.anilibria.ui.compose.ProvideGradientBackground
import ru.radiationx.quill.installModules
import ru.radiationx.quill.quillModule
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo
import java.util.Locale

class SuggestionsFragment : Fragment() {

    private val backgroundManager by lazy { GradientBackgroundManager(requireActivity()) }

    private val rowsViewModel by viewModel<SuggestionsRowsViewModel>()
    private val resultViewModel by viewModel<SuggestionsResultViewModel>()
    private val recommendsViewModel by viewModel<SuggestionsRecommendsViewModel>()

    private var queryState by mutableStateOf("")
    private var rowOrderState by mutableStateOf(listOf(SuggestionsRowsViewModel.RECOMMENDS_ROW_ID))
    private var progressState by mutableStateOf(false)
    private var resultCardsState by mutableStateOf<List<CardItem>>(emptyList())
    private var recommendsCardsState by mutableStateOf<List<CardItem>>(listOf(LoadingCard("Загрузка...")))
    private var focusRequestToken by mutableIntStateOf(1)
    private var voiceSearchAvailable by mutableStateOf(false)

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        val voiceQuery = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        if (voiceQuery.isNotBlank()) {
            handleQueryChange(voiceQuery)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installModules(quillModule {
            single<SuggestionsController>()
        })
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            voiceSearchAvailable = isVoiceSearchAvailable()
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focusRequestToken++
                }
            }
            setContent {
                ProvideGradientBackground(backgroundManager) {
                    SuggestionsScreen(
                        query = queryState,
                        sections = buildSections(),
                        progressVisible = progressState,
                        voiceSearchAvailable = voiceSearchAvailable,
                        focusRequestToken = focusRequestToken,
                        onQueryChange = ::handleQueryChange,
                        onVoiceSearchClick = ::launchVoiceSearch,
                        onItemClick = ::handleItemClick,
                        onItemFocused = { item ->
                            backgroundManager.applyCard(item)
                        },
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backgroundManager.clearGradient()

        viewLifecycleOwner.lifecycle.addObserver(rowsViewModel)
        viewLifecycleOwner.lifecycle.addObserver(resultViewModel)
        viewLifecycleOwner.lifecycle.addObserver(recommendsViewModel)

        subscribeTo(rowsViewModel.rowListData) {
            rowOrderState = it
        }

        subscribeTo(resultViewModel.progressState) {
            progressState = it
        }

        subscribeTo(resultViewModel.resultData) {
            resultCardsState = it
        }

        subscribeTo(recommendsViewModel.cardsData) {
            recommendsCardsState = it
        }
    }

    override fun onDestroyView() {
        backgroundManager.clearGradient()
        super.onDestroyView()
    }

    private fun handleQueryChange(query: String) {
        queryState = query
        resultViewModel.onQueryChange(query)
    }

    private fun launchVoiceSearch() {
        if (!voiceSearchAvailable) {
            Toast.makeText(requireContext(), "Голосовой ввод недоступен", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            voiceSearchLauncher.launch(createVoiceSearchIntent())
        } catch (_: ActivityNotFoundException) {
            voiceSearchAvailable = false
            Toast.makeText(requireContext(), "Голосовой ввод недоступен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isVoiceSearchAvailable(): Boolean {
        return createVoiceSearchIntent().resolveActivity(requireContext().packageManager) != null
    }

    private fun createVoiceSearchIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите, что искать")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
    }

    private fun buildSections(): List<SuggestionsSectionUiModel> {
        return rowOrderState.mapNotNull { rowId ->
            when (rowId) {
                SuggestionsRowsViewModel.RESULT_ROW_ID -> SuggestionsSectionUiModel(
                    id = rowId,
                    title = "Результат поиска",
                    items = resultCardsState,
                )

                SuggestionsRowsViewModel.RECOMMENDS_ROW_ID -> SuggestionsSectionUiModel(
                    id = rowId,
                    title = recommendsViewModel.defaultTitle,
                    items = recommendsCardsState.ifEmpty { listOf(LoadingCard("Загрузка...")) },
                )

                else -> null
            }
        }
    }

    private fun handleItemClick(
        rowId: Long,
        item: CardItem,
    ) {
        when (rowId) {
            SuggestionsRowsViewModel.RESULT_ROW_ID -> {
                if (item is LibriaCard) {
                    resultViewModel.onCardClick(item)
                }
            }

            SuggestionsRowsViewModel.RECOMMENDS_ROW_ID -> {
                dispatchItemClick(recommendsViewModel, item)
            }
        }
    }

    private fun dispatchItemClick(
        viewModel: BaseCardsViewModel,
        item: CardItem,
    ) {
        when (item) {
            is LibriaCard,
            is LinkCard,
            is LoadingCard,
                -> viewModel.onCardItemClick(item)

            else -> Unit
        }
    }
}
