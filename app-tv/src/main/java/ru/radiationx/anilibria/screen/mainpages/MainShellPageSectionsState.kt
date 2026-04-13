package ru.radiationx.anilibria.screen.mainpages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class MainShellPageSectionsState<RowId, Section>(
    initialOrder: List<RowId>,
    initialSections: Map<RowId, Section>,
) {
    private var rowOrderState = initialOrder
    private val sectionStateById = LinkedHashMap<RowId, Section>(initialSections)

    var orderedSections by mutableStateOf(buildOrderedSections(initialOrder))
        private set

    fun updateRowOrder(rowIds: List<RowId>) {
        if (rowOrderState == rowIds) {
            return
        }
        rowOrderState = rowIds
        syncOrderedSections()
    }

    fun updateSection(
        rowId: RowId,
        transform: (Section) -> Section,
    ) {
        val current = sectionStateById[rowId] ?: return
        val updated = transform(current)
        if (updated == current) {
            return
        }
        sectionStateById[rowId] = updated
        syncOrderedSections()
    }

    private fun syncOrderedSections() {
        val sections = buildOrderedSections(rowOrderState)
        if (orderedSections != sections) {
            orderedSections = sections
        }
    }

    private fun buildOrderedSections(rowIds: List<RowId>): List<Section> {
        return rowIds.mapNotNull(sectionStateById::get)
    }
}
