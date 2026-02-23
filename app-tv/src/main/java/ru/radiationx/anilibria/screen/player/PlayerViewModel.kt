package ru.radiationx.anilibria.screen.player

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
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
import ru.radiationx.data.datasource.holders.PreferencesHolder
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.UserViewsRepository
import timber.log.Timber
import javax.inject.Inject

class PlayerViewModel @Inject constructor(
    private val argExtra: PlayerExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val userViewsRepository: UserViewsRepository,
    private val authRepository: AuthRepository,
    private val preferencesHolder: PreferencesHolder,
    private val guidedRouter: GuidedRouter,
    private val playerController: PlayerController,
) : LifecycleViewModel() {

    val videoData = MutableStateFlow<Video?>(null)
    val seekState = MutableStateFlow<Long?>(null)

    val qualityState = MutableStateFlow<PlayerQuality?>(null)
    val speedState = MutableStateFlow<Float?>(null)
    val playAction = MutableStateFlow<Boolean?>(null)

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
        authRepository.observeAuthState()
            .onEach { canSyncRemoteViews = it == AuthState.AUTH }
            .launchIn(viewModelScope)

        // Quality
        preferencesHolder.playerQuality
            .onEach { quality ->
                currentQuality = quality
                qualityState.value = quality
                updateEpisode()
            }
            .launchIn(viewModelScope)

        // Speed
        preferencesHolder.playSpeed
            .onEach { speed ->
                currentSpeed = speed
                speedState.value = speed
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
            val releases = releaseInteractor.loadWithFranchises(argExtra.releaseId)
            currentReleases = releases
            playerController.data.value = releases

            currentRelease = releases.firstOrNull { it.id == argExtra.releaseId } ?: releases.firstOrNull()
            currentEpisodes = releases.flatMap { it.episodes }.sortedByEpisodeOrdinalAsc()

            val initialEpisodeId = argExtra.episodeId
                ?: runCatching {
                    // local continue (legacy)
                    releaseInteractor
                        .getAccesses(argExtra.releaseId)
                        .maxByOrNull { it.lastAccessRaw }
                        ?.id
                }.getOrNull()
                ?: run {
                    // remote continue (AniLiberty) — best effort
                    if (authRepository.getAuthState() == AuthState.AUTH) {
                        runCatching { userViewsRepository.findLatestEpisodeIdForRelease(argExtra.releaseId) }.getOrNull()
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
        playAction.value = true
    }

    override fun onPause() {
        super.onPause()
        playAction.value = false
    }

    fun onPauseClick(
        position: Long,
        syncRemote: Boolean,
    ) {
        saveEpisodePosition(position, syncRemote = syncRemote)
        playAction.value = false
    }

    fun onExit(position: Long) {
        saveEpisodePosition(position, syncRemote = true)
        playAction.value = false
    }

    fun onPrepare(duration: Long) {
        currentDuration = duration
        val episode = currentEpisode ?: return

        viewModelScope.launch {
            // If already completed, open end screens
            val access = releaseInteractor.getAccess(episode.id)
            currentComplete = access != null && access.seek >= duration

            if (currentComplete) {
                getCurrentRelease()?.also { release ->
                    openEndGuidedScreen(release, episode)
                }
            } else {
                playAction.value = true
            }
        }
    }

    fun onComplete(position: Long) {
        currentComplete = true
        saveEpisodePosition(position)
        playAction.value = false

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
            releaseInteractor.setAccessSeek(snapshot.episodeId, snapshot.position)

            // remote progress (AniLiberty) — best effort
            syncEpisodeProgressToRemote(snapshot, syncRemote)
        }
    }

    private suspend fun syncEpisodeProgressToRemote(snapshot: EpisodeProgressSnapshot, syncRemote: Boolean) {
        if (!syncRemote || !canSyncRemoteViews) return

        val remotePosition = if (snapshot.isWatched) 0L else snapshot.position
        runCatching {
            userViewsRepository.upsertEpisodeTimecode(
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
            val localSeek = releaseInteractor.getAccess(episode.id)?.seek ?: 0L

            // remote (AniLiberty): enables "continue on another device"
            val remoteSeek = if (canSyncRemoteViews) {
                runCatching { userViewsRepository.getEpisodeTimecode(episode.id)?.positionMs ?: 0L }.getOrDefault(0L)
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

            if (force || videoData.value?.url != newVideo.url) {
                videoData.value = newVideo
            } else if (videoData.value?.seek != newVideo.seek) {
                // url тот же, но seek изменился — отправим «одноразовый» сигнал.
                seekState.value = newVideo.seek
            }
        }
    }

    fun getCurrentReleaseId(): ReleaseId? {
        return currentEpisode?.id?.releaseId ?: argExtra.releaseId
    }

    private data class EpisodeProgressSnapshot(
        val episodeId: EpisodeId,
        val position: Long,
        val isWatched: Boolean,
    )
}
