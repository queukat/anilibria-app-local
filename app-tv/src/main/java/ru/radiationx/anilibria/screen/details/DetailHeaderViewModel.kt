package ru.radiationx.anilibria.screen.details

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.DetailDataConverter
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.screen.AuthScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.PlayerScreen
import ru.radiationx.anilibria.screen.player.formatEpisodeAccessDescription
import ru.radiationx.anilibria.screen.player.sortedByEpisodeOrdinalAsc
import ru.radiationx.anilibria.screen.watching.pickLatestLocalProgressOrNull
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.TvDetailHeaderUseCase
import ru.radiationx.data.interactors.tv.TvReleaseUseCase
import timber.log.Timber
import javax.inject.Inject

class DetailHeaderViewModel
    @Inject
    constructor(
        argExtra: DetailExtra,
        private val releaseInteractor: ReleaseInteractor,
        private val tvReleaseUseCase: TvReleaseUseCase,
        private val tvDetailHeaderUseCase: TvDetailHeaderUseCase,
        private val converter: DetailDataConverter,
        private val router: Router,
        private val tvContentUseCase: TvContentUseCase,
    ) : LifecycleViewModel() {
        private val releaseId: ReleaseId = argExtra.id

        private val _releaseData = MutableStateFlow<LibriaDetails?>(null)
        val releaseData: StateFlow<LibriaDetails?> = _releaseData.asStateFlow()
        private val _progressState = MutableStateFlow(DetailsState(loadingProgress = true))
        val progressState: StateFlow<DetailsState> = _progressState.asStateFlow()
        private val _overlayState = MutableStateFlow<DetailOverlayState?>(null)
        internal val overlayState: StateFlow<DetailOverlayState?> = _overlayState.asStateFlow()

        private val remoteFavoriteState = MutableStateFlow<Boolean?>(null)

        private var currentRelease: Release? = null

        private var favoriteJob: Job? = null
        private var favoriteStateJob: Job? = null

        init {
            combine(
                tvReleaseUseCase.observeRelease(releaseId),
                releaseInteractor.observeAccesses(releaseId),
                remoteFavoriteState,
            ) { release, accesses, remoteFavorite ->
                Triple(release, accesses, remoteFavorite)
            }
                .onEach { (release, accesses, remoteFavorite) ->
                    val cachedIsFavorite =
                        releaseInteractor
                            .getItem(releaseId = releaseId)
                            ?.favoriteInfo
                            ?.isAdded == true

                    val resolvedIsFavorite = remoteFavorite ?: (cachedIsFavorite || release.favoriteInfo.isAdded)
                    val shouldPatch = release.favoriteInfo.isAdded != resolvedIsFavorite
                    val resolvedRelease =
                        if (shouldPatch) {
                            release.copy(
                                favoriteInfo =
                                    release.favoriteInfo.copy(
                                        isAdded = resolvedIsFavorite,
                                    ),
                            )
                        } else {
                            release
                        }

                    if (shouldPatch) {
                        releaseInteractor.updateFullCache(resolvedRelease)
                    }

                    currentRelease = resolvedRelease

                    _releaseData.value =
                        converter.toDetailsUiState(
                            releaseItem = resolvedRelease,
                            accesses = accesses,
                        )
                    if (_progressState.value.loadingProgress) {
                        _progressState.value = _progressState.value.copy(loadingProgress = false)
                    }
                }
                .launchIn(viewModelScope)

            favoriteStateJob =
                viewModelScope.launch {
                    if (!tvDetailHeaderUseCase.isAuthorized()) return@launch

                    val isFavorite: Boolean? =
                        runCatching {
                            tvContentUseCase.loadFavoriteState(releaseId)
                        }.getOrElse { error ->
                            Timber.w(error, "AniLiberty: failed to load favorite ids for $releaseId")
                            null
                        }

                    remoteFavoriteState.value = isFavorite
                }
        }

        fun onContinueClick() {
            viewModelScope.launch {
                val localEpisodeId =
                    runCatching {
                        pickLatestLocalProgressOrNull(releaseInteractor.getAccesses(releaseId))?.id
                    }.getOrNull()

                if (localEpisodeId != null) {
                    router.navigateTo(PlayerScreen(releaseId, localEpisodeId))
                }
            }
        }

        fun onPlayClick() {
            val release = currentRelease ?: return
            if (release.episodes.isEmpty()) return

            // Если серия одна — открываем ее явно, чтобы плеер не падал в franchise-wide fallback.
            if (release.episodes.size == 1) {
                router.navigateTo(PlayerScreen(releaseId, release.episodes.first().id))
                return
            }

            viewModelScope.launch {
                val localEpisodeId =
                    runCatching {
                        pickLatestLocalProgressOrNull(releaseInteractor.getAccesses(releaseId))?.id
                    }.getOrNull()

                _overlayState.value = release.toEpisodePickerOverlay(localEpisodeId)
            }
        }

        fun onFavoriteClick() {
            val release = currentRelease ?: return

            favoriteJob?.cancel()
            favoriteJob =
                viewModelScope.launch {
                    if (!tvDetailHeaderUseCase.isAuthorized()) {
                        router.navigateTo(AuthScreen())
                        return@launch
                    }

                    _progressState.value = _progressState.value.copy(updateProgress = true)

                    try {
                        val wasFavorite = _releaseData.value?.isFavorite ?: release.favoriteInfo.isAdded

                        if (wasFavorite) {
                            tvDetailHeaderUseCase.deleteFavorite(releaseId)
                        } else {
                            tvDetailHeaderUseCase.addFavorite(releaseId)
                        }

                        remoteFavoriteState.value = !wasFavorite

                        val rating = release.favoriteInfo.rating
                        val newRating =
                            when {
                                wasFavorite -> (rating - 1).coerceAtLeast(0)
                                else -> rating + 1
                            }

                        val updatedRelease =
                            release.copy(
                                favoriteInfo =
                                    release.favoriteInfo.copy(
                                        rating = newRating,
                                        isAdded = !wasFavorite,
                                    ),
                            )

                        currentRelease = updatedRelease
                        releaseInteractor.updateFullCache(updatedRelease)
                    } catch (error: Throwable) {
                        Timber.e(error)
                    } finally {
                        _progressState.value = _progressState.value.copy(updateProgress = false)
                    }
                }
        }

        fun onDescriptionClick() {
            val details = _releaseData.value ?: return

            val title = details.titleRu.ifBlank { "Описание" }
            val message = details.description.ifBlank { "Описание отсутствует" }

            _overlayState.value =
                DetailOverlayState.Description(
                    title = title,
                    message = message,
                )
        }

        fun onOtherClick() {
            _overlayState.value = DetailOverlayState.Other
        }

        fun onEpisodeSelected(actionId: Long) {
            val overlay = _overlayState.value as? DetailOverlayState.EpisodePicker ?: return
            val action =
                overlay.groups
                    .asSequence()
                    .flatMap { it.actions.asSequence() }
                    .firstOrNull { it.id == actionId }
                    ?: return
            dismissOverlay()
            router.navigateTo(PlayerScreen(action.episodeId.releaseId, action.episodeId))
        }

        fun dismissOverlay() {
            _overlayState.value = null
        }

        override fun onCleared() {
            super.onCleared()
            favoriteJob?.cancel()
            favoriteStateJob?.cancel()
        }

        private suspend fun Release.toEpisodePickerOverlay(seedEpisodeId: EpisodeId?): DetailOverlayState.EpisodePicker {
            val accesses = releaseInteractor.getAccesses(id).associateBy { it.id }
            var nextId = 0L
            val actions =
                episodes.sortedByEpisodeOrdinalAsc().map { episode ->
                    DetailOverlayState.EpisodePicker.Action(
                        id = nextId++,
                        episodeId = episode.id,
                        title = episode.title.orEmpty(),
                        description = accesses[episode.id]?.let(::formatEpisodeAccessDescription),
                    )
                }
            return DetailOverlayState.EpisodePicker(
                groups =
                    listOf(
                        DetailOverlayState.EpisodePicker.Group(
                            id = 0L,
                            title = title.orEmpty(),
                            actions = actions,
                        ),
                    ),
                selectedActionId = actions.firstOrNull { it.episodeId == seedEpisodeId }?.id ?: -1L,
            )
        }
    }
