
package ru.radiationx.anilibria.screen.details

import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.AniLibertyDetailsOverlay
import ru.radiationx.anilibria.common.DetailDataConverter
import ru.radiationx.anilibria.common.DetailsState
import ru.radiationx.anilibria.common.LibriaDetails
import ru.radiationx.anilibria.common.fragment.FakeGuidedStepFragment
import ru.radiationx.anilibria.common.fragment.GuidedAppScreen
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.AuthGuidedScreen
import ru.radiationx.anilibria.screen.DetailOtherGuidedScreen
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.PlayerEpisodesGuidedScreen
import ru.radiationx.anilibria.screen.PlayerScreen
import ru.radiationx.anilibria.screen.details.description.DetailDescriptionGuidedFragment
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyApi
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyRelease
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseFields
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseKey
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.data.repository.UserViewsRepository
import timber.log.Timber
import javax.inject.Inject

class DetailHeaderViewModel @Inject constructor(
    argExtra: DetailExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val favoriteRepository: FavoriteRepository,
    private val authRepository: AuthRepository,
    private val userViewsRepository: UserViewsRepository,
    private val converter: DetailDataConverter,
    private val router: Router,
    private val guidedRouter: GuidedRouter,
    private val aniLibertyApi: AniLibertyApi,
    private val aniOverlay: AniLibertyDetailsOverlay,
) : LifecycleViewModel() {

    private val releaseId: ReleaseId = argExtra.id

    val releaseData = MutableStateFlow<LibriaDetails?>(null)
    val progressState = MutableStateFlow(DetailsState(loadingProgress = true))

    private val v1ReleaseState = MutableStateFlow<AniLibertyRelease?>(null)

    private var currentRelease: Release? = null

    private var favoriteJob: Job? = null
    private var v1Job: Job? = null

    init {
        // Combine:
        //  - legacy release (старое API)
        //  - local accesses (local progress)
        //  - optional AniLiberty release (новое API, best-effort)
        combine(
            releaseInteractor.observeFull(releaseId),
            releaseInteractor.observeAccesses(releaseId),
            v1ReleaseState,
        ) { release, accesses, v1 ->
            Triple(release, accesses, v1)
        }
            .onEach { (release, accesses, v1) ->
                currentRelease = release

                val baseDetails = converter.toDetail(
                    releaseItem = release,
                    isFull = true,
                    accesses = accesses,
                )

                // Overlay from AniLiberty (if удалось загрузить) — иначе остаёмся на legacy.
                val details = v1?.let { aniOverlay.apply(baseDetails, it) } ?: baseDetails

                releaseData.value = details

                // Убираем "initial loading" как только получили хотя бы один результат.
                if (progressState.value.loadingProgress) {
                    progressState.value = progressState.value.copy(loadingProgress = false)
                }
            }
            .launchIn(viewModelScope)

        // Best-effort loading of AniLiberty details.
        v1Job = viewModelScope.launch {
            val v1 = runCatching {
                aniLibertyApi.getRelease(
                    key = AniLibertyReleaseKey.id(releaseId.id),
                    fields = AniLibertyReleaseFields.DetailsHeader,
                )
            }.getOrElse { error ->
                Timber.w(error, "AniLiberty: failed to load details header for $releaseId")
                null
            }
            v1ReleaseState.value = v1
        }
    }

    fun onContinueClick() {
        viewModelScope.launch {
            // 1) local progress (legacy) — primary
            val localEpisodeId = runCatching {
                releaseInteractor
                    .getAccesses(releaseId)
                    .maxByOrNull { it.lastAccessRaw }
                    ?.id
            }.getOrNull()

            if (localEpisodeId != null) {
                router.navigateTo(PlayerScreen(releaseId, localEpisodeId))
                return@launch
            }

            // 2) remote progress (AniLiberty) — fallback ("continue on another device")
            if (authRepository.getAuthState() == AuthState.AUTH) {
                val remoteEpisodeId =
                    runCatching { userViewsRepository.findLatestNotWatchedEpisodeIdForRelease(releaseId) }
                        .getOrNull()
                if (remoteEpisodeId != null) {
                    router.navigateTo(PlayerScreen(releaseId, remoteEpisodeId))
                }
            }
        }
    }

    fun onPlayClick() {
        val release = currentRelease ?: return
        if (release.episodes.isEmpty()) return

        // Если серия одна — просто запускаем плеер (episodeId = null безопасно).
        if (release.episodes.size == 1) {
            router.navigateTo(PlayerScreen(releaseId, null))
            return
        }

        // Если серий много — открываем выбор серий (guided).
        viewModelScope.launch {
            // 1) local seed (legacy)
            val localEpisodeId = runCatching {
                releaseInteractor
                    .getAccesses(releaseId)
                    .maxByOrNull { it.lastAccessRaw }
                    ?.id
            }.getOrNull()

            // 2) remote seed (AniLiberty) fallback
            val seedEpisodeId = localEpisodeId ?: run {
                if (authRepository.getAuthState() == AuthState.AUTH) {
                    runCatching { userViewsRepository.findLatestEpisodeIdForRelease(releaseId) }.getOrNull()
                } else {
                    null
                }
            }

            guidedRouter.open(PlayerEpisodesGuidedScreen(releaseId, seedEpisodeId))
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

            progressState.value = progressState.value.copy(updateProgress = true)

            try {
                val wasFavorite = release.favoriteInfo.isAdded

                // 1) server mutate (token-first repository)
                if (wasFavorite) {
                    favoriteRepository.deleteFavorite(releaseId)
                } else {
                    favoriteRepository.addFavorite(releaseId)
                }

                // 2) update local cached release (keep full legacy model intact)
                val rating = release.favoriteInfo.rating
                val newRating = when {
                    wasFavorite -> (rating - 1).coerceAtLeast(0)
                    else -> rating + 1
                }

                val updatedRelease = release.copy(
                    favoriteInfo = release.favoriteInfo.copy(
                        rating = newRating,
                        isAdded = !wasFavorite,
                    )
                )

                currentRelease = updatedRelease
                releaseInteractor.updateFullCache(updatedRelease)
            } catch (error: Throwable) {
                Timber.e(error)
            } finally {
                progressState.value = progressState.value.copy(updateProgress = false)
            }
        }
    }

    fun onDescriptionClick() {
        val details = releaseData.value ?: return

        val title = details.titleRu.ifBlank { "Описание" }
        val message = details.description.ifBlank { "Описание отсутствует" }

        guidedRouter.open(
            object : GuidedAppScreen() {
                override fun createFragment(factory: FragmentFactory): FakeGuidedStepFragment {
                    return DetailDescriptionGuidedFragment.newInstance(
                        title = title,
                        message = message,
                    )
                }
            }
        )
    }

    fun onOtherClick() {
        guidedRouter.open(DetailOtherGuidedScreen(releaseId))
    }

    override fun onCleared() {
        super.onCleared()
        favoriteJob?.cancel()
        v1Job?.cancel()
    }
}

