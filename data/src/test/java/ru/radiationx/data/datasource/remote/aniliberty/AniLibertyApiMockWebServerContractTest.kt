package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.SharedBuildConfig
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeDeleteBody
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.system.Client
import ru.radiationx.data.system.ClientWrapper
import ru.radiationx.data.system.HttpException
import java.util.concurrent.TimeUnit
import javax.inject.Provider

class AniLibertyApiMockWebServerContractTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: AniLibertyApi
    private val moshi = Moshi.Builder().build()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val okHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(testHeadersInterceptor())
                .build()

        val clientWrapper =
            ClientWrapper(
                object : Provider<OkHttpClient> {
                    override fun get(): OkHttpClient = okHttpClient
                },
            )
        val realClient = Client(clientWrapper, testBuildConfig())
        val wrappedClient =
            BaseUrlRewriteClient(
                delegate = realClient,
                sourceBaseUrl = ANI_LIBERTY_BASE_URL,
                targetBaseUrl = mockWebServer.url("/api/v1").toString().removeSuffix("/"),
            )

        api = AniLibertyApi(client = wrappedClient, moshi = Moshi.Builder().build())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun getUserViewTimecodes_sendsSinceAndParsesFixture() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/user_view_timecodes_get_since.json")))
            val since = "2026-02-28T12:34:56Z"

            val result = api.getUserViewTimecodes(since = since)
            val request = takeRequest()

            assertEquals("GET", request.method)
            assertEquals("/api/v1/accounts/users/me/views/timecodes", request.requestUrl?.encodedPath)
            assertEquals(since, request.requestUrl?.queryParameter("since"))
            assertCommonHeaders(request)

            assertEquals(2, result.size)
            assertEquals("episode-1", result[0].releaseEpisodeId.value)
            assertEquals(120.5, result[0].time, 0.0)
            assertEquals(false, result[0].isWatched)
            assertEquals("episode-2", result[1].releaseEpisodeId.value)
            assertEquals(1440.0, result[1].time, 0.0)
            assertEquals(true, result[1].isWatched)
        }

    @Test
    fun getUserViewTimecodes_parsesLiveTupleFixture() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/user_view_timecodes_get_since_tuple.json")))

            val result = api.getUserViewTimecodes(since = null)

            assertEquals(2, result.size)
            assertEquals("95ccf09f-789e-11ec-ae92-0242ac120002", result[0].releaseEpisodeId.value)
            assertEquals(1423.84, result[0].time, 0.0)
            assertEquals(true, result[0].isWatched)
            assertEquals("95ccf10f-789e-11ec-ae92-0242ac120002", result[1].releaseEpisodeId.value)
            assertEquals(1342.43, result[1].time, 0.0)
            assertEquals(true, result[1].isWatched)
        }

    @Test
    fun upsertUserViewTimecodes_sendsExpectedJsonBody() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/user_view_timecodes_post_ok.json")))

            api.upsertUserViewTimecodes(
                listOf(
                    AniLibertyUserViewTimecodeUpsertBody(
                        time = 321.25,
                        isWatched = true,
                        releaseEpisodeId = "episode-42",
                    ),
                ),
            )
            val request = takeRequest()

            assertEquals("POST", request.method)
            assertEquals("/api/v1/accounts/users/me/views/timecodes", request.requestUrl?.encodedPath)
            assertCommonHeaders(request)

            val body = request.body.readUtf8()
            val itemType = Types.newParameterizedType(List::class.java, AniLibertyUserViewTimecodeUpsertBody::class.java)
            val parsedBody: List<AniLibertyUserViewTimecodeUpsertBody> =
                moshi.adapter<List<AniLibertyUserViewTimecodeUpsertBody>>(itemType).fromJson(body)
                    ?: error("Expected non-null JSON body list for upsertUserViewTimecodes.")

            assertEquals(1, parsedBody.size)
            val firstItem = parsedBody.first()
            assertEquals("episode-42", firstItem.releaseEpisodeId)
            assertEquals(321.25, firstItem.time, 0.0)
            assertEquals(true, firstItem.isWatched)
        }

    @Test
    fun deleteUserViewTimecodes_sendsExpectedJsonBody() =
        runTest {
            mockWebServer.enqueue(jsonOk("[]"))

            api.deleteUserViewTimecodes(
                listOf(
                    AniLibertyUserViewTimecodeDeleteBody(
                        releaseEpisodeId = "episode-42",
                    ),
                ),
            )
            val request = takeRequest()

            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/accounts/users/me/views/timecodes", request.requestUrl?.encodedPath)
            assertCommonHeaders(request)

            val body = request.body.readUtf8()
            val itemType = Types.newParameterizedType(List::class.java, AniLibertyUserViewTimecodeDeleteBody::class.java)
            val parsedBody: List<AniLibertyUserViewTimecodeDeleteBody> =
                moshi.adapter<List<AniLibertyUserViewTimecodeDeleteBody>>(itemType).fromJson(body)
                    ?: error("Expected non-null JSON body list for deleteUserViewTimecodes.")

            assertEquals(1, parsedBody.size)
            assertEquals("episode-42", parsedBody.first().releaseEpisodeId)
        }

    @Test
    fun getUserViewsHistory_sendsPageLimitAndParsesPagination() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/user_views_history_get_page2_limit30.json")))

            val result = api.getUserViewsHistory(page = 2, limit = 30, fields = null)
            val request = takeRequest()

            assertEquals("GET", request.method)
            assertEquals("/api/v1/accounts/users/me/views/history", request.requestUrl?.encodedPath)
            assertEquals("2", request.requestUrl?.queryParameter("page"))
            assertEquals("30", request.requestUrl?.queryParameter("limit"))
            assertCommonHeaders(request)

            assertEquals(2, result.meta.page)
            assertEquals(5, result.meta.allPages)
            assertEquals(30, result.meta.perPage)
            assertEquals(142, result.meta.allItems)
            assertEquals(2, result.data.size)
            assertEquals("episode-history-1", result.data.first().releaseEpisodeId?.value)
            assertEquals(9001, result.data.first().releaseId?.value)
            assertEquals(87.3, result.data.first().time ?: 0.0, 0.0)
            assertEquals(false, result.data.first().isWatched)
        }

    @Test
    fun getUserViewsHistory_clampsLimitToLiveServerMaximum() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/user_views_history_get_page2_limit30.json")))

            api.getUserViewsHistory(page = 1, limit = 100, fields = null)
            val request = takeRequest()

            assertEquals("GET", request.method)
            assertEquals("/api/v1/accounts/users/me/views/history", request.requestUrl?.encodedPath)
            assertEquals("1", request.requestUrl?.queryParameter("page"))
            assertEquals(MAX_USER_VIEWS_HISTORY_LIMIT.toString(), request.requestUrl?.queryParameter("limit"))
            assertCommonHeaders(request)
        }

    @Test
    fun getUserViewsHistory_parsesLiveLikePayloadWithNestedReleaseFallbacks() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/user_views_history_get_live_like.json")))

            val result = api.getUserViewsHistory(page = 1, limit = 2, fields = null)

            assertEquals(2, result.data.size)
            val first = result.data.first()
            assertEquals("a18f1aba-602f-45e2-a2bc-68255256f8fd", first.releaseEpisodeId?.value)
            assertEquals(null, first.releaseId)
            assertEquals(0.0, first.time ?: -1.0, 0.0)
            assertEquals(true, first.isWatched)
            assertEquals(null, first.createdAt)
            assertEquals("2026-04-17T18:41:34+00:00", first.updatedAt)
            assertEquals(10161, first.releaseEpisode?.releaseId?.value)
            assertEquals(10161, first.release?.id?.value)
            assertEquals(2.0, first.episode?.ordinal ?: -1.0, 0.0)

            val second = result.data[1]
            assertEquals(7.15, second.time ?: -1.0, 0.0)
            assertEquals(false, second.isWatched)
            assertEquals(1.0, second.episode?.ordinal ?: -1.0, 0.0)
        }

    @Test
    fun getEpisodeTimecode_parsesLiveLikeObjectPayload() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/release_episode_timecode_get_live_like.json")))

            val result =
                api.getEpisodeTimecode(
                    releaseEpisodeId = AniLibertyReleaseEpisodeId("a18f1aba-602f-45e2-a2bc-68255256f8fd"),
                    fields = null,
                )
            val request = takeRequest()

            assertEquals("GET", request.method)
            assertEquals("/api/v1/anime/releases/episodes/a18f1aba-602f-45e2-a2bc-68255256f8fd/timecode", request.requestUrl?.encodedPath)
            assertEquals(0.0, result.time, 0.0)
            assertEquals(true, result.isWatched)
        }

    @Test
    fun getReleaseEpisodesTimecodes_parsesLiveLikeObjectArrayPayload() =
        runTest {
            mockWebServer.enqueue(jsonOk(loadResource("aniliberty/release_episodes_timecodes_get_live_like.json")))

            val result =
                api.getReleaseEpisodesTimecodes(
                    key = AniLibertyReleaseKey.id(10161),
                    fields = null,
                )
            val request = takeRequest()

            assertEquals("GET", request.method)
            assertEquals("/api/v1/anime/releases/10161/episodes/timecodes", request.requestUrl?.encodedPath)
            assertEquals(2, result.size)
            assertEquals("a17d6fbc-0109-4435-b7cb-481d67291493", result[0].releaseEpisodeId.value)
            assertEquals(7.15, result[0].time, 0.0)
            assertEquals(false, result[0].isWatched)
            assertEquals("a18f1aba-602f-45e2-a2bc-68255256f8fd", result[1].releaseEpisodeId.value)
            assertEquals(0.0, result[1].time, 0.0)
            assertEquals(true, result[1].isWatched)
        }

    @Test
    fun upsertUserViewTimecodes_throwsForHttpErrors() =
        runTest {
            val statuses = listOf(401, 403, 429, 500)

            statuses.forEach { status ->
                mockWebServer.enqueue(
                    MockResponse()
                        .setResponseCode(status)
                        .addHeader("Content-Type", "application/json")
                        .setBody("""{"error":"status-$status"}"""),
                )

                val error =
                    runCatching {
                        api.upsertUserViewTimecodes(
                            listOf(
                                AniLibertyUserViewTimecodeUpsertBody(
                                    time = 10.0,
                                    isWatched = false,
                                    releaseEpisodeId = "episode-error",
                                ),
                            ),
                        )
                    }.exceptionOrNull()

                assertTrue("Expected HttpException for status $status", error is HttpException)
                assertEquals(status, (error as HttpException).code)

                val request = takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/api/v1/accounts/users/me/views/timecodes", request.requestUrl?.encodedPath)
                assertCommonHeaders(request)
            }
        }

    private fun takeRequest(): RecordedRequest {
        return checkNotNull(mockWebServer.takeRequest(2, TimeUnit.SECONDS)) {
            "Expected request in MockWebServer queue."
        }
    }

    private fun jsonOk(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(body)
    }

    private fun assertCommonHeaders(request: RecordedRequest) {
        assertEquals("1", request.getHeader("mobileApp"))
        assertEquals("ru.radiationx.anilibria.test", request.getHeader("App-Id"))
        assertEquals("9.9.9-test", request.getHeader("App-Ver-Name"))
        assertEquals("999", request.getHeader("App-Ver-Code"))
        assertEquals("mobileApp contract-test-agent", request.getHeader("User-Agent"))
        assertEquals(TEST_AUTHORIZATION_HEADER, request.getHeader("Authorization"))
    }

    private fun testHeadersInterceptor(): Interceptor {
        return Interceptor { chain ->
            val requestWithHeaders =
                chain.request().newBuilder()
                    .header("mobileApp", "1")
                    .header("App-Id", "ru.radiationx.anilibria.test")
                    .header("App-Ver-Name", "9.9.9-test")
                    .header("App-Ver-Code", "999")
                    .header("User-Agent", "mobileApp contract-test-agent")
                    .header("Authorization", TEST_AUTHORIZATION_HEADER)
                    .build()
            chain.proceed(requestWithHeaders)
        }
    }

    private fun testBuildConfig(): SharedBuildConfig {
        return object : SharedBuildConfig {
            override val applicationName: String = "anilibria-contract-test"
            override val applicationId: String = "ru.radiationx.anilibria.test"
            override val versionName: String = "9.9.9-test"
            override val versionCode: Int = 999
            override val buildDate: String = "2026-03-01"
            override val debug: Boolean = true
            override val hasAds: Boolean = false
        }
    }

    private fun loadResource(path: String): String {
        return checkNotNull(javaClass.classLoader?.getResource(path)) {
            "Missing resource: $path"
        }.readText()
    }

    private class BaseUrlRewriteClient(
        private val delegate: IClient,
        private val sourceBaseUrl: String,
        private val targetBaseUrl: String,
    ) : IClient by delegate {
        private fun rewrite(url: String): String {
            return if (url.startsWith(sourceBaseUrl)) {
                targetBaseUrl + url.removePrefix(sourceBaseUrl)
            } else {
                url
            }
        }

        override suspend fun get(
            url: String,
            args: Map<String, String>,
        ): String = delegate.get(rewrite(url), args)

        override suspend fun post(
            url: String,
            args: Map<String, String>,
        ): String = delegate.post(rewrite(url), args)

        override suspend fun put(
            url: String,
            args: Map<String, String>,
        ): String = delegate.put(rewrite(url), args)

        override suspend fun delete(
            url: String,
            args: Map<String, String>,
        ): String = delegate.delete(rewrite(url), args)

        override suspend fun getFull(
            url: String,
            args: Map<String, String>,
        ): NetworkResponse = delegate.getFull(rewrite(url), args)

        override suspend fun postFull(
            url: String,
            args: Map<String, String>,
        ): NetworkResponse = delegate.postFull(rewrite(url), args)

        override suspend fun putFull(
            url: String,
            args: Map<String, String>,
        ): NetworkResponse = delegate.putFull(rewrite(url), args)

        override suspend fun deleteFull(
            url: String,
            args: Map<String, String>,
        ): NetworkResponse = delegate.deleteFull(rewrite(url), args)

        override suspend fun getRaw(
            url: String,
            args: Map<String, String>,
        ): Response = delegate.getRaw(rewrite(url), args)

        override suspend fun postRaw(
            url: String,
            args: Map<String, String>,
        ): Response = delegate.postRaw(rewrite(url), args)

        override suspend fun postJson(
            url: String,
            jsonBody: String,
        ): String = delegate.postJson(rewrite(url), jsonBody)

        override suspend fun putJson(
            url: String,
            jsonBody: String,
        ): String = delegate.putJson(rewrite(url), jsonBody)

        override suspend fun deleteJson(
            url: String,
            jsonBody: String,
        ): String = delegate.deleteJson(rewrite(url), jsonBody)
    }

    private companion object {
        const val ANI_LIBERTY_BASE_URL = "https://aniliberty.top/api/v1"
        const val TEST_AUTHORIZATION_HEADER = "Bearer test-contract-token"
    }
}
