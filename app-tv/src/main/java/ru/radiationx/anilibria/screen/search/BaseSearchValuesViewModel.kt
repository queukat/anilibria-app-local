package ru.radiationx.anilibria.screen.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.radiationx.anilibria.screen.LifecycleViewModel

abstract class BaseSearchValuesViewModel(
    argExtra: SearchValuesExtra,
) : LifecycleViewModel() {

    protected val _progressState = MutableStateFlow(false)
    val progressState: StateFlow<Boolean> = _progressState.asStateFlow()
    protected val _valuesData = MutableStateFlow<List<String>>(emptyList())
    val valuesData: StateFlow<List<String>> = _valuesData.asStateFlow()
    protected val _checkedIndicesData = MutableStateFlow<List<Pair<Int, Boolean>>>(emptyList())
    val checkedIndicesData: StateFlow<List<Pair<Int, Boolean>>> = _checkedIndicesData.asStateFlow()
    protected val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    protected val currentValues = mutableListOf<String>()
    protected val checkedValues = mutableSetOf<String>()

    init {
        checkedValues.addAll(argExtra.values)
        updateChecked()
        updateSelected()
    }

    abstract fun applyValues()

    fun resetSelected() {
        checkedValues.clear()
        updateChecked()
    }

    fun setSelected(index: Int, selected: Boolean) {
        val value = currentValues[index]
        if (selected) {
            checkedValues.add(value)
        } else {
            checkedValues.remove(value)
        }
        updateChecked()
    }

    protected fun updateSelected() {
        if (currentValues.isEmpty() || checkedValues.isEmpty()) {
            return
        }
        val firstCheckedValue = currentValues.firstOrNull { checkedValues.contains(it) }
        firstCheckedValue?.also {
            val selectedIndex = currentValues.indexOf(it)
            if (selectedIndex >= 0) {
                _selectedIndex.value = selectedIndex
            }
        }
    }

    protected fun updateChecked() {
        _checkedIndicesData.value =
            currentValues.mapIndexed { index, item -> Pair(index, checkedValues.contains(item)) }
    }
}
