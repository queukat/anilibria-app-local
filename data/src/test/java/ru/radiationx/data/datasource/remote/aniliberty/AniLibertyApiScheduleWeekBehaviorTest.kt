package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse

class AniLibertyApiScheduleWeekBehaviorTest {

    private val fields = AniLibertyReleaseFields(
        include = setOf(
            AniLibertyReleaseInclude.GENRES,
            AniLibertyReleaseInclude.LATEST_EPISODE,
        ),
        exclude = setOf(
            AniLibertyReleaseExclude.EPISODES,
            AniLibertyReleaseExclude.MEMBERS,
            AniLibertyReleaseExclude.TORRENTS,
        ),
        excludeRaw = setOf(
            AniLibertyFieldName("description"),
            AniLibertyFieldName("notification"),
        ),
    )

    @Test
    fun getScheduleWeek_usesReleasePrefixedIncludeExcludeAndFallsBackToNoArgs() = runBlocking {
        val client = FakeScheduleClient(
            withArgsPayload = "[[],[]]",
            noArgsPayload = """[{"next_release_episode_number":1}]""",
        )
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val response = api.getScheduleWeek(fields)

        assertEquals(2, client.calls.size)
        val firstArgs = client.calls.first()
        val include = firstArgs["include"].orEmpty().split(",").toSet()
        val exclude = firstArgs["exclude"].orEmpty().split(",").toSet()
        assertEquals(
            setOf("release.genres", "release.latest_episode"),
            include,
        )
        assertEquals(
            setOf(
                "release.episodes",
                "release.members",
                "release.torrents",
                "release.description",
                "release.notification",
            ),
            exclude,
        )
        assertTrue("Fallback response should provide non-empty normalized list", response.data.orEmpty().isNotEmpty())
    }

    @Test
    fun getScheduleWeek_doesNotFallbackWhenPrimaryResponseIsNotEmpty() = runBlocking {
        val client = FakeScheduleClient(
            withArgsPayload = """[{"next_release_episode_number":2}]""",
            noArgsPayload = """[{"next_release_episode_number":1}]""",
        )
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val response = api.getScheduleWeek(fields)

        assertEquals(1, client.calls.size)
        assertFalse(response.data.orEmpty().isEmpty())
    }
}

private class FakeScheduleClient(
    private val withArgsPayload: String,
    private val noArgsPayload: String,
) : IClient {
    val calls = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, args: Map<String, String>): String {
        calls += args.toMap()
        return if (url.endsWith("/anime/schedule/week") && args.isNotEmpty()) {
            withArgsPayload
        } else {
            noArgsPayload
        }
    }

    override suspend fun post(url: String, args: Map<String, String>): String = error("Not used")
    override suspend fun put(url: String, args: Map<String, String>): String = error("Not used")
    override suspend fun delete(url: String, args: Map<String, String>): String = error("Not used")

    override suspend fun getFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")
    override suspend fun postFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")
    override suspend fun putFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")
    override suspend fun deleteFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used")

    override suspend fun getRaw(url: String, args: Map<String, String>): Response = error("Not used")
    override suspend fun postRaw(url: String, args: Map<String, String>): Response = error("Not used")
}
