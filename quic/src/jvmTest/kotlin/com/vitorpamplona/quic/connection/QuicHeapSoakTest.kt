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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.stream.StreamId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Long-form heap-sampling canary for soak target #1 (memory growth).
 *
 * **DEFAULT-SKIPPED.** This test is gated by the `quicSoakSeconds`
 * system property, propagated from the `-PquicSoakSeconds=N` Gradle
 * property by `quic/build.gradle.kts`. Without the property the
 * test method early-returns with a printed "SKIPPED" line, so a
 * plain `./gradlew test` run keeps it out of CI's critical path
 * (~40 ms overhead from the runBlocking + property check).
 *
 * To run the production-shaped 30-minute soak from the audio-rooms
 * prompt:
 *
 *   ./gradlew :quic:jvmTest \
 *       --tests 'com.vitorpamplona.quic.connection.QuicHeapSoakTest' \
 *       -PquicSoakSeconds=1800
 *
 * Quick local sanity (30 seconds, ~25 K stream lifecycles):
 *
 *   ./gradlew :quic:jvmTest \
 *       --tests 'com.vitorpamplona.quic.connection.QuicHeapSoakTest' \
 *       -PquicSoakSeconds=30
 *
 * Test shape: drive moq-lite-shaped peer-uni stream churn through an
 * [InMemoryQuicPipe] at a fixed rate. Sample
 * `Runtime.getRuntime().totalMemory() - freeMemory()` at six evenly-
 * spaced points across the run, calling `System.gc()` first to
 * minimise allocator-noise. The post-warmup baseline is the second
 * sample; the acceptance criterion (10 MB max growth past warmup,
 * direct from the soak prompt) is checked against the final sample.
 *
 * Why six samples: enough granularity to spot a slow drift while
 * keeping the per-sample overhead small relative to the run. The
 * second sample (~17 % into the run) is the warmup baseline — the
 * first sample is just before any work, so it understates ambient
 * usage and would inflate apparent growth.
 */
class QuicHeapSoakTest {
    @Test
    fun heapStaysFlatUnderModulatedStreamChurn() =
        runBlocking {
            val durationSec =
                System.getProperty("quicSoakSeconds")?.toIntOrNull()
            if (durationSec == null || durationSec <= 0) {
                // Default-skip path: `./gradlew test` runs this test as a
                // no-op fast-pass. Opt in via `-PquicSoakSeconds=N` (1800
                // for the 30-minute soak from the audio-rooms prompt).
                // We don't use kotlin.test's assumption because the JVM
                // backend differs between JUnit4 / JUnit5; an early
                // return prints the reason and keeps CI fast.
                println(
                    "[QuicHeapSoakTest] SKIPPED — set -PquicSoakSeconds=N " +
                        "to run (e.g. 30 for sanity, 1800 for production-shape).",
                )
                return@runBlocking
            }
            // Open the pipe; the handshake should be cheap relative to
            // the soak duration.
            val (client, pipe) = newConnectedClient()

            // moq-lite production rate: ~50 peer-uni streams per second
            // (one Opus frame per stream). A 1 800 s run mints 90 000
            // streams; a 30 s scaled-down run still moves through 1 500
            // generations of churn — plenty for retirement to fire
            // hundreds of times.
            val streamsPerSecond = 50
            val totalStreams = durationSec.toLong() * streamsPerSecond
            val perPayload = 32 // ~Opus frame envelope
            val sampleCount = 6
            val streamsPerSample = (totalStreams / sampleCount).coerceAtLeast(1L)

            val samples = LongArray(sampleCount)
            val streamCountAtSample = LongArray(sampleCount)
            var nextStreamId = StreamId.build(StreamId.Kind.SERVER_UNI, 0L)
            var streamsDelivered = 0L
            var sampleIdx = 0

            while (sampleIdx < sampleCount) {
                val target = (sampleIdx + 1) * streamsPerSample
                while (streamsDelivered < target) {
                    val payload = ByteArray(perPayload) { (streamsDelivered.toInt() and 0xFF).toByte() }
                    val packet =
                        pipe.buildServerApplicationDatagram(
                            listOf(
                                StreamFrame(
                                    streamId = nextStreamId,
                                    offset = 0L,
                                    data = payload,
                                    fin = true,
                                ),
                            ),
                        )!!
                    feedDatagram(client, packet, nowMillis = 0L)
                    client.streamById(nextStreamId)?.incoming?.toList()
                    nextStreamId += 4L
                    streamsDelivered += 1
                    // Periodically drain so retireFullyDoneStreamsLocked
                    // runs at moq-lite-realistic intervals (per ~50
                    // streams). Without periodic drains the working set
                    // grows to streamsPerSample which is misleading vs
                    // the production rate.
                    if (streamsDelivered % streamsPerSecond == 0L) {
                        drainAll(client, pipe)
                    }
                }
                drainAll(client, pipe)
                samples[sampleIdx] = sampleHeapBytes()
                streamCountAtSample[sampleIdx] = client.streamsListLocked().size.toLong()
                sampleIdx += 1
            }

            // Sanity: every stream we *actually* minted (streamsDelivered;
            // streamsPerSample integer-truncates so it can be < totalStreams)
            // must have been retired by the last sample. drainAll between
            // batches drives retirement. If retirement regressed, the gap
            // here would surface immediately.
            assertEquals(
                streamsDelivered,
                client.retiredStreamsCount,
                "every minted peer-uni stream must have been retired by end of soak",
            )
            // Working set must have stayed bounded throughout — never
            // larger than the per-sample batch size (drainAll runs at
            // every streamsPerSecond boundary, so the steady-state
            // bound is ≤ streamsPerSecond entries).
            val maxWorkingSet = streamCountAtSample.max()
            assertTrue(
                maxWorkingSet <= streamsPerSecond.toLong(),
                "tracker working set must stay ≤ $streamsPerSecond entries; observed max=$maxWorkingSet " +
                    "(samples=${streamCountAtSample.toList()})",
            )

            // Heap acceptance — direct from the audio-rooms prompt:
            // "no monotonic growth past handshake-stable (10 MB)".
            // Use sample 1 (post-warmup) as baseline, sample 5 (final)
            // as the steady-state probe.
            val warmup = samples[1]
            val finalSample = samples[sampleCount - 1]
            val growthMb = (finalSample - warmup).toDouble() / (1024.0 * 1024.0)
            // Print the sample profile so a CI failure has actionable
            // forensic data (which sample first crossed the threshold,
            // roughly when in the run).
            val profile =
                samples.indices.joinToString(prefix = "[", postfix = "]") { i ->
                    val mb = samples[i].toDouble() / (1024.0 * 1024.0)
                    "%.1fMB".format(mb)
                }
            System.err.println(
                "[QuicHeapSoakTest] duration=${durationSec}s totalStreams=$totalStreams " +
                    "samples=$profile retired=${client.retiredStreamsCount}",
            )
            assertTrue(
                growthMb <= 10.0,
                "heap grew by %.1f MB past warmup (sample 1 = %.1f MB → final = %.1f MB); ".format(
                    growthMb,
                    warmup.toDouble() / 1024.0 / 1024.0,
                    finalSample.toDouble() / 1024.0 / 1024.0,
                ) + "samples=$profile. Acceptance: ≤ 10 MB.",
            )
        }

    private fun sampleHeapBytes(): Long {
        // Best-effort GC: a single System.gc() is a hint, not a
        // guarantee. Three passes with a small sleep between gives
        // G1/CMS/ZGC enough time to actually settle. (We avoid
        // `System.runFinalization()` — deprecated since Java 18 and
        // unreliable on modern collectors.)
        repeat(3) {
            System.gc()
            Thread.sleep(20)
        }
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    private fun drainAll(
        client: QuicConnection,
        pipe: InMemoryQuicPipe,
    ) {
        while (true) {
            val out = drainOutbound(client, nowMillis = 0L) ?: break
            pipe.decryptClientApplicationFrames(out)
        }
    }

    // moq-lite-shaped fixture (matches StreamRetirementSoakTest's
    // shape) — large peer-uni cap so the heap canary can churn 90 K
    // streams in a 30-min run without bumping the cap.
    private fun newConnectedClient(): Pair<QuicConnection, InMemoryQuicPipe> =
        com.vitorpamplona.quic.connection.newConnectedClient(
            maxStreamsBidi = 4096,
            maxStreamsUni = 65_536,
            maxData = 16L * 1024 * 1024,
        )
}
