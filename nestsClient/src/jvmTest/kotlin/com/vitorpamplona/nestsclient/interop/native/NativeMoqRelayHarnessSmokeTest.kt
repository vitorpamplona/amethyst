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

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 1 smoke test for the cross-stack interop harness. Proves
 * the load-bearing infra works end-to-end:
 *
 *   - `interopBuildHangSidecars` Gradle task installed `moq-relay`
 *     and compiled the (stub) sidecar binaries.
 *   - [NativeMoqRelayHarness] boots a real `moq-relay` subprocess
 *     with a self-signed cert and `--auth-public ""`, ready to
 *     accept WebTransport handshakes.
 *   - The Phase-1 stub `hang-listen` binary runs cleanly and
 *     exits 0 (no protocol logic yet — that's Phase 2).
 *
 * Doesn't exercise any wire format. The actual interop scenarios
 * (I1 sine-wave round-trip, I2 late-join, …) live in
 * `HangInteropTest` once Phase 2 has the real subscribe/publish
 * loops in `hang-listen` / `hang-publish`. See
 * `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`
 * Phase 2 step 7.
 *
 * Gated by `-DnestsHangInterop=true`. Without that property the
 * test "skips" via `assumeHangInterop()` so default `:nestsClient:jvmTest`
 * runs stay green when the Rust toolchain isn't installed.
 */
class NativeMoqRelayHarnessSmokeTest {
    @BeforeTest
    fun gate() {
        NativeMoqRelayHarness.assumeHangInterop()
    }

    @Test
    fun harness_boots_relay_and_exposes_sidecar_binaries() {
        val harness = NativeMoqRelayHarness.shared()

        val (host, port) = harness.loopbackHostPort()
        assertEquals("127.0.0.1", host)
        assertTrue(port in 1024..65535, "expected ephemeral port, got $port")
        assertTrue(
            harness.relayUrl.startsWith("https://127.0.0.1:"),
            "relayUrl should be a localhost https URL, got ${harness.relayUrl}",
        )

        // Sidecar binaries exist + are executable. Phase 2 fills in
        // the actual subscribe/publish loops; here we just verify
        // they can be invoked.
        for (bin in listOf(harness.hangListenBin(), harness.hangPublishBin(), harness.udpLossShimBin())) {
            assertTrue(Files.isExecutable(bin), "sidecar binary not executable: $bin")
        }

        // moq-token CLI from cargo install — exercised once Phase 2
        // wires up real JWT-authenticated scenarios. Just check
        // existence here.
        assertTrue(
            Files.isExecutable(harness.moqTokenBin()),
            "moq-token CLI not executable: ${harness.moqTokenBin()}",
        )
    }

    @Test
    fun stub_hang_listen_runs_cleanly() {
        val harness = NativeMoqRelayHarness.shared()
        // The stub returns immediately with exit code 0. This proves
        // the binary is reachable from the test JVM and clap parsing
        // succeeds — i.e. Phase 2 only has to flesh out the body.
        val proc =
            ProcessBuilder(
                harness.hangListenBin().toString(),
                "--relay-url",
                harness.relayUrl,
                "--broadcast",
                "test/smoke",
                "--duration",
                "1",
            ).redirectErrorStream(true).start()
        val exited = proc.waitFor(10, TimeUnit.SECONDS)
        val output = proc.inputStream.bufferedReader().readText()
        assertTrue(exited, "hang-listen stub did not exit within 10 s. Output:\n$output")
        assertEquals(0, proc.exitValue(), "hang-listen stub exited non-zero. Output:\n$output")
        assertTrue(
            output.contains("Phase-1 stub"),
            "expected hang-listen Phase-1 stub banner in output. Got:\n$output",
        )
    }
}
