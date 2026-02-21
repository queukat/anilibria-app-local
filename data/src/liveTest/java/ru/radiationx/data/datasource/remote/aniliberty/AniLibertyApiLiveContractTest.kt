package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.datasource.remote.aniliberty.moshi.AniLibertyMoshi
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AniLibertyApiLiveContractTest {

    companion object {
        private val REQUEST_BUDGET = AtomicInteger(8)
    }

    private val moshi: Moshi = AniLibertyMoshi.configure(Moshi.Builder().build())
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    private val apiBaseUrl: HttpUrl = resolveApiBaseUrl()

    @Before
    fun requireLiveFlag() {
        assumeTrue(
            "Set ANILIBERTY_LIVE_TESTS=1 to run live contract tests.",
            isLiveEnabled(),
        )
    }

    @Test
    fun scheduleWeek_liveContract_rootArrayParsesWithoutCrash() {
        val json = executeJsonGet(
            path = "/anime/schedule/week",
            query = mapOf(
                "include" to "genres,latest_episode",
                "exclude" to "episodes,members,torrents,description,notification",
            ),
        )

        println("schedule/week prefix: ${json.take(2048)}")
        assertTrue("Expected array root for schedule/week", json.trimStart().startsWith("["))

        val parsed = parseScheduleWeekResponseJson(json, moshi)
        assertNotNull(parsed.data)
    }

    @Test
    fun scheduleNow_liveContract_parsesJsonObject() {
        val json = executeJsonGet(path = "/anime/schedule/now")
        val parsed = moshi.adapter(AniLibertyScheduleNowResponse::class.java).fromJson(json)

        assertNotNull(parsed)
        assertNotNull(parsed?.today)
    }

    @Test
    fun latestAndDetails_liveContract_parseReleaseModels() {
        val latestJson = executeJsonGet(
            path = "/anime/releases/latest",
            query = mapOf("limit" to "2"),
        )
        val listType = Types.newParameterizedType(List::class.java, AniLibertyRelease::class.java)
        val latest = moshi.adapter<List<AniLibertyRelease>>(listType).fromJson(latestJson).orEmpty()

        assertNotNull(latest)
        if (latest.isNotEmpty()) {
            val firstId = latest.first().id?.value
            assertNotNull(firstId)
            assertTrue(!latest.first().name?.main.isNullOrBlank() || !latest.first().name?.english.isNullOrBlank())

            val detailsJson = executeJsonGet(path = "/anime/releases/$firstId")
            val details = moshi.adapter(AniLibertyRelease::class.java).fromJson(detailsJson)
            assertNotNull(details)
            assertNotNull(details?.id)
        }
    }

    @Test
    fun catalogReleases_liveContract_parsesPaginatedResponse() {
        val json = executeJsonGet(
            path = "/anime/catalog/releases",
            query = mapOf(
                "page" to "1",
                "limit" to "3",
            ),
        )

        val type = Types.newParameterizedType(
            AniLibertyPaginatedResponse::class.java,
            AniLibertyRelease::class.java,
        )
        val parsed = moshi.adapter<AniLibertyPaginatedResponse<AniLibertyRelease>>(type).fromJson(json)

        assertNotNull(parsed)
        assertNotNull(parsed?.data)
    }

    private fun executeJsonGet(path: String, query: Map<String, String> = emptyMap()): String {
        val remainingBeforeRequest = REQUEST_BUDGET.getAndDecrement()
        check(remainingBeforeRequest > 0) {
            "Live-test request budget exceeded. Increase budget only if absolutely needed."
        }

        val urlBuilder = apiBaseUrl.newBuilder()
        urlBuilder.addEncodedPathSegments(path.trimStart('/'))
        query.forEach { (name, value) -> urlBuilder.addQueryParameter(name, value) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "application/json")
            .header("User-Agent", "AniLibertyContractTest/1.0")
            .get()
            .build()

        val response = executeWithSingleRetry(request)
        response.use {
            val code = it.code
            assertTrue("Unexpected HTTP code $code for ${request.url}", code in 200..299 || code == 304)
            val contentType = it.header("Content-Type").orEmpty().lowercase()
            assertTrue("Expected application/json but got '$contentType'", contentType.contains("application/json"))
            return it.body?.string().orEmpty()
        }
    }

    private fun executeWithSingleRetry(request: Request): okhttp3.Response {
        var lastError: IOException? = null
        repeat(2) { attempt ->
            try {
                return client.newCall(request).execute()
            } catch (error: IOException) {
                lastError = error
                if (attempt == 1) {
                    throw error
                }
            }
        }
        throw lastError ?: IOException("Unknown network error for ${request.url}")
    }

    private fun isLiveEnabled(): Boolean {
        return System.getenv("ANILIBERTY_LIVE_TESTS") == "1" ||
            System.getProperty("ANILIBERTY_LIVE_TESTS") == "1"
    }

    private fun resolveApiBaseUrl(): HttpUrl {
        val provided = System.getenv("ANILIBERTY_BASE_URL")
            ?: System.getProperty("ANILIBERTY_BASE_URL")
            ?: "https://aniliberty.top"

        val normalized = provided.trimEnd('/')
        val apiRoot = if (normalized.endsWith("/api/v1")) {
            normalized
        } else {
            "$normalized/api/v1"
        }
        return "$apiRoot/".toHttpUrl()
    }
}
