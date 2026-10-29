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
package com.vitorpamplona.amethyst.desktop.napplet

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletStorage
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DesktopNappletSession] end to end: blobs from a local "Blossom" server, the shared shell and shim,
 * the broker, and (opt-in) real Chromium.
 *
 * The Chromium tests need a display and download the CEF bundle on first run, so they run only with
 * AMETHYST_CEF_TEST=1, for example:
 *
 *   AMETHYST_CEF_TEST=1 xvfb-run -a ./gradlew :desktopApp:test --tests '*DesktopNappletSessionTest*'
 *
 * AMETHYST_CEF_DIR reuses an unpacked bundle; AMETHYST_DOOM_HTML adds the Doom napplet
 * (tools/napplet-doom/build/index.html).
 */
class DesktopNappletSessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tmp = createTempDirectory("napplet-session-test").toFile()
    private val blobs = ConcurrentHashMap<String, ByteArray>()
    private val requested = ConcurrentHashMap.newKeySet<String>()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requested += exchange.requestURI.path
                val body = blobs[exchange.requestURI.path.trimStart('/')]
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
    private val serverUrl = "http://127.0.0.1:${server.address.port}"
    private val sessions = mutableListOf<DesktopNappletSession>()
    private var frame: JFrame? = null

    companion object {
        /** Chromium starts once per process, as in the app; every test shares it. */
        private val sharedEngine by lazy {
            val installDir = System.getenv("AMETHYST_CEF_DIR")?.let(::File) ?: File(System.getProperty("java.io.tmpdir"), "amethyst-jcef-test")
            DesktopCefEngine(installDir, createTempDirectory("napplet-cef-cache").toFile())
        }
    }

    @After
    fun tearDown() {
        sessions.forEach { it.close() }
        SwingUtilities.invokeAndWait { frame?.dispose() }
        server.stop(0)
        scope.cancel()
        tmp.deleteRecursively()
    }

    /** Napplet storage that remembers every write, so the test sees what reached the broker. */
    private class RecordingStorage : NappletStorage {
        val values = ConcurrentHashMap<String, String>()
        val keyListings = AtomicInteger()

        override suspend fun get(
            coordinate: String,
            key: String,
        ) = values[key]

        override suspend fun set(
            coordinate: String,
            key: String,
            value: String,
        ) {
            values[key] = value
        }

        override suspend fun remove(
            coordinate: String,
            key: String,
        ) {
            values.remove(key)
        }

        override suspend fun keys(coordinate: String): List<String> {
            keyListings.incrementAndGet()
            return values.keys.toList()
        }
    }

    private fun runtime(engine: DesktopCefEngine = DesktopCefEngine(File(tmp, "jcef"), File(tmp, "jcef-cache"))) = DesktopNappletRuntime(engine, DesktopNappletBlobs(File(tmp, "blobs")) { OkHttpClient() }, scope)

    private fun session(
        html: ByteArray,
        storage: NappletStorage,
        runtime: DesktopNappletRuntime,
        servedBytes: ByteArray = html,
    ): DesktopNappletSession {
        val hash = sha256(html).toHexKey()
        blobs[hash] = servedBytes
        val author = KeyPair()
        val broker =
            NappletBroker(
                signer = NostrSignerInternal(author),
                ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()) { "tester" },
                consentPrompt = NappletConsentPrompt { _, _, _ -> GrantState.ALLOW_ONCE },
                storage = storage,
            )
        val launch =
            DesktopNappletLaunch(
                identity = NappletIdentity(author.pubKey.toHexKey(), "probe", hash),
                declared = setOf(NappletCapability.STORAGE),
                title = "Probe",
                paths = listOf(PathTag(DesktopNappletBlobs.INDEX_PATH, hash)),
                servers = listOf(serverUrl),
                accountPubKey = "tester",
            )
        return DesktopNappletSession(launch, null, broker, runtime).also { sessions += it }
    }

    @Test
    fun aBlobThatDoesNotMatchItsHashNeverReachesTheBrowser() =
        runBlocking {
            val session = session("<p>signed</p>".encodeToByteArray(), RecordingStorage(), runtime(), servedBytes = "<p>swapped</p>".encodeToByteArray())
            session.start()
            val state = withTimeout(30_000) { session.state.first { it !is DesktopNappletSession.State.Loading } }
            assertIs<DesktopNappletSession.State.Failed>(state)
            assertEquals(DesktopNappletSession.Failure.UNREACHABLE, state.reason)
        }

    private fun cefRuntime(): DesktopNappletRuntime {
        assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run napplets in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
        return runtime(sharedEngine)
    }

    private suspend fun show(session: DesktopNappletSession) {
        session.start()
        val state = withTimeout(600_000) { session.state.first { it !is DesktopNappletSession.State.Loading } }
        assertIs<DesktopNappletSession.State.Showing>(state, "the napplet did not open: $state")
        // A windowed CEF browser is created when its component is first shown.
        SwingUtilities.invokeAndWait {
            frame =
                JFrame("napplet").apply {
                    contentPane.add(state.component, BorderLayout.CENTER)
                    setBounds(0, 0, 960, 640)
                    isVisible = true
                }
        }
    }

    @Test
    fun aNappletReachesTheBrokerOnlyThroughTheShell() =
        runBlocking {
            val runtime = cefRuntime()
            val storage = RecordingStorage()
            val probe =
                """
                <!doctype html><html><body><script>
                (async function () {
                  // Chromium injects the bridge's query function into every frame, this one too.
                  // Calling it directly must reach nothing.
                  try {
                    window.__amethystNappletQuery({ request: JSON.stringify({ type: 'storage.set', id: 'x1', key: 'smuggled', value: '1' }),
                      persistent: false, onSuccess: function () {}, onFailure: function () {} });
                  } catch (e) {}
                  // CSP connect-src 'none': this must never leave the page.
                  try { await fetch('$serverUrl/leak'); } catch (e) {}
                  await window.napplet.storage.setItem('ok', 'yes');
                  var back = await window.napplet.storage.getItem('ok');
                  await window.napplet.storage.setItem('roundtrip', String(back));
                })();
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            show(session(probe, storage, runtime))

            withTimeout(60_000) { while (storage.values["roundtrip"] == null) delay(100) }
            assertEquals("yes", storage.values["roundtrip"], "a reply carried the value back to the napplet")
            assertFalse(storage.values.containsKey("smuggled"), "the iframe's own call to the bridge was refused")
            assertFalse("/leak" in requested, "the napplet's fetch never reached the network")
        }

    @Test
    fun theDoomNappletBootsAndAsksForItsSaves() =
        runBlocking {
            val runtime = cefRuntime()
            val doom = System.getenv("AMETHYST_DOOM_HTML")?.let(::File)
            assumeTrue("Set AMETHYST_DOOM_HTML to tools/napplet-doom/build/index.html", doom?.isFile == true)
            val storage = RecordingStorage()
            show(session(doom!!.readBytes(), storage, runtime))

            // The page asks for its saves while the title screen loads.
            withTimeout(120_000) { while (storage.keyListings.get() == 0) delay(100) }

            // And it draws: the start screen is not a blank window.
            delay(3_000)
            val shot = Robot().createScreenCapture(Rectangle(0, 0, 960, 640))
            val colors = HashSet<Int>()
            for (y in 0 until shot.height step 8) for (x in 0 until shot.width step 8) colors += shot.getRGB(x, y)
            System.getenv("AMETHYST_TEST_SHOT")?.let { ImageIO.write(shot, "png", File(it)) }
            assertTrue(colors.size > 8, "the napplet window shows more than a blank page (${colors.size} colors)")
        }
}
