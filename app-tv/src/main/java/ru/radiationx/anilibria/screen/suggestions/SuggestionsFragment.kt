package ru.radiationx.anilibria.screen.suggestions

import android.os.Bundle
import android.speech.SpeechRecognizer
import android.view.View
import android.view.ViewGroup
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Row
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardDiffCallback
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.handleTvCardClick
import ru.radiationx.anilibria.common.fragment.BaseTvSearchRowsFragment
import ru.radiationx.anilibria.extension.createCardsRowBy
import ru.radiationx.anilibria.ui.presenter.CardPresenterSelector
import ru.radiationx.anilibria.ui.widget.manager.ExternalProgressManager
import ru.radiationx.quill.installModules
import ru.radiationx.quill.quillModule
import ru.radiationx.quill.viewModel
import ru.radiationx.shared.ktx.android.subscribeTo
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class SuggestionsFragment : BaseTvSearchRowsFragment() {

    private val progressManager by lazy { ExternalProgressManager() }

    private val rowsViewModel by viewModel<SuggestionsRowsViewModel>()
    private val resultViewModel by viewModel<SuggestionsResultViewModel>()
    private val recommendsViewModel by viewModel<SuggestionsRecommendsViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Если раньше стояла installModules(ActivityModule(this)) — убрали
        installModules(quillModule {
            single<SuggestionsController>()
        })
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(rowsViewModel)
        viewLifecycleOwner.lifecycle.addObserver(resultViewModel)
        viewLifecycleOwner.lifecycle.addObserver(recommendsViewModel)

        backgroundManager.clearGradient()

        progressManager.rootView = view as ViewGroup
        progressManager.initialDelay = 0L

        subscribeTo(rowsViewModel.emptyResultState) { isEmpty ->
            if (isEmpty) {
                backgroundManager.clearGradient()
            }
        }

        subscribeTo(rowsViewModel.rowListData) { rowList ->
            submitRows(rowList, ::createRowBy)
        }
    }

    override fun onPause() {
        avoidSpeechRecognitinCrash()
        super.onPause()
    }

    private fun getViewModel(rowId: Long): Any? = when (rowId) {
        SuggestionsRowsViewModel.RESULT_ROW_ID -> resultViewModel
        SuggestionsRowsViewModel.RECOMMENDS_ROW_ID -> recommendsViewModel
        else -> null
    }

    private fun createRowBy(rowId: Long): Row {
        return when (rowId) {
            SuggestionsRowsViewModel.RESULT_ROW_ID -> {
                // Результат
                val cardsPresenter = CardPresenterSelector(null)
                val cardsAdapter = ArrayObjectAdapter(cardsPresenter)
                val row = ListRow(rowId, HeaderItem("Результат поиска"), cardsAdapter)

                subscribeTo(resultViewModel.resultData) { list ->
                    cardsAdapter.setItems(list, CardDiffCallback)
                }
                subscribeTo(resultViewModel.progressState) { loading ->
                    if (loading) progressManager.show() else progressManager.hide()
                }
                row
            }

            SuggestionsRowsViewModel.RECOMMENDS_ROW_ID -> {
                // Рекомендации
                createCardsRowBy(rowId, rowsAdapter, recommendsViewModel)
            }

            else -> ListRow(rowId, HeaderItem("???"), ArrayObjectAdapter())
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        resultViewModel.onQueryChange(query.orEmpty())
        return true
    }

    override fun onQueryTextChange(newQuery: String?): Boolean {
        resultViewModel.onQueryChange(newQuery.orEmpty())
        return true
    }

    override fun onRowItemClicked(item: Any?, row: Row) {
        when (val vm = getViewModel((row as ListRow).id)) {
            is BaseCardsViewModel -> vm.handleTvCardClick(item)
            is SuggestionsResultViewModel -> if (item is LibriaCard) {
                vm.onCardClick(item)
            }
        }
    }

    private fun avoidSpeechRecognitinCrash() {
        try {
            val speechRecField =
                SearchSupportFragment::class.java.getDeclaredField("mSpeechRecognizer")
            val searchBarField = SearchSupportFragment::class.java.getDeclaredField("mSearchBar")
            speechRecField.isAccessible = true
            searchBarField.isAccessible = true

            val sr = speechRecField.get(this) ?: return
            val sb = searchBarField.get(this) ?: return
            val setSpeechRecMethod = sb::class.java.getDeclaredMethod(
                "setSpeechRecognizer",
                SpeechRecognizer::class.java
            )
            setSpeechRecMethod.isAccessible = true
            setSpeechRecMethod.invoke(sb, null)

            val destroyMethod = sr::class.java.getDeclaredMethod("destroy")
            destroyMethod.isAccessible = true
            destroyMethod.invoke(sr)

            speechRecField.set(this, null)
        } catch (exception: ReflectiveOperationException) {
            logSpeechCleanupError(exception)
        } catch (exception: SecurityException) {
            logSpeechCleanupError(exception)
        }
    }

    private fun logSpeechCleanupError(error: Exception) {
        if (speechCleanupErrorLogged.compareAndSet(false, true)) {
            Timber.e(error, "Failed to cleanup SpeechRecognizer workaround")
        }
    }

    companion object {
        private val speechCleanupErrorLogged = AtomicBoolean(false)
    }
}
