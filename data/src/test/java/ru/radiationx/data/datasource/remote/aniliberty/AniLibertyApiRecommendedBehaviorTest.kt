package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse

class AniLibertyApiRecommendedBehaviorTest {

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
    fun getRecommendedReleases_fallsBackToRequestWithoutFieldsWhenWithFieldsIsEmpty() = runBlocking {
        val withFieldsPayload = "[]"
        val fallbackPayload = """
            [
              {
                "id": 9600,
                "alias": "ore-dake-level-up-na-ken",
                "name": {
                  "main": "Поднятие уровня в одиночку"
                }
              }
            ]
        """.trimIndent()

        val client = FakeRecommendedClient(
            withFieldsPayload = withFieldsPayload,
            noFieldsPayload = fallbackPayload,
        )
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val result = api.getRecommendedReleases(
            limit = 20,
            releaseId = AniLibertyReleaseId(9600),
            fields = fields,
        )

        assertEquals(2, client.calls.size)
        assertEquals("14", client.calls.first()["limit"])
        assertTrue(client.calls.first().containsKey("include"))
        assertTrue(client.calls.first().containsKey("exclude"))
        assertTrue(client.calls.last().containsKey("release_id"))
        assertEquals(9600, result.firstOrNull()?.id?.value)
    }

    @Test
    fun getRecommendedReleases_doesNotFallbackWhenWithFieldsReturnsData() = runBlocking {
        val withFieldsPayload = """
            [
              {
                "id": 9839,
                "alias": "ore-dake-level-up-na-ken-season-2-arise-from-the-shadow",
                "name": {
                  "main": "Поднятие уровня в одиночку 2"
                }
              }
            ]
        """.trimIndent()
        val noFieldsPayload = "[]"

        val client = FakeRecommendedClient(
            withFieldsPayload = withFieldsPayload,
            noFieldsPayload = noFieldsPayload,
        )
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val result = api.getRecommendedReleases(
            limit = 20,
            releaseId = AniLibertyReleaseId(9600),
            fields = fields,
        )

        assertEquals(1, client.calls.size)
        assertEquals(9839, result.firstOrNull()?.id?.value)
    }
}

private class FakeRecommendedClient(
    private val withFieldsPayload: String,
    private val noFieldsPayload: String,
) : IClient {

    val calls = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, args: Map<String, String>): String {
        calls += args.toMap()
        val isWithFieldsRequest = args.containsKey("include") || args.containsKey("exclude")
        return if (isWithFieldsRequest) withFieldsPayload else noFieldsPayload
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
