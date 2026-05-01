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

import com.vitorpamplona.nestsclient.moq.lite.MoqLitePublisherHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

/**
 * Mirror of [NestBroadcaster] but driving a moq-lite
 * [MoqLitePublisherHandle] instead of an IETF `MoqSession.TrackPublisher`.
 * Keeps the IETF broadcaster intact for the IETF unit-test suite while
 * letting the production speaker path use moq-lite.
 *
 * Lifecycle and audit comments mirror the IETF version 1:1 — only the
 * sink type changes.
 */
class NestMoqLiteBroadcaster(
    private val capture: AudioCapture,
    private val encoder: OpusEncoder,
    private val publisher: MoqLitePublisherHandle,
    private val scope: CoroutineScope,
    /**
     * Number of consecutive Opus frames to pack into a single moq-lite
     * group (= one QUIC unidirectional stream). The first call to
     * [MoqLitePublisherHandle.send] in each group opens the stream;
     * subsequent calls within the same group write additional
     * varint-prefixed frames; [MoqLitePublisherHandle.endGroup] FINs
     * the stream and the next [send] starts a fresh group.
     *
     * **Default = 1 (one Opus frame per group).** Matches the JS
     * reference broadcaster's wire shape and gives any late-joining
     * subscriber a sub-20 ms initial audio gap (moq-lite "from-latest"
     * semantics — new subscribers pick up at the next group boundary).
     *
     * Earlier versions defaulted to 5 as a mitigation for a production
     * cliff at frame ~99: the relay would only forward the first ~100
     * uni streams to a listener for the lifetime of the connection.
     * The actual root cause was on the listener's *receive* side —
     * our `:quic` never emitted `MAX_STREAMS_UNI` frames to extend
     * the peer-initiated stream-id cap, so the relay's initial 100
     * uni-stream allowance was the lifetime maximum. Once
     * [com.vitorpamplona.quic.connection.QuicConnectionWriter]
     * started emitting periodic `MAX_STREAMS_*` extensions, packing
     * stopped being necessary. See
     * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
     *
     * Set to a higher value to amortise per-stream overhead (CPU,
     * relay bookkeeping) at the cost of late-join latency: with
     * `framesPerGroup = 5` the late-join initial gap is ≤ 100 ms
     * instead of ≤ 20 ms.
     */
    private val framesPerGroup: Int = DEFAULT_FRAMES_PER_GROUP,
) {
    init {
        require(framesPerGroup >= 1) { "framesPerGroup must be >= 1, got $framesPerGroup" }
    }

    private var job: Job? = null

    @Volatile private var stopped: Boolean = false

    @Volatile private var muted: Boolean = false

    /**
     * Start capturing + encoding + publishing in the background.
     * Returns immediately. Calling twice is an error. If
     * [AudioCapture.start] throws, the broadcaster is left stopped and
     * the exception propagates.
     */
    fun start(onError: (AudioException) -> Unit = { /* swallow */ }) {
        check(!stopped) { "NestMoqLiteBroadcaster already stopped" }
        check(job == null) { "NestMoqLiteBroadcaster.start already called" }

        try {
            capture.start()
        } catch (t: Throwable) {
            stopped = true
            runCatching { capture.stop() }
            throw t
        }
        job =
            scope.launch {
                // Counts frames written to the current moq-lite group.
                // Reset to 0 immediately after each [endGroup] so the
                // next [send] auto-starts a fresh group via
                // [MoqLitePublisherHandle.send]'s "open on first frame"
                // contract.
                var framesInCurrentGroup = 0
                try {
                    while (true) {
                        val pcm = capture.readFrame() ?: break
                        val opus =
                            try {
                                encoder.encode(pcm)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (t: Throwable) {
                                onError(
                                    AudioException(
                                        AudioException.Kind.DecoderError,
                                        "Opus encode failed for a frame",
                                        t,
                                    ),
                                )
                                continue
                            }
                        if (opus.isEmpty()) continue
                        if (muted) continue
                        // Pack [framesPerGroup] consecutive Opus frames
                        // into the same moq-lite group / QUIC uni stream
                        // before FINning. See the [framesPerGroup] kdoc
                        // for the production cliff this works around.
                        runCatching {
                            publisher.send(opus)
                            framesInCurrentGroup += 1
                            if (framesInCurrentGroup >= framesPerGroup) {
                                publisher.endGroup()
                                framesInCurrentGroup = 0
                            }
                        }.onFailure { t ->
                            if (t is CancellationException) throw t
                            onError(
                                AudioException(
                                    AudioException.Kind.PlaybackFailed,
                                    "publisher.send failed",
                                    t,
                                ),
                            )
                        }
                    }
                    // EOF on the capture side. Flush whatever's in the
                    // open group so the relay sees its FIN and the
                    // listener doesn't sit on a half-delivered group
                    // buffer. Mirrors the per-group endGroup above.
                    if (framesInCurrentGroup > 0) {
                        runCatching { publisher.endGroup() }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    onError(
                        AudioException(
                            AudioException.Kind.PlaybackFailed,
                            "audio capture pipeline failed",
                            t,
                        ),
                    )
                }
            }
    }

    /**
     * Toggle whether captured frames reach the wire. Mic stays open and
     * encoder keeps state consistent so unmute is sample-accurate.
     */
    fun setMuted(muted: Boolean) {
        if (stopped) return
        this.muted = muted
    }

    /**
     * Stop the loop, release the mic, release the encoder, close the
     * moq-lite publisher (which sends `Announce(Ended)` on every active
     * announce bidi). Idempotent. We `cancelAndJoin` the loop before
     * releasing the encoder + publisher so the loop's last
     * `encoder.encode` / `publisher.send` can't race the close path.
     */
    suspend fun stop() {
        if (stopped) return
        stopped = true
        job?.cancelAndJoin()
        runCatching { capture.stop() }
        runCatching { encoder.release() }
        runCatching { publisher.close() }
    }

    companion object {
        /**
         * Default moq-lite group size = 1 Opus frame per group, matching
         * the JS reference broadcaster's wire shape. See [framesPerGroup]
         * kdoc for the full rationale + history.
         */
        const val DEFAULT_FRAMES_PER_GROUP: Int = 1
    }
}
