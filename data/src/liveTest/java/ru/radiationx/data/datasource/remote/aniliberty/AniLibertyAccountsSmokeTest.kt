package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import java.util.concurrent.TimeUnit

class AniLibertyAccountsSmokeTest {

    private val token: String? = System.getenv("ANILIBERTY_TOKEN")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private val api: AniLibertyApi by lazy {
        AniLibertyApi(
            client = LiveAniLibertyAccountsClient(
                apiBaseUrl = resolveApiBaseUrl(),
                token = requireNotNull(token),
            ),
            moshi = Moshi.Builder().build(),
        )
    }

    @Before
    fun requireToken() {
        assumeTrue(
            "Set ANILIBERTY_TOKEN to run AniLiberty accounts smoke tests.",
            !token.isNullOrBlank(),
        )
    }

    @Test
    fun getUserFavoriteReleases_smokeReadOnly() {
        runBlocking {
            api.getUserFavoriteReleases(
                page = 1,
                limit = 5,
                fields = null,
            )
        }
    }

    @Test
    fun getUserViewsHistory_smokeReadOnly() {
        runBlocking {
            api.getUserViewsHistory(
                page = 1,
                limit = 5,
                fields = null,
            )
        }
    }

    @Test
    fun getUserViewTimecodes_smokeReadOnly() {
        runBlocking {
            api.getUserViewTimecodes(since = null)
        }
    }

    @Test
    fun upsertUserViewTimecodes_smokeWriteOptIn() {
        runBlocking {
            val writeEnabled = System.getenv("ANILIBERTY_E2E_WRITE") == "1"
            val episodeId = System.getenv("ANILIBERTY_TEST_EPISODE_ID")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            assumeTrue(
                "Set ANILIBERTY_E2E_WRITE=1 and ANILIBERTY_TEST_EPISODE_ID to run write smoke test.",
                writeEnabled && episodeId != null,
            )
            val targetEpisodeId = requireNotNull(episodeId)

            api.upsertUserViewTimecodes(
                items = listOf(
                    AniLibertyUserViewTimecodeUpsertBody(
                        time = 1.0,
                        isWatched = false,
                        releaseEpisodeId = targetEpisodeId,
                    ),
                ),
            )
            api.getUserViewTimecodes(since = null)
        }
    }

    private fun resolveApiBaseUrl(): HttpUrl {
        val provided = System.getenv("ANILIBERTY_BASE_URL")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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

private class LiveAniLibertyAccountsClient(
    private val apiBaseUrl: HttpUrl,
    private val token: String,
) : IClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun get(url: String, args: Map<String, String>): String {
        return execute(
            method = "GET",
            httpUrl = rewriteUrl(url = url, args = args),
            body = null,
        )
    }

    override suspend fun post(url: String, args: Map<String, String>): String = error("Not used in smoke tests")
    override suspend fun put(url: String, args: Map<String, String>): String = error("Not used in smoke tests")
    override suspend fun delete(url: String, args: Map<String, String>): String = error("Not used in smoke tests")

    override suspend fun getFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used in smoke tests")
    override suspend fun postFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used in smoke tests")
    override suspend fun putFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used in smoke tests")
    override suspend fun deleteFull(url: String, args: Map<String, String>): NetworkResponse = error("Not used in smoke tests")

    override suspend fun getRaw(url: String, args: Map<String, String>): Response = error("Not used in smoke tests")
    override suspend fun postRaw(url: String, args: Map<String, String>): Response = error("Not used in smoke tests")

    override suspend fun postJson(url: String, jsonBody: String): String {
        val body = jsonBody.toRequestBody(jsonMediaType)
        return execute(
            method = "POST",
            httpUrl = rewriteUrl(url = url, args = emptyMap()),
            body = body,
        )
    }

    private fun execute(method: String, httpUrl: HttpUrl, body: RequestBody?): String {
        val requestBuilder = Request.Builder()
            .url(httpUrl)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "AniLibertyAccountsSmokeTest/1.0")

        when (method) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post(requireNotNull(body))
            else -> error("Unsupported HTTP method: $method")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Unexpected HTTP ${response.code} for ${httpUrl.encodedPath}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun rewriteUrl(url: String, args: Map<String, String>): HttpUrl {
        val sourceUrl = url.toHttpUrl()
        val sourcePath = sourceUrl.encodedPath.trimStart('/')
        val pathSuffix = if (sourcePath.startsWith("api/v1/")) {
            sourcePath.removePrefix("api/v1/")
        } else {
            sourcePath.removePrefix("api/v1")
        }

        val builder = apiBaseUrl.newBuilder()
        if (pathSuffix.isNotBlank()) {
            builder.addEncodedPathSegments(pathSuffix)
        }
        args.forEach { (name, value) ->
            builder.addQueryParameter(name, value)
        }
        return builder.build()
    }
}
