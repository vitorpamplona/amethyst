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
package com.vitorpamplona.amethyst.commons.ui.playback.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.PLAYBACK_DIAG_TAG
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackState
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlayerFailure
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

// Debug tag for the playback-error lifecycle. Logs every appearance, clear, synthetic-stall raise
// and recovery so a transient decoder-init collision (which self-recovers on a later attempt) can
// be told apart from a genuinely undecodable stream in a field logcat. See WatchPlaybackErrors.
const val WATCH_ERROR_LOG_TAG = "PlaybackError"

private fun playbackStateName(state: PlaybackState): String = state.name

// Summarizes the currently-selected video track (codec + resolution + mime). A change in this line
// across an #EXT-X-DISCONTINUITY is the signature of an ad-splice codec reconfig — a second, distinct
// failure mode from BEHIND_LIVE_WINDOW.
private fun List<VideoTrack>.selectedVideoSummary(): String {
    for (f in this) {
        if (f.selected) {
            return "codec=${f.codecs} ${f.width}x${f.height} mime=${f.mimeType} fps=${f.frameRate}"
        }
    }
    return "none"
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

// A live HLS stream (a FAST channel / kind:30311 broadcast) publishes only a short sliding window
// of segments — e.g. ~60 s — with no ENDLIST. If the playhead drifts before the start of that
// window (the player was prepared then held off the live edge, or a rebuffer outlasted the DVR
// depth), ExoPlayer raises ERROR_CODE_BEHIND_LIVE_WINDOW. That is not a terminal failure: the
// documented recovery is to seek back to the default (live) position and re-prepare.
//
// This is the ONLY error we auto-recover. An earlier version also re-prepared on any live I/O error
// (the 2xxx band), but that thrashed on a stream whose segments fail to parse
// (UnexpectedLoaderException / IllegalArgumentException): each re-prepare briefly reached READY then
// hit the same bad segment, and because the cap reset on READY it never gave up. I/O and decode
// errors are now left terminal — ExoPlayer's own LoadErrorHandlingPolicy already retries transient
// segment loads, and RenderPlaybackError shows the honest "open in browser" overlay for the rest.
internal const val MAX_LIVE_STREAM_RECOVERY_ATTEMPTS = 5

// The playhead must advance at least this far past the position an error was raised at before the
// recovery budget is refilled. Reaching READY alone is not enough: a stream that flaps READY for a
// fraction of a second at the same spot must exhaust the cap and surface, not loop forever.
private const val RECOVERY_RESET_PROGRESS_MS = 3_000L

/**
 * Whether a terminal [PlayerFailure] should be recovered by seek-to-live + re-prepare rather
 * than surfaced. Only a live stream behind its window (media3's `ERROR_CODE_BEHIND_LIVE_WINDOW`)
 * qualifies — it is a rejoin signal, not a failure. Everything else (I/O, decode, parse) is left
 * terminal. Pure, so it is unit-testable.
 */
fun isRecoverableLiveError(failure: PlayerFailure): Boolean = failure.isBehindLiveWindow

/**
 * Mirrors the MediaController's terminal-error state into [MediaControllerState.playbackError]
 * so [RenderPlaybackError] can show the codec-not-supported overlay with a browser fallback.
 *
 * ExoPlayer enters the ERROR state silently on decoder init / decode / format failures; the
 * video surface then renders nothing and the user is left staring at a blank box. Surfacing the
 * exception gives [RenderVideoPlayer] something to render and lets the user open the URL
 * externally instead.
 *
 * Some codec failures never surface as a media3 `PlaybackException` at all: a software HEVC decoder that
 * can't keep up (e.g. iPhone-recorded `hvc1` video on a device without a HEVC hardware decoder)
 * just parks the player in [PlaybackState.BUFFERING] forever — the buffer fills to the LoadControl
 * cap, yet the playhead never leaves 0 and no error is ever raised. [watchForDecodeStall] polls
 * for that signature and synthesizes a [PlayerFailure] so the same overlay kicks in.
 */
@Composable
fun WatchPlaybackErrors(controllerState: MediaControllerState) {
    val controller = controllerState.controller
    val errorState = controllerState.playbackError

    DisposableEffect(controllerState) {
        // Prime from the controller's current state — a warm-pool player may already be in ERROR
        // when we attach, in which case onPlayerErrorChanged will not fire again until prepare().
        errorState.value = controller.error
        controller.error?.let {
            Log.w(WATCH_ERROR_LOG_TAG) { "Primed with existing error on ${controller.mediaId}: ${it.describe()}" }
            Log.d(PLAYBACK_DIAG_TAG) { "PRIMED with existing error on ${controller.mediaId}: ${it.describe()}" }
        }

        // Re-preparations attempted for the current stream. Reset on a media-item change and once
        // playback makes real progress past the point an error was raised (see recoveryAnchorMs),
        // never merely on reaching READY — a sub-second READY flap must not refill the budget.
        var liveRecoveryAttempts = 0
        var recoveryAnchorMs = Long.MIN_VALUE

        val listener =
            object : AmethystPlayer.Listener {
                override fun onPlayerErrorChanged(error: PlayerFailure?) {
                    if (error != null) {
                        val mediaId = controller.mediaId
                        val recoverable = isRecoverableLiveError(error)
                        Log.d(PLAYBACK_DIAG_TAG) {
                            "ERROR code=${error.code} recoverable=$recoverable " +
                                "attempts=$liveRecoveryAttempts pos=${controller.currentPosition} buffered=${controller.bufferedPosition} " +
                                "on $mediaId :: ${error.describe()}"
                        }
                        Log.w(WATCH_ERROR_LOG_TAG) { "Error raised on $mediaId: ${error.describe()}" }

                        if (recoverable && liveRecoveryAttempts < MAX_LIVE_STREAM_RECOVERY_ATTEMPTS) {
                            liveRecoveryAttempts++
                            recoveryAnchorMs = controller.currentPosition
                            Log.d(PLAYBACK_DIAG_TAG) {
                                "RECOVER attempt $liveRecoveryAttempts/$MAX_LIVE_STREAM_RECOVERY_ATTEMPTS " +
                                    "after ${error.code} on $mediaId — seekToDefaultPosition + prepare"
                            }
                            // Rejoin the live edge and re-prepare. Leave errorState null so the
                            // overlay never appears for a recoverable hiccup.
                            controller.seekToDefaultPosition()
                            controller.prepare()
                            return
                        }

                        Log.d(PLAYBACK_DIAG_TAG) {
                            val why = if (recoverable) "GAVE UP after $liveRecoveryAttempts attempts" else "TERMINAL (not recovering) ${error.code}"
                            "$why on $mediaId — overlay will show"
                        }
                    } else if (errorState.value != null) {
                        Log.d(WATCH_ERROR_LOG_TAG) { "Error cleared on ${controller.mediaId}" }
                    }
                    errorState.value = error
                }

                override fun onMediaItemTransition(mediaId: String?) {
                    Log.d(PLAYBACK_DIAG_TAG) { "ITEM -> $mediaId" }
                    liveRecoveryAttempts = 0
                    recoveryAnchorMs = Long.MIN_VALUE
                    // A new item on a pooled player starts fresh; drop any error from the old one.
                    if (errorState.value != null) {
                        Log.d(WATCH_ERROR_LOG_TAG) { "Error dropped on media transition -> $mediaId" }
                        errorState.value = null
                    }
                }

                override fun onPlaybackStateChanged(state: PlaybackState) {
                    Log.d(PLAYBACK_DIAG_TAG) {
                        "STATE=${playbackStateName(state)} playWhenReady=${controller.playWhenReady} " +
                            "pos=${controller.currentPosition} buffered=${controller.bufferedPosition} " +
                            "on ${controller.mediaId}"
                    }
                    // A successful transition to READY (the renderer produced output) is the only
                    // real recovery — clear the overlay then. We deliberately do NOT clear on
                    // BUFFERING: the synthetic decode-stall error below is raised *while*
                    // buffering, and clearing on every buffering event would wipe it instantly.
                    if (state == PlaybackState.READY) {
                        // Refill the recovery budget only after genuine forward progress past the last
                        // error, so a stream that re-errors at the same spot every ~1 s still hits the
                        // cap and surfaces instead of thrashing forever.
                        if (controller.currentPosition >= recoveryAnchorMs + RECOVERY_RESET_PROGRESS_MS) {
                            liveRecoveryAttempts = 0
                            recoveryAnchorMs = Long.MIN_VALUE
                        }
                        if (errorState.value != null) {
                            Log.d(WATCH_ERROR_LOG_TAG) { "Recovered (STATE_READY) on ${controller.mediaId} — clearing overlay" }
                            errorState.value = null
                        }
                    }
                }

                override fun onPositionDiscontinuity() {
                    Log.d(PLAYBACK_DIAG_TAG) {
                        "DISCONTINUITY -> ${controller.currentPosition}ms on ${controller.mediaId}"
                    }
                }

                override fun onTracksChanged() {
                    Log.d(PLAYBACK_DIAG_TAG) {
                        "TRACKS video[${controller.videoTracks.selectedVideoSummary()}] on ${controller.mediaId}"
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
 * [STALL_TIMEOUT_MS], writes a synthetic [PlayerFailure] so the browser-fallback overlay shows.
 *
 * Stall signature: the player wants to play and is stuck in [PlaybackState.BUFFERING] with at least
 * [STALL_MIN_BUFFER_AHEAD_MS] of media buffered ahead (so the decoder is fed, not network-starved),
 * yet the playhead has not advanced. A genuine mid-stream rebuffer fails the buffer-ahead guard —
 * its buffer is depleted, which is precisely why it rebuffers — so it is never flagged.
 */
private suspend fun watchForDecodeStall(
    controller: AmethystPlayer,
    errorState: MutableState<PlayerFailure?>,
) {
    val clock = TimeSource.Monotonic.markNow()
    var unproductiveSinceMs = -1L
    var lastPosition = Long.MIN_VALUE

    // delay() is cancellable, so the loop exits when the LaunchedEffect is torn down.
    while (true) {
        delay(STALL_POLL_INTERVAL_MS)

        val position = controller.currentPosition
        val progressed = position != lastPosition
        lastPosition = position

        val decoderFedButFrozen =
            controller.playbackState == PlaybackState.BUFFERING &&
                controller.playWhenReady &&
                controller.bufferedPosition - position >= STALL_MIN_BUFFER_AHEAD_MS

        if (decoderFedButFrozen && !progressed) {
            val now = clock.elapsedNow().inWholeMilliseconds
            if (unproductiveSinceMs < 0) {
                unproductiveSinceMs = now
            } else if (now - unproductiveSinceMs >= STALL_TIMEOUT_MS && errorState.value == null) {
                Log.w(WATCH_ERROR_LOG_TAG) {
                    "Synthetic decode-stall after ${STALL_TIMEOUT_MS}ms fed-but-frozen " +
                        "(pos=$position buffered=${controller.bufferedPosition}) on ${controller.mediaId}"
                }
                Log.d(PLAYBACK_DIAG_TAG) {
                    "SYNTHETIC-STALL fed-but-frozen ${STALL_TIMEOUT_MS}ms " +
                        "(pos=$position buffered=${controller.bufferedPosition}) on ${controller.mediaId} — likely codec reconfig/unsupported"
                }
                errorState.value =
                    PlayerFailure(
                        message = "Video decoding stalled with a full buffer — likely an unsupported codec",
                        code = "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED",
                        isDecoderProblem = true,
                    )
            }
        } else {
            unproductiveSinceMs = -1L

            // "If it works, just play": if the playhead is advancing again the stream is decoding
            // after all (a slow device that eventually produced a frame, a recovered hiccup), so
            // drop the stall overlay. Real decoder errors leave the player IDLE with a frozen
            // playhead, so they never progress here and are left for the STATE_READY listener.
            if (progressed && errorState.value != null) {
                Log.d(WATCH_ERROR_LOG_TAG) { "Playhead progressed to $position — clearing stall overlay on ${controller.mediaId}" }
                errorState.value = null
            }
        }
    }
}

/** One line for the logs: the engine's own account when it gave one, else the code and message. */
private fun PlayerFailure.describe(): String = diagnostic ?: "code=$code msg=$message"
