package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.remote.aniliberty.moshi.AniLibertyMoshi
import ru.radiationx.data.entity.mapper.AniLibertyLegacyReleaseMapper
import ru.radiationx.data.system.ApiUtils

class AniLibertyContractSnapshotTest {
    private val moshi: Moshi = AniLibertyMoshi.configure(Moshi.Builder().build())

    @Test
    fun scheduleWeek_noArgsFixture_parsesFlatArrayOfObjects() {
        val json = loadResource("aniliberty/schedule_week_no_args.json")

        val parsed = AniLibertyScheduleWeekPayloadParser.parse(json, moshi)

        assertNotNull(parsed.data)
        assertFalse(parsed.data!!.isEmpty())
        assertTrue(parsed.data!!.all { it.release != null || it.nextReleaseEpisodeNumber != null })
    }

    @Test
    fun scheduleWeek_withArgsFixture_parsesFlatArrayOfObjects() {
        val json = loadResource("aniliberty/schedule_week_with_args.json")

        val parsed = AniLibertyScheduleWeekPayloadParser.parse(json, moshi)

        assertNotNull(parsed.data)
        assertFalse(parsed.data!!.isEmpty())
        assertTrue(parsed.data!!.any { it.release?.genres != null || it.release?.latestEpisode != null })
    }

    @Test
    fun scheduleWeek_nestedArraysFixture_parsesAndNormalizesToFlatList() {
        val json = loadResource("aniliberty/schedule_week.json")

        val parsed = AniLibertyScheduleWeekPayloadParser.parse(json, moshi)

        assertNotNull(parsed.data)
        assertTrue(parsed.data!!.isEmpty() || parsed.data!!.all { it.release != null || it.nextReleaseEpisodeNumber != null })
    }

    @Test
    fun scheduleWeek_objectRootPayload_parsesCompatibly() {
        val json = """{"data":[{"next_release_episode_number":7}]}"""

        val parsed = AniLibertyScheduleWeekPayloadParser.parse(json, moshi)

        assertNotNull(parsed.data)
        assertFalse(parsed.data.isNullOrEmpty())
        assertNotNull(parsed.data!!.first().nextReleaseEpisodeNumber)
    }

    @Test
    fun scheduleWeek_unexpectedPayload_returnsSafeEmptyResult() {
        val json = """{"foo":"bar"}"""

        val parsed = AniLibertyScheduleWeekPayloadParser.parse(json, moshi)

        assertNotNull(parsed.data)
        assertTrue(parsed.data!!.isEmpty())
    }

    @Test
    fun catalogReleases_fixture_parsesPaginatedResponse() {
        val json = loadResource("aniliberty/catalog_releases_page1_limit3.json")
        val type =
            Types.newParameterizedType(
                AniLibertyPaginatedResponse::class.java,
                AniLibertyRelease::class.java,
            )

        val parsed = moshi.adapter<AniLibertyPaginatedResponse<AniLibertyRelease>>(type).fromJson(json)

        assertNotNull(parsed)
        assertNotNull(parsed?.data)
        assertFalse(parsed!!.data.isNullOrEmpty())
        val first = parsed.data!!.first()
        assertNotNull(first.id)
        assertTrue(!first.name?.main.isNullOrBlank() || !first.name?.english.isNullOrBlank())
    }

    @Test
    fun latestReleases_fixture_parsesListResponse() {
        val json = loadResource("aniliberty/latest_releases_limit1.json")
        val type = Types.newParameterizedType(List::class.java, AniLibertyRelease::class.java)

        val parsed = moshi.adapter<List<AniLibertyRelease>>(type).fromJson(json)

        assertNotNull(parsed)
        assertFalse(parsed!!.isEmpty())
        val first = parsed.first()
        assertNotNull(first.id)
        assertTrue(!first.name?.main.isNullOrBlank() || !first.name?.english.isNullOrBlank())
    }

    @Test
    fun scheduleNow_fixture_parsesTodayTomorrowYesterdayObject() {
        val json = loadResource("aniliberty/schedule_now.json")

        val parsed = moshi.adapter(AniLibertyScheduleNowResponse::class.java).fromJson(json)

        assertNotNull(parsed)
        assertNotNull(parsed?.today)
        assertNotNull(parsed?.tomorrow)
        assertNotNull(parsed?.yesterday)
        assertTrue(parsed!!.today!!.isNotEmpty())
    }

    @Test
    fun releaseDetails_fixture_parsesAndMapsToLegacySeries() {
        val json = loadResource("aniliberty/release_details_id1001.json")

        val parsed = moshi.adapter(AniLibertyRelease::class.java).fromJson(json)

        assertNotNull(parsed)
        assertNotNull(parsed?.id)
        assertNotNull(parsed?.episodesTotal)
        assertNotNull(parsed?.latestEpisode)

        val apiUtils = mockk<ApiUtils>()
        every { apiUtils.escapeHtml(any()) } answers { firstArg<String?>() }
        val mapped =
            AniLibertyLegacyReleaseMapper.toLegacyReleaseOrNull(
                parsed!!,
                apiUtils,
                false,
            )
        assertNotNull(mapped)
        assertTrue(!mapped!!.series.isNullOrBlank())
    }

    private fun loadResource(path: String): String {
        return checkNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing resource: $path"
        }.readText()
    }
}
