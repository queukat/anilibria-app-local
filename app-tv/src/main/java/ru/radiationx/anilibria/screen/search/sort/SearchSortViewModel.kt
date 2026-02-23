package ru.radiationx.anilibria.screen.search.sort

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.search.SearchController
import ru.radiationx.data.entity.domain.search.SearchForm
import javax.inject.Inject

class SearchSortViewModel @Inject constructor(
    argExtra: SearchSortExtra,
    private val searchController: SearchController,
    private val guidedRouter: GuidedRouter,
) : LifecycleViewModel() {

    private val titles = listOf(
        "По популярности",
        "По новизне"
    )

    private val _titlesData = MutableStateFlow<List<String>>(emptyList())
    val titlesData: StateFlow<List<String>> = _titlesData.asStateFlow()
    private val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    init {
        _titlesData.value = titles
        _selectedIndex.value = when (argExtra.sort) {
            SearchForm.Sort.RATING -> 0
            SearchForm.Sort.DATE -> 1
        }
    }

    fun applySort(index: Int) {
        guidedRouter.close()
        val sort = when (index) {
            0 -> SearchForm.Sort.RATING
            1 -> SearchForm.Sort.DATE
            else -> null
        }
        sort?.also {
            searchController.sortEvent.emit(it)
        }
    }
}
