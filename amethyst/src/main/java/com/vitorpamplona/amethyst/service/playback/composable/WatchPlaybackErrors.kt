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
package com.vitorpamplona.amethyst.service.playback.composable

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay

// Debug tag for the playback-error lifecycle. Logs every appearance, clear, synthetic-stall raise
// and recovery so a transient decoder-init collision (which self-recovers on a later attempt) can
// be told apart from a genuinely undecodable stream in a field logcat. See WatchPlaybackErrors.
internal const val ERROR_LOG_TAG = "PlaybackError"

/**
 * Flattens a [PlaybackException] into a single line: error code, message, and the full nested
 * cause chain (e.g. `DecoderInitializationException <- MediaCodec.CodecException`). The cause
 * chain is what distinguishes "format truly unsupported" from "decoder failed to start while a
 * second controller held the codec" — both surface as the same top-level renderer error with
 * `format_supported=YES`.
 *
 * Internal (not private) so [GetVideoController] can log the same detail at controller-acquire
 * time — a warm-pool player can arrive already in ERROR and get re-prepared (cleared) before
 * this watcher ever attaches, so the acquire site is the only place that error is observable.
 */
internal fun PlaybackException.describe(): String {
    val causeChain =
        generateSequence(cause) { it.cause }
            .joinToString(" <- ") { "${it::class.simpleName}: ${it.message}" }
            .ifEmpty { "none" }
    return "code=$errorCodeName($errorCode) msg=$message causes=[$causeChain]"
}

// How often the decode-stall watchdog samples the controller's position/buffer.
private const val STALL_POLL_INTERVAL_MS = 1_000L

// The decoder is only considered "fed" once it has comfortably more than bufferForPlaybackMs
// (750 ms in feedTunedLoadControl) of media ready ahead of the playhead. Below this we treat a
// frozen playhead as ordinary network starvation, not a decode failure, and leave it alone.
private const val STALL_MIN_BUFFER_AHEAD_MS = 2_000L

// How long the playhead may stay frozen while the decoder is fed before we declare the stream
// undecodable. A healthy player crosses bufferForPlaybackMs and starts rendering within a second
// or two, so 8 s of fed-but-frozen buffering is unambiguous.
private const val STALL_TIMEOUT_MS = 8_000L

/**
 * Mirrors the MediaController's terminal-error state into [MediaControllerState.playbackError]
 * so [RenderPlaybackError] can show the codec-not-supported overlay with a browser fallback.
 *
 * ExoPlayer enters the ERROR state silently on decoder init / decode / format failures; the
 * ContentFrame then renders nothing and the user is left staring at a blank box. Surfacing the
 * exception gives [RenderVideoPlayer] something to render and lets the user open the URL
 * externally instead.
 *
 * Some codec failures never surface as a [PlaybackException] at all: a software HEVC decoder that
 * can't keep up (e.g. iPhone-recorded `hvc1` video on a device without a HEVC hardware decoder)
 * just parks the player in [Player.STATE_BUFFERING] forever — the buffer fills to the LoadControl
 * cap, yet the playhead never leaves 0 and no error is ever raised. [watchForDecodeStall] polls
 * for that signature and synthesizes a [PlaybackException] so the same overlay kicks in.
 */
@Composable
fun WatchPlaybackErrors(controllerState: MediaControllerState) {
    val controller = controllerState.controller
    val errorState = controllerState.playbackError

    DisposableEffect(controllerState) {
        // Prime from the controller's current state — a warm-pool player may already be in ERROR
        // when we attach, in which case onPlayerErrorChanged will not fire again until prepare().
        errorState.value = controller.playerError
        controller.playerError?.let {
            Log.w(ERROR_LOG_TAG) { "Primed with existing error on ${controller.currentMediaItem?.mediaId}: ${it.describe()}" }
        }

        val listener =
            object : Player.Listener {
                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    if (error != null) {
                        Log.w(ERROR_LOG_TAG) { "Error raised on ${controller.currentMediaItem?.mediaId}: ${error.describe()}" }
                    } else if (errorState.value != null) {
                        Log.d(ERROR_LOG_TAG) { "Error cleared on ${controller.currentMediaItem?.mediaId}" }
                    }
                    errorState.value = error
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    // A new item on a pooled player starts fresh; drop any error from the old one.
                    if (errorState.value != null) {
                        Log.d(ERROR_LOG_TAG) { "Error dropped on media transition (reason=$reason) -> ${mediaItem?.mediaId}" }
                        errorState.value = null
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    // A successful transition to READY (the renderer produced output) is the only
                    // real recovery — clear the overlay then. We deliberately do NOT clear on
                    // STATE_BUFFERING: the synthetic decode-stall error below is raised *while*
                    // buffering, and clearing on every buffering event would wipe it instantly.
                    if (state == Player.STATE_READY) {
                        if (errorState.value != null) {
                            Log.d(ERROR_LOG_TAG) { "Recovered (STATE_READY) on ${controller.currentMediaItem?.mediaId} — clearing overlay" }
                            errorState.value = null
                        }
                    }
                }
            }

        controller.addListener(listener)
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(controllerState) {
        watchForDecodeStall(controller, errorState)
    }
}

/**
 * Polls [controller] for the silent-decode-stall signature and, once seen continuously for
 * [STALL_TIMEOUT_MS], writes a synthetic [PlaybackException] so the browser-fallback overlay shows.
 *
 * Stall signature: the player wants to play and is stuck in [Player.STATE_BUFFERING] with at least
 * [STALL_MIN_BUFFER_AHEAD_MS] of media buffered ahead (so the decoder is fed, not network-starved),
 * yet the playhead has not advanced. A genuine mid-stream rebuffer fails the buffer-ahead guard —
 * its buffer is depleted, which is precisely why it rebuffers — so it is never flagged.
 */
@OptIn(UnstableApi::class)
private suspend fun watchForDecodeStall(
    controller: Player,
    errorState: MutableState<PlaybackException?>,
) {
    var unproductiveSinceMs = -1L
    var lastPosition = Long.MIN_VALUE

    // delay() is cancellable, so the loop exits when the LaunchedEffect is torn down.
    while (true) {
        delay(STALL_POLL_INTERVAL_MS)

        val position = controller.currentPosition
        val progressed = position != lastPosition
        lastPosition = position

        val decoderFedButFrozen =
            controller.playbackState == Player.STATE_BUFFERING &&
                controller.playWhenReady &&
                controller.bufferedPosition - position >= STALL_MIN_BUFFER_AHEAD_MS

        if (decoderFedButFrozen && !progressed) {
            val now = SystemClock.elapsedRealtime()
            if (unproductiveSinceMs < 0) {
                unproductiveSinceMs = now
            } else if (now - unproductiveSinceMs >= STALL_TIMEOUT_MS && errorState.value == null) {
                Log.w(ERROR_LOG_TAG) {
                    "Synthetic decode-stall after ${STALL_TIMEOUT_MS}ms fed-but-frozen " +
                        "(pos=$position buffered=${controller.bufferedPosition}) on ${controller.currentMediaItem?.mediaId}"
                }
                errorState.value =
                    PlaybackException(
                        "Video decoding stalled with a full buffer — likely an unsupported codec",
                        null,
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                    )
            }
        } else {
            unproductiveSinceMs = -1L

            // "If it works, just play": if the playhead is advancing again the stream is decoding
            // after all (a slow device that eventually produced a frame, a recovered hiccup), so
            // drop the stall overlay. Real decoder errors leave the player IDLE with a frozen
            // playhead, so they never progress here and are left for the STATE_READY listener.
            if (progressed && errorState.value != null) {
                Log.d(ERROR_LOG_TAG) { "Playhead progressed to $position — clearing stall overlay on ${controller.currentMediaItem?.mediaId}" }
                errorState.value = null
            }
        }
    }
}
