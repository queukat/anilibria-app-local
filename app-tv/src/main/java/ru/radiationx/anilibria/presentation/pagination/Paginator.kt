package ru.radiationx.anilibria.presentation.pagination

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Paginator<T>(
    private val scope: CoroutineScope,
    private val loader: suspend (page: Int) -> List<T>,
    private val hasMorePolicy: (newItems: List<T>, allItems: List<T>) -> Boolean,
    private val firstPage: Int = 1,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val loadedItems = mutableListOf<T>()
    private var currentPage = firstPage - 1
    private var failedPage: Int? = null
    private var requestJob: Job? = null

    private val _state = MutableStateFlow(PaginatorState<T>())
    val state: StateFlow<PaginatorState<T>> = _state.asStateFlow()

    fun onEvent(event: PaginatorEvent) {
        when (event) {
            PaginatorEvent.Refresh -> requestPage(page = firstPage, clearBeforeLoad = true)
            PaginatorEvent.LoadNextPage -> {
                if (!canRequestMore()) return
                val page = if (currentPage >= firstPage) currentPage + 1 else firstPage
                requestPage(page = page, clearBeforeLoad = false)
            }
            PaginatorEvent.Retry -> {
                val page = failedPage
                    ?: if (currentPage >= firstPage) currentPage else firstPage
                requestPage(page = page, clearBeforeLoad = page == firstPage)
            }
        }
    }

    fun cancel() {
        requestJob?.cancel()
    }

    fun clear() {
        cancel()
        loadedItems.clear()
        currentPage = firstPage - 1
        failedPage = null
        _state.value = PaginatorState()
    }

    private fun canRequestMore(): Boolean {
        if (requestJob?.isActive == true) return false
        return state.value.canLoadMore || loadedItems.isEmpty()
    }

    private fun requestPage(page: Int, clearBeforeLoad: Boolean) {
        if (requestJob?.isActive == true) return

        val previousCanLoadMore = _state.value.canLoadMore
        val currentItems = if (clearBeforeLoad) {
            emptyList()
        } else {
            loadedItems.toList()
        }

        _state.value = PaginatorState(
            items = currentItems,
            isLoading = true,
            canLoadMore = previousCanLoadMore,
            error = null,
            currentPage = if (currentPage >= firstPage) currentPage else null,
            failedPage = null,
        )

        requestJob = scope.launch {
            runCatching {
                withContext(dispatcher) { loader(page) }
            }.onSuccess { newItems ->
                if (clearBeforeLoad) {
                    loadedItems.clear()
                }
                currentPage = page
                loadedItems.addAll(newItems)
                failedPage = null

                _state.value = PaginatorState(
                    items = loadedItems.toList(),
                    isLoading = false,
                    canLoadMore = hasMorePolicy(newItems, loadedItems),
                    error = null,
                    currentPage = currentPage,
                    failedPage = null,
                )
            }.onFailure { error ->
                failedPage = page
                _state.value = PaginatorState(
                    items = loadedItems.toList(),
                    isLoading = false,
                    canLoadMore = previousCanLoadMore,
                    error = error,
                    currentPage = if (currentPage >= firstPage) currentPage else null,
                    failedPage = failedPage,
                )
            }
        }
    }
}
