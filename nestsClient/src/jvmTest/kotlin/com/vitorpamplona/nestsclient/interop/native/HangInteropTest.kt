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
import com.vitorpamplona.nestsclient.NestsRoomConfig
import com.vitorpamplona.nestsclient.audio.AudioFormat
import com.vitorpamplona.nestsclient.audio.JvmOpusEncoder
import com.vitorpamplona.nestsclient.audio.PcmAssertions
import com.vitorpamplona.nestsclient.audio.SineWaveAudioCapture
import com.vitorpamplona.nestsclient.connectNestsSpeaker
import com.vitorpamplona.nestsclient.transport.QuicWebTransportFactory
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
 * Phase 2 ships:
 *   - **I1 forward**: Amethyst Kotlin speaker → `hang-listen`. The
 *     speaker pins `framesPerGroup = 5` (per the cliff-investigation
 *     plan); larger groups overflow moq-relay 0.10.x's per-subscriber
 *     buffer and the relay holds frame bytes without forwarding.
 *     Diagnosed via the companion
 *     [KotlinSpeakerKotlinListenerThroughNativeRelayTest] which
 *     reproduces the same cliff Kotlin↔Kotlin.
 *   - **Rust↔Rust** round-trip: pure-Rust through our harness, proves
 *     the cargo workspace + relay config + `moq-lite-03` ALPN.
 *
 * Both scenarios assert FFT peak / ZCR / sample-count on the decoded
 * Float32 PCM hang-listen wrote to disk. Gated by `-DnestsHangInterop=true`.
 */
class HangInteropTest {
    @BeforeTest
    fun gate() {
        NativeMoqRelayHarness.assumeHangInterop()
    }

    /**
     * I1 — Amethyst Kotlin speaker → reference `hang-listen`. The
     * speaker uses 5 frames per moq-lite group; the relay's per-
     * subscriber forward queue keeps up at that cadence (per
     * `nestsClient/plans/2026-05-01-quic-stream-cliff-investigation.md`).
     * Asserts FFT peak at 440 Hz and ZCR at 880/sec on the decoded
     * PCM hang-listen wrote to disk.
     */
    @Test
    fun amethyst_speaker_to_hang_listener_static_tone_440() =
        runBlocking {
            val harness = NativeMoqRelayHarness.shared()

            val signer: NostrSigner = NostrSignerInternal(KeyPair())
            val pubkey = signer.pubKey
            val roomId = "rt-${UUID.randomUUID()}"
            val room =
                NestsRoomConfig(
                    authBaseUrl = "<unused-public-relay>",
                    endpoint = harness.relayUrl,
                    hostPubkey = pubkey,
                    roomId = roomId,
                )
            val moqNamespace = room.moqNamespace()

            val pcmFile = File.createTempFile("hang-listen-pcm", ".bin").also { it.deleteOnExit() }

            val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transport =
                QuicWebTransportFactory(
                    certificateValidator = PermissiveCertificateValidator(),
                )

            // Sequence: speaker fully up → spawn hang-listen.
            // Catches the `setOnNewSubscriber` race in
            // MoqLiteNestsSpeaker — the catalog hook is set AFTER
            // session.publish() returns, so a subscriber that races
            // in faster registers with hook=null and never gets the
            // catalog. Letting the hook install before hang-listen
            // attaches sidesteps this for the test.
            lateinit var listenProc: Process
            try {
                val speaker =
                    connectNestsSpeaker(
                        httpClient = StaticTokenNestsClient,
                        transport = transport,
                        scope = pumpScope,
                        room = room,
                        signer = signer,
                        speakerPubkeyHex = pubkey,
                        captureFactory = { SineWaveAudioCapture(freqHz = 440) },
                        encoderFactory = { JvmOpusEncoder() },
                        // Per cliff-investigation plan: 5 frames/group
                        // = 10 streams/sec, comfortably within
                        // moq-relay 0.10's per-subscriber forward
                        // ceiling. The repo's current
                        // `DEFAULT_FRAMES_PER_GROUP=50` exceeds the
                        // moq-relay 0.10.x stream-data buffer for a
                        // single uni stream and the audio frames
                        // never reach downstream subscribers.
                        framesPerGroup = 5,
                    )
                val handle = speaker.startBroadcasting()
                delay(150)

                listenProc =
                    ProcessBuilder(
                        harness.hangListenBin().toString(),
                        "--relay-url",
                        harness.relayUrl,
                        "--broadcast",
                        moqNamespace,
                        "--duration",
                        "6",
                        "--output-pcm",
                        pcmFile.absolutePath,
                    ).redirectErrorStream(true)
                        .also { it.environment()["RUST_LOG"] = "info" }
                        .start()

                delay(5_000)
                handle.close()
                speaker.close()
            } finally {
                pumpScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }

            val exited = listenProc.waitFor(15, TimeUnit.SECONDS)
            val output = listenProc.inputStream.bufferedReader().readText()
            assertTrue(exited, "hang-listen did not exit within 15 s. Output:\n$output")
            assertEquals(
                0,
                listenProc.exitValue(),
                "hang-listen exited non-zero. Output:\n$output",
            )

            val pcm = readFloat32Pcm(pcmFile)
            PcmAssertions.assertSampleCount(pcm, expectedDurationSec = 5.0, tolerance = 0.20)
            // Skip first 40 ms — Opus look-ahead silence.
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 25
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)
            PcmAssertions.assertFftPeak(analysed, expectedHz = 440.0, halfWindowHz = 5.0)
            PcmAssertions.assertZeroCrossingRate(
                analysed,
                expectedPerSecond = 880.0,
                tolerance = 0.05,
            )
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
 * Bypass the NIP-98 auth handshake — the harness boots moq-relay
 * with `--auth-public ""`, which grants any path without a JWT.
 */
private object StaticTokenNestsClient : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String = ""
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
