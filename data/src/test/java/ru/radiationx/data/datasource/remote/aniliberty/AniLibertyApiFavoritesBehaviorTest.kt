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

class AniLibertyApiFavoritesBehaviorTest {

    @Test
    fun getUserFavoriteReleasesFiltered_withFavoritesFields_sendsSortingAndExcludeParams() = runBlocking {
        val client = FakeFavoritesClient(payload = favoritesPayload())
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val result = api.getUserFavoriteReleasesFiltered(
            page = 1,
            limit = 25,
            sorting = AniLibertyFavoriteSorting.FreshAtDesc,
            fields = AniLibertyReleaseFields.FavoritesList,
        )

        val args = client.calls.single()
        assertEquals("1", args["page"])
        assertEquals("25", args["limit"])
        assertEquals("FRESH_AT_DESC", args["f[sorting]"])
        assertFalse(args.containsKey("include"))
        assertTrue(args.containsKey("exclude"))

        val exclude = args["exclude"].orEmpty()
        assertTrue(exclude.contains("episodes"))
        assertTrue(exclude.contains("members"))
        assertTrue(exclude.contains("torrents"))
        assertTrue(exclude.contains("description"))
        assertTrue(exclude.contains("notification"))

        assertEquals(10096, result.data.firstOrNull()?.id?.value)
    }

    @Test
    fun getUserFavoriteReleasesFiltered_withNullFields_omitsIncludeExcludeParams() = runBlocking {
        val client = FakeFavoritesClient(payload = favoritesPayload())
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        api.getUserFavoriteReleasesFiltered(
            page = 1,
            limit = 25,
            sorting = AniLibertyFavoriteSorting.FreshAtDesc,
            fields = null,
        )

        val args = client.calls.single()
        assertEquals("FRESH_AT_DESC", args["f[sorting]"])
        assertFalse(args.containsKey("include"))
        assertFalse(args.containsKey("exclude"))
    }

    private fun favoritesPayload(): String = """
        {
          "data": [
            {
              "id": 10096,
              "alias": "hell-mode-yarikomi-suki-no-gamer-wa-hai-setting-no-isekai-de-musou-suru",
              "name": {
                "main": "Hell Mode"
              }
            }
          ],
          "meta": {
            "pagination": {
              "total": 1,
              "count": 1,
              "per_page": 25,
              "current_page": 1,
              "total_pages": 1
            }
          }
        }
    """.trimIndent()
}

private class FakeFavoritesClient(
    private val payload: String,
) : IClient {

    val calls = mutableListOf<Map<String, String>>()

    override suspend fun get(url: String, args: Map<String, String>): String {
        calls += args.toMap()
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
