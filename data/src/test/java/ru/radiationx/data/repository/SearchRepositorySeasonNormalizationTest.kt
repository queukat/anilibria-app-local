package ru.radiationx.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.data.entity.domain.release.SeasonItem

class SearchRepositorySeasonNormalizationTest {
    @Test
    fun toLegacySearchSeasonItem_mapsAniLibertyEnglishSeasonValues() {
        val normalized =
            listOf(
                SeasonItem(title = "Winter", value = "winter").toLegacySearchSeasonItem(),
                SeasonItem(title = "Spring", value = "spring").toLegacySearchSeasonItem(),
                SeasonItem(title = "Summer", value = "summer").toLegacySearchSeasonItem(),
                SeasonItem(title = "Autumn", value = "autumn").toLegacySearchSeasonItem(),
            )

        assertEquals(
            listOf("зима", "весна", "лето", "осень"),
            normalized.map(SeasonItem::value),
        )
    }

    @Test
    fun toLegacySearchSeasonItem_keepsLegacyRussianValuesUntouched() {
        val normalized =
            SeasonItem(
                title = "Зима",
                value = "зима",
            ).toLegacySearchSeasonItem()

        assertEquals("зима", normalized.value)
    }

    @Test
    fun toLegacySearchSeasonItem_supportsFallAlias() {
        val normalized =
            SeasonItem(
                title = "Fall",
                value = "fall",
            ).toLegacySearchSeasonItem()

        assertEquals("осень", normalized.value)
    }
}
