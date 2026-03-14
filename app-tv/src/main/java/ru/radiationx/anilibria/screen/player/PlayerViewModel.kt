package ru.radiationx.anilibria.screen.player

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.PlayerEndEpisodeGuidedScreen
import ru.radiationx.anilibria.screen.PlayerEndSeasonGuidedScreen
import ru.radiationx.anilibria.screen.PlayerEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.PlayerQualityGuidedScreen
import ru.radiationx.anilibria.screen.PlayerSpeedGuidedScreen
import ru.radiationx.data.contracts.tv.TvPlayerFacade
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import javax.inject.Inject

sealed interface PlayerCommand {
    data object Play : PlayerCommand
    data object Pause : PlayerCommand
    data class Seek(val positionMs: Long) : PlayerCommand
    data class NextEpisodeSelected(val episodeId: EpisodeId) : PlayerCommand
}

class PlayerViewModel @Inject constructor(
    private val argExtra: PlayerExtra,
    private val tvPlayerFacade: TvPlayerFacade,
    private val preferencesHolder: PreferencesHolder,
    private val guidedRouter: GuidedRouter,
    private val playerController: PlayerController,
) : LifecycleViewModel() {

    private val _videoData = MutableStateFlow<Video?>(null)
    val videoData: StateFlow<Video?> = _videoData.asStateFlow()
    private val _qualityState = MutableStateFlow(preferencesHolder.playerQuality.value)
    val qualityState: StateFlow<PlayerQuality> = _qualityState.asStateFlow()
    private val _speedState = MutableStateFlow(preferencesHolder.playSpeed.value)
    val speedState: StateFlow<Float> = _speedState.asStateFlow()
    private val _commands = MutableSharedFlow<PlayerCommand>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val commands: SharedFlow<PlayerCommand> = _commands.asSharedFlow()

    private var currentReleases: List<Release> = emptyList()
    private var currentEpisodes: List<Episode> = emptyList()

    private var currentRelease: Release? = null
    private var currentEpisode: Episode? = null

    private var currentDuration: Long = 0L
    private var currentComplete: Boolean = false

    private var currentQuality: PlayerQuality = preferencesHolder.playerQuality.value
    private var currentSpeed: Float = preferencesHolder.playSpeed.value

    private var canSyncRemoteViews: Boolean = false

    init {
        // PlayerController — singleton. Сбрасываем данные, чтобы guided-экраны
        // не подхватывали список серий от предыдущего просмотра.
        playerController.reset()

        // Auth: включаем удалённую синхронизацию прогресса только если AUTH.
        tvPlayerFacade.observeAuthState()
            .onEach { canSyncRemoteViews = it == AuthState.AUTH }
            .launchIn(viewModelScope)

        // Quality
        preferencesHolder.playerQuality
            .onEach { quality ->
                currentQuality = quality
                _qualityState.value = quality
                updateEpisode()
            }
            .launchIn(viewModelScope)

        // Speed
        preferencesHolder.playSpeed
            .onEach { speed ->
                currentSpeed = speed
                _speedState.value = speed
            }
            .launchIn(viewModelScope)

        // Episode selection from guided screens (end-episode / episodes list)
        playerController.selectEpisodeRelay
            .onEach { episodeId ->
                val episode = currentEpisodes.firstOrNull { it.id == episodeId } ?: return@onEach
                playEpisode(episode)
            }
            .launchIn(viewModelScope)

        // Load initial release(s)
        viewModelScope.launch {
            val releases = tvPlayerFacade.loadWithFranchises(argExtra.releaseId)
            currentReleases = releases
            playerController.data.value = releases

            currentRelease = releases.firstOrNull { it.id == argExtra.releaseId } ?: releases.firstOrNull()
            currentEpisodes = releases.toPlaybackEpisodesOrder()

            val initialEpisodeId = argExtra.episodeId
                ?: runCatching {
                    tvPlayerFacade.getLocalContinueEpisodeId(argExtra.releaseId)
                }.getOrNull()
                ?: run {
                    // remote continue (AniLiberty) — best effort
                    if (tvPlayerFacade.getAuthState() == AuthState.AUTH) {
                        runCatching { tvPlayerFacade.getRemoteContinueEpisodeId(argExtra.releaseId) }.getOrNull()
                    } else {
                        null
                    }
                }

            val episode = currentEpisodes.firstOrNull { it.id == initialEpisodeId }
                ?: currentEpisodes.firstOrNull()

            episode?.also { playEpisode(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        playerController.bindPlayer()

        // Если плеер вернулся из бэкстека/конфига и данные уже есть — отдадим их в controller.
        if (currentReleases.isNotEmpty()) {
            playerController.data.value = currentReleases
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Экран плеера больше не активен (view уничтожен) — очищаем singleton-состояние,
        // иначе следующий экран может увидеть «чужие» серии.
        playerController.unbindPlayer()
    }

    override fun onCleared() {
        super.onCleared()
        // На всякий случай (если view уже уничтожен, а ViewModel очищается позже)
        playerController.unbindPlayer()
    }

    override fun onResume() {
        super.onResume()
        emitCommand(PlayerCommand.Play)
    }

    override fun onPause() {
        super.onPause()
        emitCommand(PlayerCommand.Pause)
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
            // If already completed, open end screens
            val accessSeek = tvPlayerFacade.getLocalEpisodeSeek(episode.id)
            currentComplete = accessSeek >= duration

            if (currentComplete) {
                getCurrentRelease()?.also { release ->
                    openEndGuidedScreen(release, episode)
                }
            } else {
                emitCommand(PlayerCommand.Play)
            }
        }
    }

    fun onComplete(position: Long) {
        currentComplete = true
        saveEpisodePosition(position)
        emitCommand(PlayerCommand.Pause)

        // Автоплей следующей серии (если включено и серия существует)
        val next = getNextEpisode()
        if (next != null && preferencesHolder.playerAutoplay.value) {
            // важно: сбросить флаг, иначе следующий эпизод может сохраниться как "просмотрен"
            currentComplete = false
            playEpisode(next)
            return
        }

        val release = getCurrentRelease() ?: return
        val episode = currentEpisode ?: return
        openEndGuidedScreen(release, episode)
    }


    fun onNextClick(position: Long) {
        saveEpisodePosition(position)
        val next = getNextEpisode() ?: return
        playEpisode(next)
        emitCommand(PlayerCommand.NextEpisodeSelected(next.id))
    }

    fun onPrevClick(position: Long) {
        saveEpisodePosition(position, syncRemote = false)
        val prev = getPrevEpisode() ?: return
        playEpisode(prev)
    }

    fun onQualityClick(position: Long) {
        saveEpisodePosition(position, syncRemote = false)
        guidedRouter.open(PlayerQualityGuidedScreen(getCurrentReleaseId() ?: return, currentEpisode?.id))
    }

    fun onSpeedClick() {
        guidedRouter.open(PlayerSpeedGuidedScreen(getCurrentReleaseId() ?: return, currentEpisode?.id))
    }

    fun onEpisodesClick(position: Long) {
        saveEpisodePosition(position, syncRemote = false)
        guidedRouter.open(PlayerEpisodesGuidedScreen(getCurrentReleaseId() ?: return, currentEpisode?.id))
    }

    private fun openEndGuidedScreen(release: Release, episode: Episode) {
        val next = getNextEpisode()

        if (next != null) {
            guidedRouter.open(PlayerEndEpisodeGuidedScreen(release.id, episode.id))
        } else {
            guidedRouter.open(PlayerEndSeasonGuidedScreen(release.id, episode.id))
        }
    }

    private fun saveEpisodePosition(position: Long, syncRemote: Boolean = true) {
        getCurrentRelease() ?: return
        val episode = currentEpisode ?: return

        // фиксируем значения ДО launch, чтобы переключение эпизода не ломало расчёт
        val snapshot = EpisodeProgressSnapshot(
            episodeId = episode.id,
            position = position,
            isWatched = currentComplete || (currentDuration > 0 && position >= currentDuration),
        )

        viewModelScope.launch {
            // local progress (legacy) — always
            tvPlayerFacade.saveLocalEpisodeSeek(snapshot.episodeId, snapshot.position)

            // remote progress (AniLiberty) — best effort
            syncEpisodeProgressToRemote(snapshot, syncRemote)
        }
    }

    private suspend fun syncEpisodeProgressToRemote(snapshot: EpisodeProgressSnapshot, syncRemote: Boolean) {
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
        currentEpisode = episode
        currentRelease = currentReleases.firstOrNull { it.id == episode.id.releaseId } ?: currentReleases.firstOrNull()
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

        viewModelScope.launch {
            val newUrl = episode.qualityInfo.getSafeUrlFor(quality)

            // local (legacy): always available
            val localSeek = tvPlayerFacade.getLocalEpisodeSeek(episode.id)

            // remote (AniLiberty): enables "continue on another device"
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
                // url тот же, но seek изменился — отправим одноразовую команду.
                emitCommand(PlayerCommand.Seek(newVideo.seek))
            }
        }
    }

    fun getCurrentReleaseId(): ReleaseId? {
        return currentEpisode?.id?.releaseId ?: argExtra.releaseId
    }

    fun hasNextEpisode(): Boolean = getNextEpisode() != null

    fun hasPreviousEpisode(): Boolean = getPrevEpisode() != null

    private data class EpisodeProgressSnapshot(
        val episodeId: EpisodeId,
        val position: Long,
        val isWatched: Boolean,
    )

    private fun emitCommand(command: PlayerCommand) {
        _commands.tryEmit(command)
    }
}
