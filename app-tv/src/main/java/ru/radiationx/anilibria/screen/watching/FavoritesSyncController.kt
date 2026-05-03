package ru.radiationx.anilibria.screen.watching

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase

internal class FavoritesSyncController(
    private val tvFavoritesUseCase: TvFavoritesUseCase,
    private val maxFavoritesSyncPages: Int = MAX_FAVORITES_SYNC_PAGES,
    private val maxUnchangedPages: Int = MAX_UNCHANGED_PAGES,
    private val minRefreshIntervalMs: Long = MIN_REFRESH_INTERVAL_MS,
) {
    fun shouldRefresh(
        lastSuccessfulSyncMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (lastSuccessfulSyncMs == 0L) {
            return true
        }
        return (nowMs - lastSuccessfulSyncMs) >= minRefreshIntervalMs
    }

    suspend fun loadAllFavoritesIncremental(onPartialLoaded: suspend (List<Release>) -> Unit): List<Release> {
        val result = LinkedHashMap<Int, Release>()
        var page = 1
        var unchangedPages = 0
        var shouldContinue = true

        while (shouldContinue && page <= maxFavoritesSyncPages && currentCoroutineContext().isActive) {
            val response = tvFavoritesUseCase.loadFavorites(page)
            val data = response.data
            if (data.isEmpty()) {
                break
            }

            val before = result.size
            data.forEach { release ->
                result[release.id.id] = release
            }
            val after = result.size

            if (after > before) {
                onPartialLoaded(result.values.toList())
            }

            val responsePage = response.page
            val responseAllPages = response.allPages
            val reachedLastPage =
                responsePage != null &&
                    responseAllPages != null &&
                    responsePage >= responseAllPages
            val hitUnchangedPagesLimit =
                if (after == before) {
                    unchangedPages += 1
                    unchangedPages >= maxUnchangedPages
                } else {
                    unchangedPages = 0
                    false
                }

            shouldContinue = !reachedLastPage && !hitUnchangedPagesLimit
            if (shouldContinue) {
                page += 1
            }
        }

        return result.values.toList()
    }

    private companion object {
        const val MIN_REFRESH_INTERVAL_MS = 2L * 60L * 1000L
        const val MAX_FAVORITES_SYNC_PAGES = 50
        const val MAX_UNCHANGED_PAGES = 2
    }
}
