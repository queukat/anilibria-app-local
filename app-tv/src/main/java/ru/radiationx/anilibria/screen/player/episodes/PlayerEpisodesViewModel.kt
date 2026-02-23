package ru.radiationx.anilibria.screen.player.episodes

import androidx.lifecycle.viewModelScope
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.radiationx.anilibria.common.fragment.GuidedRouter
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.anilibria.screen.PlayerScreen
import ru.radiationx.anilibria.screen.player.PlayerController
import ru.radiationx.anilibria.screen.player.PlayerExtra
import ru.radiationx.anilibria.screen.player.sortedByEpisodeOrdinalAsc
import ru.radiationx.data.entity.domain.release.EpisodeAccess
import ru.radiationx.data.entity.domain.release.Release
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.interactors.ReleaseInteractor
import ru.radiationx.shared.ktx.asTimeSecString
import java.util.Date
import javax.inject.Inject

class PlayerEpisodesViewModel @Inject constructor(
    private val argExtra: PlayerExtra,
    private val releaseInteractor: ReleaseInteractor,
    private val guidedRouter: GuidedRouter,
    private val playerController: PlayerController,
    private val router: Router,
) : LifecycleViewModel() {

    val episodesData = MutableStateFlow<List<Group>>(emptyList())
    val selectedActionId = MutableStateFlow(-1L)

    init {
        val releasesFlow = if (playerController.isPlayerActive) {
            // Внутри плеера: используем данные из PlayerController (там могут быть франшизы/сезоны).
            // Если данные ещё не успели загрузиться — временно показываем только текущий релиз.
            playerController.data.flatMapLatest { releases ->
                if (releases != null) {
                    flowOf(releases)
                } else {
                    releaseInteractor.observeFull(argExtra.releaseId).map { listOf(it) }
                }
            }
        } else {
            // Снаружи плеера (например, из Details): всегда грузим релиз по аргументу.
            // Даже если в PlayerController остались данные от прошлого просмотра.
            releaseInteractor.observeFull(argExtra.releaseId).map { listOf(it) }
        }

        releasesFlow
            .onEach { updateEpisodes(it) }
            .launchIn(viewModelScope)
    }

    fun applyEpisode(actionId: Long) {
        val action = episodesData.value.findAction { it.id == actionId } ?: return
        val episodeId = action.episodeId

        guidedRouter.close()

        if (playerController.isPlayerActive) {
            // Плеер уже открыт — просто переключаем серию
            playerController.selectEpisodeRelay.emit(episodeId)
        } else {
            // Плеера нет — открываем экран просмотра
            router.navigateTo(PlayerScreen(episodeId.releaseId, episodeId))
        }
    }

    private fun updateEpisodes(releases: List<Release>) {
        viewModelScope.launch {
            val accesses = releases
                .flatMap { releaseInteractor.getAccesses(it.id) }
                .associateBy { it.id }
            val groups = releases.toGroups(accesses)
            episodesData.value = groups
            selectedActionId.value = groups.findAction { it.episodeId == argExtra.episodeId }?.id ?: -1L
        }
    }

    private fun List<Group>.findAction(block: (Action) -> Boolean): Action? {
        forEach {
            val action = it.actions.find(block)
            if (action != null) {
                return action
            }
        }
        return null
    }

    private fun List<Release>.toGroups(accesses: Map<EpisodeId, EpisodeAccess>): List<Group> {
        var id = 0L
        return map { release ->
            val groupId = id++
            val actions = release.episodes.sortedByEpisodeOrdinalAsc().map { episode ->
                val access = accesses[episode.id]
                val description = access?.let(::formatEpisodeAccessDescription)
                Action(
                    id = id++,
                    episodeId = episode.id,
                    title = episode.title.orEmpty(),
                    description = description
                )
            }
            Group(
                id = groupId,
                title = release.title.orEmpty(),
                actions = actions
            )
        }
    }

    data class Group(
        val id: Long,
        val title: String,
        val actions: List<Action>,
    )

    data class Action(
        val id: Long,
        val episodeId: EpisodeId,
        val title: String,
        val description: String?,
    )
}

internal fun formatEpisodeAccessDescription(access: EpisodeAccess): String? {
    return when {
        !access.isViewed -> null
        access.seek > 0L -> "Остановлена на ${Date(access.seek).asTimeSecString()}"
        else -> "Просмотрено"
    }
}
