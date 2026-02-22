package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse

class AniLibertyApiFranchisesParsingTest {

    @Test
    fun getFranchisesByRelease_parsesArrayIntoTypedModels() = runBlocking {
        val payload = """
            [
              {
                "id": "franchise-1",
                "name": "Franchise name",
                "franchise_releases": [
                  {
                    "id": "item-1",
                    "sort_order": 1,
                    "release_id": 9600,
                    "franchise_id": "franchise-1"
                  }
                ]
              }
            ]
        """.trimIndent()
        val client = FakeAniLibertyClient(payload)
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val result = api.getFranchisesByRelease(AniLibertyReleaseId(9600), fields = null)

        assertEquals(1, result.size)
        assertEquals("franchise-1", result.first().id)
        assertNotNull(result.first().franchiseReleases)
        assertEquals(9600, result.first().franchiseReleases?.first()?.releaseId)
    }

    @Test
    fun getFranchises_parsesArrayWithoutLinkedHashMaps() = runBlocking {
        val payload = """[{"id":"franchise-1","name":"Franchise name"}]"""
        val client = FakeAniLibertyClient(payload)
        val api = AniLibertyApi(client = client, moshi = Moshi.Builder().build())

        val result = api.getFranchises(fields = null)

        assertEquals(1, result.size)
        assertTrue(result.first() is AniLibertyFranchise)
    }
}

private class FakeAniLibertyClient(
    private val payload: String,
) : IClient {

    override suspend fun get(url: String, args: Map<String, String>): String = payload
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
