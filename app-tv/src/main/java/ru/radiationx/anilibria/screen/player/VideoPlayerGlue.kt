/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.radiationx.anilibria.screen.player

import android.content.Context
import android.view.KeyEvent
import android.view.View
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.widget.Action
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow
import androidx.leanback.widget.PlaybackControlsRow.FastForwardAction
import androidx.leanback.widget.PlaybackControlsRow.MultiAction
import androidx.leanback.widget.PlaybackControlsRow.RewindAction
import androidx.leanback.widget.PlaybackControlsRow.SkipNextAction
import androidx.leanback.widget.PlaybackControlsRow.SkipPreviousAction
import androidx.leanback.widget.PlaybackRowPresenter
import androidx.leanback.widget.PlaybackTransportRowPresenter
import androidx.leanback.widget.RowPresenter
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.leanback.LeanbackPlayerAdapter
import ru.radiationx.data.entity.common.PlayerQuality
import java.util.concurrent.TimeUnit


/**
 * Manages customizing the actions in the [PlaybackControlsRow].
 * Also adds custom actions for quality, speed, episodes, etc.
 */
@UnstableApi
class VideoPlayerGlue(
    context: Context,
    /**
     * Ссылка на наш [VideoSupportFragment], чтобы можно было управлять оверлеем.
     */
    private val fragment: VideoSupportFragment,
    playerAdapter: LeanbackPlayerAdapter,
) : PlaybackTransportControlGlue<LeanbackPlayerAdapter>(context, playerAdapter) {

    interface OnActionClickedListener {
        fun onPrevious()
        fun onNext()
        fun onQualityClick()
        fun onSpeedClick()
        fun onEpisodesClick()
    }

    interface PlaybackListener {
        fun onUpdateProgress()
    }

    var actionListener: OnActionClickedListener? = null
    var playbackListener: PlaybackListener? = null

    private val previousAction by lazy { SkipPreviousAction(context) }
    private val nextAction by lazy { SkipNextAction(context) }
    private val forwardAction by lazy { FastForwardAction(context) }
    private val rewindAction by lazy { RewindAction(context) }

    private val qualityAction by lazy { QualityAction(context) }
    private val speedAction by lazy { SpeedAction(context) }
    private val episodesAction by lazy { EpisodesAction(context) }

    init {
        // Leanback's built-in seek mode freezes progress updates until the user confirms with OK.
        // For TV scrubbing we want immediate seek behavior instead, so progress stays in sync.
        isSeekEnabled = false
    }

    override fun onCreateRowPresenter(): PlaybackRowPresenter {
        val detailsPresenter = object : AbstractDetailsDescriptionPresenter() {
            override fun onBindDescription(viewHolder: ViewHolder, obj: Any) {
                val glue = obj as VideoPlayerGlue
                viewHolder.title.text = glue.title
                viewHolder.subtitle.text = glue.subtitle
            }
        }
        return object : PlaybackTransportRowPresenter() {
            override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
                super.onBindRowViewHolder(vh, item)
                vh.setOnKeyListener(this@VideoPlayerGlue)
                bindImmediateSeekListener(vh.view)
            }

            override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
                clearImmediateSeekListener(vh.view)
                super.onUnbindRowViewHolder(vh)
                vh.setOnKeyListener(null)
            }
        }.apply {
            setDescriptionPresenter(detailsPresenter)
        }
    }

    override fun onCreatePrimaryActions(adapter: ArrayObjectAdapter) {
        super.onCreatePrimaryActions(adapter)
        adapter.add(previousAction)
        adapter.add(nextAction)
    }

    override fun onCreateSecondaryActions(adapter: ArrayObjectAdapter) {
        super.onCreateSecondaryActions(adapter)
        // По умолчанию иконка качества у нас стоит на HD
        qualityAction.index = 1

        adapter.add(qualityAction)
        adapter.add(speedAction)
        adapter.add(episodesAction)
    }

    override fun onActionClicked(action: Action) {
        if (shouldDispatchAction(action)) {
            dispatchAction(action)
        } else {
            super.onActionClicked(action)
        }
    }

    override fun onUpdateProgress() {
        super.onUpdateProgress()
        playbackListener?.onUpdateProgress()
    }

    /**
     * Вызывается, когда переключаемся между Play/Pause.
     * Если вы нажимаете аппаратную кнопку Play/Pause и `BasePlayerFragment` «глотает»
     * это событие, Leanback может не запустить «автоскрытие» оверлея автоматически.
     *
     * Чтобы этого не случилось, мы явно перезапускаем показ + автоскрытие.
     */
    override fun onPlayStateChanged() {
        super.onPlayStateChanged()
        // Показываем оверлей
        fragment.showControlsOverlay(false)
        // Принудительно перезапускаем таймер автоскрытия
        fragment.isControlsOverlayAutoHideEnabled = false
        fragment.isControlsOverlayAutoHideEnabled = true
    }

    private fun shouldDispatchAction(action: Action): Boolean {
        return action === rewindAction ||
                action === forwardAction ||
                action === qualityAction ||
                action === speedAction ||
                action === episodesAction
    }

    private fun dispatchAction(action: Action) {
        when {
            action === rewindAction -> rewind()
            action === forwardAction -> fastForward()
            action === qualityAction -> actionListener?.onQualityClick()
            action === speedAction -> actionListener?.onSpeedClick()
            action === episodesAction -> actionListener?.onEpisodesClick()
            action is MultiAction -> {
                action.nextIndex()
                val row = controlsRow ?: return
                (row.secondaryActionsAdapter as? ArrayObjectAdapter)?.also {
                    notifyActionChanged(action, it)
                }
            }
        }
    }

    private fun notifyActionChanged(action: MultiAction, adapter: ArrayObjectAdapter) {
        val index = adapter.indexOf(action)
        if (index >= 0) {
            adapter.notifyArrayItemRangeChanged(index, 1)
        }
    }

    override fun next() {
        actionListener?.onNext()
    }

    override fun previous() {
        actionListener?.onPrevious()
    }

    /** Skips backwards 10 seconds.  */
    fun rewind() {
        seekToImmediate(currentPosition - TEN_SECONDS)
    }

    /** Skips forward 10 seconds.  */
    fun fastForward() {
        seekToImmediate(currentPosition + TEN_SECONDS)
    }

    fun seekToImmediate(positionMs: Long) {
        val targetPosition = clampPosition(positionMs)
        playerAdapter?.seekTo(targetPosition)
        syncProgressPosition(targetPosition)
    }

    fun syncProgressPosition(positionMs: Long) {
        controlsRow?.currentPosition = clampPosition(positionMs)
    }

    /** Установить иконку качества (SD / HD / FULLHD) в панель управления. */
    fun setQuality(quality: PlayerQuality) {
        qualityAction.index = when (quality) {
            PlayerQuality.SD -> QualityAction.INDEX_SD
            PlayerQuality.HD -> QualityAction.INDEX_HD
            PlayerQuality.FULLHD -> QualityAction.INDEX_FHD
        }
        controlsRow?.let { row ->
            (row.secondaryActionsAdapter as? ArrayObjectAdapter)?.let { adapter ->
                notifyActionChanged(qualityAction, adapter)
            }
        }
    }

    private fun bindImmediateSeekListener(rootView: View) {
        rootView.findViewById<View>(androidx.leanback.R.id.playback_progress)?.setOnKeyListener(
            ::onProgressBarKey
        )
    }

    private fun clearImmediateSeekListener(rootView: View) {
        rootView.findViewById<View>(androidx.leanback.R.id.playback_progress)?.setOnKeyListener(
            null
        )
    }

    private fun onProgressBarKey(
        view: View,
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MINUS -> handleSeekKey(event, -TEN_SECONDS)

            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_PLUS -> handleSeekKey(event, TEN_SECONDS)

            else -> false
        }
    }

    private fun handleSeekKey(event: KeyEvent, deltaMs: Long): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            seekToImmediate(currentPosition + deltaMs)
        }
        return true
    }

    private fun clampPosition(positionMs: Long): Long {
        val boundedPosition = positionMs.coerceAtLeast(0L)
        return duration.takeIf { it > 0 }?.let { boundedPosition.coerceAtMost(it) }
            ?: boundedPosition
    }

    companion object {
        private val TEN_SECONDS = TimeUnit.SECONDS.toMillis(10)
    }
}
