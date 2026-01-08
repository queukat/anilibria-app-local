package ru.radiationx.anilibria.screen.details

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.AniLibertyDetailDataConverter
import ru.radiationx.anilibria.common.AniLibertyDetailsOverlay
import ru.radiationx.anilibria.common.DetailDataConverter
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.LibriaCard
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.AuthGuidedScreen
import ru.radiationx.anilibria.screen.DetailOtherGuidedScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.PlayerEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.PlayerScreen
import ru.radiationx.anilibria.screen.player.PlayerController
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject

class DetailHeaderViewModel @Inject constructor(
    argExtra: DetailExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val favoriteRepository: FavoriteRepository,
    private val authRepository: AuthRepository,
    private val converter: DetailDataConverter,
    private val router: Router,
    private val guidedRouter: GuidedRouter,
    private val playerController: PlayerController,
    private val aniLibertyApi: AniLibertyApi,
    private val aniOverlay: AniLibertyDetailsOverlay,
    private val aniDetailConverter: AniLibertyDetailDataConverter,
) : LifecycleViewModel() {

    private val v1Release = MutableStateFlow<AniLibertyRelease?>(null)

    val releaseData = MutableStateFlow<LibriaDetails?>(null)
    val progressState = MutableStateFlow(DetailsState())

    private var currentRelease: Release? = null
    private var isFullLoaded = false

    private var selectEpisodeJob: Job? = null
    private var favoriteJob: Job? = null

    private val releaseId: ReleaseId = argExtra.id

    init {
        updateProgress()

        // fast local cache (legacy)
        releaseInteractor.getItem(releaseId)?.also {
            updateRelease(it, emptyList(), v1Release.value)
        }

        // v1-first request (new API)
        viewModelScope.launch {
            val key = releaseId.toAniLibertyKey()
            coRunCatching {
                aniLibertyApi.getRelease(
                    key = key,
                    fields = AniLibertyReleaseFields.DetailsHeader,
                )
            }.onSuccess { v1Release.value = it }
                .onFailure { Timber.e(it) }
        }

        // whenever something changes - rebuild details
        combine(
            releaseInteractor.observeFull(releaseId),
            releaseInteractor.observeAccesses(releaseId),
            v1Release
        ) { releaseFull, accesses, v1 ->
            isFullLoaded = true
            Triple(releaseFull, accesses, v1)
        }.onEach { (releaseFull, accesses, v1) ->
            updateRelease(releaseFull, accesses, v1)
        }.launchIn(viewModelScope)
    }

    override fun onResume() {
        super.onResume()
        selectEpisodeJob?.cancel()
        selectEpisodeJob = playerController
            .selectEpisodeRelay
            .onEach { episodeId ->
                router.navigateTo(PlayerScreen(releaseId, episodeId))
            }
            .launchIn(viewModelScope)
    }

    override fun onPause() {
        super.onPause()
        selectEpisodeJob?.cancel()
    }

    fun onContinueClick() {
        viewModelScope.launch {
            val accesses = releaseInteractor.getAccesses(releaseId)
            val lastEpisode = accesses.maxByOrNull { it.lastAccessRaw }
            lastEpisode?.also {
                router.navigateTo(PlayerScreen(releaseId, it.id))
            }
        }
    }

    fun onPlayClick() {
        val release = currentRelease ?: return
        if (release.episodes.isEmpty()) return

        if (release.episodes.size == 1) {
            router.navigateTo(PlayerScreen(releaseId, null))
        } else {
            viewModelScope.launch {
                val episodeId = releaseInteractor.getAccesses(releaseId)
                    .maxByOrNull { it.lastAccessRaw }?.id
                guidedRouter.open(PlayerEpisodesGuidedScreen(releaseId, episodeId))
            }
        }
    }

    fun onFavoriteClick() {
        val release = currentRelease ?: return
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch {
            if (authRepository.getAuthState() != AuthState.AUTH) {
                guidedRouter.open(AuthGuidedScreen())
                return@launch
            }
            coRunCatching {
                if (release.favoriteInfo.isAdded) {
                    favoriteRepository.deleteFavorite(releaseId)
                } else {
                    favoriteRepository.addFavorite(releaseId)
                }
            }.onSuccess { updatedRelease ->
                currentRelease?.let { old ->
                    val newData = old.copy(favoriteInfo = updatedRelease.favoriteInfo)
                    releaseInteractor.updateFullCache(newData)
                }
            }.onFailure { Timber.e(it) }
            updateProgress()
        }
        updateProgress()
    }

    fun onDescriptionClick() { /* no-op for now */ }

    fun onOtherClick() {
        guidedRouter.open(DetailOtherGuidedScreen(releaseId))
    }

    fun onLinkCardClick() { /* no-op */ }
    fun onLoadingCardClick() { /* no-op */ }
    fun onLibriaCardClick(card: LibriaCard) { /* no-op */ }

    private fun updateRelease(
        release: Release?,
        accesses: List<EpisodeAccess>,
        v1: AniLibertyRelease?,
    ) {
        currentRelease = release

        val hasViewed = accesses.any { it.isViewed }
        val isFavorite = release?.favoriteInfo?.isAdded == true

        val legacyDetails: LibriaDetails? = release?.let {
            converter.toDetail(it, isFullLoaded, accesses)
        }

        val details: LibriaDetails? = when {
            // v1 arrived + legacy exists -> overlay v1 onto legacy to keep action flags/progress
            v1 != null && legacyDetails != null -> {
                aniOverlay.apply(legacyDetails, v1)
            }

            // v1 arrived but no legacy yet -> show v1, hide actions for now
            v1 != null -> {
                val d = aniDetailConverter.toDetail(
                    releaseId = releaseId,
                    r = v1,
                    isFavorite = isFavorite,
                    hasViewed = hasViewed,
                )
                d.copy(
                    hasEpisodes = false,
                    hasWebPlayer = false,
                    hasFullHd = false,
                )
            }

            // no v1 -> fallback to legacy
            legacyDetails != null -> legacyDetails

            else -> null
        }

        releaseData.value = details
        updateProgress(details)
    }

    private fun updateProgress(details: LibriaDetails? = releaseData.value) {
        progressState.value = DetailsState(
            loadingProgress = (details == null),
            updateProgress = (favoriteJob?.isActive == true)
        )
    }

    private fun ReleaseId.toAniLibertyKey(): AniLibertyReleaseKey {
        // Prefer numeric id if it fits, otherwise fall back to alias string
        val raw = runCatching { this.id.toLong() }.getOrNull()
        return if (raw != null && raw > 0 && raw <= Int.MAX_VALUE.toLong()) {
            AniLibertyReleaseKey.id(raw.toInt())
        } else {
            AniLibertyReleaseKey.alias(this.id.toString())
        }
    }
}
