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

class TvCardsPaginator<T>(
    private val scope: CoroutineScope,
    private val firstPage: Int = 1,
    private val dispatcherProvider: () -> CoroutineDispatcher = { Dispatchers.IO },
    private val loadPage: suspend (Int, TvPagingState<T>) -> TvPagingLoadResult<T>,
    private val onStateChanged: (TvPagingState<T>) -> Unit = {},
) {
    private val _state = MutableStateFlow(TvPagingState<T>())
    val state: StateFlow<TvPagingState<T>> = _state.asStateFlow()

    private var requestJob: Job? = null

    fun refresh(showProgress: Boolean) {
        requestPage(firstPage, showProgress)
    }

    fun append(showProgress: Boolean) {
        val currentState = state.value
        if (!currentState.canLoadMore) {
            return
        }
        val nextPage = (currentState.currentPage ?: (firstPage - 1)) + 1
        requestPage(nextPage, showProgress)
    }

    fun retry(showProgress: Boolean) {
        val currentState = state.value
        val retryPage = currentState.failedPage ?: currentState.currentPage ?: firstPage
        requestPage(retryPage, showProgress)
    }

    fun replaceItems(items: List<T>) {
        updateState { current ->
            current.copy(items = items)
        }
    }

    private fun requestPage(
        requestPage: Int,
        showProgress: Boolean,
    ) {
        if (requestJob?.isActive == true) {
            return
        }
        requestJob =
            scope.launch {
                if (showProgress) {
                    updateState { current ->
                        current.copy(
                            isLoading = true,
                            error = null,
                            failedPage = null,
                        )
                    }
                }

                val previousState = state.value
                runCatching {
                    withContext(dispatcherProvider()) {
                        loadPage(requestPage, previousState)
                    }
                }.onSuccess { result ->
                    val mergedItems =
                        result.mergedItems ?: if (requestPage == firstPage) {
                            result.pageItems
                        } else {
                            previousState.items + result.pageItems
                        }
                    updateState {
                        it.copy(
                            items = mergedItems,
                            isLoading = false,
                            canLoadMore = result.canLoadMore,
                            error = null,
                            currentPage = result.appliedPage ?: requestPage,
                            failedPage = null,
                        )
                    }
                }.onFailure { error ->
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = TvPagingError(page = requestPage, cause = error),
                            failedPage = requestPage,
                        )
                    }
                }
            }
    }

    private fun updateState(update: (TvPagingState<T>) -> TvPagingState<T>) {
        val nextState = update(_state.value)
        _state.value = nextState
        onStateChanged(nextState)
    }
}
