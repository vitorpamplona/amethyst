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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletInlineAsks
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [CefNappletSession] end to end: blobs from a local "Blossom" server, the shared shell and shim,
 * the broker, and (opt-in) real Chromium.
 *
 * The Chromium tests need a display and download the CEF bundle on first run, so they run only with
 * AMETHYST_CEF_TEST=1, for example:
 *
 *   AMETHYST_CEF_TEST=1 xvfb-run -a ./gradlew :desktopApp:test --tests '*CefNappletSessionTest*'
 *
 * AMETHYST_CEF_DIR reuses an unpacked bundle; AMETHYST_DOOM_HTML adds the Doom napplet
 * (tools/napplet-doom/build/index.html).
 */
class CefNappletSessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tmp = createTempDirectory("napplet-session-test").toFile()
    private val blobs = ConcurrentHashMap<String, ByteArray>()
    private val requested = ConcurrentHashMap.newKeySet<String>()
    private val reports = ConcurrentHashMap<String, String>()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requested += exchange.requestURI.path
                exchange.requestURI.rawQuery?.let { query ->
                    query.split('&').forEach { pair ->
                        reports[pair.substringBefore('=')] = URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
                    }
                }
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
    private val sessions = mutableListOf<CefNappletSession>()
    private val frames = mutableListOf<JFrame>()

    companion object {
        private const val BURST = 100

        /** Chromium starts once per process, as in the app; every test shares it. */
        private val sharedEngine by lazy {
            val installDir = System.getenv("AMETHYST_CEF_DIR")?.let(::File) ?: File(System.getProperty("java.io.tmpdir"), "amethyst-jcef-test")
            // Loopback allowed: the test's local server stands in for the web a site talks to.
            DesktopCefEngine(installDir, createTempDirectory("napplet-cef-cache").toFile(), DesktopSocksRouter(allowLocal = true))
        }
    }

    @After
    fun tearDown() {
        sessions.forEach { it.close() }
        SwingUtilities.invokeAndWait { frames.forEach { it.dispose() } }
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

    private fun runtime(
        engine: DesktopCefEngine = DesktopCefEngine(File(tmp, "jcef"), File(tmp, "jcef-cache")),
        torPort: StateFlow<Int?> = MutableStateFlow(null),
    ) = DesktopNappletRuntime(engine, NappletBlobCache(File(tmp, "blobs")), { OkHttpClient() }, scope, torPort, torWaitMs = 500)

    private fun session(
        html: ByteArray,
        storage: NappletStorage,
        runtime: DesktopNappletRuntime,
        servedBytes: ByteArray = html,
    ): CefNappletSession {
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
            NappletLaunch(
                identity = NappletIdentity(author.pubKey.toHexKey(), "probe", hash),
                profile = HostProfile.NAPPLET,
                declared = setOf(NappletCapability.STORAGE),
                requires = listOf("storage"),
                title = "Probe",
                paths = listOf(PathTag("/index.html", hash)),
                servers = listOf(serverUrl),
            )
        return CefNappletSession(launch, "tester", useTor = false, account = null, broker = broker, runtime = runtime).also { sessions += it }
    }

    @Test
    fun aBlobThatDoesNotMatchItsHashNeverReachesTheBrowser() =
        runBlocking {
            val session = session("<p>signed</p>".encodeToByteArray(), RecordingStorage(), runtime(), servedBytes = "<p>swapped</p>".encodeToByteArray())
            session.start()
            val state = withTimeout(30_000) { session.state.first { it is CefNappletSession.State.Showing || it is CefNappletSession.State.Failed } }
            assertIs<CefNappletSession.State.Failed>(state)
            assertEquals(CefNappletSession.Failure.UNREACHABLE, state.reason)
        }

    private fun cefRuntime(): DesktopNappletRuntime {
        assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run napplets in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
        return runtime(sharedEngine)
    }

    private suspend fun show(session: CefNappletSession) {
        session.start()
        val state = withTimeout(600_000) { session.state.first { it is CefNappletSession.State.Showing || it is CefNappletSession.State.Failed } }
        assertIs<CefNappletSession.State.Showing>(state, "the napplet did not open: $state")
        // A windowed CEF browser is created when its component is first shown.
        SwingUtilities.invokeAndWait {
            frames +=
                JFrame("napplet").apply {
                    contentPane.add(requireNotNull(state.component), BorderLayout.CENTER)
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
    fun aNappletDrawsItsOwnInlineImagesAndFonts() =
        runBlocking {
            val runtime = cefRuntime()
            val storage = RecordingStorage()
            // A single-file napplet carries its pictures and fonts as data: URLs.
            val pixel = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII="
            val probe =
                """
                <!doctype html><html><body><script>
                var img = new Image();
                img.onload = function () { window.napplet.storage.setItem('img', 'loaded'); };
                img.onerror = function () { window.napplet.storage.setItem('img', 'blocked'); };
                img.src = 'data:image/png;base64,$pixel';
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            show(session(probe, storage, runtime))

            withTimeout(60_000) { while (storage.values["img"] == null) delay(100) }
            assertEquals("loaded", storage.values["img"])
        }

    @Test
    fun aWebxdcAppKeepsItsStorageAndUpdatesButHasNoNetwork() =
        runBlocking {
            val runtime = cefRuntime()
            // The app reports through its console, the one way out it has; the session logs it.
            val results = ConcurrentLinkedQueue<String>()
            val previousSink = Log.sink
            Log.sink =
                LogSink { level, tag, message, throwable ->
                    message.substringAfter("XDCRESULT ", "").takeIf { it.isNotEmpty() }?.let { results += it.substringBefore(" (") }
                    previousSink.log(level, tag, message, throwable)
                }
            try {
                val page =
                    """
                    <!doctype html><html><head><script src="webxdc.js"></script></head><body><script>
                    (async function () {
                      var r = { self: window.webxdc.selfAddr, name: window.webxdc.selfName };
                      try { r.before = localStorage.getItem('k'); localStorage.setItem('k', 'v'); r.local = localStorage.getItem('k'); } catch (e) { r.local = 'blocked'; }
                      try { await fetch('$serverUrl/leak'); r.net = 'open'; } catch (e) { r.net = 'blocked'; }
                      try { await fetch('/js/data.json').then(function (x) { return x.json(); }).then(function (j) { r.own = j.ok; }); } catch (e) { r.own = 'blocked'; }
                      r.rtc = typeof RTCPeerConnection;
                      var got = [];
                      await window.webxdc.setUpdateListener(function (u) { got.push(u.payload.n + '@' + u.serial); }, 0);
                      r.replayed = got.join(',');
                      window.webxdc.sendUpdate({ payload: { n: got.length + 1 } }, '');
                      setTimeout(function () { r.after = got.join(','); console.log('XDCRESULT ' + JSON.stringify(r)); }, 500);
                    })();
                    </script></body></html>
                    """.trimIndent()
                val archive = zip("index.html" to page, "js/data.json" to "{\"ok\":true}", "manifest.toml" to "name = \"Probe\"")
                blobs["probe.xdc"] = archive
                val app = WebxdcApp("$serverUrl/probe.xdc", sha256(archive).toHexKey(), "instance-1", "Probe", null, null)
                val poster = KeyPair().pubKey.toHexKey()
                val me = KeyPair().pubKey.toHexKey()
                val launch = NappletLaunch.fromWebxdc(app, "post-1", poster, me, "Tester")

                fun open() = CefNappletSession(launch, me, useTor = false, account = null, broker = refusingBroker(), runtime = runtime).also { sessions += it }

                val first = open()
                show(first)
                val run1 =
                    withTimeout(60_000) {
                        while (results.isEmpty()) delay(100)
                        results.poll()
                    }
                assertTrue(run1.contains("\"local\":\"v\""), run1)
                assertTrue(run1.contains("\"net\":\"blocked\""), run1)
                assertTrue(run1.contains("\"own\":true"), "its own files load: $run1")
                assertTrue(run1.contains("\"rtc\":\"undefined\""), run1)
                assertTrue(run1.contains("\"replayed\":\"\""), run1)
                assertTrue(run1.contains("\"after\":\"1@1\""), run1)
                assertTrue(run1.contains("\"name\":\"Tester\"") && run1.contains("npub1"), run1)
                first.close()

                // Opened again: the same origin, so its storage and its update log are still there.
                show(open())
                val run2 =
                    withTimeout(60_000) {
                        while (results.isEmpty()) delay(100)
                        results.poll()
                    }
                assertTrue(run2.contains("\"before\":\"v\""), run2)
                assertTrue(run2.contains("\"replayed\":\"1@1\""), run2)
                assertTrue(run2.contains("\"after\":\"1@1,2@2\""), run2)
                assertFalse(requested.contains("/leak"), "the app reached the network")
            } finally {
                Log.sink = previousSink
            }
        }

    /** A broker that answers nothing: a webxdc app never reaches one. */
    private fun refusingBroker() =
        NappletBroker(
            signer = NostrSignerInternal(KeyPair()),
            ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()) { "tester" },
            consentPrompt = NappletConsentPrompt { _, _, _ -> GrantState.DENY },
        )

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun aBurstOfRepliesReachesTheNappletWhole() =
        runBlocking {
            val runtime = cefRuntime()
            val storage = RecordingStorage()
            repeat(BURST) { storage.values["k$it"] = "v$it" }
            // Replies to requests in flight together go to the page in batches: each must still
            // reach the request it answers.
            val probe =
                """
                <!doctype html><html><body><script>
                (async function () {
                  var reads = [];
                  for (var i = 0; i < $BURST; i++) reads.push(window.napplet.storage.getItem('k' + i));
                  var values = await Promise.all(reads);
                  await window.napplet.storage.setItem('burst', values.join(','));
                })();
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            show(session(probe, storage, runtime))

            withTimeout(60_000) { while (storage.values["burst"] == null) delay(100) }
            assertEquals((0 until BURST).joinToString(",") { "v$it" }, storage.values["burst"])
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

    /** Counts the NIP-07 prompts a site raised; every prompt says yes. */
    private class SitePrompts {
        val connects = AtomicInteger()
        val signatures = AtomicInteger()
    }

    private suspend fun site(
        files: Map<String, ByteArray>,
        runtime: DesktopNappletRuntime,
        user: NostrSignerInternal,
        author: NostrSignerInternal,
        prompts: SitePrompts = SitePrompts(),
        useTor: Boolean = false,
    ): CefNappletSession {
        files.values.forEach { blobs[sha256(it).toHexKey()] = it }
        val paths = files.map { (path, bytes) -> PathTag(path, sha256(bytes).toHexKey()) }
        val event: NamedSiteEvent = author.sign(NamedSiteEvent.build("blog", paths, servers = listOf(serverUrl), title = "Blog"))
        val broker =
            NappletBroker(
                signer = user,
                ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()) { user.pubKey },
                consentPrompt = NappletConsentPrompt { _, _, _ -> GrantState.ALLOW_ONCE },
                signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore()),
                nostrConnectPrompt =
                    NostrConnectPrompt { _, _ ->
                        prompts.connects.incrementAndGet()
                        // Paranoid: every signature asks, so the test sees the per-operation prompt.
                        AppConnectResult.Connected(AppSignerPolicy.PARANOID)
                    },
                signerConsentPrompt =
                    NostrSignerConsentPrompt { _, _, _ ->
                        prompts.signatures.incrementAndGet()
                        SignerOpGrant.AllowOnce
                    },
            )
        val launch = NappletLaunch.fromSite(event).getOrThrow()
        return CefNappletSession(launch, user.pubKey, useTor, account = null, broker = broker, runtime = runtime).also { sessions += it }
    }

    @Test
    fun aTorSiteDoesNotOpenWhileTorIsDown() =
        runBlocking {
            val user = NostrSignerInternal(KeyPair())
            val runtime = runtime()
            val session = site(mapOf("/index.html" to "<p>hi</p>".encodeToByteArray()), runtime, user, NostrSignerInternal(KeyPair()), useTor = true)
            session.start()
            val state = withTimeout(30_000) { session.state.first { it is CefNappletSession.State.Showing || it is CefNappletSession.State.Failed } }
            assertIs<CefNappletSession.State.Failed>(state)
            assertEquals(CefNappletSession.Failure.ROUTE_BLOCKED, state.reason)
            assertTrue(requested.isEmpty(), "not even its files were fetched: $requested")
            // The failed window keeps no claim: a site on the open web still gets its route.
            assertTrue(runtime.routes.claim(Any(), useTor = false, timeoutMs = 0))
            assertEquals(DesktopSocksRouter.Route.Direct, runtime.engine.router.route)
        }

    @Test
    fun aSiteRunsAtItsOwnOriginWithNip07() =
        runBlocking {
            val runtime = cefRuntime()
            val user = NostrSignerInternal(KeyPair())
            val author = NostrSignerInternal(KeyPair())
            val prompts = SitePrompts()
            val index = "<!doctype html><html><body><p>SITE-MARKER</p><script src=\"/app.js\"></script></body></html>"
            val app =
                """
                (async function () {
                  var server = '$serverUrl';
                  function report(key, value) {
                    return fetch(server + '/report?' + key + '=' + encodeURIComponent(String(value)), { mode: 'no-cors' }).catch(function () {});
                  }
                  try {
                    await report('origin', location.origin);
                    await report('missing', (await fetch('/missing.js')).status);
                    var deep = await (await fetch('/deep/route', { headers: { Accept: 'text/html' } })).text();
                    await report('spa', deep.indexOf('SITE-MARKER') >= 0);
                    localStorage.setItem('k', 'stored');
                    await report('storage', localStorage.getItem('k'));
                    await report('pubkey', await window.nostr.getPublicKey());
                    var signed = await window.nostr.signEvent({ kind: 1, created_at: 1700000000, tags: [], content: 'hello from an nsite' });
                    await report('signed', signed.pubkey + ':' + (signed.sig ? signed.sig.length : 0));
                  } catch (e) {
                    await report('error', (e && e.message) || e);
                  }
                  await report('done', 1);
                  // Not a click: the page's own frame must stay where it is.
                  location.href = server + '/escaped';
                })();
                """.trimIndent()
            show(site(mapOf("/index.html" to index.encodeToByteArray(), "/app.js" to app.encodeToByteArray()), runtime, user, author, prompts))

            withTimeout(60_000) { while (reports["done"] == null) delay(100) }
            assertEquals(null, reports["error"], "the page ran without an error")
            val origin = NappletWebContract.appOrigin(NappletWebContract.appId(author.pubKey, "blog", partition = user.pubKey))
            assertEquals(origin, reports["origin"], "its own origin, for this account")
            assertEquals("404", reports["missing"], "a file the site doesn't list is a 404")
            assertEquals("true", reports["spa"], "a client route falls back to the page")
            assertEquals("stored", reports["storage"])
            assertEquals(user.pubKey, reports["pubkey"], "window.nostr reached the signer")
            assertEquals("${user.pubKey}:128", reports["signed"], "and signed as the user")
            assertEquals(1, prompts.connects.get(), "the first NIP-07 call asked to connect")
            assertTrue(prompts.signatures.get() >= 1, "the signature asked first")

            delay(2_000)
            assertFalse("/escaped" in requested, "the page could not navigate itself away")
        }

    @Test
    fun aNappletsWebRtcCannotGoAroundTheRouter() =
        runBlocking {
            val runtime = cefRuntime()
            // A STUN server on this machine's own network address: loopback alone would not show a leak,
            // since Chromium gathers no candidates on it.
            val address =
                NetworkInterface
                    .getNetworkInterfaces()
                    .asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { it is Inet4Address }
            assumeTrue("No non-loopback IPv4 address to stand a STUN server on", address != null)
            val stun = DatagramSocket(InetSocketAddress(address, 0))
            val packets = AtomicInteger()
            thread(isDaemon = true) {
                val buffer = ByteArray(2048)
                while (!stun.isClosed) runCatching { stun.receive(DatagramPacket(buffer, buffer.size)).also { packets.incrementAndGet() } }
            }
            try {
                val probe =
                    """
                    <!doctype html><html><body><script>
                    (async function () {
                      var pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:${address!!.hostAddress}:${stun.localPort}' }] });
                      pc.createDataChannel('x');
                      await pc.setLocalDescription(await pc.createOffer());
                    })();
                    </script></body></html>
                    """.trimIndent().encodeToByteArray()
                show(session(probe, RecordingStorage(), runtime))
                delay(8_000)
                assertEquals(0, packets.get(), "no UDP left the machine around the router")
            } finally {
                stun.close()
            }
        }

    @Test
    fun aNappletCannotReachAnOpenRouteThroughWebRtc() =
        runBlocking {
            val runtime = cefRuntime()
            // TURN over TCP goes through the proxy like any page request, so while an nSite has the
            // route open, WebRTC alone would give a napplet a way out. The napplet, and a frame it
            // makes, each try one.
            val address =
                NetworkInterface
                    .getNetworkInterfaces()
                    .asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.asSequence() }
                    .firstOrNull { it is Inet4Address }
            assumeTrue("No non-loopback IPv4 address to stand a TURN server on", address != null)
            val turn = ServerSocket(0, 50, address)
            val connections = AtomicInteger()
            thread(isDaemon = true) {
                while (!turn.isClosed) runCatching { turn.accept().also { connections.incrementAndGet() }.close() }
            }
            runtime.engine.router.route = DesktopSocksRouter.Route.Direct
            try {
                val attempt =
                    "try{var c=new RTCPeerConnection({iceServers:[{urls:'turn:${address!!.hostAddress}:${turn.localPort}?transport=tcp'," +
                        "username:'u',credential:'p'}],iceTransportPolicy:'relay'});c.createDataChannel('x');" +
                        "c.createOffer().then(function(o){return c.setLocalDescription(o);});}catch(e){}"
                val probe =
                    """
                    <!doctype html><html><body><script>
                    $attempt
                    var child = document.createElement('iframe');
                    child.srcdoc = '<script>' + "$attempt" + '<' + '/script>';
                    document.body.appendChild(child);
                    </script></body></html>
                    """.trimIndent().encodeToByteArray()
                show(session(probe, RecordingStorage(), runtime))
                delay(8_000)
                assertEquals(0, connections.get(), "no TURN connection left through the open route")
            } finally {
                runtime.engine.router.route = DesktopSocksRouter.Route.Blocked
                turn.close()
            }
        }

    @Test
    fun zoomingOneWindowLeavesTheOthersAlone() =
        runBlocking {
            val runtime = cefRuntime()
            // Each napplet keeps reporting how wide its page is laid out: zoomed in, it gets narrower.
            val probe =
                """
                <!doctype html><html><body><script>
                setInterval(function () { window.napplet.storage.setItem('width', String(window.innerWidth)); }, 200);
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            val first = RecordingStorage()
            val second = RecordingStorage()
            val zoomed = session(probe, first, runtime)
            show(zoomed)
            show(session(probe + " ".encodeToByteArray(), second, runtime))

            suspend fun widthOf(storage: RecordingStorage) =
                withTimeout(60_000) {
                    while (storage.values["width"] == null) delay(100)
                    storage.values.getValue("width").toInt()
                }
            val before = widthOf(first)
            val otherBefore = widthOf(second)

            zoomed.setZoom(200)
            withTimeout(30_000) { while (first.values.getValue("width").toInt() > before * 2 / 3) delay(100) }
            delay(1_000)
            assertEquals(otherBefore, second.values.getValue("width").toInt(), "the other window kept its size")
        }

    @Test
    fun aNappletInsideANotePaintsWithoutAWindowTakesClicksAndNeverAsks() =
        runBlocking {
            val runtime = cefRuntime()
            val storage = RecordingStorage()
            val asks = NappletInlineAsks()
            val prompts = AtomicInteger()
            // Red until clicked, then blue. On load it writes to storage, which needs the user.
            val html =
                """
                <!doctype html><html><body style="margin:0;height:100vh;background:rgb(255,0,0)"
                  onclick="document.body.style.background='rgb(0,0,255)'"><script>
                window.napplet.storage.setItem('boot', '1').catch(function () {});
                document.addEventListener('keydown', function (e) { if (e.key === 'ArrowDown') document.body.style.background = 'rgb(0,255,0)'; });
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            val hash = sha256(html).toHexKey()
            blobs[hash] = html
            val author = KeyPair()
            val broker =
                NappletBroker(
                    signer = NostrSignerInternal(author),
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()) { "tester" },
                    consentPrompt =
                        NappletConsentPrompt { _, _, _ ->
                            prompts.incrementAndGet()
                            GrantState.ALLOW_ONCE
                        },
                    storage = storage,
                    inlineAsks = asks,
                )
            val launch =
                NappletLaunch(
                    identity = NappletIdentity(author.pubKey.toHexKey(), "inline", hash),
                    profile = HostProfile.NAPPLET,
                    declared = setOf(NappletCapability.STORAGE),
                    requires = listOf("storage"),
                    title = "Inline",
                    paths = listOf(PathTag("/index.html", hash)),
                    servers = listOf(serverUrl),
                ).inline("note-1")
            val view = OffscreenNappletView()
            view.resize(320, 200, 1.0)
            val session = CefNappletSession(launch, "tester", useTor = false, account = null, broker = broker, runtime = runtime, offscreen = view).also { sessions += it }
            session.start()

            val state = withTimeout(600_000) { session.state.first { it is CefNappletSession.State.Showing || it is CefNappletSession.State.Failed } }
            assertIs<CefNappletSession.State.Showing>(state, "the napplet did not open: $state")
            assertEquals(null, state.component, "no window to place")

            val red = withTimeout(60_000) { view.frame.first { it != null && it.centerPixel().red > 0.9f } }!!
            assertEquals(320 to 200, red.width to red.height, "frames come at the size the note gave")

            SwingUtilities.invokeAndWait {
                val now = System.currentTimeMillis()
                val press = MouseEvent(Canvas(), MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK, 160, 100, 1, false, MouseEvent.BUTTON1)
                view.mouse(press, MouseEvent.MOUSE_PRESSED, 160, 100)
                val release = MouseEvent(Canvas(), MouseEvent.MOUSE_RELEASED, now + 20, 0, 160, 100, 1, false, MouseEvent.BUTTON1)
                view.mouse(release, MouseEvent.MOUSE_RELEASED, 160, 100)
            }
            withTimeout(30_000) { view.frame.first { it != null && it.centerPixel().blue > 0.9f } }

            // The note forwards the window's own key events once the page has focus.
            SwingUtilities.invokeAndWait {
                view.focus(true)
                val now = System.currentTimeMillis()
                view.key(KeyEvent(Canvas(), KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))
                view.key(KeyEvent(Canvas(), KeyEvent.KEY_RELEASED, now + 20, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED))
            }
            withTimeout(30_000) { view.frame.first { it != null && it.centerPixel().green > 0.9f } }

            withTimeout(30_000) { asks.asked("note-1").first { it } }
            assertEquals(0, prompts.get(), "nothing was asked from inside the note")
            assertFalse(storage.values.containsKey("boot"), "the write that needed the user was refused")
        }

    private fun ImageBitmap.centerPixel(): Color = toPixelMap()[width / 2, height / 2]
}
