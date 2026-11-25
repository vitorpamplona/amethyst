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
package com.vitorpamplona.amethyst.service.playback.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.amethyst.commons.ui.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.commons.ui.playback.controls.VIDEO_QUALITY_TAG
import com.vitorpamplona.amethyst.commons.ui.playback.controls.needsViewportUpdate
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackState
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlayerFailure
import com.vitorpamplona.amethyst.commons.ui.playback.player.TextTrack
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSize
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import com.vitorpamplona.quartz.utils.Log
import androidx.media3.common.VideoSize as Media3VideoSize

/**
 * The shared player UI's [AmethystPlayer] over a media3 [Player] (in the app, the pooled
 * `MediaController`). Every member forwards to the media3 one with the same meaning, so the moved
 * composables behave as they did when they called media3 directly. Like media3, it must be used on
 * the player's application thread (the main thread), and its listeners are called there.
 *
 * Track ids are `"<group index>:<track index>"` into [Player.getCurrentTracks].
 */
@OptIn(UnstableApi::class)
class Media3Player(
    val player: Player,
) : AmethystPlayer {
    private val listeners = HashMap<AmethystPlayer.Listener, Player.Listener>()

    override val mediaId: String? get() = player.currentMediaItem?.mediaId

    override var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    override val isPlaying: Boolean get() = player.isPlaying

    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }

    override val playbackState: PlaybackState get() = playbackStateOf(player.playbackState)

    override val currentPosition: Long get() = player.currentPosition

    override val duration: Long get() = player.duration

    override val bufferedPosition: Long get() = player.bufferedPosition

    override val videoSize: VideoSize get() = videoSizeOf(player.videoSize)

    // The exception and its mapping are kept together: WatchPlaybackErrors primes from this on every
    // attach, and the same exception should read as the same failure.
    private var lastException: PlaybackException? = null
    private var lastFailure: PlayerFailure? = null

    override val error: PlayerFailure? get() = failureOf(player.playerError)

    private fun failureOf(exception: PlaybackException?): PlayerFailure? {
        if (exception == null) return null
        if (exception !== lastException) {
            lastException = exception
            lastFailure = exception.toPlayerFailure()
        }
        return lastFailure
    }

    override var playbackSpeed: Float
        get() = player.playbackParameters.speed
        set(value) {
            player.setPlaybackSpeed(value)
        }

    override var repeatOne: Boolean
        get() = player.repeatMode == Player.REPEAT_MODE_ONE
        set(value) {
            player.repeatMode = if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }

    override val isPlaybackSuppressed: Boolean get() = isSuppressed(player.playbackSuppressionReason)

    override val canSetPlaybackSpeed: Boolean get() = player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun prepare() = player.prepare()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun seekToDefaultPosition() = player.seekToDefaultPosition()

    // Tracks

    override val isAudioOnly: Boolean get() = player.currentTracks.isAudio()

    override val hasVideoTrack: Boolean get() = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }

    override val hasAudioTrack: Boolean get() = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }

    override val videoTracks: List<VideoTrack>
        get() {
            val groups = player.currentTracks.groups
            val groupIndex = groups.indexOfFirst { it.type == C.TRACK_TYPE_VIDEO && it.length > 0 }
            if (groupIndex < 0) return emptyList()
            val group = groups[groupIndex]
            return (0 until group.length).map { i ->
                val format = group.getTrackFormat(i)
                VideoTrack(
                    id = "$groupIndex:$i",
                    width = format.width,
                    height = format.height,
                    bitrate = format.bitrate,
                    selected = group.isTrackSelected(i),
                    supported = group.isTrackSupported(i),
                    codecs = format.codecs,
                    mimeType = format.sampleMimeType,
                    frameRate = format.frameRate,
                )
            }
        }

    override val hasVideoTrackOverride: Boolean
        get() = player.trackSelectionParameters.overrides.any { (key, _) -> key.type == C.TRACK_TYPE_VIDEO }

    override fun selectVideoTrack(track: VideoTrack) {
        val (group, trackIndex) = groupOf(track.id) ?: return
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                .build()
    }

    override fun clearVideoTrackOverride() {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
    }

    // Runs from onSizeChanged on the player's application looper (main thread), which is where
    // trackSelectionParameters must be written. Writing them re-runs track selection and, for a
    // pooled controller, crosses an IPC boundary, so [needsViewportUpdate] keeps a no-op layout pass
    // from churning either.
    override fun constrainToViewport(
        width: Int,
        height: Int,
    ) {
        val current = player.trackSelectionParameters
        if (!needsViewportUpdate(current.viewportWidth, current.viewportHeight, width, height)) return

        Log.d(VIDEO_QUALITY_TAG) {
            "viewport ${width}x$height mediaId=${player.currentMediaItem?.mediaId} " +
                "(was ${current.viewportWidth}x${current.viewportHeight})"
        }

        player.trackSelectionParameters =
            current
                .buildUpon()
                // orientationMayChange mirrors media3's own default: it keeps a landscape rendition in
                // a portrait box (a letterboxed live stream) from being judged against the short edge.
                .setViewportSize(width, height, true)
                .build()
    }

    /**
     * The text tracks a player has, in the order the media declared them. Groups the player reports
     * as unsupported are dropped: offering a language the decoder will refuse is worse than not
     * offering it.
     */
    override val textTracks: List<TextTrack>
        get() {
            val choices = mutableListOf<TextTrack>()
            player.currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val format = group.getTrackFormat(i)
                    choices.add(
                        TextTrack(
                            id = "$groupIndex:$i",
                            language = format.language,
                            label = format.label,
                            selected = group.isTrackSelected(i),
                        ),
                    )
                }
            }
            return choices
        }

    override fun selectTextTrack(track: TextTrack?) {
        if (track == null) {
            captionsEnabled = false
            return
        }
        val (group, trackIndex) = groupOf(track.id) ?: return
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                // Clearing the type disable as well: picking a language is a request to see captions,
                // so it has to undo an earlier "off" rather than silently select a track nothing draws.
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                .build()
    }

    // Disabling the whole track type rather than deselecting one group: the type-level switch
    // survives the player later picking a different track.
    override var captionsEnabled: Boolean
        get() = !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        set(value) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !value)
                    .build()
        }

    override val cues: List<String> get() = player.currentCues.lines()

    private fun groupOf(id: String): Pair<Tracks.Group, Int>? {
        val groupIndex = id.substringBefore(':').toIntOrNull() ?: return null
        val trackIndex = id.substringAfter(':').toIntOrNull() ?: return null
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return null
        if (trackIndex !in 0 until group.length) return null
        return group to trackIndex
    }

    // Listeners

    override fun addListener(listener: AmethystPlayer.Listener) {
        if (listeners.containsKey(listener)) return
        val bridge = Bridge(listener)
        listeners[listener] = bridge
        player.addListener(bridge)
    }

    override fun removeListener(listener: AmethystPlayer.Listener) {
        listeners.remove(listener)?.let { player.removeListener(it) }
    }

    private inner class Bridge(
        val listener: AmethystPlayer.Listener,
    ) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)

        override fun onPlaybackStateChanged(playbackState: Int) = listener.onPlaybackStateChanged(playbackStateOf(playbackState))

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) = listener.onPlayWhenReadyChanged(playWhenReady)

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = listener.onPlaybackSuppressionChanged(isSuppressed(playbackSuppressionReason))

        override fun onTimelineChanged(
            timeline: Timeline,
            reason: Int,
        ) = listener.onTimelineChanged()

        override fun onVolumeChanged(volume: Float) = listener.onVolumeChanged(volume)

        override fun onVideoSizeChanged(videoSize: Media3VideoSize) = listener.onVideoSizeChanged(videoSizeOf(videoSize))

        override fun onTracksChanged(tracks: Tracks) = listener.onTracksChanged()

        override fun onPlayerErrorChanged(error: PlaybackException?) = listener.onPlayerErrorChanged(failureOf(error))

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) = listener.onPositionDiscontinuity()

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) = listener.onMediaItemTransition(mediaItem?.mediaId)

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = listener.onPlaybackSpeedChanged(playbackParameters.speed)

        override fun onCues(cueGroup: CueGroup) = listener.onCues(cueGroup.lines())
    }
}

/** The media3 player behind a controller the Android platform handed out. */
val MediaControllerState.media3: Player get() = (controller as Media3Player).player

fun Tracks.isAudio() = groups.isNotEmpty() && groups.none { it.type == C.TRACK_TYPE_VIDEO }

private fun CueGroup.lines(): List<String> = cues.mapNotNull { it.text?.toString() }.filter { it.isNotBlank() }

private fun isSuppressed(reason: Int) = reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE && reason != Player.PLAYBACK_SUPPRESSION_REASON_SCRUBBING

private fun playbackStateOf(state: Int): PlaybackState =
    when (state) {
        Player.STATE_BUFFERING -> PlaybackState.BUFFERING
        Player.STATE_READY -> PlaybackState.READY
        Player.STATE_ENDED -> PlaybackState.ENDED
        else -> PlaybackState.IDLE
    }

private fun videoSizeOf(size: Media3VideoSize): VideoSize =
    if (size.width == 0 && size.height == 0) {
        VideoSize.UNKNOWN
    } else {
        VideoSize(size.width, size.height, size.pixelWidthHeightRatio)
    }

/**
 * Flattens a [PlaybackException] into a single line: error code, message, and the full nested
 * cause chain (e.g. `DecoderInitializationException <- MediaCodec.CodecException`). The cause
 * chain is what distinguishes "format truly unsupported" from "decoder failed to start while a
 * second controller held the codec" — both surface as the same top-level renderer error with
 * `format_supported=YES`.
 *
 * Also logged at controller-acquire time — a warm-pool player can arrive already in ERROR and get
 * re-prepared (cleared) before WatchPlaybackErrors ever attaches, so the acquire site is the only
 * place that error is observable.
 */
fun PlaybackException.describe(): String {
    val causeChain =
        generateSequence(cause) { it.cause }
            .joinToString(" <- ") { "${it::class.simpleName}: ${it.message}" }
            .ifEmpty { "none" }
    return "code=$errorCodeName($errorCode) msg=$message causes=[$causeChain]"
}

fun PlaybackException.toPlayerFailure(): PlayerFailure = playerFailureOf(errorCode, errorCodeName, message, describe())

/**
 * The shared [PlayerFailure] for a media3 error code. Decoder errors (the 4xxx band) are the
 * format problems worth an "open in browser"; I/O errors (2xxx) are the network's; and
 * `ERROR_CODE_BEHIND_LIVE_WINDOW` is the live-stream rejoin signal the UI recovers from.
 */
fun playerFailureOf(
    errorCode: Int,
    errorCodeName: String,
    message: String?,
    diagnostic: String? = null,
): PlayerFailure =
    PlayerFailure(
        message = message ?: errorCodeName,
        code = errorCodeName,
        isDecoderProblem = errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
        isNetworkProblem = errorCode in PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        isBehindLiveWindow = errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        diagnostic = diagnostic,
    )
