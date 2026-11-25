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
package com.vitorpamplona.amethyst.commons.ui.playback.player

import androidx.compose.runtime.Immutable

/**
 * The player the shared video and audio UI drives. A subset of media3's `Player`, with the same
 * meaning for every member, so code moved from Android keeps its behaviour: Android wraps its
 * media3 `Player`/`MediaController`, iOS an `AVPlayer`, and the desktop could wrap its engine.
 *
 * Reads are cheap and safe from the main thread. Changes are announced through [Listener], on the
 * main thread, like media3 does.
 */
interface AmethystPlayer {
    /** The id of the loaded item: its URL. Null before anything is loaded. */
    val mediaId: String?

    /** 0f (muted) to 1f. */
    var volume: Float

    /** Whether the picture and sound are moving now (ready, wanted to play, not stalled). */
    val isPlaying: Boolean

    /** Whether it plays as soon as it can. */
    var playWhenReady: Boolean

    val playbackState: PlaybackState

    /** In milliseconds. */
    val currentPosition: Long

    /** In milliseconds, or [TIME_UNSET] when not known yet (or a live stream). */
    val duration: Long

    /** How far it has buffered, in milliseconds. */
    val bufferedPosition: Long

    /** The picture's size; [VideoSize.UNKNOWN] for audio or before the first frame. */
    val videoSize: VideoSize

    /** The failure that stopped playback, or null when healthy. */
    val error: PlayerFailure?

    /** 1f is normal speed. */
    var playbackSpeed: Float

    /** Loops the item forever when true. */
    var repeatOne: Boolean

    /**
     * Something outside the app holds playback back although [playWhenReady] is set: media3's
     * `playbackSuppressionReason` other than none (or scrubbing), e.g. a transient loss of audio
     * focus to a phone call. The play/pause button then offers play, as media3's own does.
     * Changes are announced through [Listener.onPlaybackSuppressionChanged].
     */
    val isPlaybackSuppressed: Boolean get() = false

    /** Whether [playbackSpeed] can be changed (media3's `COMMAND_SET_SPEED_AND_PITCH`). */
    val canSetPlaybackSpeed: Boolean get() = true

    fun play()

    fun pause()

    /** Loads the item again after an error or a release. */
    fun prepare()

    fun seekTo(positionMs: Long)

    /** Jumps to the live edge of a live stream, or the start of anything else. */
    fun seekToDefaultPosition()

    // Tracks

    /** The item carries sound and no picture. */
    val isAudioOnly: Boolean

    /**
     * The tracks list a picture. False until the player has read the tracks, so, with
     * [hasAudioTrack], it tells "audio only" apart from "not known yet".
     */
    val hasVideoTrack: Boolean

    /** The tracks list sound. False until the player has read the tracks. */
    val hasAudioTrack: Boolean

    /**
     * The renditions of the picture (HLS variants): every track of the first video group, so one
     * entry for a plain file and none for audio or before the tracks are known. Check
     * [VideoTrack.supported] before offering one.
     */
    val videoTracks: List<VideoTrack>

    /** Whether the user pinned a rendition instead of letting the player pick. */
    val hasVideoTrackOverride: Boolean

    fun selectVideoTrack(track: VideoTrack)

    fun clearVideoTrackOverride()

    /**
     * Lets the player pick renditions no larger than the view: [width]x[height] pixels. A zero
     * dimension (the view is not laid out yet) leaves the current constraint as it is, and pushing
     * the size the player already has does nothing.
     */
    fun constrainToViewport(
        width: Int,
        height: Int,
    )

    /** The caption tracks the item offers. */
    val textTracks: List<TextTrack>

    /**
     * Shows [track]'s captions (turning [captionsEnabled] on), or none when null (turning it off).
     */
    fun selectTextTrack(track: TextTrack?)

    /**
     * Whether caption tracks are shown at all: media3's `TRACK_TYPE_TEXT` not disabled. Separate
     * from the pick in [selectTextTrack], which stays when this is switched off and on again, so
     * the switch survives the player later choosing a different track.
     */
    var captionsEnabled: Boolean

    /** The captions on screen now (the text of media3's `currentCues`); empty when none. */
    val cues: List<String>

    fun addListener(listener: Listener)

    fun removeListener(listener: Listener)

    /** Every callback runs on the main thread. */
    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean) {}

        fun onPlaybackStateChanged(state: PlaybackState) {}

        /** [playWhenReady] changed, whether by the app, the user or an audio-focus loss. */
        fun onPlayWhenReadyChanged(playWhenReady: Boolean) {}

        /** [isPlaybackSuppressed] changed. */
        fun onPlaybackSuppressionChanged(suppressed: Boolean) {}

        /** The item's timeline changed: [duration] may have become known or moved (media3's `onTimelineChanged`). */
        fun onTimelineChanged() {}

        fun onVolumeChanged(volume: Float) {}

        fun onVideoSizeChanged(size: VideoSize) {}

        /** [videoTracks], [textTracks] or [isAudioOnly] changed. */
        fun onTracksChanged() {}

        fun onPlayerErrorChanged(error: PlayerFailure?) {}

        /** A seek, a loop or a jump moved [currentPosition] without playing through. */
        fun onPositionDiscontinuity() {}

        fun onMediaItemTransition(mediaId: String?) {}

        fun onPlaybackSpeedChanged(speed: Float) {}

        /** The captions on screen now; empty to clear. */
        fun onCues(lines: List<String>) {}
    }

    companion object {
        const val TIME_UNSET = Long.MIN_VALUE + 1
    }
}

enum class PlaybackState { IDLE, BUFFERING, READY, ENDED }

@Immutable
data class VideoSize(
    val width: Int,
    val height: Int,
    /** Width of a pixel over its height; not 1 for anamorphic video. */
    val pixelWidthHeightRatio: Float = 1f,
) {
    companion object {
        val UNKNOWN = VideoSize(0, 0)
    }
}

/** One rendition of the picture. [id] is the engine's own and only meaningful to it. */
@Immutable
data class VideoTrack(
    val id: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val selected: Boolean,
    val supported: Boolean = true,
    val codecs: String? = null,
    /** The sample MIME type (`video/hevc`), for diagnostics and describing the media to a cast receiver. */
    val mimeType: String? = null,
    /** Frames per second, or a negative number when the stream doesn't say. For diagnostics. */
    val frameRate: Float = -1f,
)

/** One caption track. */
@Immutable
data class TextTrack(
    val id: String,
    val language: String?,
    val label: String?,
    val selected: Boolean,
)

/**
 * Why playback stopped. [code] is the engine's name for it (media3's `errorCodeName`, an
 * AVFoundation error code), shown to the user under "Can't play this video".
 */
@Immutable
data class PlayerFailure(
    val message: String,
    val code: String,
    /** The format can't be decoded here: worth an "open in browser", not a retry. */
    val isDecoderProblem: Boolean = false,
    val isNetworkProblem: Boolean = false,
    /** A live stream fell behind its window: rejoin the live edge rather than give up. */
    val isBehindLiveWindow: Boolean = false,
    /**
     * The engine's full account for the logs (media3: the error code and the nested cause chain,
     * which tells "format truly unsupported" from "decoder failed to start"). Never shown to the user.
     */
    val diagnostic: String? = null,
) {
    companion object {
        /** What the UI reports itself when frames stop arriving while the sound plays on. */
        fun decodeStall() = PlayerFailure("The video stopped decoding", "DECODING_FORMAT_UNSUPPORTED", isDecoderProblem = true)
    }
}
