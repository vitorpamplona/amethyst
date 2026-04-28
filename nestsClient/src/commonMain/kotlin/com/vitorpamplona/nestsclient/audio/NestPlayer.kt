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
) {
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

        player.start()
        job =
            scope.launch {
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
                            player.enqueue(pcm)
                        }
                    }
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
     * Peak amplitude of a 16-bit PCM frame, normalized to `[0, 1]`. Peak
     * (vs RMS) is jittery on its own but responds instantly to onsets,
     * which suits a visual ring that the UI smooths via animateDpAsState.
     */
    private fun peakAmplitude(pcm: ShortArray): Float {
        var maxAbs = 0
        for (s in pcm) {
            val abs = if (s.toInt() < 0) -s.toInt() else s.toInt()
            if (abs > maxAbs) maxAbs = abs
        }
        return (maxAbs / 32768f).coerceIn(0f, 1f)
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
