package ru.radiationx.anilibria.screen.search

import kotlinx.coroutines.flow.MutableStateFlow
import ru.radiationx.anilibria.screen.LifecycleViewModel

abstract class BaseSearchValuesViewModel(
    argExtra: SearchValuesExtra,
) : LifecycleViewModel() {

    val progressState = MutableStateFlow(false)
    val valuesData = MutableStateFlow<List<String>>(emptyList())
    val checkedIndicesData = MutableStateFlow<List<Pair<Int, Boolean>>>(emptyList())
    val selectedIndex = MutableStateFlow(-1)

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
                this.selectedIndex.value = selectedIndex
            }
        }
    }

    protected fun updateChecked() {
        checkedIndicesData.value =
            currentValues.mapIndexed { index, item -> Pair(index, checkedValues.contains(item)) }
    }
}
