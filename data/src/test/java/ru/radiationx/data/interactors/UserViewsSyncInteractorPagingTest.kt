package ru.radiationx.data.interactors

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.radiationx.data.datasource.holders.AuthTokenHolder
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.datasource.holders.HistoryHolder
import ru.radiationx.data.datasource.holders.UserViewsSyncHolder
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyUserViewHistoryItem
import ru.radiationx.data.entity.response.PaginatedResponse
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking

class UserViewsSyncInteractorPagingTest {

    @Test
    fun sync_lightImportStopsAtConfiguredMaxPages() = runBlocking {
        val token = "stream3-token"
        val tokenHash = sha256(token)
        val requestedPages = mutableListOf<Int>()

        val aniLibertyApi = mockk<AniLibertyApi>()
        coEvery { aniLibertyApi.getUserViewsHistory(any(), any(), any()) } answers {
            val page = firstArg<Int>()
            requestedPages += page
            historyPage(
                page = page,
                allPages = 999,
                items = listOf(historyItem(index = page, releaseId = page)),
            )
        }

        val authTokenHolder = mockk<AuthTokenHolder>()
        coEvery { authTokenHolder.getToken() } returns token

        val episodesCheckerHolder = mockk<EpisodesCheckerHolder>(relaxed = true)
        coEvery { episodesCheckerHolder.getEpisodes() } returns emptyList()
        coEvery { episodesCheckerHolder.putAllEpisode(any()) } just Runs
        coEvery { episodesCheckerHolder.putAllEpisodeBatched(any(), any(), any()) } just Runs

        val historyHolder = mockk<HistoryHolder>(relaxed = true)
        coEvery { historyHolder.getIds() } returns emptyList()
        coEvery { historyHolder.putAllIds(any()) } just Runs
        coEvery { historyHolder.putAllIdsBatched(any(), any(), any()) } just Runs

        val syncHolder = mockk<UserViewsSyncHolder>(relaxed = true)
        coEvery { syncHolder.getLastUploadTokenHash() } returns tokenHash
        coEvery { syncHolder.getLastFullImportTokenHash() } returns tokenHash

        val interactor = UserViewsSyncInteractor(
            aniLibertyApi = aniLibertyApi,
            authTokenHolder = authTokenHolder,
            episodesCheckerHolder = episodesCheckerHolder,
            historyHolder = historyHolder,
            syncHolder = syncHolder,
        )

        interactor.syncIfNeeded()

        assertEquals(listOf(1, 2, 3), requestedPages)
        coVerify(exactly = 3) {
            aniLibertyApi.getUserViewsHistory(any(), any(), any())
        }
    }

    private fun historyPage(
        page: Int,
        allPages: Int,
        items: List<AniLibertyUserViewHistoryItem>,
    ): PaginatedResponse<AniLibertyUserViewHistoryItem> = PaginatedResponse(
        data = items,
        meta = PaginatedResponse.PaginationResponse(
            page = page,
            allPages = allPages,
            perPage = 50,
            allItems = allPages * 50,
        ),
    )

    private fun historyItem(
        index: Int,
        releaseId: Int,
    ): AniLibertyUserViewHistoryItem {
        val releaseEpisodeId = AniLibertyReleaseEpisodeId("episode-$index")
        val aniReleaseId = AniLibertyReleaseId(releaseId)
        return AniLibertyUserViewHistoryItem(
            releaseEpisodeId = releaseEpisodeId,
            releaseId = aniReleaseId,
            time = 10.0,
            isWatched = false,
            createdAt = null,
            updatedAt = null,
            releaseEpisode = AniLibertyUserViewHistoryItem.ReleaseEpisodeWithRelease(
                id = releaseEpisodeId,
                name = null,
                ordinal = index.toDouble(),
                ending = null,
                opening = null,
                preview = null,
                hls480 = null,
                hls720 = null,
                hls1080 = null,
                duration = 1_200.0,
                rutubeId = null,
                youtubeId = null,
                updatedAt = null,
                sortOrder = null,
                releaseId = aniReleaseId,
                nameEnglish = null,
                release = null,
            ),
        )
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return buildString(bytes.size * 2) {
            bytes.forEach { b ->
                append(((b.toInt() and 0xFF).toString(16)).padStart(2, '0'))
            }
        }
    }
}
