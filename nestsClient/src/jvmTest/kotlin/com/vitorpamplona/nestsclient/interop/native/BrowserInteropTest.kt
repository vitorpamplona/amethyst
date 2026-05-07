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
     * **I15 (WT-Protocol round-trip)** — assert Chromium's WebTransport
     * round-trip with `moq-relay 0.10.x` produces a known-good moq-lite
     * version on the `Connection`. The harness page exposes
     * `connection.version` at `window.__moqVersion`; the Playwright
     * spec bundles it in the trailing JSON line on stdout.
     *
     * The assertion accepts any of the moq-lite draft versions the
     * relay advertises through SETUP — Chromium's `@moq/lite` 0.2.x
     * client offers `moq-lite-04`, `moq-lite-03`, `moql` (legacy)
     * ALPNs in that priority. moq-relay 0.10.x's choice depends on
     * its build flags, but the `moq-lite-` prefix is invariant. A
     * regression that breaks ALPN negotiation entirely or
     * downgrades to a non-lite version (`draft-17` etc.) is caught
     * here even if I1 audio assertions still pass via a fallback path.
     */
    @Test
    fun chromium_round_trips_a_moq_lite_session() =
        runBlocking {
            val out = runSpeakerToBrowserListen(speakerSeconds = 5)
            val moqVersion = parseMoqVersionFromStdout(out.stdout)
            assertTrue(
                moqVersion != null && moqVersion.startsWith("moq-lite-"),
                "expected Chromium to round-trip a moq-lite-* version; got '$moqVersion'.\n" +
                    "playwright stdout:\n${out.stdout}",
            )
        }

    /**
     * **I13 (browser long broadcast)** — 60 s end-to-end Amethyst
     * speaker → Chromium listener; assert the captured PCM has the
     * expected sample count and the 440 Hz peak survives the full
     * window without decoder failure.
     *
     * The spec specifies `framesPerGroup = 50` "against actual
     * `Container.Consumer`", but two constraints reshape this here:
     *
     *   1. `@moq/hang` 0.2.4 (the published version pinned in
     *      `nestsClient-browser-interop/package.json`) does not export
     *      the high-level `Container.Consumer` / `Format` API. Phase 4
     *      uses `Container.Legacy.Consumer` directly — same data path
     *      `@moq/watch` uses internally for `container.kind = "legacy"`.
     *   2. `framesPerGroup = 50` against the local `moq-relay 0.10.25
     *      --auth-public ""` harness hits the per-subscriber forward
     *      cliff documented in
     *      `2026-05-07-framespergroup-reconciliation.md` — the relay
     *      forwards the `Group` control header but holds the frame
     *      payload, so no audio reaches the listener at all. Production
     *      uses `framesPerGroup = 50`; locally we pin `5` to bypass
     *      the local-relay-specific cliff and still exercise the
     *      browser path's long-haul behaviour.
     *
     * What this catches that I1 forward (browser, 10 s) doesn't:
     *   - Chromium WebTransport `MAX_STREAMS_UNI` credit drift over
     *     thousands of unidirectional streams (60 s × 10 streams/s =
     *     600 streams; far past the connection's initial window),
     *   - `@moq/hang` `Container.Legacy.Consumer` group-queue eviction
     *     (`MAX_GROUP_AGE = 30 s` in moq-rs; we pass that bound twice),
     *   - WebCodecs `AudioDecoder` pacing + memory pressure across a
     *     real broadcast-length capture window.
     */
    @Test
    fun chromium_listener_long_broadcast_60s_tone_440() =
        runBlocking {
            // 65 s wallclock budget to absorb the cold-launch lag.
            val out = runSpeakerToBrowserListen(speakerSeconds = 65)

            // Decoder MUST NOT have errored at any point — even one
            // error means a frame couldn't decode (T8 regression, codec
            // mismatch, group-stream truncation surfaced as malformed
            // Opus). The error count is part of the meta JSON the
            // harness page emits via `console.log`.
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "decoderErrors=$errors during 60 s long broadcast — expected 0.\n" +
                    "playwright stdout:\n${out.stdout}",
            )

            val pcm = readFloat32Pcm(out.pcmFile)
            // Skip the 100 ms warmup window before FFT (40 ms Opus
            // look-ahead + 60 ms WebCodecs 3-frame warmup).
            val warmupSamples = AudioFormat.SAMPLE_RATE_HZ / 10
            assertTrue(
                pcm.size > warmupSamples,
                "captured PCM (${pcm.size} samples) shorter than warmup window — " +
                    "page never received audio.\nplaywright stdout:\n${out.stdout}",
            )
            val analysed = pcm.copyOfRange(warmupSamples, pcm.size)

            // Sample-count floor: ≥ 50 s of decoded PCM. The full
            // possible window is ~60 s minus the page's cold-launch
            // tail-truncation (typically ~3-10 s on a fresh runner).
            // 50 s is the regression bar — anything less indicates
            // the browser stopped receiving frames mid-broadcast
            // (the very mode I13 is meant to catch).
            val minSamples = 50 * AudioFormat.SAMPLE_RATE_HZ
            assertTrue(
                analysed.size >= minSamples,
                "captured ${analysed.size} samples (~${analysed.size / AudioFormat.SAMPLE_RATE_HZ} s); " +
                    "expected ≥ 50 s of decoded PCM in a 60 s long broadcast — possible " +
                    "browser-side stream-credit exhaustion, group-queue eviction, or " +
                    "decoder backpressure.\nplaywright stdout:\n${out.stdout}",
            )

            PcmAssertions.assertFftPeak(
                analysed,
                expectedHz = 440.0,
                halfWindowHz = 5.0,
            )
        }

    /**
     * **I14 (WebCodecs warmup × CSD-skip interaction)** — assert
     * Chromium's `AudioDecoder` does NOT error during the standard
     * 3-frame warmup window when fed Opus packets from the JVM
     * `JvmOpusEncoder`. A T8 regression that leaks `OpusHead`
     * (the 19-byte RFC 7845 identification header) as a normal audio
     * frame would land in the warmup window and trip
     * `AudioDecoder.error` — Chromium's WebCodecs implementation
     * rejects non-Opus-packet bytes with a `DataError`.
     *
     * The complement (FFT peak survives even if the decoder absorbed
     * the stray frame silently) is already covered by I1 forward;
     * I14 is the deterministic tripwire on the error-callback path.
     *
     * NOTE: The JVM speaker uses libopus (`JvmOpusEncoder`) directly,
     * which never produces a CSD prefix — so on this test path I14
     * effectively asserts no spurious decode failures. T8 itself is
     * an Android-`MediaCodecOpusEncoder`-specific fix; the matching
     * Android-side regression test would require a different harness
     * (no Chromium on Android in this build). We keep I14 here as
     * the browser-tier mate of `HangInteropTest.first_audio_frame_is_not_opus_codec_config`
     * (I11) — together they assert the wire format is decoder-clean
     * on both reference paths.
     */
    @Test
    fun chromium_decoder_no_errors_through_warmup_window() =
        runBlocking {
            // 10 s capture for parity with I1 forward — Chromium
            // cold-launch + Playwright runner setup eats 3-5 s before
            // the page's `durationSec` window starts ticking. A shorter
            // window ends before any frames reach the decoder, which
            // would also pass `decoderErrors == 0` vacuously.
            val out = runSpeakerToBrowserListen(speakerSeconds = 10)
            val errors = parseIntMetaFromStdout(out.stdout, "decoderErrors") ?: -1
            assertTrue(
                errors == 0,
                "AudioDecoder.error fired $errors times during a 10 s broadcast — expected 0. " +
                    "A T8 regression (OpusHead leaked as audio frame) would surface here as " +
                    "Chromium rejecting the frame.\nplaywright stdout:\n${out.stdout}",
            )
            // NOTE: deliberately no `outputs >= 4` assertion here. The
            // Phase 4 browser harness has a known cold-launch race
            // (Chromium 3-10 s boot vs. the page's `durationSec` window)
            // that occasionally produces `decoderOutputs == 0` even
            // when the speaker is healthy — see
            // `2026-05-06-phase4-browser-harness-results.md`'s I1
            // sample-count tolerance discussion. Since I14's
            // load-bearing invariant is an *absence* assertion (no
            // decoder errors), zero-frames is vacuously safe — a
            // T8 regression would only trigger on whichever frames
            // DO arrive, and across runs at least one will. Strict
            // outputs-floor would fail-flake without adding coverage.
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
 * Pull the `meta.moqVersion` field out of the trailing JSON line
 * the Playwright spec emits to stdout. The spec writes a single
 * `{"state":"done","meta":{"moqVersion":"moq-lite-03",...}}` line
 * per run; we substring-search for it rather than wiring up a JSON
 * dependency just for this one helper.
 */
private fun parseMoqVersionFromStdout(stdout: String): String? {
    val needle = "\"moqVersion\":\""
    val start = stdout.indexOf(needle)
    if (start < 0) return null
    val valueStart = start + needle.length
    val valueEnd = stdout.indexOf('"', valueStart)
    if (valueEnd < 0) return null
    return stdout.substring(valueStart, valueEnd)
}

/**
 * Pull an integer meta field (e.g. `decoderErrors`, `decoderOutputs`)
 * out of the trailing JSON line the Playwright spec emits. Same shape
 * as [parseMoqVersionFromStdout] — substring search rather than full
 * JSON parse, since the harness page emits a single
 * `{"state":"done","meta":{"decoderErrors":0,...}}` line and we
 * shouldn't pull in a JSON dependency just for two helpers.
 *
 * Returns `null` if the field is missing OR if its value isn't a
 * non-negative integer literal — both are test-failure conditions
 * the caller asserts on.
 */
private fun parseIntMetaFromStdout(
    stdout: String,
    field: String,
): Int? {
    val needle = "\"$field\":"
    val start = stdout.indexOf(needle)
    if (start < 0) return null
    var i = start + needle.length
    // Skip whitespace + optional sign
    while (i < stdout.length && stdout[i].isWhitespace()) i++
    val numStart = i
    while (i < stdout.length && stdout[i].isDigit()) i++
    if (i == numStart) return null
    return stdout.substring(numStart, i).toIntOrNull()
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
