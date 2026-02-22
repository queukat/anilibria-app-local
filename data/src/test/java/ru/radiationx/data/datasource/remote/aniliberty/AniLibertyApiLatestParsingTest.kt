package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse

class AniLibertyApiLatestParsingTest {

    @Test
    fun getLatestReleases_parsesTypedReleaseItems() = runBlocking {
        val payload = """
            [
              {
                "id": 10126,
                "alias": "arne-no-jikenbo",
                "name": {
                  "main": "Дело Арне"
                },
                "is_ongoing": true,
                "episodes_total": 12,
                "latest_episode": {
                  "id": "a12281c2-7f0b-4f1a-9ce8-128b34ca21fc",
                  "ordinal": 7
                }
              }
            ]
        """.trimIndent()

        val client = FakeLatestClient(payload)
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val result = api.getLatestReleases(limit = 20, fields = null)

        assertEquals(1, result.size)
        assertEquals(10126, result.first().id?.value)
        assertEquals(7.0, result.first().latestEpisode?.ordinal ?: 0.0, 0.0)
        assertEquals("20", client.lastArgs["limit"])
        assertFalse(client.lastArgs.containsKey("include"))
        assertFalse(client.lastArgs.containsKey("exclude"))
    }
}

private class FakeLatestClient(
    private val payload: String,
) : IClient {

    var lastArgs: Map<String, String> = emptyMap()

    override suspend fun get(url: String, args: Map<String, String>): String {
        lastArgs = args.toMap()
        return payload
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
