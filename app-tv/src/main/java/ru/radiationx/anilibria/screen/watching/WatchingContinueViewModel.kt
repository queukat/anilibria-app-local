package ru.radiationx.anilibria.screen.watching

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import ru.radiationx.anilibria.common.BaseCardsViewModel
import ru.radiationx.anilibria.common.CardsDataConverter
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaCardRouter
import ru.radiationx.data.datasource.holders.EpisodesCheckerHolder
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.repository.HistoryRepository
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(FlowPreview::class)
class WatchingContinueViewModel
    @Inject
    constructor(
        private val converter: CardsDataConverter,
        private val historyRepository: HistoryRepository,
        private val episodesCheckerHolder: EpisodesCheckerHolder,
        private val cardRouter: LibriaCardRouter,
    ) : BaseCardsViewModel() {
        override val defaultTitle: String = "Продолжить просмотр"

        private val autoRefreshSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        init {
            autoRefreshSignals
                .debounce(AUTO_REFRESH_DEBOUNCE_MS)
                .onEach { onRefreshClick() }
                .launchIn(viewModelScope)

            episodesCheckerHolder.observeEpisodes()
                .map(::toLocalProgressState)
                .distinctUntilChanged()
                .onEach { requestAutoRefresh() }
                .launchIn(viewModelScope)

            historyRepository.observeReleases()
                .map { history -> history.items.map { release -> release.id } }
                .distinctUntilChanged()
                .onEach { requestAutoRefresh() }
                .launchIn(viewModelScope)
        }

        override fun onLibriaCardClick(card: LibriaCard) {
            cardRouter.navigate(card)
        }

        override suspend fun getLoader(requestPage: Int): List<LibriaCard> {
            if (requestPage != firstPage) {
                return emptyList()
            }

            return try {
                loadLocalContinue()
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    throw error
                }
                throw error
            }
        }

        override fun hasMoreCards(
            newCards: List<LibriaCard>,
            allCards: List<LibriaCard>,
        ): Boolean = false

        private fun requestAutoRefresh() {
            autoRefreshSignals.tryEmit(Unit)
        }

        private fun toLocalProgressState(episodes: List<EpisodeAccess>): LocalProgressState {
            val latestByRelease =
                episodes
                    .groupBy { it.id.releaseId }
                    .mapValues { (_, accesses) ->
                        pickLatestLocalProgressOrNull(accesses) ?: accesses.first()
                    }

            return LocalProgressState(latestByRelease = latestByRelease)
        }

        private fun buildLocalContinueDescription(access: EpisodeAccess): String {
            val episodeOrdinal = normalizeOrdinalOrNull(access.id.id)
            val positionText =
                access.seek
                    .takeIf { position -> position > 0L }
                    ?.let(::formatPosition)

            return when {
                episodeOrdinal != null && positionText != null ->
                    "Вы остановились на серии $episodeOrdinal • $positionText"

                episodeOrdinal != null ->
                    "Вы остановились на серии $episodeOrdinal"

                positionText != null ->
                    "Вы остановились • $positionText"

                else ->
                    "Вы остановились"
            }
        }

        private fun normalizeOrdinalOrNull(value: String): String? {
            return normalizeEpisodeOrdinal(value)
        }

        private fun formatPosition(positionMs: Long): String {
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(positionMs).coerceAtLeast(0L)
            val hours = TimeUnit.SECONDS.toHours(totalSeconds)
            val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % SECONDS_IN_MINUTE
            val seconds = totalSeconds % SECONDS_IN_MINUTE

            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        }

        private suspend fun loadLocalContinue(): List<LibriaCard> {
            val latestByRelease = episodesCheckerHolder.getEpisodes()
                .groupBy { access -> access.id.releaseId }
                .mapValues { (_, accesses) ->
                    pickLatestLocalProgressOrNull(accesses) ?: accesses.first()
                }

            if (latestByRelease.isEmpty()) return emptyList()

            val releasesById = historyRepository.getReleases().items.associateBy { release -> release.id }

            return latestByRelease.entries
                .sortedByDescending { (_, access) -> access.lastAccessRaw }
                .mapNotNull { (releaseId, lastEpisode) ->
                    val release = releasesById[releaseId] ?: return@mapNotNull null
                    converter.toCard(release).copy(
                        description = buildLocalContinueDescription(lastEpisode),
                    )
                }
        }

        private companion object {
            private const val AUTO_REFRESH_DEBOUNCE_MS = 250L
            private const val SECONDS_IN_MINUTE = 60L
        }

        private data class LocalProgressState(
            val latestByRelease: Map<ReleaseId, EpisodeAccess>,
        )
    }
