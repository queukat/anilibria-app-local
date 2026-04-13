package ru.radiationx.anilibria.screen.mainpages

import org.junit.Assert.assertEquals
import org.junit.Test

class MainShellPageSectionsStateTest {
    @Test
    fun updateRowOrder_reordersVisibleSections() {
        val state =
            MainShellPageSectionsState(
                initialOrder = listOf(1L, 2L, 3L),
                initialSections =
                    linkedMapOf(
                        1L to TestSection("one"),
                        2L to TestSection("two"),
                        3L to TestSection("three"),
                    ),
            )

        state.updateRowOrder(listOf(3L, 1L))

        assertEquals(
            listOf(TestSection("three"), TestSection("one")),
            state.orderedSections,
        )
    }

    @Test
    fun updateSection_updatesOnlyTargetSection() {
        val state =
            MainShellPageSectionsState(
                initialOrder = listOf(1L, 2L),
                initialSections =
                    linkedMapOf(
                        1L to TestSection("one"),
                        2L to TestSection("two"),
                    ),
            )

        state.updateSection(2L) { section ->
            section.copy(title = "updated")
        }

        assertEquals(
            listOf(TestSection("one"), TestSection("updated")),
            state.orderedSections,
        )
    }

    @Test
    fun updateSection_ignoresUnknownRowId() {
        val state =
            MainShellPageSectionsState(
                initialOrder = listOf(1L),
                initialSections = linkedMapOf(1L to TestSection("one")),
            )

        state.updateSection(99L) { section ->
            section.copy(title = "updated")
        }

        assertEquals(listOf(TestSection("one")), state.orderedSections)
    }

    private data class TestSection(
        val title: String,
    )
}
