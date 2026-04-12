package ru.radiationx.anilibria.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import ru.radiationx.data.entity.domain.types.ReleaseId

class TvCardInteractionsTest {
    @Test
    fun toTvCardDescription_mapsSupportedCardTypes() {
        val libriaCard = releaseCard()
        val linkCard = LinkCard("Еще")
        val loadingCard =
            LoadingCard(
                title = "Ошибка",
                description = "Попробуйте снова",
                isError = true,
            )

        assertEquals(
            TvCardDescription(title = "Наруто", subtitle = "Описание"),
            libriaCard.toTvCardDescription(),
        )
        assertEquals(
            TvCardDescription(title = "Еще", subtitle = ""),
            linkCard.toTvCardDescription(),
        )
        assertEquals(
            TvCardDescription(title = "Ошибка", subtitle = "Попробуйте снова"),
            loadingCard.toTvCardDescription(),
        )
        assertEquals(TvCardDescription(), Any().toTvCardDescription())
    }

    @Test
    fun toTvCardDescription_usesCustomLibriaSubtitleResolver() {
        val description =
            releaseCard().toTvCardDescription { card ->
                "${card.title} (dynamic)"
            }

        assertEquals(
            TvCardDescription(title = "Наруто", subtitle = "Наруто (dynamic)"),
            description,
        )
    }

    @Test
    fun handleTvCardClick_routesToMatchingCallbacks() {
        val viewModel = RecordingCardsViewModel()
        val card = releaseCard()

        viewModel.handleTvCardClick(LinkCard("Еще"))
        viewModel.handleTvCardClick(LoadingCard("Ошибка", "Попробуйте снова", isError = true))
        viewModel.handleTvCardClick(card)
        viewModel.handleTvCardClick(Any())

        assertEquals(1, viewModel.linkClicks)
        assertEquals(1, viewModel.loadingClicks)
        assertSame(card, viewModel.clickedCard)
    }

    @Test
    fun getOrPutRow_reusesCachedValue() {
        val rows = mutableMapOf<Long, RowMarker>()
        var created = 0

        val first =
            rows.getOrPutRow(42L) {
                created += 1
                RowMarker(it)
            }
        val second =
            rows.getOrPutRow(42L) {
                created += 1
                RowMarker(it)
            }

        assertEquals(1, created)
        assertSame(first, second)
    }

    private fun releaseCard(): LibriaCard {
        return LibriaCard(
            title = "Наруто",
            description = "Описание",
            image = "poster.jpg",
            type = LibriaCard.Type.Release(ReleaseId(7)),
        )
    }

    private class RecordingCardsViewModel : BaseCardsViewModel() {
        var linkClicks = 0
        var loadingClicks = 0
        var clickedCard: LibriaCard? = null

        override fun onLinkCardClick() {
            linkClicks += 1
        }

        override fun onLoadingCardClick() {
            loadingClicks += 1
        }

        override fun onLibriaCardClick(card: LibriaCard) {
            clickedCard = card
        }

        override suspend fun getLoader(requestPage: Int): List<LibriaCard> = emptyList()
    }

    private data class RowMarker(val id: Long)
}
