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
     * **Default = 5 (≈ 100 ms of audio per group).** This is a
     * mitigation for a production loss cliff observed against
     * `nostrnests.com`: at one-frame-per-group and 20 ms cadence the
     * client opens 50 uni streams/second, and somewhere around the
     * 99th stream the connection enters a state where new uni streams'
     * data stops being delivered to the listener, even though
     * `publisher.send` still returns true. Packing 5 frames per group
     * reduces the stream-creation rate to 10/second, which the prod
     * sweep (`NostrnestsProdAudioTransmissionTest.sweep_frames_per_group_5`)
     * shows delivers 100/100 frames cleanly. See
     * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`.
     *
     * Trade-off: a brand-new subscriber that attaches mid-broadcast
     * picks up at the next group boundary per moq-lite "from-latest"
     * semantics. With 5 frames per group, late joiners miss up to
     * 100 ms of audio. With the original 1 frame per group it was
     * up to 20 ms. Both are imperceptible for live audio rooms.
     *
     * Set to 1 to restore the original behaviour for testing or for
     * deployments to relays that don't exhibit the stream-cliff bug.
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
         * Default moq-lite group size = 5 Opus frames ≈ 100 ms of audio.
         * Picked to keep the QUIC uni-stream creation rate under the
         * production cliff observed against `nostrnests.com` while
         * still giving late-joining subscribers a sub-100 ms initial
         * audio gap. See [framesPerGroup] kdoc for the full rationale.
         */
        const val DEFAULT_FRAMES_PER_GROUP: Int = 5
    }
}
