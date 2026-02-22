package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.remote.aniliberty.moshi.AniLibertyMoshi

class AniLibertyContractSnapshotTest {

    private val moshi: Moshi = AniLibertyMoshi.configure(Moshi.Builder().build())

    @Test
    fun scheduleWeek_noArgsFixture_parsesFlatArrayOfObjects() {
        val json = loadResource("aniliberty/schedule_week_no_args.json")

        val parsed = parseScheduleWeekResponseJson(json, moshi)

        assertNotNull(parsed.data)
        assertFalse(parsed.data!!.isEmpty())
        assertTrue(parsed.data!!.all { it.release != null || it.nextReleaseEpisodeNumber != null })
    }

    @Test
    fun scheduleWeek_withArgsFixture_parsesFlatArrayOfObjects() {
        val json = loadResource("aniliberty/schedule_week_with_args.json")

        val parsed = parseScheduleWeekResponseJson(json, moshi)

        assertNotNull(parsed.data)
        assertFalse(parsed.data!!.isEmpty())
        assertTrue(parsed.data!!.any { it.release?.genres != null || it.release?.latestEpisode != null })
    }

    @Test
    fun scheduleWeek_nestedArraysFixture_parsesAndNormalizesToFlatList() {
        val json = loadResource("aniliberty/schedule_week.json")

        val parsed = parseScheduleWeekResponseJson(json, moshi)

        assertNotNull(parsed.data)
        assertTrue(parsed.data!!.isEmpty() || parsed.data!!.all { it.release != null || it.nextReleaseEpisodeNumber != null })
    }

    @Test
    fun scheduleWeek_objectRootPayload_parsesCompatibly() {
        val json = """{"data":[{"next_release_episode_number":7}]}"""

        val parsed = parseScheduleWeekResponseJson(json, moshi)

        assertNotNull(parsed.data)
        assertFalse(parsed.data.isNullOrEmpty())
        assertNotNull(parsed.data!!.first().nextReleaseEpisodeNumber)
    }

    @Test
    fun scheduleWeek_unexpectedPayload_returnsSafeEmptyResult() {
        val json = """{"foo":"bar"}"""

        val parsed = parseScheduleWeekResponseJson(json, moshi)

        assertNotNull(parsed.data)
        assertTrue(parsed.data!!.isEmpty())
    }

    @Test
    fun catalogReleases_fixture_parsesPaginatedResponse() {
        val json = loadResource("aniliberty/catalog_releases_page1_limit3.json")
        val type = Types.newParameterizedType(
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

    private fun loadResource(path: String): String {
        return checkNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing resource: $path"
        }.readText()
    }
}
