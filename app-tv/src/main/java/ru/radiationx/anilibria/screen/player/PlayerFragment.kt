package ru.radiationx.anilibria.screen.player

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import com.github.terrakok.cicerone.Router
import kotlinx.coroutines.flow.filterNotNull
import ru.radiationx.anilibria.screen.DetailsScreen
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.entity.domain.types.EpisodeId
import ru.radiationx.data.entity.domain.types.ReleaseId
import ru.radiationx.quill.get
import ru.radiationx.quill.getViewModel
import ru.radiationx.shared.ktx.android.getExtra
import ru.radiationx.shared.ktx.android.getExtraNotNull
import ru.radiationx.shared.ktx.android.putExtra
import ru.radiationx.shared.ktx.android.subscribeTo

@OptIn(UnstableApi::class)
class PlayerFragment : BasePlayerFragment() {
    companion object {
        private const val ARG_RELEASE_ID = "release id"
        private const val ARG_EPISODE_ID = "episode id"

        fun newInstance(
            releaseId: ReleaseId,
            episodeId: EpisodeId?,
        ): PlayerFragment =
            PlayerFragment().putExtra {
                putParcelable(ARG_RELEASE_ID, releaseId)
                putParcelable(ARG_EPISODE_ID, episodeId)
            }
    }

    private val argumentsReleaseId by lazy { getExtraNotNull<ReleaseId>(ARG_RELEASE_ID) }

    private lateinit var router: Router
    private lateinit var viewModel: PlayerViewModel
    private var completionOverlayState by mutableStateOf<PlayerCompletionOverlay?>(null)

    override fun onAttach(context: Context) {
        super.onAttach(context)
        router = get()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel =
            getViewModel(PlayerViewModel::class) {
                PlayerExtra(
                    releaseId = argumentsReleaseId,
                    episodeId = getExtra(ARG_EPISODE_ID),
                )
            }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycle.addObserver(viewModel)
        updateNavigationState(
            canPrevious = viewModel.hasPreviousEpisode(),
            canNext = viewModel.hasNextEpisode(),
        )

        subscribeTo(viewModel.videoData.filterNotNull()) { video ->
            updatePlayerInfo(
                title = video.title,
                subtitle = video.subtitle,
            )
            updateNavigationState(
                canPrevious = viewModel.hasPreviousEpisode(),
                canNext = viewModel.hasNextEpisode(),
            )
            preparePlayer(video.url, video.seek)
            skipsPart?.setSkips(video.skips)
            skipsPart?.update(video.seek)
        }

        subscribeTo(viewModel.commands) { command ->
            when (command) {
                is PlayerCommand.Play -> playPlayback(revealControls = command.revealControls)
                PlayerCommand.Pause -> pausePlayback()
                is PlayerCommand.Seek ->
                    seekToPosition(
                        positionMs = command.positionMs,
                        reason = "view_model_command",
                    )
            }
        }

        subscribeTo(viewModel.completionOverlay) {
            completionOverlayState = it
        }

        subscribeTo(viewModel.speedState) { speedValue ->
            updatePlayerSpeed(speedValue)
            player?.playbackParameters = PlaybackParameters(speedValue)
        }

        subscribeTo(viewModel.availableSpeeds) { speeds ->
            updateAvailableSpeeds(speeds)
        }

        subscribeTo(viewModel.qualityState) { quality ->
            updatePlayerQuality(quality)
        }

        subscribeTo(viewModel.availableQualities) { qualities ->
            updateAvailableQualities(qualities)
        }

        subscribeTo(viewModel.episodeOptions) { options ->
            updateEpisodeOptions(
                options = options,
                selectedEpisodeId = viewModel.selectedEpisodeId.value,
            )
        }

        subscribeTo(viewModel.selectedEpisodeId) { selectedEpisodeId ->
            updateEpisodeOptions(
                options = viewModel.episodeOptions.value,
                selectedEpisodeId = selectedEpisodeId,
            )
        }

        subscribeTo(viewModel.startupFailure.filterNotNull()) { failure ->
            context?.let { safeContext ->
                Toast.makeText(safeContext, failure.message, Toast.LENGTH_LONG).show()
            }
            viewModel.consumeStartupFailure()
            if (failure.shouldExitPlayer && player?.currentMediaItem == null && isAdded) {
                router.exit()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPauseClick(getCurrentPosition(), syncRemote = false)
    }

    override fun onStop() {
        super.onStop()
        viewModel.onExit(getCurrentPosition())

        val newReleaseId = viewModel.getCurrentReleaseId() ?: return
        if (newReleaseId != argumentsReleaseId) {
            router.replaceScreen(DetailsScreen(newReleaseId))
        }
    }

    override fun onCompletePlaying() {
        viewModel.onComplete(getCurrentPosition())
        updateNavigationState(
            canPrevious = viewModel.hasPreviousEpisode(),
            canNext = viewModel.hasNextEpisode(),
        )
    }

    override fun onPreparePlaying() {
        viewModel.onPrepare(getDurationValue())
    }

    override fun onPreviousAction(position: Long) {
        viewModel.onPrevClick(position)
        updateNavigationState(
            canPrevious = viewModel.hasPreviousEpisode(),
            canNext = viewModel.hasNextEpisode(),
        )
    }

    override fun onNextAction(position: Long) {
        viewModel.onNextClick(position)
        updateNavigationState(
            canPrevious = viewModel.hasPreviousEpisode(),
            canNext = viewModel.hasNextEpisode(),
        )
    }

    override fun onQualitySelected(
        position: Long,
        quality: PlayerQuality,
    ) {
        viewModel.setQuality(position, quality)
    }

    override fun onSpeedSelected(speed: Float) {
        viewModel.setSpeed(speed)
    }

    override fun onEpisodeSelected(
        position: Long,
        episodeId: EpisodeId,
    ) {
        viewModel.onEpisodeSelected(position, episodeId)
        updateNavigationState(
            canPrevious = viewModel.hasPreviousEpisode(),
            canNext = viewModel.hasNextEpisode(),
        )
    }

    @Composable
    override fun RenderPlayerOverlay() {
        completionOverlayState?.let { overlay ->
            PlayerCompletionOverlayHost(
                overlay = overlay,
                onReplayEpisodeClick = viewModel::onReplayEpisodeClick,
                onNextEpisodeClick = viewModel::onNextEpisodeClick,
                onReplaySeasonClick = viewModel::onReplaySeasonClick,
                onClosePlayerClick = router::exit,
            )
        }
    }

    override fun handlePlayerOverlayBack(): Boolean {
        val overlay = completionOverlayState ?: return false
        viewModel.dismissCompletionOverlay()
        if (overlay == PlayerCompletionOverlay.EpisodeComplete && viewModel.hasNextEpisode()) {
            restoreNextEpisodeFocus()
        } else {
            restorePlayPauseFocus()
        }
        return true
    }
}
