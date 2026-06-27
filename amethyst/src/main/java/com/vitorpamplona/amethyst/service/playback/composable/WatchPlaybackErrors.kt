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
import kotlinx.coroutines.delay

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

        val listener =
            object : Player.Listener {
                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    errorState.value = error
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    // A new item on a pooled player starts fresh; drop any error from the old one.
                    if (errorState.value != null) errorState.value = null
                }

                override fun onPlaybackStateChanged(state: Int) {
                    // A successful transition to READY (the renderer produced output) is the only
                    // real recovery — clear the overlay then. We deliberately do NOT clear on
                    // STATE_BUFFERING: the synthetic decode-stall error below is raised *while*
                    // buffering, and clearing on every buffering event would wipe it instantly.
                    if (state == Player.STATE_READY) {
                        if (errorState.value != null) errorState.value = null
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
                errorState.value = null
            }
        }
    }
}
