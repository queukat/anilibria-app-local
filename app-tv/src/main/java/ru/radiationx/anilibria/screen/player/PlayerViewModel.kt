package ru.radiationx.anilibria.screen.player

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.data.contracts.tv.TvPlayerFacade
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.release.isNearEpisodeEnd
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import timber.log.Timber
import javax.inject.Inject

sealed interface PlayerCommand {
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data class Seek(val positionMs: Long) : PlayerCommand
}

class PlayerViewModel @Inject constructor(
    private val argExtra: PlayerExtra,
    private val tvPlayerFacade: TvPlayerFacade,
    private val preferencesHolder: PreferencesHolder,
    private val router: Router,
) : LifecycleViewModel() {

    data class EpisodeOptionUiModel(
        val episodeId: EpisodeId,
        val label: String,
    )

    data class StartupFailure(
        val message: String,
        val shouldExitPlayer: Boolean = true,
    )

    private val _videoData = MutableStateFlow<Video?>(null)
    val videoData: StateFlow<Video?> = _videoData.asStateFlow()

    private val _qualityState = MutableStateFlow(preferencesHolder.playerQuality.value)
    val qualityState: StateFlow<PlayerQuality> = _qualityState.asStateFlow()

    private val _speedState = MutableStateFlow(preferencesHolder.playSpeed.value)
    val speedState: StateFlow<Float> = _speedState.asStateFlow()

    private val _availableQualities = MutableStateFlow<List<PlayerQuality>>(emptyList())
    val availableQualities: StateFlow<List<PlayerQuality>> = _availableQualities.asStateFlow()

    private val _availableSpeeds = MutableStateFlow(preferencesHolder.availableSpeeds.value)
    val availableSpeeds: StateFlow<List<Float>> = _availableSpeeds.asStateFlow()

    private val _episodeOptions = MutableStateFlow<List<EpisodeOptionUiModel>>(emptyList())
    val episodeOptions: StateFlow<List<EpisodeOptionUiModel>> = _episodeOptions.asStateFlow()

    private val _selectedEpisodeId = MutableStateFlow<EpisodeId?>(null)
    val selectedEpisodeId: StateFlow<EpisodeId?> = _selectedEpisodeId.asStateFlow()

    private val _commands = MutableSharedFlow<PlayerCommand>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val commands: SharedFlow<PlayerCommand> = _commands.asSharedFlow()

    private val _startupFailure = MutableStateFlow<StartupFailure?>(null)
    val startupFailure: StateFlow<StartupFailure?> = _startupFailure.asStateFlow()
    private val _completionOverlay = MutableStateFlow<PlayerCompletionOverlay?>(null)
    internal val completionOverlay: StateFlow<PlayerCompletionOverlay?> = _completionOverlay.asStateFlow()

    private var currentReleases: List<Release> = emptyList()
    private var currentEpisodes: List<Episode> = emptyList()

    private var currentRelease: Release? = null
    private var currentEpisode: Episode? = null

    private var currentDuration: Long = 0L
    private var currentComplete: Boolean = false
    private var promptedForCompletedResumeEpisodeId: EpisodeId? = null

    private var currentQuality: PlayerQuality = preferencesHolder.playerQuality.value
    private var canSyncRemoteViews: Boolean = false

    init {
        tvPlayerFacade.observeAuthState()
            .onEach { canSyncRemoteViews = it == AuthState.AUTH }
            .launchIn(viewModelScope)

        preferencesHolder.playerQuality
            .onEach { quality ->
                currentQuality = quality
                _qualityState.value = quality
                updateEpisode()
            }
            .launchIn(viewModelScope)

        preferencesHolder.playSpeed
            .onEach { speed ->
                _speedState.value = speed
            }
            .launchIn(viewModelScope)

        preferencesHolder.availableSpeeds
            .onEach { speeds ->
                _availableSpeeds.value = speeds
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val releases = runCatching {
                tvPlayerFacade.loadWithFranchises(argExtra.releaseId)
            }.onFailure { error ->
                Timber.e(error, "Player bootstrap failed for %s", argExtra.releaseId)
                _startupFailure.value = StartupFailure(
                    message = "Не удалось открыть серию. Проверьте подключение и попробуйте снова.",
                )
            }.getOrElse { emptyList() }
            if (releases.isEmpty()) {
                return@launch
            }
            currentReleases = releases

            currentRelease = releases.firstOrNull { it.id == argExtra.releaseId } ?: releases.firstOrNull()
            currentEpisodes = releases.toPlaybackEpisodesOrder()

            val initialEpisodeId = argExtra.episodeId
                ?: runCatching {
                    tvPlayerFacade.getLocalContinueEpisodeId(argExtra.releaseId)
                }.getOrNull()
                ?: run {
                    if (tvPlayerFacade.getAuthState() == AuthState.AUTH) {
                        runCatching { tvPlayerFacade.getRemoteContinueEpisodeId(argExtra.releaseId) }.getOrNull()
                    } else {
                        null
                    }
                }

            val episode = currentEpisodes.firstOrNull { it.id == initialEpisodeId }
                ?: currentEpisodes.firstOrNull()

            if (episode == null) {
                _startupFailure.value = StartupFailure(
                    message = "Эта серия пока недоступна для воспроизведения.",
                )
                return@launch
            }

            playEpisode(episode)
        }
    }

    fun onPauseClick(
        position: Long,
        syncRemote: Boolean,
    ) {
        saveEpisodePosition(position, syncRemote = syncRemote)
        emitCommand(PlayerCommand.Pause)
    }

    fun onExit(position: Long) {
        saveEpisodePosition(position, syncRemote = true)
        emitCommand(PlayerCommand.Pause)
    }

    fun onPrepare(duration: Long) {
        currentDuration = duration
        val episode = currentEpisode ?: return

        viewModelScope.launch {
            val accessSeek = _videoData.value?.seek ?: tvPlayerFacade.getLocalEpisodeSeek(episode.id)
            val completedSeek = isNearEpisodeEnd(
                positionMs = accessSeek,
                durationMs = duration,
            )
            currentComplete = completedSeek

            if (completedSeek) {
                emitCommand(PlayerCommand.Pause)
                if (promptedForCompletedResumeEpisodeId != episode.id) {
                    promptedForCompletedResumeEpisodeId = episode.id
                    showCompletionOverlay()
                }
            } else {
                promptedForCompletedResumeEpisodeId = null
                emitCommand(PlayerCommand.Play)
            }
        }
    }

    fun onComplete(position: Long) {
        currentComplete = true
        saveEpisodePosition(position)
        emitCommand(PlayerCommand.Pause)

        val next = getNextEpisode()
        if (next != null && preferencesHolder.playerAutoplay.value) {
            currentComplete = false
            playEpisode(next)
            return
        }

        showCompletionOverlay()
    }

    fun onNextClick(position: Long) {
        saveEpisodePosition(position)
        dismissCompletionOverlay()
        val next = getNextEpisode() ?: return
        playEpisode(next)
    }

    fun onPrevClick(position: Long) {
        saveEpisodePosition(position, syncRemote = false)
        val prev = getPrevEpisode() ?: return
        playEpisode(prev)
    }

    fun onEpisodeSelected(
        position: Long,
        episodeId: EpisodeId,
    ) {
        val targetEpisode = currentEpisodes.firstOrNull { it.id == episodeId } ?: return
        if (targetEpisode.id == currentEpisode?.id) {
            return
        }
        saveEpisodePosition(position)
        dismissCompletionOverlay()
        playEpisode(targetEpisode)
    }

    fun setQuality(
        position: Long,
        quality: PlayerQuality,
    ) {
        saveEpisodePosition(position, syncRemote = false)
        preferencesHolder.playerQuality.value = quality
    }

    fun setSpeed(speed: Float) {
        preferencesHolder.playSpeed.value = speed
    }

    fun dismissCompletionOverlay() {
        _completionOverlay.value = null
    }

    fun onReplayEpisodeClick() {
        val episode = currentEpisode ?: return
        viewModelScope.launch {
            tvPlayerFacade.saveLocalEpisodeSeek(episode.id, 0L)
            playEpisode(episode)
        }
    }

    fun onNextEpisodeClick() {
        val nextEpisode = getNextEpisode() ?: return
        playEpisode(nextEpisode)
    }

    fun onReplaySeasonClick() {
        val firstEpisode = currentEpisodes.firstOrNull() ?: return
        playEpisode(firstEpisode)
    }

    fun onClosePlayerClick() {
        dismissCompletionOverlay()
        router.exit()
    }

    private fun saveEpisodePosition(position: Long, syncRemote: Boolean = true) {
        getCurrentRelease() ?: return
        val episode = currentEpisode ?: return

        val snapshot = EpisodeProgressSnapshot(
            episodeId = episode.id,
            position = position,
            isWatched = currentComplete || isNearEpisodeEnd(
                positionMs = position,
                durationMs = currentDuration,
            ),
        )

        viewModelScope.launch {
            tvPlayerFacade.saveLocalEpisodeSeek(snapshot.episodeId, snapshot.position)
            syncEpisodeProgressToRemote(snapshot, syncRemote)
        }
    }

    private suspend fun syncEpisodeProgressToRemote(
        snapshot: EpisodeProgressSnapshot,
        syncRemote: Boolean,
    ) {
        if (!syncRemote || !canSyncRemoteViews) return

        val remotePosition = if (snapshot.isWatched) 0L else snapshot.position
        runCatching {
            tvPlayerFacade.saveRemoteEpisodeProgress(
                episodeId = snapshot.episodeId,
                positionMs = remotePosition,
                isWatched = snapshot.isWatched,
            )
        }
    }

    private fun playEpisode(episode: Episode) {
        promptedForCompletedResumeEpisodeId = null
        currentComplete = false
        currentDuration = 0L
        dismissCompletionOverlay()
        currentEpisode = episode
        currentRelease = currentReleases.firstOrNull { it.id == episode.id.releaseId } ?: currentReleases.firstOrNull()
        _episodeOptions.value = currentEpisodes.map(::toEpisodeOptionUiModel)
        _selectedEpisodeId.value = episode.id
        updateEpisode(force = true)
    }

    private fun getCurrentRelease(): Release? {
        return currentRelease ?: currentReleases.firstOrNull()
    }

    private fun getNextEpisode(): Episode? {
        val current = currentEpisode ?: return null
        val idx = currentEpisodes.indexOfFirst { it.id == current.id }
        if (idx < 0) return null
        return currentEpisodes.getOrNull(idx + 1)
    }

    private fun getPrevEpisode(): Episode? {
        val current = currentEpisode ?: return null
        val idx = currentEpisodes.indexOfFirst { it.id == current.id }
        if (idx < 0) return null
        return currentEpisodes.getOrNull(idx - 1)
    }

    private fun updateEpisode(force: Boolean = false) {
        val release = getCurrentRelease() ?: return
        val episode = currentEpisode ?: return
        val quality = currentQuality
        _availableQualities.value = episode.qualityInfo.available.toList()
        _qualityState.value = episode.qualityInfo.getActualFor(quality) ?: quality

        viewModelScope.launch {
            val newUrl = episode.qualityInfo.getSafeUrlFor(quality)

            val localSeek = tvPlayerFacade.getLocalEpisodeSeek(episode.id)
            val remoteSeek = if (canSyncRemoteViews) {
                runCatching { tvPlayerFacade.getRemoteEpisodeSeek(episode.id) }.getOrDefault(0L)
            } else {
                0L
            }
            val seek = maxOf(localSeek, remoteSeek)

            val newVideo = Video(
                url = newUrl,
                seek = seek,
                title = release.title.orEmpty(),
                subtitle = episode.title.orEmpty(),
                skips = episode.skips,
            )

            if (force || _videoData.value?.url != newVideo.url) {
                _videoData.value = newVideo
            } else if (_videoData.value?.seek != newVideo.seek) {
                emitCommand(PlayerCommand.Seek(newVideo.seek))
            }
        }
    }

    fun getCurrentReleaseId(): ReleaseId? {
        return currentEpisode?.id?.releaseId ?: argExtra.releaseId
    }

    fun hasNextEpisode(): Boolean = getNextEpisode() != null

    fun hasPreviousEpisode(): Boolean = getPrevEpisode() != null

    fun consumeStartupFailure() {
        _startupFailure.value = null
    }

    private fun showCompletionOverlay() {
        _completionOverlay.value = if (getNextEpisode() != null) {
            PlayerCompletionOverlay.EpisodeComplete
        } else {
            PlayerCompletionOverlay.SeasonComplete
        }
    }

    private data class EpisodeProgressSnapshot(
        val episodeId: EpisodeId,
        val position: Long,
        val isWatched: Boolean,
    )

    private fun emitCommand(command: PlayerCommand) {
        _commands.tryEmit(command)
    }

    private fun toEpisodeOptionUiModel(episode: Episode): EpisodeOptionUiModel {
        val ordinalLabel = episode.id.id.trim().ifBlank { "?" }
        val rawTitleLabel = episode.title?.trim().orEmpty()
        val titleLabel = rawTitleLabel
            .removePrefix("$ordinalLabel •")
            .removePrefix("$ordinalLabel.")
            .removePrefix("$ordinalLabel ")
            .trim()
        val label = if (
            titleLabel.isNotBlank() &&
            titleLabel != ordinalLabel &&
            !titleLabel.equals("серия $ordinalLabel", ignoreCase = true)
        ) {
            "$ordinalLabel • $titleLabel"
        } else {
            "Серия $ordinalLabel"
        }
        return EpisodeOptionUiModel(
            episodeId = episode.id,
            label = label,
        )
    }
}
