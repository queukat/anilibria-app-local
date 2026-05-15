package ru.radiationx.anilibria.screen.watching

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.anilibria.common.CardItem
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LinkCard
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.data.entity.domain.types.ReleaseId

class TvSectionFocusCoordinatorTest {
    @Test
    fun viewportTarget_usesTargetRowsVisibleColumnInsteadOfSourceIndex() {
        val targetIndex =
            resolveTvSectionTargetIndexFromViewport(
                items = releaseCards(10),
                visibleItems =
                    listOf(
                        TvSectionVisibleItem(index = 0, offset = 0, size = 100),
                        TvSectionVisibleItem(index = 1, offset = 116, size = 100),
                        TvSectionVisibleItem(index = 2, offset = 232, size = 100),
                    ),
                firstVisibleItemIndex = 0,
                horizontalAnchorPx = 50,
                preferredItemIndex = 8,
            )

        assertEquals(0, targetIndex)
    }

    @Test
    fun viewportTarget_usesNearestVisibleTargetToSourceAnchor() {
        val targetIndex =
            resolveTvSectionTargetIndexFromViewport(
                items = releaseCards(10),
                visibleItems =
                    listOf(
                        TvSectionVisibleItem(index = 4, offset = 0, size = 100),
                        TvSectionVisibleItem(index = 5, offset = 116, size = 100),
                        TvSectionVisibleItem(index = 6, offset = 232, size = 100),
                    ),
                firstVisibleItemIndex = 4,
                horizontalAnchorPx = 166,
                preferredItemIndex = 8,
            )

        assertEquals(5, targetIndex)
    }

    @Test
    fun viewportTarget_fallsBackToTargetRowsFirstVisibleIndex() {
        val targetIndex =
            resolveTvSectionTargetIndexFromViewport(
                items = releaseCards(10),
                visibleItems = emptyList(),
                firstVisibleItemIndex = 3,
                horizontalAnchorPx = 50,
                preferredItemIndex = 8,
            )

        assertEquals(3, targetIndex)
    }

    @Test
    fun viewportTarget_keepsStateOnlySectionsOnTheirActionItem() {
        val targetIndex =
            resolveTvSectionTargetIndexFromViewport(
                items = listOf(LoadingCard("Loading"), LinkCard("Retry")),
                visibleItems =
                    listOf(
                        TvSectionVisibleItem(index = 0, offset = 0, size = 100),
                        TvSectionVisibleItem(index = 1, offset = 116, size = 100),
                    ),
                firstVisibleItemIndex = 0,
                horizontalAnchorPx = 0,
                preferredItemIndex = 8,
            )

        assertEquals(1, targetIndex)
    }

    private fun releaseCards(count: Int): List<CardItem> {
        return List(count) { index ->
            LibriaCard(
                title = "Release $index",
                description = "",
                image = "",
                type = LibriaCard.Type.Release(ReleaseId(index)),
            )
        }
    }
}
