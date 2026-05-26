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
package com.vitorpamplona.amethyst.ui.tor

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Tier-3 integration tests that drive the real Arti JNI shim on JVM, against the
 * Linux x86_64 host build of the same wrapper crate that powers Android. The .so
 * is checked in at `amethyst/src/test/native-libs/x86_64-linux/libarti_android.so`
 * and the Gradle test task sets `java.library.path` to point at it.
 *
 * **Layered safety net for our Tor stack:**
 *   - [TorManagerTest] — fast unit tests, no Arti, virtual time. Covers Kotlin
 *     self-heal logic.
 *   - This file (smoke) — JNI bridge loads, version JNI call works. Always runs
 *     on Linux x86_64 hosts. ~10ms. Catches build/link regressions in the .so.
 *   - This file (bootstrap) — opt-in via `-Pamethyst.arti.integration=true`. Hits
 *     `check.torproject.org` through real Tor. ~30-90s per test. Needs outbound
 *     TCP egress to arbitrary IPs/ports — works on most dev machines and Docker
 *     hosts with default networking; *will hang* on CI runners with restrictive
 *     egress lists.
 *   - `androidTest/.../tor/TorBootstrapInstrumentedTest` — same shape but against
 *     the Android .so on a connected device/emulator.
 *
 * **Run the slow ones:**
 * ```
 * ./gradlew :amethyst:testPlayDebugUnitTest \
 *     --tests "com.vitorpamplona.amethyst.ui.tor.TorArtiNativeIntegrationTest" \
 *     -Pamethyst.arti.integration=true
 * ```
 */
class TorArtiNativeIntegrationTest {
    private var dataDir: File? = null

    @After
    fun tearDown() {
        // Drop the in-process client between tests so the state file lock doesn't
        // bleed across (and our tests stay independent). Idempotent — no-op if
        // initialize never ran.
        try {
            ArtiNative.destroy()
        } catch (_: Throwable) {
            // Library may not have loaded if assumeArchAvailable skipped us.
        }
        dataDir?.deleteRecursively()
    }

    /**
     * Always-on smoke check: the host .so loads via `System.loadLibrary("arti_android")`
     * (configured by the Gradle test task's `java.library.path`) and a trivial JNI
     * function returns. If this fails, every other Tor test is moot — typical causes
     * are a stale .so after an arti version bump, a missing rebuild on the test
     * native-libs path, or a build that didn't export the expected JNI symbol.
     */
    @Test
    fun `library loads and reports a version`() {
        assumeArchAvailable()
        val version = ArtiNative.getVersion()
        assertTrue("Version string was: $version", version.startsWith("Arti "))
    }

    /**
     * Real bootstrap + SOCKS round trip. Regression net for: rustls
     * `CryptoProvider` install after the v2.3.0 bump, `fs-mistrust` host-trust
     * override, every Java_..._ArtiNative_* JNI export. Opt-in because it costs
     * 30-90s and needs network egress that not every CI grants.
     */
    @Test(timeout = BOOTSTRAP_TIMEOUT_MS + 60_000L)
    fun `bootstraps and proxies an HTTPS request through Tor`() {
        assumeArchAvailable()
        assumeIntegrationEnabled()
        dataDir = Files.createTempDirectory("arti-integ-").toFile()

        ArtiNative.setLogCallback { line -> println("[arti] $line") }

        val initResult = ArtiNative.initialize(dataDir!!.absolutePath)
        assertEquals("initialize returned $initResult", 0, initResult)

        val port = pickPort()
        val socksResult = ArtiNative.startSocksProxy(port)
        assertEquals("startSocksProxy returned $socksResult", 0, socksResult)

        val body = fetchThroughSocks(port, "https://check.torproject.org/api/ip")
        assertTrue(
            "Response should report IsTor:true — body was: $body",
            body.contains("\"IsTor\":true"),
        )
    }

    /**
     * Direct regression test for the destroy() / state-file-lock fix landed in
     * the self-heal work: after destroy, the *same* on-disk data dir must be
     * re-acquirable by a fresh initialize() — no lock-held error, no need to
     * clearAllArtiData.
     */
    @Test(timeout = (BOOTSTRAP_TIMEOUT_MS * 2) + 60_000L)
    fun `destroy then re-initialize releases the state file lock cleanly`() {
        assumeArchAvailable()
        assumeIntegrationEnabled()
        dataDir = Files.createTempDirectory("arti-integ-").toFile()

        ArtiNative.setLogCallback { line -> println("[arti] $line") }
        assertEquals("first initialize", 0, ArtiNative.initialize(dataDir!!.absolutePath))
        assertEquals("first startSocksProxy", 0, ArtiNative.startSocksProxy(pickPort()))

        assertEquals("destroy returned non-zero", 0, ArtiNative.destroy())

        // Re-init against the same data dir. Must NOT see "state file already locked".
        assertEquals(
            "re-initialize after destroy should succeed without clearing data",
            0,
            ArtiNative.initialize(dataDir!!.absolutePath),
        )
        val secondPort = pickPort()
        assertEquals("post-destroy startSocksProxy", 0, ArtiNative.startSocksProxy(secondPort))

        val body = fetchThroughSocks(secondPort, "https://check.torproject.org/api/ip")
        assertTrue("post-re-init IsTor — body was: $body", body.contains("\"IsTor\":true"))
    }

    private fun fetchThroughSocks(
        port: Int,
        url: String,
    ): String {
        val client =
            OkHttpClient
                .Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        return client.newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals("HTTP 200 from $url", 200, it.code)
            it.body.string()
        }
    }

    private fun pickPort(): Int = (40_000..49_999).random()

    /**
     * The checked-in test .so is built for Linux x86_64 only. Skip on other hosts
     * rather than failing — a developer on macOS or aarch64 shouldn't see a build
     * break just because they ran the full test suite.
     */
    private fun assumeArchAvailable() {
        val arch = System.getProperty("os.arch")?.lowercase().orEmpty()
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        assumeTrue(
            "Test .so is provided only for Linux x86_64 (was: $os $arch). " +
                "To run elsewhere, rebuild with `cargo build --release --target <host>` " +
                "and place at amethyst/src/test/native-libs/<host>/libarti_android.so.",
            os.contains("linux") && (arch == "amd64" || arch == "x86_64"),
        )
    }

    private fun assumeIntegrationEnabled() {
        assumeTrue(
            "Set -Pamethyst.arti.integration=true to enable. Needs network egress " +
                "to arbitrary IPs/ports (Tor directory authorities + guards) — restrictive " +
                "CI runners will hang in initialize().",
            System.getProperty("amethyst.arti.integration") == "true",
        )
    }

    companion object {
        private const val BOOTSTRAP_TIMEOUT_MS: Long = 120_000L
    }
}
