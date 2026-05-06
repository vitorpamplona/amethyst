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

import com.vitorpamplona.nestsclient.audio.AudioFormat
import com.vitorpamplona.nestsclient.audio.PcmAssertions
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-stack interop scenarios driving the reference `kixelated/moq`
 * `hang-publish` and `hang-listen` Rust binaries through
 * [NativeMoqRelayHarness] (i.e. through the same `moq-relay`
 * subprocess Amethyst tests use).
 *
 * Phase 2 ships the **Rust↔Rust** scenario — a pure-Rust round-trip
 * over our harness. This proves:
 *   - the cargo workspace at `cli/hang-interop/` builds binaries
 *     that interop with `moq-relay 0.10.x` over `moq-lite-03`;
 *   - the harness's relay configuration (`--auth-public ""`, self-
 *     signed TLS, sandbox-IPv4 client bind) accepts real publishers
 *     and subscribers;
 *   - signal-domain assertions over a 5 s 440 Hz tone catch any
 *     wire-format drift in either binary.
 *
 * **Phase 2 deferred**: the **Amethyst speaker → hang-listen**
 * scenario (the spec's I1) is wired in `:nestsClient` but currently
 * fails because the Kotlin speaker's audio uni stream isn't
 * delivering frame bytes to the upstream hang `Container::Legacy`
 * decoder — the Group control message arrives but no
 * `varint(timestamp_us) + opus` payload follows. Tracked in
 * `nestsClient/plans/2026-05-06-cross-stack-interop-test-results.md`.
 *
 * Gated by `-DnestsHangInterop=true`.
 */
class HangInteropTest {
    @BeforeTest
    fun gate() {
        NativeMoqRelayHarness.assumeHangInterop()
    }

    /**
     * Drive the Rust `hang-publish` and `hang-listen` binaries
     * through our harness's `moq-relay` subprocess. End-to-end:
     * 5 s of 440 Hz mono Opus → 880 zero-crossings/sec, FFT peak
     * at 440 Hz, ~5 s of decoded PCM in the temp file.
     */
    @Test
    fun rust_hang_publish_to_rust_hang_listener_round_trip_440() {
        val harness = NativeMoqRelayHarness.shared()
        val broadcast = "test/${UUID.randomUUID()}"
        val pcmFile = File.createTempFile("hang-listen-pcm", ".bin").also { it.deleteOnExit() }

        val publishProc =
            ProcessBuilder(
                harness.hangPublishBin().toString(),
                "--relay-url",
                "${harness.relayUrl}/$broadcast",
                "--broadcast",
                broadcast,
                "--track-name",
                "audio",
                "--duration",
                "5",
                "--freq-hz",
                "440",
            ).redirectErrorStream(true)
                .also { it.environment()["RUST_LOG"] = "info" }
                .start()
        // Tiny breathing room so the publisher's ANNOUNCE Active
        // has propagated to the relay before the listener's
        // OriginConsumer.announced() returns.
        Thread.sleep(300)
        val listenProc =
            ProcessBuilder(
                harness.hangListenBin().toString(),
                "--relay-url",
                harness.relayUrl,
                "--broadcast",
                broadcast,
                "--duration",
                "6",
                "--output-pcm",
                pcmFile.absolutePath,
            ).redirectErrorStream(true)
                .also { it.environment()["RUST_LOG"] = "info" }
                .start()

        val pubExit = publishProc.waitFor(15, TimeUnit.SECONDS)
        val listenExit = listenProc.waitFor(15, TimeUnit.SECONDS)
        val pubOut = publishProc.inputStream.bufferedReader().readText()
        val listenOut = listenProc.inputStream.bufferedReader().readText()
        assertTrue(pubExit, "hang-publish did not exit. Output:\n$pubOut")
        assertTrue(listenExit, "hang-listen did not exit. Output:\n$listenOut")
        assertEquals(0, publishProc.exitValue(), "hang-publish exited non-zero. Output:\n$pubOut")
        assertEquals(0, listenProc.exitValue(), "hang-listen exited non-zero. Output:\n$listenOut")

        val pcm = readFloat32Pcm(pcmFile)
        // hang-publish ran for 5 s @ 50 fps mono Opus. With Opus
        // look-ahead + relay buffering + listener's per-group
        // catch-up window, expect 4.5–5.0 s of decoded audio.
        PcmAssertions.assertSampleCount(pcm, expectedDurationSec = 5.0, tolerance = 0.20)
        // Skip the first 40 ms so the FFT window doesn't include
        // Opus's silence-prefilled look-ahead.
        val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 25
        val analysed = pcm.copyOfRange(warmupSamples, pcm.size)
        PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
        PcmAssertions.assertZeroCrossingRate(
            analysed,
            expectedPerSecond = 880.0,
            tolerance = 0.05,
        )
    }
}

/**
 * Read a file of native-endian Float32 little-endian PCM into a
 * [FloatArray]. The hang-listen binary writes LE Float32, no header.
 */
private fun readFloat32Pcm(file: File): FloatArray {
    val bytes = file.readBytes()
    require(bytes.size % 4 == 0) {
        "PCM file size ${bytes.size} is not a multiple of 4 (Float32)"
    }
    val n = bytes.size / 4
    val out = FloatArray(n)
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until n) out[i] = buf.float
    return out
}
