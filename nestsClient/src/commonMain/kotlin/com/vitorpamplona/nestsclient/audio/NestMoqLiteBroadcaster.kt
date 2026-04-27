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
) {
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
                        runCatching { publisher.send(opus) }
                            .onFailure { t ->
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
}
