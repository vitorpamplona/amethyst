/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.desktop.media

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One shared-UI player handle. See [ComposeMediaPlayerEngine] for why several
 * handles share one decoder.
 */
class ComposeMediaPlayerHandle internal constructor(
    private val engine: ComposeMediaPlayerEngine,
) : Player {
    private val listeners = CopyOnWriteArrayList<Player.Listener>()

    private var item: MediaItem? = null
    private var wantsToPlay = false
    private var released = false

    /** The engine, but only while this handle owns it. */
    private val state: VideoPlayerState?
        get() = if (engine.bound === this) engine.engineOrNull() else null

    override val isPlaying: Boolean get() = state?.isPlaying == true

    override val currentMediaItem: MediaItem? get() = item

    override val currentPosition: Long get() = state?.let { secondsToMs(it.currentTime) } ?: 0L

    /**
     * ComposeMediaPlayer exposes no separate buffered position, so this reports
     * the play position. The progress bar then shows no buffer-ahead bar rather
     * than an invented one.
     */
    override val bufferedPosition: Long get() = currentPosition

    override val duration: Long
        get() {
            val seconds = state?.duration ?: return C.TIME_UNSET
            return if (seconds > 0.0) secondsToMs(seconds) else C.TIME_UNSET
        }

    override val playbackState: Int
        get() {
            val engineState = state ?: return Player.STATE_IDLE
            return when {
                engineState.isLoading -> Player.STATE_BUFFERING
                engineState.hasMedia -> Player.STATE_READY
                else -> Player.STATE_IDLE
            }
        }

    override val playerError: PlaybackException?
        get() =
            state?.error?.let {
                // The desktop backends report a reason string, not media3's
                // taxonomy, so everything lands on the unspecified code and the
                // UI shows its generic playback-failed copy with the message.
                PlaybackException(it.toString(), null, PlaybackException.ERROR_CODE_UNSPECIFIED)
            }

    /** No track enumeration on desktop; the UI's "unknown tracks" path applies. */
    override val currentTracks: Tracks get() = Tracks.EMPTY

    override val videoSize: VideoSize
        get() {
            val ratio = state?.aspectRatio ?: return VideoSize.UNKNOWN
            return if (ratio > 0f) VideoSize(width = 0, height = 0, pixelWidthHeightRatio = ratio) else VideoSize.UNKNOWN
        }

    override val isReleased: Boolean get() = released

    override var volume: Float = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            field = clamped
            state?.volume = clamped
            listeners.forEach { it.onVolumeChanged(clamped) }
        }

    override var playWhenReady: Boolean
        get() = wantsToPlay
        set(value) {
            if (value) play() else pause()
        }

    override var repeatMode: Int = Player.REPEAT_MODE_OFF

    override var trackSelectionParameters: TrackSelectionParameters = TrackSelectionParameters.DEFAULT

    override fun setMediaItem(mediaItem: MediaItem) {
        item = mediaItem
    }

    override fun prepare() = Unit

    override fun play() {
        if (released) return
        val uri = item?.uri ?: return
        wantsToPlay = true
        val engineState = engine.bind(this)
        engineState.volume = volume
        engineState.openUri(uri)
        engineState.play()
        notifyPlaying(true)
    }

    override fun pause() {
        wantsToPlay = false
        state?.pause()
        notifyPlaying(false)
    }

    override fun stop() {
        wantsToPlay = false
        state?.stop()
        engine.unbind(this)
        notifyPlaying(false)
    }

    override fun release() {
        if (released) return
        released = true
        stop()
        listeners.clear()
    }

    /**
     * media3 seeks in milliseconds; ComposeMediaPlayer seeks in per-mille of
     * the whole media, so a seek is impossible until the duration is known.
     * Dropping it is better than seeking to an arbitrary point.
     */
    override fun seekTo(positionMs: Long) {
        val engineState = state ?: return
        val totalMs = duration
        if (totalMs <= 0L) return
        engineState.seekTo((positionMs.toFloat() / totalMs * 1000f).coerceIn(0f, 1000f))
    }

    /** Desktop never sleeps on Amethyst's behalf; there is no wake lock to take. */
    override fun setWakeMode(wakeMode: Int) = Unit

    /** Scaling is decided by the surface's ContentScale, not by the engine. */
    override fun setVideoScalingMode(scalingMode: Int) = Unit

    override fun addListener(listener: Player.Listener) {
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
    }

    /** Called when another handle takes the engine away from this one. */
    internal fun onUnbound() {
        wantsToPlay = false
        notifyPlaying(false)
    }

    private fun notifyPlaying(playing: Boolean) {
        listeners.forEach {
            it.onIsPlayingChanged(playing)
            it.onPlaybackStateChanged(playbackState)
        }
    }

    private fun secondsToMs(seconds: Double): Long = (seconds * 1000.0).toLong().coerceAtLeast(0L)
}
