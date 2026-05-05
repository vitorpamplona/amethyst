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
package com.vitorpamplona.nestsclient.audio

import com.vitorpamplona.nestsclient.moq.MoqObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Bridges a track's `Flow<MoqObject>` (from [com.vitorpamplona.nestsclient.moq.SubscribeHandle.objects])
 * through an [OpusDecoder] into an [AudioPlayer].
 *
 * Single-track. To play multiple speakers in a room, instantiate one
 * [NestPlayer] per [com.vitorpamplona.nestsclient.moq.SubscribeHandle];
 * each owns its own decoder (Opus state is per-track).
 *
 * Lifecycle:
 *   - [play] starts the player and the decode loop. Returns immediately.
 *   - [stop] cancels the decode loop, stops the player, and releases the
 *     decoder. Idempotent.
 */
class NestPlayer(
    private val decoder: OpusDecoder,
    private val player: AudioPlayer,
    private val scope: CoroutineScope,
    /**
     * Number of decoded PCM frames to buffer before starting the underlying
     * [AudioPlayer]. Without pre-roll, the device begins consuming audio the
     * instant the first frame arrives and underruns at the first decode that
     * misses its 20 ms cadence — the most common cause of perceptible
     * dropouts on devices where Compose recomposition or GC briefly stalls
     * the decode loop.
     *
     * Production callers typically pass `5` (≈ 100 ms) — long enough to mask
     * a typical Main-thread hiccup, short enough that listeners don't notice
     * the join latency. Tests pass `0` to preserve the synchronous test
     * scheduler behaviour the existing assertions rely on.
     *
     * Default is `0` so existing tests stand without modification.
     */
    private val prerollFrames: Int = 0,
) {
    init {
        require(prerollFrames >= 0) { "prerollFrames must be >= 0, got $prerollFrames" }
    }

    private var job: Job? = null
    private var stopped = false

    /**
     * Start consuming [objects] in the background. Each MoQ object's payload
     * is fed to the Opus decoder; the resulting PCM frame is enqueued to the
     * player.
     *
     * Decoder errors are reported via [onError] but do NOT stop the loop —
     * one bad packet shouldn't tear down the room. Player errors are fatal.
     *
     * [onLevel] receives the peak amplitude of each successfully decoded
     * frame, normalized to `[0, 1]`. Default no-op so callers that don't
     * care about levels (tests, audio-only consumers) pay zero cost.
     * Invoked on the same dispatcher as the decode loop — typically
     * `viewModelScope`'s Main, so the consumer must keep its handler
     * lightweight (e.g. a HashMap put followed by a coalesced StateFlow
     * emission).
     */
    fun play(
        objects: Flow<MoqObject>,
        onError: (AudioException) -> Unit = { /* swallow */ },
        onLevel: (Float) -> Unit = { /* no-op */ },
    ) {
        check(!stopped) { "NestPlayer already stopped" }
        check(job == null) { "NestPlayer.play already called" }

        // Pre-roll buffer holds decoded PCM until either [prerollFrames]
        // frames have arrived or the upstream flow ends. The underlying
        // [AudioPlayer] is only started once we have something to flush,
        // so a flow that never produces PCM (decoder always empty, no
        // audio in the room) never opens the device.
        //
        // Note: `player.start()` is now deferred into the launch body. If
        // it throws, the failure is reported via `onError` rather than
        // propagating synchronously to `play()`'s caller. This is
        // intentional — the device-allocation cost was previously paid
        // up-front and amplified perceived join latency; pushing it
        // behind the first decoded frame both lets pre-roll work and
        // matches the rest of the pipeline's "audible-failure-via-onError"
        // contract.
        job =
            scope.launch {
                val preroll = ArrayDeque<ShortArray>(prerollFrames.coerceAtLeast(1))
                var started = false

                suspend fun startAndFlushIfNeeded() {
                    if (started) return
                    if (preroll.isEmpty()) return
                    player.start()
                    started = true
                    while (preroll.isNotEmpty()) {
                        player.enqueue(preroll.removeFirst())
                    }
                }
                try {
                    objects.collect { obj ->
                        val pcm =
                            try {
                                decoder.decode(obj.payload)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (t: Throwable) {
                                onError(
                                    AudioException(
                                        AudioException.Kind.DecoderError,
                                        "Opus decode failed for object ${obj.objectId}",
                                        t,
                                    ),
                                )
                                return@collect
                            }
                        if (pcm.isNotEmpty()) {
                            onLevel(peakAmplitude(pcm))
                            if (started) {
                                player.enqueue(pcm)
                            } else {
                                preroll.addLast(pcm)
                                if (preroll.size >= prerollFrames) {
                                    startAndFlushIfNeeded()
                                }
                            }
                        }
                    }
                    // Flow ended without enough frames to fill the pre-roll
                    // (e.g. the publisher cycled before pre-roll was full,
                    // or the room ended). Flush whatever's queued so any
                    // already-decoded audio still reaches the device.
                    startAndFlushIfNeeded()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    onError(
                        AudioException(
                            AudioException.Kind.PlaybackFailed,
                            "audio pipeline failed",
                            t,
                        ),
                    )
                }
            }
    }

    /**
     * Stop playback, cancel the decode loop, release the decoder. Idempotent.
     *
     * Suspending so callers can await the loop's exit before releasing
     * native resources. Calling `decoder.release()` while another coroutine
     * is mid-`decoder.decode(...)` is undefined behaviour for MediaCodec
     * (and most native decoders); `cancelAndJoin` waits for the loop to
     * unwind through its CancellationException path before we proceed.
     */
    suspend fun stop() {
        if (stopped) return
        stopped = true
        job?.cancelAndJoin()
        runCatching { player.stop() }
        runCatching { decoder.release() }
    }
}
