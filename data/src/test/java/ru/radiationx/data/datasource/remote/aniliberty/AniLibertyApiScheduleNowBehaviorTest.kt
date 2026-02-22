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

class AniLibertyApiScheduleNowBehaviorTest {

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
    fun getScheduleNow_usesReleasePrefixedIncludeExcludeAndFallsBackToNoArgsWhenPrimaryIsEmpty() = runBlocking {
        val client = FakeScheduleNowClient(
            withArgsPayload = """{"today":[],"tomorrow":[],"yesterday":[]}""",
            noArgsPayload = """{"today":[{"next_release_episode_number":1}],"tomorrow":[],"yesterday":[]}""",
        )
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val response = api.getScheduleNow(fields)

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
        assertFalse("Fallback response should provide non-empty today list", response.today.orEmpty().isEmpty())
    }

    @Test
    fun getScheduleNow_doesNotFallbackWhenPrimaryResponseHasItems() = runBlocking {
        val client = FakeScheduleNowClient(
            withArgsPayload = """{"today":[{"next_release_episode_number":2}],"tomorrow":[],"yesterday":[]}""",
            noArgsPayload = """{"today":[{"next_release_episode_number":1}],"tomorrow":[],"yesterday":[]}""",
        )
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val response = api.getScheduleNow(fields)

        assertEquals(1, client.calls.size)
        assertTrue(response.today.orEmpty().isNotEmpty())
    }
}

private class FakeScheduleNowClient(
    private val withArgsPayload: String,
    private val noArgsPayload: String,
) : IClient {
    val calls = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, args: Map<String, String>): String {
        calls += args.toMap()
        return if (url.endsWith("/anime/schedule/now") && args.isNotEmpty()) {
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
