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
package com.vitorpamplona.nestsclient.interop.native

import com.vitorpamplona.nestsclient.NestsClient
import com.vitorpamplona.nestsclient.NestsListenerState
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.JvmOpusEncoder
import com.vitorpamplona.nestsclient.audio.SineWaveAudioCapture
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.connectReconnectingNestsListener
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Side-by-side **inter-arrival jitter** measurement for two moq-lite
 * audio publishers feeding the same relay through the same listener:
 *
 *   - the Amethyst Kotlin speaker (`connectNestsSpeaker` →
 *     `MoqLiteNestsSpeaker` → `NestMoqLiteBroadcaster` → moq-lite uni
 *     streams),
 *   - the upstream reference `hang-publish` Rust binary
 *     (`nestsClient/tests/hang-interop/hang-publish`).
 *
 * Both publish identical-shape tracks (440 Hz mono Opus, 20 ms frames,
 * 32 kbps) at the same wall-clock cadence into the same `moq-relay`
 * the harness brought up; a single Kotlin listener subscribes to
 * BOTH speaker pubkeys so the receive path is identical for both
 * sides — the variable under test is the **publisher stack's pacing
 * fidelity**.
 *
 * What we measure (per side):
 *   - `frames` — how many objects reached the listener inside the
 *     [DURATION_MS] window. Floor of 80 % of the theoretical maximum
 *     `(DURATION_MS / FRAME_MS)` proves the publisher actually got
 *     audio onto the wire and through the relay; values close to the
 *     ceiling mean no frames were dropped at the publisher's `send`
 *     site or stalled in the relay's egress.
 *   - `medianInterArrivalMs`, `p95`, `p99`, `maxInterArrivalMs` —
 *     consecutive-object delta at the listener. Real-time audio
 *     wants this tight around `FRAME_MS = 20`; the spread is the
 *     publisher stack's queue jitter (mic → encoder → publisher.send
 *     → uni-stream open → QUIC writer → wire), since the relay-side
 *     fan-out and the listener-side decode are constant.
 *   - `timeToFirstFrameMs` — wall clock from publisher startup to
 *     the first frame at the listener. Includes one-time costs
 *     (handshake, ANNOUNCE / SUBSCRIBE fan-out, first uni-stream
 *     open). Informative, NOT directly comparable across stacks
 *     (the Rust side pays a process-spawn that the JVM side doesn't),
 *     so it's reported but not asserted on.
 *
 * What this test does NOT measure:
 *   - End-to-end glass-to-ear latency — both publishers run their
 *     own pacing loops; we don't have a synchronised `send_t` from
 *     `hang-publish` because adding one would require modifying the
 *     Rust sidecar. Jitter at the listener captures pacing fidelity
 *     without needing send-side timestamps, which is the part the
 *     **publisher stack** actually controls.
 *   - Recovery under packet loss / congestion — both sides run on
 *     a fast localhost link with no simulator interposed. For loss
 *     behaviour see the `quic-network-simulator`-based runs in
 *     `quic/interop/`.
 *
 * Asserts:
 *   - Each side delivers ≥ 80 % of the expected frame count
 *     (catches a publisher that silently stalled).
 *   - Each side's median inter-arrival sits in `[15, 35] ms`
 *     (catches a stack that's not pacing at all and just bursts).
 * Tail percentiles + `timeToFirstFrameMs` are printed for
 * diagnostics but not gated — a CI-grade jitter SLO would have to
 * be tuned to the test host and isn't this test's job.
 *
 * Gated by `-DnestsHangInterop=true` because it needs the
 * cargo-built `hang-publish` sidecar; same opt-in as every other
 * test in `interop/native/`.
 */
class AudioLatencyComparisonTest {
    @Test
    fun kotlin_speaker_jitter_matches_hang_publish_within_real_time_budget() =
        runBlocking {
            val harness = NativeMoqRelayHarness.shared()

            // Shared room — both publishers ANNOUNCE inside the same
            // moq-lite namespace, each claiming a distinct broadcast
            // suffix (their own pubkey). One Kotlin listener subscribes
            // to both so the receive path is identical and any jitter
            // delta must come from the publisher side.
            val hostSigner: NostrSigner = NostrSignerInternal(KeyPair())
            val kotlinSpeakerSigner: NostrSigner = NostrSignerInternal(KeyPair())
            val rustSpeakerSigner: NostrSigner = NostrSignerInternal(KeyPair())
            val kotlinPubkey = kotlinSpeakerSigner.pubKey
            val rustPubkey = rustSpeakerSigner.pubKey

            val room =
                NestsRoomConfig(
                    authBaseUrl = "<unused-public-relay>",
                    endpoint = harness.relayUrl,
                    hostPubkey = hostSigner.pubKey,
                    roomId = "lat-${UUID.randomUUID()}",
                )

            val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val transport =
                    QuicWebTransportFactory(
                        parentScope = pumpScope,
                        certificateValidator = PermissiveCertificateValidator(),
                    )

                // ---- Listener first. Use the RECONNECTING wrapper so
                // its inner re-issuance pump retries SUBSCRIBE with
                // exponential backoff (100 → 200 → 400 → 800 → 1 000 ms)
                // — without that, the plain listener's
                // `subscribeSpeaker` against an unannounced broadcast
                // hits "subscribe stream FIN before reply" the moment
                // the relay sees no producer. With it, subscribing
                // BEFORE either publisher is up is safe; the retry
                // catches whichever ANNOUNCE arrives first.
                val listener =
                    connectReconnectingNestsListener(
                        httpClient = LatencyTestNestsClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = hostSigner,
                        tokenRefreshAfterMs = 0L,
                    )
                withTimeoutOrNull(5_000L) {
                    listener.state.first { it is NestsListenerState.Connected }
                } ?: error("listener never reached Connected within 5 s")
                val kotlinSub = listener.subscribeSpeaker(kotlinPubkey)
                val rustSub = listener.subscribeSpeaker(rustPubkey)

                val kotlinArrivals = ArrayList<Long>(EXPECTED_FRAMES + 64)
                val rustArrivals = ArrayList<Long>(EXPECTED_FRAMES + 64)
                val kotlinCollect =
                    pumpScope.launch {
                        kotlinSub.objects.collect { kotlinArrivals += System.nanoTime() }
                    }
                val rustCollect =
                    pumpScope.launch {
                        rustSub.objects.collect { rustArrivals += System.nanoTime() }
                    }

                val publishProc: Process
                val kotlinSpeaker =
                    try {
                        // Start the Rust process FIRST since process-spawn
                        // is the slow setup. Capture its t0 right before
                        // start() and the Kotlin t0 right before
                        // connectNestsSpeaker; the time-to-first-frame
                        // numbers below subtract these.
                        val rustT0 = System.nanoTime()
                        publishProc =
                            ProcessBuilder(
                                harness.hangPublishBin().toString(),
                                "--relay-url",
                                "${harness.relayUrl}/${room.moqNamespace()}",
                                "--broadcast",
                                rustPubkey,
                                "--track-name",
                                "audio/data",
                                "--duration",
                                (DURATION_MS / 1_000L).toString(),
                                "--freq-hz",
                                "440",
                            ).redirectErrorStream(true)
                                .also { it.environment()["RUST_LOG"] = "info" }
                                .start()

                        // Kotlin speaker. `connectNestsSpeaker` suspends
                        // through the WebTransport handshake + moq-lite
                        // SETUP; `startBroadcasting` opens the audio and
                        // catalog publishers and is the moment "frames
                        // can start flowing" — capture t0 around that.
                        val kotlinT0 = System.nanoTime()
                        val speaker =
                            connectNestsSpeaker(
                                httpClient = LatencyTestNestsClient,
                                transport = transport,
                                scope = pumpScope,
                                room = room,
                                signer = kotlinSpeakerSigner,
                                speakerPubkeyHex = kotlinPubkey,
                                captureFactory = { SineWaveAudioCapture(freqHz = 440) },
                                encoderFactory = { JvmOpusEncoder() },
                            )
                        speaker.startBroadcasting()
                        publisherStartNanos = PublisherStart(kotlinT0, rustT0)
                        speaker
                    } catch (t: Throwable) {
                        kotlinCollect.cancel()
                        rustCollect.cancel()
                        pumpScope.coroutineContext[Job]?.cancel()
                        throw t
                    }

                try {
                    delay(DURATION_MS + COLLECTION_HEADROOM_MS)
                } finally {
                    runCatching { kotlinSpeaker.close() }
                    runCatching { publishProc.destroy() }
                    // Stop the collectors AFTER the publishers — late
                    // frames sit in QUIC stream buffers and arrive a few
                    // ms after speaker.close, so cancelling first would
                    // truncate the tail of the sample.
                    delay(200)
                    kotlinCollect.cancel()
                    rustCollect.cancel()
                }

                val starts =
                    checkNotNull(publisherStartNanos) { "publisher t0 should have been captured" }
                val kotlinStats =
                    JitterStats.compute(label = "Kotlin speaker", startNanos = starts.kotlin, arrivals = kotlinArrivals)
                val rustStats =
                    JitterStats.compute(label = "Rust hang-publish", startNanos = starts.rust, arrivals = rustArrivals)

                // Print so a CI run captures the comparison even when the
                // test passes — that's the whole point of the test (it's
                // diagnostic, not regression-gating).
                println("===== Audio publisher pacing comparison =====")
                println(kotlinStats)
                println(rustStats)
                println("=============================================")

                assertTrue(
                    kotlinStats.frames >= MIN_FRAMES_ACCEPTED,
                    "Kotlin speaker delivered only ${kotlinStats.frames} frames, expected " +
                        "≥ $MIN_FRAMES_ACCEPTED of $EXPECTED_FRAMES — publisher stalled or " +
                        "the relay dropped the audio track. Stats:\n$kotlinStats",
                )
                assertTrue(
                    rustStats.frames >= MIN_FRAMES_ACCEPTED,
                    "Rust hang-publish delivered only ${rustStats.frames} frames, expected " +
                        "≥ $MIN_FRAMES_ACCEPTED of $EXPECTED_FRAMES — sidecar process never " +
                        "made it to steady state. Stats:\n$rustStats",
                )
                assertTrue(
                    kotlinStats.medianInterArrivalMs in PACING_MEDIAN_OK_MS,
                    "Kotlin speaker median inter-arrival ${kotlinStats.medianInterArrivalMs} ms " +
                        "is outside the sane pacing window $PACING_MEDIAN_OK_MS — implies the " +
                        "publisher's not pacing at all (just bursting frames into the relay).",
                )
                assertTrue(
                    rustStats.medianInterArrivalMs in PACING_MEDIAN_OK_MS,
                    "Rust hang-publish median inter-arrival ${rustStats.medianInterArrivalMs} ms " +
                        "is outside the sane pacing window $PACING_MEDIAN_OK_MS — sidecar's " +
                        "pacing loop is broken (or the relay is queueing).",
                )
            } finally {
                pumpScope.coroutineContext[Job]?.cancel()
            }
        }

    // Captured inside the try because Kotlin doesn't let us write
    // `val (kotlinT0, rustT0) = …` across the try boundary.
    private var publisherStartNanos: PublisherStart? = null

    private data class PublisherStart(
        val kotlin: Long,
        val rust: Long,
    )

    /**
     * Per-publisher pacing summary. Inter-arrival deltas are computed
     * BETWEEN consecutive received objects (skipping the first one,
     * which has no predecessor), so a count of `N` arrivals yields
     * `N - 1` deltas. Times stored as nanoseconds during collection
     * (single allocation per frame) and converted to ms at compute
     * time so the on-path collector loop stays allocation-light.
     */
    private data class JitterStats(
        val label: String,
        val frames: Int,
        val timeToFirstFrameMs: Double,
        val medianInterArrivalMs: Double,
        val p95InterArrivalMs: Double,
        val p99InterArrivalMs: Double,
        val maxInterArrivalMs: Double,
    ) {
        override fun toString(): String =
            "%-18s frames=%4d  ttf=%6.1f ms  inter-arrival: p50=%5.2f  p95=%5.2f  p99=%5.2f  max=%6.2f ms"
                .format(
                    label,
                    frames,
                    timeToFirstFrameMs,
                    medianInterArrivalMs,
                    p95InterArrivalMs,
                    p99InterArrivalMs,
                    maxInterArrivalMs,
                )

        companion object {
            fun compute(
                label: String,
                startNanos: Long,
                arrivals: List<Long>,
            ): JitterStats {
                if (arrivals.isEmpty()) {
                    return JitterStats(label, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
                }
                val ttfMs = (arrivals.first() - startNanos) / 1_000_000.0
                if (arrivals.size < 2) {
                    return JitterStats(label, arrivals.size, ttfMs, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
                }
                val deltasMs = DoubleArray(arrivals.size - 1)
                for (i in 1 until arrivals.size) {
                    deltasMs[i - 1] = (arrivals[i] - arrivals[i - 1]) / 1_000_000.0
                }
                deltasMs.sort()
                return JitterStats(
                    label = label,
                    frames = arrivals.size,
                    timeToFirstFrameMs = ttfMs,
                    medianInterArrivalMs = percentile(deltasMs, 0.50),
                    p95InterArrivalMs = percentile(deltasMs, 0.95),
                    p99InterArrivalMs = percentile(deltasMs, 0.99),
                    maxInterArrivalMs = deltasMs.last(),
                )
            }

            /**
             * Linear-interpolated percentile (R-7 / "type 7" — the
             * default in NumPy, R, and Excel's PERCENTILE). Input
             * MUST already be sorted ascending; we sort the array
             * before this is called.
             */
            private fun percentile(
                sorted: DoubleArray,
                q: Double,
            ): Double {
                if (sorted.isEmpty()) return Double.NaN
                if (sorted.size == 1) return sorted[0]
                val rank = q * (sorted.size - 1)
                val lo = rank.toInt()
                val frac = rank - lo
                val hi = (lo + 1).coerceAtMost(sorted.size - 1)
                return sorted[lo] + frac * (sorted[hi] - sorted[lo])
            }
        }
    }

    /**
     * Bypass the NIP-98 auth handshake — the harness's moq-relay runs
     * with `--auth-public ""`, which grants any path without a JWT.
     * Same shape as `ReverseStaticTokenNestsClient` in the other
     * native-relay tests, but they're file-private so we can't share.
     */
    private object LatencyTestNestsClient : NestsClient {
        override suspend fun mintToken(
            room: NestsRoomConfig,
            publish: Boolean,
            signer: NostrSigner,
        ): String = ""
    }

    companion object {
        /** moq-lite / Opus frame cadence; matches `hang-publish`'s `FRAME_DURATION_US`. */
        private const val FRAME_MS = 20L

        /** Measurement window. 10 s = 500 frames per side is plenty for stable P95/P99. */
        private const val DURATION_MS = 10_000L

        /** Extra wall-clock after publishers stop, so the listener drains in-flight uni streams. */
        private const val COLLECTION_HEADROOM_MS = 1_500L

        private const val EXPECTED_FRAMES = (DURATION_MS / FRAME_MS).toInt()

        /** 80 % delivery — sanity floor for "publisher actually got onto the wire". */
        private const val MIN_FRAMES_ACCEPTED = (EXPECTED_FRAMES * 0.8).toInt()

        /**
         * Median inter-arrival should sit very close to [FRAME_MS] = 20 ms.
         * The window catches a publisher that's just bursting frames
         * (median would collapse to a fraction of a ms) without being
         * so tight it fails on a loaded CI host. Tail percentiles are
         * NOT asserted — they're reported for human inspection only.
         */
        private val PACING_MEDIAN_OK_MS = 15.0..35.0
    }
}
