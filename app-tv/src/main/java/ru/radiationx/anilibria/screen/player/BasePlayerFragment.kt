package ru.radiationx.anilibria.screen.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.radiationx.data.entity.common.PlayerQuality
import ru.radiationx.data.player.PlayerDataSourceProvider
import ru.radiationx.quill.get
import java.util.concurrent.TimeUnit

open class BasePlayerFragment : Fragment() {

    protected val player: ExoPlayer?
        get() = playerState

    protected val skipsPart: PlayerSkipsPart?
        get() = skipsPartState

    private var playerState by mutableStateOf<ExoPlayer?>(null)
    private var skipsPartState by mutableStateOf<PlayerSkipsPart?>(null)

    private var controlsVisibleState by mutableStateOf(true)
    private var controlsFocusTargetState by mutableStateOf(PlayerOverlayFocusTarget.PlayPause)
    private var controlsFocusTokenState by mutableIntStateOf(1)
    private var lastFocusedControlState by mutableStateOf(PlayerOverlayFocusTarget.PlayPause)
    private var resumeFocusRestoreTargetState by mutableStateOf<PlayerOverlayFocusTarget?>(null)
    private var resumePlaybackAfterPauseState by mutableStateOf(false)

    private var titleState by mutableStateOf("")
    private var subtitleState by mutableStateOf("")
    private var qualityState by mutableStateOf(PlayerQuality.HD)
    private var speedState by mutableFloatStateOf(1f)
    private var aspectRatioModeState by mutableStateOf(PlayerAspectRatioMode.FIT)
    private var availableQualitiesState by mutableStateOf<List<PlayerQuality>>(emptyList())
    private var availableSpeedsState by mutableStateOf<List<Float>>(emptyList())
    private var canPreviousState by mutableStateOf(false)
    private var canNextState by mutableStateOf(false)

    private var isLoadingState by mutableStateOf(true)
    private var isBufferingState by mutableStateOf(false)
    private var isPlayingState by mutableStateOf(false)
    private var positionState by mutableLongStateOf(0L)
    private var durationState by mutableLongStateOf(0L)
    private var bufferedPositionState by mutableLongStateOf(0L)

    private var backPressedCallback: OnBackPressedCallback? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            isBufferingState = playbackState == Player.STATE_BUFFERING
            when (playbackState) {
                Player.STATE_READY -> {
                    isLoadingState = false
                    syncPlayerProgress()
                    onPreparePlaying()
                }

                Player.STATE_ENDED -> {
                    isLoadingState = false
                    showControls()
                    onCompletePlaying()
                }

                Player.STATE_BUFFERING -> Unit

                Player.STATE_IDLE -> {
                    if (playerState?.currentMediaItem != null) {
                        isLoadingState = true
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isPlayingState = isPlaying
            if (!isPlaying && playerState?.currentMediaItem != null) {
                showControls()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            isLoadingState = false
            isBufferingState = false
            context?.let { safeContext ->
                Toast.makeText(
                    safeContext,
                    "Ошибка при воспроизведении: ${error.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PlayerScreenContent(
                    player = playerState,
                    title = titleState,
                    subtitle = subtitleState,
                    controlsVisible = controlsVisibleState,
                    controlsFocusTarget = controlsFocusTargetState,
                    controlsFocusToken = controlsFocusTokenState,
                    isPlaying = isPlayingState,
                    isLoading = isLoadingState,
                    isBuffering = isBufferingState,
                    currentPositionMs = positionState,
                    durationMs = durationState,
                    bufferedPositionMs = bufferedPositionState,
                    qualityLabel = qualityState.toPlayerLabel(),
                    speedLabel = speedState.toPlayerLabel(),
                    aspectRatioMode = aspectRatioModeState,
                    availableQualities = availableQualitiesState,
                    availableSpeeds = availableSpeedsState,
                    availableAspectRatios = PlayerAspectRatioMode.entries.toList(),
                    canPrevious = canPreviousState,
                    canNext = canNextState,
                    skipsPart = skipsPartState,
                    onControlFocused = ::rememberFocusedControl,
                    onShowControls = ::showControls,
                    onAutoHideControls = ::hideControls,
                    onBackRequested = ::handleBackPressed,
                    onTogglePlayback = ::togglePlayback,
                    onSeekBack = { seekBy(-SEEK_DELTA_MS) },
                    onSeekForward = { seekBy(SEEK_DELTA_MS) },
                    onPreviousClick = { onPreviousAction(getCurrentPosition()) },
                    onNextClick = { onNextAction(getCurrentPosition()) },
                    onQualitySelected = { quality ->
                        onQualitySelected(
                            position = getCurrentPosition(),
                            quality = quality,
                        )
                    },
                    onSpeedSelected = ::onSpeedSelected,
                    onAspectRatioSelected = ::onAspectRatioSelected,
                    onEpisodesClick = { onEpisodesAction(getCurrentPosition()) },
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initializePlayer()
        initializePlayerUi()
        installBackHandler()
    }

    override fun onResume() {
        super.onResume()
        val restoreTarget = resumeFocusRestoreTargetState
        if (controlsVisibleState && restoreTarget != null) {
            showControls(restoreTarget)
        }
        if (resumePlaybackAfterPauseState && playerState?.currentMediaItem != null) {
            playerState?.play()
        }
        resumeFocusRestoreTargetState = null
        resumePlaybackAfterPauseState = false
    }

    override fun onPause() {
        super.onPause()
        if (controlsVisibleState) {
            resumeFocusRestoreTargetState = resumeFocusRestoreTargetState ?: lastFocusedControlState
        }
        resumePlaybackAfterPauseState = isPlayingState
        pausePlayback()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backPressedCallback?.remove()
        backPressedCallback = null
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        releasePlayer()
        skipsPartState = null
    }

    protected open fun onCompletePlaying() {}

    protected open fun onPreparePlaying() {}

    protected open fun onPreviousAction(position: Long) {}

    protected open fun onNextAction(position: Long) {}

    protected open fun onQualitySelected(
        position: Long,
        quality: PlayerQuality,
    ) {}

    protected open fun onSpeedSelected(speed: Float) {}

    protected open fun onAspectRatioSelected(mode: PlayerAspectRatioMode) {
        updatePlayerAspectRatio(mode)
    }

    protected open fun onEpisodesAction(position: Long) {}

    protected fun restoreEpisodesButtonFocusOnNextResume() {
        restoreControlsFocusOnNextResume(PlayerOverlayFocusTarget.Episodes)
    }

    private fun restoreControlsFocusOnNextResume(target: PlayerOverlayFocusTarget) {
        resumeFocusRestoreTargetState = target
    }

    protected fun updatePlayerInfo(
        title: String,
        subtitle: String,
    ) {
        titleState = title
        subtitleState = subtitle
    }

    protected fun updateNavigationState(
        canPrevious: Boolean,
        canNext: Boolean,
    ) {
        canPreviousState = canPrevious
        canNextState = canNext
    }

    protected fun updatePlayerQuality(quality: PlayerQuality) {
        qualityState = quality
    }

    protected fun updatePlayerSpeed(speed: Float) {
        speedState = speed
    }

    protected fun updatePlayerAspectRatio(mode: PlayerAspectRatioMode) {
        aspectRatioModeState = mode
    }

    protected fun updateAvailableQualities(qualities: List<PlayerQuality>) {
        availableQualitiesState = qualities
    }

    protected fun updateAvailableSpeeds(speeds: List<Float>) {
        availableSpeedsState = speeds
    }

    protected fun setPlayerLoading(loading: Boolean) {
        isLoadingState = loading
    }

    protected fun preparePlayer(
        url: String,
        startPositionMs: Long = 0L,
    ) {
        val player = playerState ?: return
        val safeStartPosition = startPositionMs.coerceAtLeast(0L)
        isLoadingState = true
        isBufferingState = true
        showControls()
        player.setMediaItem(
            MediaItem.fromUri(url),
            safeStartPosition,
        )
        syncProgressPosition(safeStartPosition)
        player.prepare()
    }

    protected fun playPlayback() {
        playerState?.play()
        showControls()
    }

    protected fun pausePlayback() {
        playerState?.pause()
    }

    protected fun seekToPosition(positionMs: Long) {
        val player = playerState ?: return
        val targetPosition = clampPosition(player, positionMs)
        player.seekTo(targetPosition)
        syncProgressPosition(targetPosition)
    }

    protected fun getCurrentPosition(): Long = playerState?.currentPosition ?: positionState

    protected fun getDurationValue(): Long = playerState?.duration?.takeIf { it > 0L } ?: durationState

    private fun initializePlayerUi() {
        skipsPartState = PlayerSkipsPart(onSeek = ::seekToPosition)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    syncPlayerProgress()
                    delay(PROGRESS_SYNC_INTERVAL_MS)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        check(playerState == null) { "Player already initialized" }

        val dataSourceProvider = get<PlayerDataSourceProvider>()
        val dataSourceType = dataSourceProvider.get()
        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), dataSourceType.factory)
        val mediaSourceFactory = DefaultMediaSourceFactory(requireContext()).apply {
            setDataSourceFactory(dataSourceFactory)
        }
        playerState = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(playerListener)
            }
    }

    private fun releasePlayer() {
        playerState?.removeListener(playerListener)
        playerState?.release()
        playerState = null
        isPlayingState = false
        isLoadingState = true
        isBufferingState = false
        positionState = 0L
        durationState = 0L
        bufferedPositionState = 0L
        resumePlaybackAfterPauseState = false
    }

    private fun installBackHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                this@BasePlayerFragment.handleBackPressed()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            backPressedCallback!!,
        )
    }

    private fun handleBackPressed() {
        when {
            skipsPartState?.isVisible == true -> skipsPartState?.cancelCurrent()
            controlsVisibleState -> hideControls()
            else -> {
                val callback = backPressedCallback ?: return
                callback.isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                callback.isEnabled = true
            }
        }
    }

    private fun togglePlayback() {
        if (isPlayingState) {
            pausePlayback()
        } else {
            playPlayback()
        }
    }

    private fun seekBy(deltaMs: Long) {
        seekToPosition(getCurrentPosition() + deltaMs)
    }

    private fun syncPlayerProgress() {
        val player = playerState ?: return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        positionState = if (duration > 0L) {
            position.coerceAtMost(duration)
        } else {
            position
        }
        durationState = duration
        bufferedPositionState = if (duration > 0L) {
            player.bufferedPosition.coerceIn(0L, duration)
        } else {
            player.bufferedPosition.coerceAtLeast(0L)
        }
        skipsPartState?.update(positionState)
    }

    private fun syncProgressPosition(positionMs: Long) {
        positionState = positionMs.coerceAtLeast(0L)
        skipsPartState?.update(positionState)
    }

    private fun showControls(target: PlayerOverlayFocusTarget? = null) {
        controlsVisibleState = true
        controlsFocusTargetState = target ?: lastFocusedControlState
        controlsFocusTokenState += 1
    }

    private fun hideControls() {
        controlsVisibleState = false
    }

    private fun rememberFocusedControl(target: PlayerOverlayFocusTarget) {
        lastFocusedControlState = target
    }

    private fun clampPosition(
        player: ExoPlayer,
        positionMs: Long,
    ): Long {
        val boundedPosition = positionMs.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0L } ?: return boundedPosition
        return boundedPosition.coerceAtMost(duration)
    }

    private fun PlayerQuality.toPlayerLabel(): String = when (this) {
        PlayerQuality.SD -> "SD"
        PlayerQuality.HD -> "HD"
        PlayerQuality.FULLHD -> "1080"
    }

    private fun Float.toPlayerLabel(): String {
        return if (this == 1.0f) {
            "1x"
        } else {
            "${this}x"
        }
    }

    private companion object {
        const val PROGRESS_SYNC_INTERVAL_MS = 250L
        val SEEK_DELTA_MS = TimeUnit.SECONDS.toMillis(10L)
    }
}
