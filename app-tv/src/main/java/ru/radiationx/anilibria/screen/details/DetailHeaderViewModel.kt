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
import ru.radiationx.data.entity.common.AuthState
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.repository.AuthRepository
import ru.radiationx.data.repository.FavoriteRepository
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber
import javax.inject.Inject


/**
 * ViewModel для «шапки» (детальной части экрана),
 * показывающей большое изображение, описание, кнопки «Play», «Продолжить» и т.д.
 *
 * Замечание: чтобы при клике на LinkCard/LoadingCard не было "unresolved reference",
 * добавлены no-op методы onLinkCardClick() / onLoadingCardClick() / onLibriaCardClick().
 */
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


    /** Состояние детали для «шапки» (название, описание, постер, кнопки и т.д.) */
    val releaseData = MutableStateFlow<LibriaDetails?>(null)

    /** Отдельный стейт (прогресс, обновление и т.д.) */
    val progressState = MutableStateFlow(DetailsState())

    private var currentRelease: Release? = null
    private var isFullLoaded = false

    private var selectEpisodeJob: Job? = null
    private var favoriteJob: Job? = null

    private val releaseId = argExtra.id

    init {
        updateProgress()

        // быстрый локальный кеш (legacy)
        releaseInteractor.getItem(releaseId)?.also {
            updateRelease(it, emptyList(), v1Release.value)
        }

        // v1-first запрос
        viewModelScope.launch {
            coRunCatching {
                aniLibertyApi.getRelease(
                    idOrAlias = releaseId.id.toString(),
                    fields = AniLibertyReleaseFields.DetailsHeader
                )
            }.onSuccess { v1Release.value = it }
                .onFailure { Timber.e(it) }
        }

        // как только что-то меняется — пересобираем детали
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
        // Следим за «selectEpisodeRelay»
        selectEpisodeJob?.cancel()
        selectEpisodeJob = playerController
            .selectEpisodeRelay
            .onEach { episodeId ->
                // Переходим сразу на PlayerScreen
                router.navigateTo(PlayerScreen(releaseId, episodeId))
            }
            .launchIn(viewModelScope)
    }

    override fun onPause() {
        super.onPause()
        selectEpisodeJob?.cancel()
    }

    // --------------------------------
    // Основные методы (логика кнопок)
    // --------------------------------

    fun onContinueClick() {
        viewModelScope.launch {
            val accesses = releaseInteractor.getAccesses(releaseId)
            val lastEpisode = accesses.maxByOrNull { it.lastAccessRaw }
            lastEpisode?.also {
                router.navigateTo(PlayerScreen(releaseId, it.id))
            }
        }
    }

    /** Кнопка «Play» */
    fun onPlayClick() {
        val release = currentRelease ?: return
        if (release.episodes.isEmpty()) return

        if (release.episodes.size == 1) {
            router.navigateTo(PlayerScreen(releaseId, null))
        } else {
            // Если серий > 1, откроем «список серий»
            viewModelScope.launch {
                val episodeId = releaseInteractor.getAccesses(releaseId)
                    .maxByOrNull { it.lastAccessRaw }?.id
                guidedRouter.open(PlayerEpisodesGuidedScreen(releaseId, episodeId))
            }
        }
    }

    /** Кнопка «Избранное» */
    fun onFavoriteClick() {
        val release = currentRelease ?: return
        favoriteJob?.cancel()
        favoriteJob = viewModelScope.launch {
            // Если юзер не авторизован
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
                // Обновим локальный кэш
                currentRelease?.let { old ->
                    val newData = old.copy(favoriteInfo = updatedRelease.favoriteInfo)
                    releaseInteractor.updateFullCache(newData)
                }
            }.onFailure { Timber.e(it) }
            updateProgress()
        }
        updateProgress()
    }

    /** Кнопка «Описание» */
    fun onDescriptionClick() {
        // Пока заглушка
    }

    /** Кнопка «Другое» (сбросить просмотры, отметить как просмотрено и т.д.) */
    fun onOtherClick() {
        guidedRouter.open(DetailOtherGuidedScreen(releaseId))
    }

    // -----------------------------------
    // No-op методы, если DetailFragment
    // вызывает onLinkCardClick() / onLibriaCardClick()
    // -----------------------------------
    fun onLinkCardClick() { /* no-op */ }
    fun onLoadingCardClick() { /* no-op */ }
    fun onLibriaCardClick(card: LibriaCard) { /* no-op */ }

    // -----------------------------------
    // Вспомогательные приватные методы
    // -----------------------------------
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
            // v1 пришёл + есть legacy → overlay на legacy (и все action-флаги сохраняются)
            v1 != null && legacyDetails != null -> {
                aniOverlay.apply(legacyDetails, v1)
            }

            // v1 пришёл, legacy ещё нет → показываем v1 (действия пока прячем)
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

            // v1 нет → fallback на legacy
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
}
