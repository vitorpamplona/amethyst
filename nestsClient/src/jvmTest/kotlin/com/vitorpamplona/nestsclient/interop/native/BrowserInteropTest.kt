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

import com.vitorpamplona.nestsclient.AudioBroadcastConfig
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 4 (T16) — browser-side cross-stack interop scenarios.
 *
 * Drives a headless Chromium subprocess (via [PlaywrightDriver])
 * through the same [NativeMoqRelayHarness] moq-relay that
 * [HangInteropTest] uses. The browser harness page connects via
 * Chromium's WebTransport stack (separate implementation from quinn /
 * `:quic`), decodes Opus via WebCodecs `AudioDecoder`, and posts
 * Float32 LE PCM frames back to a bun WebSocket back-channel that
 * appends them to a file the test reads.
 *
 * Phase 4.B P0 scenario:
 *   - **I1 browser** — sine-wave round-trip Amethyst Kotlin speaker →
 *     Chromium @moq/lite + @moq/hang listener; assert FFT peak at
 *     440 Hz on the captured PCM.
 *
 * Speaker pinned at `framesPerGroup = 5` to stay under the
 * `moq-relay 0.10.x` per-subscriber forward cliff (same as the
 * hang-tier scenarios).
 *
 * Gate: `-DnestsBrowserInterop=true` (also implies
 * `-DnestsHangInterop=true` indirectly because we boot the same
 * `NativeMoqRelayHarness`).
 */
class BrowserInteropTest {
    @BeforeTest
    fun gate() {
        PlaywrightDriver.assumeBrowserInterop()
        // The browser harness reuses the moq-relay subprocess that the
        // hang-tier scenarios bring up. Without `-DnestsHangInterop=true`
        // the harness would refuse to boot — flip it on automatically
        // for the browser gate so the user only has to set one flag.
        if (!NativeMoqRelayHarness.isEnabled()) {
            System.setProperty(NativeMoqRelayHarness.ENABLE_PROPERTY, "true")
        }
    }

    /**
     * **I1 forward (browser)** — Amethyst Kotlin speaker → Chromium
     * `@moq/lite` listener with `@moq/hang` `Container.Legacy.Consumer`.
     * Asserts the captured PCM has the expected sample count and the
     * 440 Hz tone survives end-to-end.
     *
     * What this catches that the Rust hang-listen path doesn't:
     *   - Chromium's WebTransport ALPN negotiation (independent
     *     implementation from quinn),
     *   - WebCodecs `AudioDecoder` first-frame handling (different
     *     warmup behaviour from libopus — drops the first 3 output
     *     frames; we offset the warmup window accordingly),
     *   - `OpusHead` codec-config wedging would still bypass the
     *     decoder warmup window and produce a click at offset 0;
     *     T8's filter is verified again here.
     */
    @Test
    fun amethyst_speaker_to_chromium_listener_static_tone_440() =
        runBlocking {
            // Speaker runs for 10 s wallclock; we assert the page captured
            // ≥ 1 s of decoded PCM. The looser bound reflects how the page's
            // capture window opens *after* Chromium cold-launch + WebTransport
            // handshake (3–5 s on a fresh runner), and the Kotlin speaker
            // pins `framesPerGroup = 5` (= 100 ms groups) so a late-joining
            // subscriber gets only the tail per `moq-relay 0.10.x`'s
            // per-subscriber cache semantics. The load-bearing assertion is
            // the FFT peak at 440 Hz — that catches a wire-format regression
            // (downmix, channel swap, OpusHead-as-frame leak) regardless of
            // how many seconds of tail the page captured.
            val out = runSpeakerToBrowserListen(speakerSeconds = 10)
            val pcm = readFloat32Pcm(out.pcmFile)
            // Skip the warmup window before FFT: 40 ms Opus
            // look-ahead + 60 ms WebCodecs 3-frame warmup-skip = 100 ms.
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
            assertTrue(
                pcm.size > warmupSamples,
                "captured PCM (${pcm.size} samples) shorter than the WebCodecs warmup " +
                    "window — page never received any audio.\nplaywright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)
            assertTrue(
                analysed.size >= AudioFormat.SAMPLE_RATE_HZ,
                "after warmup window only ${analysed.size} samples remain; " +
                    "expected ≥ 1 s of decoded audio.\nplaywright stdout:\n${out.stdout}",
            )
            PcmAssertions.assertFftPeak(
                analysed,
                expectedHz = 440.0,
                halfWindowHz = 5.0,
            )
        }
}

/**
 * Output bundle from one [runSpeakerToBrowserListen] invocation.
 */
private class BrowserListenOutput(
    val pcmFile: File,
    val stdout: String,
)

/**
 * Run the Kotlin speaker for [speakerSeconds] seconds, drive the
 * Playwright + Chromium harness page to capture decoded PCM via the
 * bun WS back-channel, and return the captured bundle.
 *
 * Mirror of [HangInteropTest]'s `runSpeakerToHangListen`, but the
 * listener subprocess is `npx playwright test` instead of
 * `hang-listen`. The relay endpoint is identical — both consumers
 * connect via WebTransport to the same `NativeMoqRelayHarness`
 * instance.
 */
private suspend fun runSpeakerToBrowserListen(
    speakerSeconds: Int,
    listenerLateJoinDelayMs: Long = 150L,
    channelCount: Int = 1,
    freqHzPerChannel: IntArray? = null,
): BrowserListenOutput {
    val harness = NativeMoqRelayHarness.shared()

    val signer: NostrSigner = NostrSignerInternal(KeyPair())
    val pubkey = signer.pubKey

    val (relayHost, relayPort) = harness.loopbackHostPort()
    val speakerEndpoint = "https://$relayHost:$relayPort"

    val room =
        NestsRoomConfig(
            authBaseUrl = "<unused-public-relay>",
            endpoint = speakerEndpoint,
            hostPubkey = pubkey,
            roomId = "rt-${UUID.randomUUID()}",
        )
    val moqNamespace = room.moqNamespace()
    // Build the same connect target the Kotlin speaker uses; the
    // Chromium page consumes it directly via `new URL(relay)`.
    // `NestsConnect.kt` uses `?jwt=<token>` query for auth and the
    // moq-rs relay we boot has `--auth-public ""` so the token is
    // empty — Chromium's WebTransport accepts an empty query value.
    val pageRelayUrl = "$speakerEndpoint/$moqNamespace?jwt="

    val pumpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Use a cert-capturing validator so we can pin the relay's
    // self-signed cert into Chromium's WebTransport via
    // `serverCertificateHashes`. The validator is just a wrapper —
    // it accepts every chain (delegating to PermissiveCertificateValidator
    // semantics) but stashes the leaf DER on the first handshake.
    val certCapture = CertCapturingValidator()
    val transport =
        QuicWebTransportFactory(
            parentScope = pumpScope,
            certificateValidator = certCapture,
        )

    val captureFactory: () -> SineWaveAudioCapture = {
        SineWaveAudioCapture(
            freqHz = 440,
            channelCount = channelCount,
            freqHzPerChannel = freqHzPerChannel,
        )
    }
    val encoderFactory: () -> JvmOpusEncoder = {
        JvmOpusEncoder(channelCount = channelCount)
    }
    val broadcastConfig = AudioBroadcastConfig(channelCount = channelCount)

    val speaker =
        connectNestsSpeaker(
            httpClient = StaticTokenNestsClientForBrowser,
            transport = transport,
            scope = pumpScope,
            room = room,
            signer = signer,
            speakerPubkeyHex = pubkey,
            captureFactory = captureFactory,
            encoderFactory = encoderFactory,
            broadcastConfig = broadcastConfig,
            framesPerGroup = 5,
        )
    val handle = speaker.startBroadcasting()
    delay(listenerLateJoinDelayMs)

    // The speaker's connect path completes a QUIC handshake before
    // returning, so the cert validator has captured the leaf cert by
    // now. Compute the SHA-256 the WebTransport spec wants — `value`
    // in `serverCertificateHashes` is the SHA-256 of the entire
    // DER-encoded X.509 certificate, NOT of the SPKI.
    val derSha256 =
        certCapture.derSha256()
            ?: error("cert capture failed — speaker handshake did not invoke validator")
    val derSha256B64 =
        java.util.Base64
            .getEncoder()
            .encodeToString(derSha256)

    // Run Playwright on a side thread; keep the Kotlin speaker alive
    // until Playwright signals done. Chromium cold-launch + Playwright
    // setup eats 3–5 s before the page starts its `durationSec`
    // capture window, so closing the speaker on a fixed wall-clock
    // delay is fundamentally racy. Instead, the speaker stays
    // broadcasting until the side thread reports completion (which
    // happens after the page reaches `body[data-state="done"]` and
    // the bun server's WS shutdown).
    val pwResultRef =
        java.util.concurrent.atomic
            .AtomicReference<PlaywrightDriver.HarnessRun>()
    val pwErrorRef =
        java.util.concurrent.atomic
            .AtomicReference<Throwable>()
    val pwLatch = java.util.concurrent.CountDownLatch(1)
    val pwThread =
        Thread({
            try {
                pwResultRef.set(
                    PlaywrightDriver.openListenPage(
                        relayUrlFull = pageRelayUrl,
                        broadcastPath = pubkey,
                        durationSec = speakerSeconds,
                        // Bigger overall timeout: Chromium cold-launch +
                        // Playwright runner setup eats 3–10 s on a busy
                        // CI runner before the page starts capturing.
                        overallTimeoutSec = speakerSeconds + 90,
                        serverCertHashB64 = derSha256B64,
                    ),
                )
            } catch (t: Throwable) {
                pwErrorRef.set(t)
            } finally {
                pwLatch.countDown()
            }
        }, "browser-interop-playwright").apply {
            isDaemon = true
            start()
        }

    // Wait (off the main coroutine) for Playwright to finish. The
    // speaker keeps broadcasting in the background of `pumpScope`
    // until we close it below.
    val pwOverallTimeoutSec = speakerSeconds + 120
    val ok =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            pwLatch.await(pwOverallTimeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)
        }
    runCatching { handle.close() }
    runCatching { speaker.close() }
    val out =
        try {
            if (!ok) error("Playwright did not complete within ${pwOverallTimeoutSec}s")
            pwErrorRef.get()?.let { throw it }
            pwResultRef.get() ?: error("Playwright thread did not produce a result")
        } finally {
            pumpScope.coroutineContext[Job]?.cancel()
        }

    assertTrue(
        out.exitCode == 0,
        "Playwright exited with code ${out.exitCode}.\n--- stdout ---\n${out.playwrightStdout}",
    )
    return BrowserListenOutput(pcmFile = out.pcmFile, stdout = out.playwrightStdout)
}

/**
 * Stub NestsClient for the browser interop scenarios. The harness's
 * `--auth-public ""` flag grants any path without a JWT, so we mint
 * an empty token. Mirrors [HangInteropTest]'s
 * `StaticTokenNestsClient`.
 */
private object StaticTokenNestsClientForBrowser : NestsClient {
    override suspend fun mintToken(
        room: NestsRoomConfig,
        publish: Boolean,
        signer: NostrSigner,
    ): String = ""
}

/**
 * Read a file of native-endian Float32 LE PCM into a [FloatArray].
 * Matches the format the bun WS server appends per binary frame —
 * which itself matches `hang-listen`'s output format so the existing
 * [PcmAssertions] helpers slot in unchanged.
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
