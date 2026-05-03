package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import ru.radiationx.data.datasource.remote.IClient
import ru.radiationx.data.datasource.remote.NetworkResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyUserViewTimecodeUpsertBody
import ru.radiationx.data.entity.domain.release.Release
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class AniLibertyAccountsSmokeTest {
    private val token: String? =
        System.getenv("ANILIBERTY_TOKEN")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private val liveClient: LiveAniLibertyAccountsClient by lazy {
        LiveAniLibertyAccountsClient(
            apiBaseUrl = resolveApiBaseUrl(),
            token = requireNotNull(token),
        )
    }

    private val api: AniLibertyApi by lazy {
        AniLibertyApi(
            client = liveClient,
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
        runTest {
            api.getUserFavoriteReleases(
                page = 1,
                limit = 5,
                fields = null,
            )
        }
    }

    @Test
    fun getUserViewsHistory_smokeReadOnly() {
        runTest {
            api.getUserViewsHistory(
                page = 1,
                limit = 5,
                fields = null,
            )
        }
    }

    @Test
    fun getUserViewTimecodes_smokeReadOnly() {
        runTest {
            api.getUserViewTimecodes(since = null)
        }
    }

    @Test
    fun getUserFavoriteReleases_fieldsPreset_notLargerThanFullPayload() {
        runTest {
            api.getUserFavoriteReleasesFiltered(
                page = 1,
                limit = 5,
                sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                fields = null,
            )
            val fullBytes = liveClient.lastResponseBodyBytes

            api.getUserFavoriteReleasesFiltered(
                page = 1,
                limit = 5,
                sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                fields = AniLibertyReleaseFields.FavoritesList,
            )
            val slimBytes = liveClient.lastResponseBodyBytes
            val slimExclude = liveClient.lastRequestUrl?.queryParameter("exclude").orEmpty()

            assertTrue(
                "Expected non-empty exclude query parameter for favorites slim fields request.",
                slimExclude.isNotBlank(),
            )
            assertTrue(
                "Expected slim payload to be <= full payload, but got slim=$slimBytes bytes, full=$fullBytes bytes.",
                slimBytes <= fullBytes,
            )
        }
    }

    @Test
    fun getUserFavoriteReleases_sortedByFreshAtDescWhenRequested() {
        runTest {
            val response =
                api.getUserFavoriteReleasesFiltered(
                    page = 1,
                    limit = 25,
                    sorting = AniLibertyFavoriteSorting.FreshAtDesc,
                    fields = AniLibertyReleaseFields.FavoritesList,
                )

            val freshAtInstants =
                response.data
                    .mapNotNull { parseInstantOrNull(it.freshAt) }

            assumeTrue(
                "Need at least 2 favorites with fresh_at to assert sorting.",
                freshAtInstants.size >= 2,
            )

            val isSortedDesc =
                freshAtInstants
                    .zipWithNext()
                    .all { (previous, next) -> !previous.isBefore(next) }

            assertTrue(
                "Expected favorites to be sorted by fresh_at desc when requested.",
                isSortedDesc,
            )
        }
    }

    @Test
    fun getUserFavoriteReleases_slimFields_preserveMappedStatusCodes() {
        runTest {
            verifySlimFieldsPreserveMappedStatusCodes(AniLibertyFavoriteSorting.FreshAtDesc)
        }
    }

    @Test
    fun getUserFavoriteReleases_yearDesc_slimFields_preserveMappedStatusCodes() {
        runTest {
            val summary = verifySlimFieldsPreserveMappedStatusCodes(AniLibertyFavoriteSorting.YearDesc)
            println(summary)
        }
    }

    @Test
    fun upsertUserViewTimecodes_smokeWriteOptIn() {
        runTest {
            val writeEnabled = System.getenv("ANILIBERTY_E2E_WRITE") == "1"
            val episodeId =
                System.getenv("ANILIBERTY_TEST_EPISODE_ID")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

            assumeTrue(
                "Set ANILIBERTY_E2E_WRITE=1 and ANILIBERTY_TEST_EPISODE_ID to run write smoke test.",
                writeEnabled && episodeId != null,
            )
            val targetEpisodeId = requireNotNull(episodeId)

            api.upsertUserViewTimecodes(
                items =
                    listOf(
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
        val provided =
            System.getenv("ANILIBERTY_BASE_URL")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "https://aniliberty.top"

        val normalized = provided.trimEnd('/')
        val apiRoot =
            if (normalized.endsWith("/api/v1")) {
                normalized
            } else {
                "$normalized/api/v1"
            }
        return "$apiRoot/".toHttpUrl()
    }

    private suspend fun verifySlimFieldsPreserveMappedStatusCodes(sorting: AniLibertyFavoriteSorting): String {
        val fullResponse =
            api.getUserFavoriteReleasesFiltered(
                page = 1,
                limit = 25,
                sorting = sorting,
                fields = null,
            )
        val slimResponse =
            api.getUserFavoriteReleasesFiltered(
                page = 1,
                limit = 25,
                sorting = sorting,
                fields = AniLibertyReleaseFields.FavoritesList,
            )
        val ambiguousIds =
            slimResponse.data
                .mapNotNull { release ->
                    release.id?.takeIf { release.needsStatusResolutionFromFullRelease() }
                }
                .distinctBy { it.value }
        val resolvedSlimById =
            if (ambiguousIds.isEmpty()) {
                emptyMap()
            } else {
                api.getReleasesList(
                    ids = ambiguousIds,
                    aliases = null,
                    page = 1,
                    limit = ambiguousIds.size,
                    fields = null,
                ).data
                    .mapNotNull { release ->
                        release.id?.value?.let { id -> id to release }
                    }
                    .toMap()
            }

        val fullMapped =
            fullResponse.data
                .mapNotNull { release ->
                    release.id?.value?.let { id -> id to (release to deriveLegacyStatusCode(release)) }
                }
                .toMap()
        val slimMapped =
            slimResponse.data
                .mapNotNull { release ->
                    val resolvedRelease = release.id?.value?.let { resolvedSlimById[it] } ?: release
                    resolvedRelease.id?.value?.let { id ->
                        id to (resolvedRelease to deriveLegacyStatusCode(resolvedRelease))
                    }
                }
                .toMap()

        val overlappingIds = fullMapped.keys.intersect(slimMapped.keys)
        assumeTrue(
            "Need overlapping favorites from full and slim responses to compare mapped statuses.",
            overlappingIds.isNotEmpty(),
        )

        val mismatches =
            overlappingIds.mapNotNull { id ->
                val (fullRelease, full) = fullMapped.getValue(id)
                val (slimRelease, slim) = slimMapped.getValue(id)
                if (full == slim) {
                    null
                } else {
                    "release#$id full=$full ${describeReleaseStatusInputs(
                        fullRelease,
                    )}; slim=$slim ${describeReleaseStatusInputs(slimRelease)}"
                }
            }

        assertTrue(
            "Expected slim favorites fields to preserve mapped statusCode for ${sorting.value}. Mismatches: ${mismatches.joinToString()}",
            mismatches.isEmpty(),
        )

        val orderedIds =
            slimResponse.data
                .mapNotNull { it.id?.value }
                .filter { it in overlappingIds }
        val statusCounts =
            orderedIds
                .mapNotNull { slimMapped[it]?.second }
                .groupingBy { it }
                .eachCount()
                .toSortedMap()
        val samples =
            orderedIds
                .take(5)
                .joinToString(separator = " | ") { id ->
                    val (release, status) = slimMapped.getValue(id)
                    val suffix = if (id in resolvedSlimById.keys) " resolved" else ""
                    "#$id ${release.year ?: "?"}/${release.season?.value?.value ?: release.season?.description ?: "?"} -> $status$suffix ${describeReleaseStatusInputs(
                        release,
                    )}"
                }
        val completedTitles =
            orderedIds
                .filter { slimMapped[it]?.second == Release.STATUS_CODE_COMPLETE }
                .mapNotNull { id ->
                    slimMapped[id]
                        ?.first
                        ?.name
                        ?.main
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }
                .joinToString(separator = " | ")

        return buildString {
            append("AniLiberty favorites ")
            append(sorting.value)
            append(": items=")
            append(overlappingIds.size)
            append(", ambiguous=")
            append(ambiguousIds.size)
            append(", statusCounts=")
            append(statusCounts)
            if (samples.isNotBlank()) {
                append(", samples=")
                append(samples)
            }
            if (completedTitles.isNotBlank()) {
                append(", completedTitles=")
                append(completedTitles)
            }
        }
    }

    private fun parseInstantOrNull(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value) }
            .getOrElse {
                runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            }
    }

    private fun deriveLegacyStatusCode(release: AniLibertyRelease): String {
        val latest =
            release.latestEpisode?.sortOrder?.takeIf { it > 0.0 }
                ?: release.latestEpisode?.ordinal?.takeIf { it > 0.0 }
        val hasPublishedEpisodes =
            latest != null ||
                release.episodes.orEmpty().isNotEmpty() ||
                (release.episodesTotal ?: 0) > 0
        val hasSchedule = release.publishDay?.value?.value != null

        if (release.isOngoing == true || release.isInProduction == true) {
            return Release.STATUS_CODE_PROGRESS
        }

        val hasExplicitStoppedState = release.isOngoing == false || release.isInProduction == false
        if (!hasExplicitStoppedState) {
            return Release.STATUS_CODE_NOTHING
        }

        return if (!hasPublishedEpisodes && hasSchedule) {
            Release.STATUS_CODE_NOT_ONGOING
        } else {
            Release.STATUS_CODE_COMPLETE
        }
    }

    private fun AniLibertyRelease.needsStatusResolutionFromFullRelease(): Boolean {
        val hasPublishedEpisodesHint =
            latestEpisode != null ||
                episodes.orEmpty().isNotEmpty() ||
                (episodesTotal ?: 0) > 0
        val hasExplicitStoppedState = isOngoing == false || isInProduction == false
        val hasSchedule = publishDay?.value?.value != null
        return hasExplicitStoppedState && !hasPublishedEpisodesHint && hasSchedule
    }

    private fun describeReleaseStatusInputs(release: AniLibertyRelease): String {
        val latest =
            release.latestEpisode?.sortOrder?.takeIf { it > 0.0 }
                ?: release.latestEpisode?.ordinal?.takeIf { it > 0.0 }
        val day = release.publishDay?.value?.value
        return "(ongoing=${release.isOngoing}, inProduction=${release.isInProduction}, total=${release.episodesTotal}, latest=$latest, hasEpisodes=${release.episodes.orEmpty().isNotEmpty()}, day=$day)"
    }
}

private class LiveAniLibertyAccountsClient(
    private val apiBaseUrl: HttpUrl,
    private val token: String,
) : IClient {
    @Volatile
    var lastResponseBodyBytes: Int = 0
        private set

    @Volatile
    var lastRequestUrl: HttpUrl? = null
        private set

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun get(
        url: String,
        args: Map<String, String>,
    ): String {
        return execute(
            method = "GET",
            httpUrl = rewriteUrl(url = url, args = args),
            body = null,
        )
    }

    override suspend fun post(
        url: String,
        args: Map<String, String>,
    ): String = error("Not used in smoke tests")

    override suspend fun put(
        url: String,
        args: Map<String, String>,
    ): String = error("Not used in smoke tests")

    override suspend fun delete(
        url: String,
        args: Map<String, String>,
    ): String = error("Not used in smoke tests")

    override suspend fun getFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse = error("Not used in smoke tests")

    override suspend fun postFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse = error("Not used in smoke tests")

    override suspend fun putFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse = error("Not used in smoke tests")

    override suspend fun deleteFull(
        url: String,
        args: Map<String, String>,
    ): NetworkResponse = error("Not used in smoke tests")

    override suspend fun getRaw(
        url: String,
        args: Map<String, String>,
    ): Response = error("Not used in smoke tests")

    override suspend fun postRaw(
        url: String,
        args: Map<String, String>,
    ): Response = error("Not used in smoke tests")

    override suspend fun postJson(
        url: String,
        jsonBody: String,
    ): String {
        val body = jsonBody.toRequestBody(jsonMediaType)
        return execute(
            method = "POST",
            httpUrl = rewriteUrl(url = url, args = emptyMap()),
            body = body,
        )
    }

    private fun execute(
        method: String,
        httpUrl: HttpUrl,
        body: RequestBody?,
    ): String {
        lastRequestUrl = httpUrl

        val requestBuilder =
            Request.Builder()
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
                error("Unexpected HTTP ${response.code} for ${httpUrl.encodedPath}")
            }
            val responseBody = response.body?.string().orEmpty()
            lastResponseBodyBytes = responseBody.toByteArray(Charsets.UTF_8).size
            return responseBody
        }
    }

    private fun rewriteUrl(
        url: String,
        args: Map<String, String>,
    ): HttpUrl {
        val sourceUrl = url.toHttpUrl()
        val sourcePath = sourceUrl.encodedPath.trimStart('/')
        val pathSuffix =
            if (sourcePath.startsWith("api/v1/")) {
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
