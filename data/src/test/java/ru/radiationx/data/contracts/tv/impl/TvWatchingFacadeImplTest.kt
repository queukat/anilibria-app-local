package ru.radiationx.data.contracts.tv.impl

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.entity.domain.watching.UserViewHistoryItem
import ru.radiationx.data.entity.response.PaginatedResponse
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.HistoryRepository
import ru.radiationx.data.repository.UserViewsRepository

class TvWatchingFacadeImplTest {

    @Test
    fun probeRemoteAvailability_returnsHistoryAndContinueFlags() = runBlocking {
        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getViewsHistory(page = 1, limit = 25) } returns PaginatedResponse(
            data = listOf(
                UserViewHistoryItem(
                    releaseId = ReleaseId(1),
                    titleMain = "A",
                    titleEnglish = null,
                    titleAlternative = null,
                    posterPreview = null,
                    posterThumbnail = null,
                    episodeOrdinal = 1.0,
                    timeSeconds = 120.0,
                    isWatched = false,
                )
            ),
            meta = PaginatedResponse.PaginationResponse(
                page = 1,
                allPages = 1,
                perPage = 25,
                allItems = 1,
            ),
        )

        val facade = TvWatchingFacadeImpl(
            authRepository = mockk<AuthRepository>(relaxed = true),
            historyRepository = mockk<HistoryRepository>(relaxed = true),
            episodesCheckerHolder = mockk<EpisodesCheckerHolder>(relaxed = true),
            userViewsRepository = userViewsRepository,
        )

        val availability = facade.probeRemoteAvailability(limit = 25)

        assertTrue(availability.hasHistory)
        assertTrue(availability.hasContinue)
    }

    @Test
    fun probeRemoteAvailability_returnsFalseFlags_whenRepositoryFails() = runBlocking {
        val userViewsRepository = mockk<UserViewsRepository>()
        coEvery { userViewsRepository.getViewsHistory(page = 1, limit = 25) } throws IllegalStateException("offline")

        val facade = TvWatchingFacadeImpl(
            authRepository = mockk<AuthRepository>(relaxed = true),
            historyRepository = mockk<HistoryRepository>(relaxed = true),
            episodesCheckerHolder = mockk<EpisodesCheckerHolder>(relaxed = true),
            userViewsRepository = userViewsRepository,
        )

        val availability = facade.probeRemoteAvailability(limit = 25)

        assertFalse(availability.hasHistory)
        assertFalse(availability.hasContinue)
    }
}
