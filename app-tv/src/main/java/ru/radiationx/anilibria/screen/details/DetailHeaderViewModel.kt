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
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.DetailHeaderRemoteData
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.TvDetailHeaderUseCase
import timber.log.Timber
import javax.inject.Inject

class DetailHeaderViewModel @Inject constructor(
    argExtra: DetailExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val tvDetailHeaderUseCase: TvDetailHeaderUseCase,
    private val converter: DetailDataConverter,
    private val router: Router,
    private val guidedRouter: GuidedRouter,
    private val tvContentUseCase: TvContentUseCase,
    private val aniOverlay: AniLibertyDetailsOverlay,
) : LifecycleViewModel() {

    private val releaseId: ReleaseId = argExtra.id

    val releaseData = MutableStateFlow<LibriaDetails?>(null)
    val progressState = MutableStateFlow(DetailsState(loadingProgress = true))

    private val v1ReleaseState = MutableStateFlow<DetailHeaderRemoteData?>(null)

    /**
     * Реальное состояние "в избранном" для пользователя (token-based, AniLiberty v1).
     * null = неизвестно/не удалось загрузить/не авторизован.
     */
    private val v1FavoriteState = MutableStateFlow<Boolean?>(null)

    private var currentRelease: Release? = null

    private var favoriteJob: Job? = null
    private var v1Job: Job? = null
    private var favoriteStateJob: Job? = null

    init {
        // Combine:
        //  - legacy release (старое API)
        //  - local accesses (local progress)
        //  - optional AniLiberty release (новое API, best-effort)
        //  - optional AniLiberty favorite state (token-based, best-effort)
        combine(
            releaseInteractor.observeFull(releaseId),
            releaseInteractor.observeAccesses(releaseId),
            v1ReleaseState,
            v1FavoriteState,
        ) { release, accesses, v1, v1Favorite ->
            Triple(release, accesses, v1 to v1Favorite)
        }
            .onEach { (release, accesses, v1Pair) ->
                val (v1, v1Favorite) = v1Pair

                // Быстрый локальный fallback:
                // если пришли из "Избранного" (items cache), там isAdded=true уже есть.
                val cachedIsFavorite = releaseInteractor
                    .getItem(releaseId = releaseId)
                    ?.favoriteInfo
                    ?.isAdded == true

                // Источник истины:
                // 1) token-based v1Favorite (если удалось)
                // 2) иначе: items-cache (если есть)
                // 3) иначе: legacy (как было раньше)
                val resolvedIsFavorite = v1Favorite ?: (cachedIsFavorite || release.favoriteInfo.isAdded)

                // Если legacy релиз не знает про избранное (token-only auth),
                // патчим только флаг isAdded, чтобы UI/клики работали корректно.
                val shouldPatch = release.favoriteInfo.isAdded != resolvedIsFavorite
                val resolvedRelease = if (shouldPatch) {
                    release.copy(
                        favoriteInfo = release.favoriteInfo.copy(
                            isAdded = resolvedIsFavorite
                        )
                    )
                } else {
                    release
                }

                if (shouldPatch) {
                    releaseInteractor.updateFullCache(resolvedRelease)
                }

                currentRelease = resolvedRelease

                val baseDetails = converter.toDetail(
                    releaseItem = resolvedRelease,
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
            val v1 = runCatching { tvContentUseCase.loadDetailHeaderRemote(releaseId) }
                .getOrElse { error ->
                    Timber.w(error, "AniLiberty: failed to load details header for $releaseId")
                    null
                }
            v1ReleaseState.value = v1
        }

        // Best-effort loading of "is in my favorites" via token (AniLiberty).
        favoriteStateJob = viewModelScope.launch {
            if (!tvDetailHeaderUseCase.isAuthorized()) return@launch

            val isFavorite: Boolean? = runCatching {
                tvContentUseCase.loadDetailFavoriteState(releaseId)
            }.getOrElse { error ->
                Timber.w(error, "AniLiberty: failed to load favorite ids for $releaseId")
                null
            }

            v1FavoriteState.value = isFavorite
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
            if (tvDetailHeaderUseCase.isAuthorized()) {
                val remoteEpisodeId =
                    runCatching { tvDetailHeaderUseCase.findLatestNotWatchedEpisodeIdForRelease(releaseId) }
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
                if (tvDetailHeaderUseCase.isAuthorized()) {
                    runCatching { tvDetailHeaderUseCase.findLatestEpisodeIdForRelease(releaseId) }.getOrNull()
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
            if (!tvDetailHeaderUseCase.isAuthorized()) {
                guidedRouter.open(AuthGuidedScreen())
                return@launch
            }

            progressState.value = progressState.value.copy(updateProgress = true)

            try {
                // Берём состояние из UI (оно уже "нормализовано" нашей логикой),
                // иначе fallback на legacy.
                val wasFavorite = releaseData.value?.isFavorite ?: release.favoriteInfo.isAdded

                // 1) server mutate (token-first repository)
                if (wasFavorite) {
                    tvDetailHeaderUseCase.deleteFavorite(releaseId)
                } else {
                    tvDetailHeaderUseCase.addFavorite(releaseId)
                }

                // Важно: обновляем override-состояние, чтобы combine не "откатил" текст кнопки.
                v1FavoriteState.value = !wasFavorite

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
        favoriteStateJob?.cancel()
    }
}
