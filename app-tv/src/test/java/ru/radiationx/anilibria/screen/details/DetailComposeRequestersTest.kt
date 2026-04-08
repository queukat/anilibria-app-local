package ru.radiationx.anilibria.screen.details

import androidx.compose.ui.focus.FocusRequester
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import ru.radiationx.anilibria.common.LoadingCard
import ru.radiationx.anilibria.screen.main.MainSectionUiModel

class DetailComposeRequestersTest {

    @Test
    fun stableRequesters_areReusedForItemsWithSameIdAcrossRefresh() {
        val cache = mutableMapOf<Long, MutableMap<Int, FocusRequester>>()
        val before = listOf(
            MainSectionUiModel(
                id = 101L,
                title = "Related",
                items = listOf(
                    LoadingCard("same"),
                    LoadingCard("removed"),
                ),
            ),
        )
        val after = listOf(
            MainSectionUiModel(
                id = 101L,
                title = "Related",
                items = listOf(
                    LoadingCard("same"),
                    LoadingCard("added"),
                ),
            ),
        )

        val firstRequesters = buildStableDetailSectionRequesters(before, cache)
        val secondRequesters = buildStableDetailSectionRequesters(after, cache)

        assertSame(firstRequesters[0][0], secondRequesters[0][0])
        assertNotSame(firstRequesters[0][1], secondRequesters[0][1])
    }
}
