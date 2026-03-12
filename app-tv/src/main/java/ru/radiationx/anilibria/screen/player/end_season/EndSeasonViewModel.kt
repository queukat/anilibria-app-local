package ru.radiationx.anilibria.screen.player.end_season

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.player.PlayerController
import ru.radiationx.anilibria.screen.player.PlayerExtra
import ru.radiationx.anilibria.screen.player.sortedByEpisodeOrdinalAsc
import ru.radiationx.data.entity.domain.release.Episode
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.data.interactors.tv.TvReleaseUseCase
import javax.inject.Inject

class EndSeasonViewModel @Inject constructor(
    private val argExtra: PlayerExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val tvReleaseUseCase: TvReleaseUseCase,
    private val guidedRouter: GuidedRouter,
    private val playerController: PlayerController,
    private val router: Router,
) : LifecycleViewModel() {

    private val currentEpisodes = mutableListOf<Episode>()
    private val currentEpisode
        get() = currentEpisodes.firstOrNull { it.id == argExtra.episodeId }

    init {
        viewModelScope.launch {
            runCatching { tvReleaseUseCase.loadRelease(argExtra.releaseId) }.getOrNull()?.also {
                currentEpisodes.clear()
                currentEpisodes.addAll(it.episodes.sortedByEpisodeOrdinalAsc())
            }
        }
    }

    fun onReplayEpisodeClick() {
        val episode = currentEpisode ?: return
        viewModelScope.launch {
            releaseInteractor.setAccessSeek(episode.id, 0)
            playerController.selectEpisodeRelay.emit(episode.id)
            guidedRouter.close()
        }
    }

    fun onReplaySeasonClick() {
        guidedRouter.close()
        currentEpisodes.firstOrNull()?.also { firstEpisode ->
            playerController.selectEpisodeRelay.emit(firstEpisode.id)
        }
    }

    fun onCloseClick() {
        guidedRouter.finishGuidedChain()
        router.exit()
    }

}
