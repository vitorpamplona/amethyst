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
package androidx.media3.common

/**
 * JVM stand-in for androidx.media3.common.Player.
 *
 * Amethyst's whole video UI — every control, overlay and visibility rule —
 * talks to this interface and nothing deeper; only the twelve files under
 * `service/playback/{playerPool,service,diskCache,websocket}` reach into
 * ExoPlayer itself, and those are Android-only by construction. So this is the
 * seam: implement `Player` on the JVM and the 37 UI files work unchanged.
 *
 * Declared as the subset the app actually calls rather than media3's full
 * interface. That is deliberate — a member missing here fails at compile time
 * on the JVM target and names exactly what a desktop engine still has to
 * provide, which is the same fail-closed property the rest of the stubs have.
 */
interface Player {
    val isPlaying: Boolean
    val currentMediaItem: MediaItem?
    val currentPosition: Long
    val bufferedPosition: Long
    val duration: Long
    val playbackState: Int
    val playerError: PlaybackException?
    val currentTracks: Tracks
    val videoSize: VideoSize
    val isReleased: Boolean

    var volume: Float
    var playWhenReady: Boolean
    var repeatMode: Int
    var trackSelectionParameters: TrackSelectionParameters

    fun play()

    fun pause()

    fun stop()

    fun prepare()

    fun release()

    fun seekTo(positionMs: Long)

    fun setMediaItem(mediaItem: MediaItem)

    fun setWakeMode(wakeMode: Int)

    fun setVideoScalingMode(scalingMode: Int)

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    /**
     * Every callback defaults to doing nothing, exactly as media3's does, so a
     * listener overriding only the two events it cares about still compiles.
     */
    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean) = Unit

        fun onPlaybackStateChanged(playbackState: Int) = Unit

        fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) = Unit

        fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) = Unit

        fun onTracksChanged(tracks: Tracks) = Unit

        fun onVideoSizeChanged(videoSize: VideoSize) = Unit

        fun onVolumeChanged(volume: Float) = Unit

        fun onPlayerError(error: PlaybackException) = Unit

        fun onPlayerErrorChanged(error: PlaybackException?) = Unit

        fun onTimelineChanged(
            timeline: Timeline,
            reason: Int,
        ) = Unit

        fun onPositionDiscontinuity(
            oldPosition: PositionInfo,
            newPosition: PositionInfo,
            reason: Int,
        ) = Unit
    }

    class PositionInfo(
        val positionMs: Long = 0L,
        val mediaItemIndex: Int = 0,
    )

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4

        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2

        const val MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0
        const val MEDIA_ITEM_TRANSITION_REASON_AUTO = 1
        const val MEDIA_ITEM_TRANSITION_REASON_SEEK = 2
        const val MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3

        const val PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1

        const val DISCONTINUITY_REASON_AUTO_TRANSITION = 0
        const val DISCONTINUITY_REASON_SEEK = 1
    }
}
