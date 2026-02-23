package ru.radiationx.anilibria.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.radiationx.anilibria.screen.LifecycleViewModel

abstract class BaseRowsViewModel : LifecycleViewModel() {

    protected val _rowListData = MutableStateFlow<List<Long>>(emptyList())
    val rowListData: StateFlow<List<Long>> = _rowListData.asStateFlow()

    protected abstract val rowIds: List<Long>

    protected abstract val availableRows: MutableSet<Long>

    override fun onCreate() {
        super.onCreate()
        updateRows()
    }

    protected fun updateAvailableRow(rowId: Long, available: Boolean) {
        if (available) {
            availableRows.add(rowId)
        } else {
            availableRows.remove(rowId)
        }
        updateRows()
    }

    private fun updateRows() {
        _rowListData.value = getRows()
    }

    private fun getRows(): List<Long> =
        rowIds.toMutableList().filter { availableRows.contains(it) }

}
