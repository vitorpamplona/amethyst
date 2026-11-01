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
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentCatalog
import com.vitorpamplona.amethyst.commons.napplet.NappletIntentDispatcher
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsChunk
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsPickOptions
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFsWriteMode
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletPickedFile
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
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.tags.NappletIntent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogSink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.cef.browser.CefBrowser
import org.cef.browser.OffscreenCefBrowser
import org.cef.handler.CefFocusHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
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
import java.awt.image.BufferedImage
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
 * (tools/napplet-doom/build/index.html); AMETHYST_PHOTOHOP_HTML adds PhotoHop, the NAP-FS image
 * editor (its blob, `16511c25…`, from any of its Blossom servers).
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
        offscreen: OffscreenNappletView? = null,
        declared: Set<NappletCapability> = setOf(NappletCapability.STORAGE),
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
                declared = declared,
                requires = declared.map { it.name.lowercase() },
                title = "Probe",
                paths = listOf(PathTag("/index.html", hash)),
                servers = listOf(serverUrl),
            )
        return CefNappletSession(launch, "tester", useTor = false, account = null, broker = broker, runtime = runtime, offscreen = offscreen).also { sessions += it }
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
                  Object.getOwnPropertyNames(window).filter(function (n) { return n.indexOf('__amethystNappletQuery') === 0; }).forEach(function (n) {
                    try {
                      window[n]({ request: JSON.stringify({ type: 'storage.set', id: 'x1', key: 'smuggled', value: '1' }),
                        persistent: false, onSuccess: function () {}, onFailure: function () {} });
                    } catch (e) {}
                  });
                  window.napplet.storage.setItem('found', String(Object.getOwnPropertyNames(window).some(function (n) { return n.indexOf('__amethystNappletQuery_') === 0; })));
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
            assertEquals("true", storage.values["found"], "the iframe did find the query function to try it")
            assertFalse("/leak" in requested, "the napplet's fetch never reached the network")
        }

    @Test
    fun closingOneNappletLeavesTheOthersBridgeWorking() =
        runBlocking {
            val runtime = cefRuntime()

            // Writes `early` on load and `late` a while after: the second write tells whether the
            // page could still reach the broker after another session went away.
            fun probe(name: String) =
                """
                <!doctype html><html><body><script>
                window.napplet.storage.setItem('early', '$name');
                setTimeout(function () { window.napplet.storage.setItem('late', '$name'); }, 6000);
                </script></body></html>
                """.trimIndent().encodeToByteArray()

            val keptStorage = RecordingStorage()
            val kept = session(probe("kept"), keptStorage, runtime).also { show(it) }
            // The one that closes runs inside a note, as when its window is opened from the feed.
            val closingStorage = RecordingStorage()
            val closing =
                session(probe("closing"), closingStorage, runtime, offscreen = OffscreenNappletView().apply { resize(320, 240, 1.0) }).also {
                    it.start()
                    withTimeout(600_000) { it.state.first { state -> state is CefNappletSession.State.Showing } }
                }
            withTimeout(60_000) { while (keptStorage.values["early"] == null || closingStorage.values["early"] == null) delay(100) }

            // A note's run ends (the user opened its window, or scrolled away) while another runs on.
            closing.close()
            withTimeout(60_000) { while (keptStorage.values["late"] == null) delay(100) }
            assertEquals("kept", keptStorage.values["late"], "the page still open lost its bridge when another closed")

            // Opening a note's app in a window: the window starts while the note's run is closing.
            val noteStorage = RecordingStorage()
            val note =
                session(probe("note"), noteStorage, runtime, offscreen = OffscreenNappletView().apply { resize(320, 240, 1.0) }).also {
                    it.start()
                    withTimeout(600_000) { it.state.first { state -> state is CefNappletSession.State.Showing } }
                }
            withTimeout(60_000) { while (noteStorage.values["early"] == null) delay(100) }
            val windowStorage = RecordingStorage()
            val window = session(probe("window"), windowStorage, runtime)
            val opening = async { show(window) }
            withTimeout(60_000) { window.state.first { it is CefNappletSession.State.Loading || it is CefNappletSession.State.Showing } }
            note.close()
            opening.await()
            withTimeout(60_000) { while (windowStorage.values["early"] == null) delay(100) }
            assertEquals("window", windowStorage.values["early"], "the window opened as the note's run closed reaches the broker")
            kept.close()
        }

    @Test
    fun twoNappletsTalkOverInc() =
        runBlocking {
            val runtime = cefRuntime()
            val declared = setOf(NappletCapability.STORAGE, NappletCapability.INC)

            // Listens on a topic; on the first event it opens a channel back to whoever sent it.
            val listener =
                """
                <!doctype html><html><body><script>
                var inc = window.napplet.inc, opened = false;
                var sub = inc.on('napplet:profile/open', function (e) {
                  if (opened) return; opened = true;
                  window.napplet.storage.setItem('pubkey', e.payload.pubkey);
                  window.napplet.storage.setItem('sender', e.sender);
                  inc.channel.open(e.sender).then(function (ch) {
                    ch.on(function (m) { window.napplet.storage.setItem('ack', JSON.stringify(m.payload)); ch.close(); });
                    ch.emit({ hello: 'speaker' });
                  }, function (err) { window.napplet.storage.setItem('openError', String(err.message)); });
                });
                sub.ready.then(function () { window.napplet.storage.setItem('ready', 'yes'); });
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            // Emits a convention URI until a channel opens to it, answers on it, and records its end.
            val speaker =
                """
                <!doctype html><html><body><script>
                var inc = window.napplet.inc;
                var timer = setInterval(function () { inc.emit('napplet:profile/open?pubkey=ab%2Bc'); }, 200);
                inc.channel.onOpened(function (ch) {
                  clearInterval(timer);
                  window.napplet.storage.setItem('peer', ch.peer);
                  ch.on(function (m) { window.napplet.storage.setItem('hello', m.payload.hello); ch.emit({ ok: true }); });
                  ch.onClosed(function (c) { window.napplet.storage.setItem('closed', c.channelId === ch.id ? 'yes' : 'wrong id'); });
                });
                </script></body></html>
                """.trimIndent().encodeToByteArray()

            val listenerStorage = RecordingStorage()
            val speakerStorage = RecordingStorage()
            show(session(listener, listenerStorage, runtime, declared = declared))
            withTimeout(60_000) { while (listenerStorage.values["ready"] == null) delay(100) }
            show(session(speaker, speakerStorage, runtime, declared = declared))

            withTimeout(60_000) { while (speakerStorage.values["closed"] == null || listenerStorage.values["ack"] == null) delay(100) }
            assertEquals("ab+c", listenerStorage.values["pubkey"], "the convention URI's query reached the listener as its payload")
            val sender = listenerStorage.values["sender"]!!
            assertTrue(sender.matches(Regex("[0-9a-f]{32}")), "the sender is a runtime endpoint id, not the d tag: $sender")
            assertEquals("speaker", speakerStorage.values["hello"])
            assertEquals("""{"ok":true}""", listenerStorage.values["ack"])
            assertEquals("yes", speakerStorage.values["closed"], "closing the channel told the other end")
            assertFalse(listenerStorage.values.containsKey("openError"))
            assertTrue(speakerStorage.values["peer"]!!.matches(Regex("[0-9a-f]{32}")))
            assertTrue(speakerStorage.values["peer"] != sender, "each napplet has its own endpoint id")
        }

    @Test
    fun anIntentStartsItsHandlerAndIsDeliveredWhenItIsReady() =
        runBlocking {
            val runtime = cefRuntime()
            val declared = setOf(NappletCapability.STORAGE, NappletCapability.INTENT)

            // The handler records what it is handed, whenever it registers for it.
            val handler =
                """
                <!doctype html><html><body><script>
                setTimeout(function () {
                  window.napplet.intent.onDelivery(function (d) {
                    window.napplet.storage.setItem('delivered', JSON.stringify(d));
                  });
                }, 1500);
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            // The source asks for a feed to be opened, by convention URI, and records the result.
            val source =
                """
                <!doctype html><html><body><script>
                window.napplet.intent.invoke('napplet:feed/open?filters=a%2Bb').then(function (r) {
                  window.napplet.storage.setItem('result', JSON.stringify(r));
                });
                </script></body></html>
                """.trimIndent().encodeToByteArray()

            val handlerStorage = RecordingStorage()
            val launched = mutableListOf<CefNappletSession>()
            val publisher = KeyPair()
            val manifest =
                NostrSignerInternal(publisher).sign(
                    NamedNappletEvent.build("reader", "ab".repeat(32), "reads feeds", optional = listOf("intent"), archetypes = listOf("feed"), intents = listOf(NappletIntent("napplet:feed/open", listOf("filters")))),
                )
            runtime.intents =
                NappletIntentDispatcher(
                    catalog = { NappletIntentCatalog(listOfNotNull(NappletIntentCatalog.handlerOf(manifest))) },
                    launch = {
                        session(handler, handlerStorage, runtime, declared = declared).also {
                            launched += it
                            show(it)
                        }
                    },
                )

            val sourceStorage = RecordingStorage()
            val sourceSession = session(source, sourceStorage, runtime, declared = declared).also { show(it) }

            withTimeout(120_000) { while (handlerStorage.values["delivered"] == null || sourceStorage.values["result"] == null) delay(100) }
            val result = Json.parseToJsonElement(sourceStorage.values["result"]!!).jsonObject
            assertEquals("true", result["ok"]!!.jsonPrimitive.content)
            assertEquals("35129:${publisher.pubKey.toHexKey()}:reader", result["handler"]!!.jsonPrimitive.content)
            assertEquals(1, launched.size, "the handler was started for it")
            val delivery = Json.parseToJsonElement(handlerStorage.values["delivered"]!!).jsonObject
            assertEquals("napplet:feed/open", delivery["convention"]!!.jsonPrimitive.content)
            assertEquals("a+b", delivery["payload"]!!.jsonObject["filters"]!!.jsonPrimitive.content)
            // The sender is the source's catalog id, from its launch, not anything it said.
            assertEquals(NappletIntentCatalog.catalogId(sourceSession.launch.identity), delivery["sender"]!!.jsonPrimitive.content)
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
                      // A frame the app makes itself gets a fresh window: WebRTC must be gone there too.
                      try { var f = document.createElement('iframe'); document.body.appendChild(f); r.frameRtc = typeof f.contentWindow.RTCPeerConnection; } catch (e) { r.frameRtc = 'error'; }
                      // Every app lives under napplet.local: a cookie on that domain would reach all the others.
                      try { document.cookie = 'xdcprobe=1; domain=napplet.local; max-age=3600; secure; samesite=none'; r.cookie = document.cookie; } catch (e) { r.cookie = 'error'; }
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
                assertTrue(run1.contains("\"frameRtc\":\"undefined\""), "a frame the app makes has no WebRTC either: $run1")
                assertFalse(run1.contains("xdcprobe"), "an app can't keep a cookie the other apps would see: $run1")
                assertTrue(run1.contains("\"replayed\":\"\""), run1)
                assertTrue(run1.contains("\"after\":\"1@1\""), run1)
                // A pseudonym of its own in this app, never the user's key.
                assertTrue(run1.contains("\"name\":\"Tester\"") && run1.contains("\"self\":\"${WebxdcApp.selfAddr(me, poster, "instance-1")}\""), run1)
                assertFalse(run1.contains("npub1") || run1.contains(me), run1)
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

                // Two documents of the app at once (the note's run and a window): each takes its own
                // serial, where both used to take the next one and the second write replaced the first.
                show(open())
                show(open())
                val pair =
                    withTimeout(60_000) {
                        while (results.size < 2) delay(100)
                        listOf(results.poll(), results.poll())
                    }
                val serials = pair.map { run -> Regex("@(\\d+)").findAll(run.substringAfter("\"after\":")).map { it.groupValues[1].toInt() }.toList() }
                serials.forEach { assertEquals(it.distinct(), it, "no serial twice: $pair") }
                assertEquals(setOf(1, 2, 3, 4), serials.flatten().toSet(), "both updates kept: $pair")
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

    /** NAP-FS with the system dialog scripted: what the user "picks", and every byte written. */
    private class ScriptedFiles(
        private val opened: ByteArray,
    ) : NappletFilePort {
        val picks = AtomicInteger()
        val reads = AtomicInteger()
        val saved = ConcurrentHashMap<String, ByteArray>()

        override suspend fun pickOpen(
            options: NappletFsPickOptions,
            multiple: Boolean,
        ): List<NappletPickedFile> {
            picks.incrementAndGet()
            return listOf(NappletPickedFile("open", "gradient.png", opened.size.toLong()))
        }

        override suspend fun pickSave(options: NappletFsPickOptions): NappletPickedFile {
            picks.incrementAndGet()
            return NappletPickedFile("save", options.suggestedName ?: "export.png", writable = true)
        }

        override suspend fun size(handle: String): Long? = (if (handle == "open") opened else saved[handle])?.size?.toLong()

        override suspend fun read(
            handle: String,
            offset: Long,
            length: Int,
        ): NappletFsChunk {
            reads.incrementAndGet()
            val from = minOf(offset, opened.size.toLong()).toInt()
            return NappletFsChunk(opened.copyOfRange(from, minOf(opened.size, from + length)), opened.size.toLong())
        }

        override suspend fun write(
            handle: String,
            data: ByteArray,
            mode: NappletFsWriteMode,
        ): Long? {
            val next = if (mode == NappletFsWriteMode.Append) (saved[handle] ?: ByteArray(0)) + data else data
            saved[handle] = next
            return next.size.toLong()
        }
    }

    /**
     * The published PhotoHop ("Fotohop", which requires NAP-FS) opens a PNG and exports one through
     * its own Open and Export PNG buttons, in Chromium, over the shell, shim, router, broker and
     * NAP-FS session. Only the system dialog is scripted ([ScriptedFiles]).
     */
    @Test
    fun photoHopOpensAndExportsThroughNapFs() =
        runBlocking<Unit> {
            val runtime = cefRuntime()
            val photoHop = System.getenv("AMETHYST_PHOTOHOP_HTML")?.let(::File)
            assumeTrue("Set AMETHYST_PHOTOHOP_HTML to PhotoHop's index.html (blob 16511c25…)", photoHop?.isFile == true)
            // Clicks PhotoHop's own buttons once it is up, and reports what it shows.
            val probe =
                """
                <script>
                (function () {
                  function report(k, v) { window.napplet.storage.setItem('probe.' + k, String(v)); }
                  function text(id) { var e = document.getElementById(id); return e ? e.textContent : ''; }
                  async function until(test, ms) { var end = Date.now() + ms; while (Date.now() < end) { if (test()) return true; await new Promise(function (r) { setTimeout(r, 200); }); } return false; }
                  window.addEventListener('load', async function () {
                    await until(function () { var b = document.getElementById('open-btn'); return b && !b.disabled; }, 30000);
                    report('fs', !!window.napplet.fs);
                    document.getElementById('open-btn').click();
                    var opened = await until(function () { return text('document-tabs').indexOf('gradient') >= 0; }, 30000);
                    report('opened', opened + '|' + text('status'));
                    document.getElementById('save-btn').click();
                    await until(function () { return /saved|exported|cancel|fail|error/i.test(text('status')); }, 30000);
                    report('saved', text('status'));
                  });
                })();
                </script>
                """.trimIndent()
            val html = photoHop!!.readBytes() + probe.encodeToByteArray()
            val hash = sha256(html).toHexKey()
            blobs[hash] = html
            // The file the scripted Open dialog hands PhotoHop: a 64x48 gradient.
            val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until 48) for (x in 0 until 64) image.setRGB(x, y, (x * 4 shl 16) or (y * 5 shl 8) or 200)
            val gradient = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
            val files = ScriptedFiles(gradient)
            val storage = RecordingStorage()
            val author = KeyPair()
            val broker =
                NappletBroker(
                    signer = NostrSignerInternal(author),
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()) { "tester" },
                    consentPrompt = NappletConsentPrompt { _, _, _ -> GrantState.ALLOW_ONCE },
                    storage = storage,
                )
            val declared = setOf(NappletCapability.FS, NappletCapability.STORAGE)
            val launch =
                NappletLaunch(
                    identity = NappletIdentity(author.pubKey.toHexKey(), "", hash),
                    profile = HostProfile.NAPPLET,
                    declared = declared,
                    requires = listOf("fs", "storage"),
                    title = "Fotohop",
                    paths = listOf(PathTag("/index.html", hash)),
                    servers = listOf(serverUrl),
                )
            show(CefNappletSession(launch, "tester", useTor = false, account = null, broker = broker, runtime = runtime, filePort = files).also { sessions += it })

            withTimeout(120_000) { while (storage.values["probe.saved"] == null) delay(200) }
            assertEquals("true", storage.values["probe.fs"], "window.napplet.fs is there")
            assertTrue(storage.values["probe.opened"]!!.startsWith("true"), "the picked PNG opened: ${storage.values["probe.opened"]}")
            assertTrue(files.reads.get() > 0, "PhotoHop read the file through fs.read")
            val exported = files.saved.values.singleOrNull()
            assertTrue(exported != null && exported.size > 8, "PhotoHop wrote an export: ${storage.values["probe.saved"]}")
            assertTrue(exported.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)), "the export is a PNG")
            assertEquals(2, files.picks.get(), "one open pick and one save pick")
            System.getenv("AMETHYST_PHOTOHOP_EXPORT")?.let { File(it).writeBytes(exported) }
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
    fun aClosedWindowStopsItsPage() =
        runBlocking {
            val runtime = cefRuntime()
            // Seen live: a closed game window went on running (and playing) unseen, since Chromium
            // refuses to close a windowed browser it wasn't told it may close.
            val ticks = AtomicInteger()
            server.createContext("/tick") { exchange ->
                ticks.incrementAndGet()
                exchange.sendResponseHeaders(204, -1)
                exchange.close()
            }
            val index = "<!doctype html><html><body><script>setInterval(function () { fetch('$serverUrl/tick', { mode: 'no-cors' }).catch(function () {}); }, 100);</script></body></html>"
            val session = site(mapOf("/index.html" to index.encodeToByteArray()), runtime, NostrSignerInternal(KeyPair()), NostrSignerInternal(KeyPair()))
            show(session)
            withTimeout(60_000) { while (ticks.get() < 5) delay(100) }

            // As the app does it: Close closes the session on the AWT thread, and the window that held
            // the browser goes right after, while Chromium has the close to do.
            SwingUtilities.invokeAndWait {
                session.close()
                frames.removeLast().dispose()
            }
            delay(1_500)
            val afterClose = ticks.get()
            delay(1_500)
            assertEquals(afterClose, ticks.get(), "the page kept running after its window closed")
        }

    @Test
    fun openingAndClosingNappletsOverAndOverKeepsChromiumAlive() =
        runBlocking {
            val runtime = cefRuntime()

            // Busy pages, like the games people open and close: they paint and write all the time.
            fun probe(name: String) =
                """
                <!doctype html><html><body style="margin:0;height:100vh;background:rgb(255,0,0)"><script>
                window.napplet.storage.setItem('early', '$name');
                setInterval(function () { document.body.textContent = String(Date.now()); }, 16);
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            val cycles = System.getenv("AMETHYST_CEF_CYCLES")?.toIntOrNull() ?: 15
            cycleNapplets(cycles, runtime, ::probe)

            // Chromium is still there and still serves a new window.
            val lastStorage = RecordingStorage()
            show(session(probe("last"), lastStorage, runtime))
            withTimeout(60_000) { while (lastStorage.values["early"] == null) delay(50) }
            assertEquals("last", lastStorage.values["early"])
        }

    /**
     * Closes a window's session as the app does: the session on the AWT thread, and the window that
     * held the browser right after.
     */
    private fun closeWindow(session: CefNappletSession) {
        SwingUtilities.invokeAndWait {
            session.close()
            frames.removeLast().dispose()
        }
    }

    private suspend fun cycleNapplets(
        cycles: Int,
        runtime: DesktopNappletRuntime,
        probe: (String) -> ByteArray,
    ) {
        repeat(cycles) { i ->
            // A window, closed the way its Close button does.
            val windowStorage = RecordingStorage()
            val window = session(probe("window$i"), windowStorage, runtime)
            show(window)
            withTimeout(60_000) { while (windowStorage.values["early"] == null) delay(50) }
            closeWindow(window)

            // An nSite's window, at its own origin and with a network route.
            val siteIndex = "<!doctype html><html><body><p>site $i</p><script>setInterval(function () { document.title = String(Date.now()); }, 16);</script></body></html>"
            val site = site(mapOf("/index.html" to siteIndex.encodeToByteArray()), runtime, NostrSignerInternal(KeyPair()), NostrSignerInternal(KeyPair()))
            show(site)
            delay(300)
            closeWindow(site)

            // A run inside a note, closed as the note scrolls away.
            val noteStorage = RecordingStorage()
            val view = OffscreenNappletView().apply { resize(320, 240, 1.0) }
            val note = session(probe("note$i"), noteStorage, runtime, offscreen = view)
            note.start()
            withTimeout(60_000) { while (noteStorage.values["early"] == null) delay(50) }
            note.close()

            // A window closed in the same breath it was shown, before Chromium made its browser.
            val hasty = session(probe("hasty$i"), RecordingStorage(), runtime)
            show(hasty)
            closeWindow(hasty)

            // A note's run stopped before its first frame.
            val quick = session(probe("quick$i"), RecordingStorage(), runtime, offscreen = OffscreenNappletView().apply { resize(320, 240, 1.0) })
            quick.start()
            withTimeout(60_000) { quick.state.first { it is CefNappletSession.State.Showing } }
            quick.close()
        }
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
    fun focusingAnOffscreenBrowserDoesNotLoopWithChromium() =
        runBlocking {
            cefRuntime()
            val app = sharedEngine.app()
            val gotFocus = AtomicInteger()
            val client = app.createClient()
            client.addFocusHandler(
                object : CefFocusHandlerAdapter() {
                    override fun onGotFocus(browser: CefBrowser) {
                        gotFocus.incrementAndGet()
                    }
                },
            )
            val created = CompletableDeferred<Unit>()
            client.addLifeSpanHandler(
                object : CefLifeSpanHandlerAdapter() {
                    override fun onAfterCreated(browser: CefBrowser) {
                        created.complete(Unit)
                    }
                },
            )
            val view = OffscreenNappletView()
            val browser = OffscreenCefBrowser(client, "about:blank", null, 30, view)
            view.browser = browser
            browser.createImmediately()
            withTimeout(60_000) { created.await() }
            try {
                // Chromium answers a windowless browser's SetFocus(true) with OnGotFocus, and JCEF's
                // client answers that with setFocus(true): passed on every time, the two called each
                // other until the thread ran out of stack (a StackOverflowError swallowed by JNI).
                SwingUtilities.invokeAndWait { view.focus(true) }
                delay(1_000)
                assertTrue(gotFocus.get() in 1..2, "Chromium reported focus ${gotFocus.get()} times for one focus")
                SwingUtilities.invokeAndWait { view.focus(false) }
                SwingUtilities.invokeAndWait { view.focus(true) }
                delay(1_000)
                assertTrue(gotFocus.get() in 2..4, "Chromium reported focus ${gotFocus.get()} times for two focuses")
            } finally {
                SwingUtilities.invokeAndWait {
                    browser.setCloseAllowed()
                    browser.close(true)
                    client.dispose()
                }
            }
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

    /**
     * NIP-5D binds a napplet's identity to the document the shell verified, not to its frame: a
     * frame keeps its `Window` when it navigates itself, so a document loaded later in the same
     * frame must reach nothing. Each route replaces the verified document with bytes nobody
     * signed; those bytes then try the old window-level channel and a hello of their own.
     */
    @Test
    fun aDocumentThatReplacesTheVerifiedNappletReachesNothing() =
        runBlocking {
            val runtime = cefRuntime()

            fun stage2(route: String) =
                """
                <!doctype html><html><body><script>
                new Image().src = '$serverUrl/landed?landed_$route=1';
                for (var i = 0; i < 5; i++) parent.postMessage({ type: 'storage.set', id: 'w' + i, key: 'window_$route', value: '1' }, '*');
                var c = new MessageChannel();
                parent.postMessage({ type: 'napplet.hello' }, '*', [c.port2]);
                c.port1.postMessage({ type: 'storage.set', id: 'p1', key: 'port_$route', value: '1' });
                </script></body></html>
                """.trimIndent()

            blobs["stage2-network"] = stage2("network").encodeToByteArray()

            // A JS string literal of [text], safe inside an inline script (no `</script>` in it).
            fun literal(text: String) = Json.encodeToString(String.serializer(), text).replace("</", "<\\/")
            val targets =
                mapOf(
                    "network" to "'$serverUrl/stage2-network'",
                    "data" to "'data:text/html;base64,' + btoa(${literal(stage2("data"))})",
                    "blob" to "URL.createObjectURL(new Blob([${literal(stage2("blob"))}], { type: 'text/html' }))",
                )
            // How the replacement takes the frame. The napplet/evil fixture (rung 9) uses location.replace
            // and, failing that, document.open.
            val swaps =
                mapOf(
                    "network" to "location.href = %s;",
                    "data" to "location.replace(%s);",
                    "blob" to "location.href = %s;",
                )
            val routes =
                targets.mapValues { (route, target) -> swaps.getValue(route).replace("%s", target) } +
                    ("open" to "document.open(); document.write(${literal(stage2("open"))}); document.close();")
            for ((route, swap) in routes) {
                val storage = RecordingStorage()
                val html =
                    """
                    <!doctype html><html><body><script>
                    (async function () {
                      await window.napplet.storage.setItem('verified', '1');
                      // Written only if the navigation was refused and this document is still here.
                      setTimeout(function () { window.napplet.storage.setItem('stayed', '1'); }, 3000);
                      $swap
                    })();
                    </script></body></html>
                    """.trimIndent().encodeToByteArray()
                val session = session(html, storage, runtime, offscreen = OffscreenNappletView().apply { resize(320, 240, 1.0) })
                session.start()
                withTimeout(600_000) { session.state.first { it is CefNappletSession.State.Showing } }
                withTimeout(60_000) { while (storage.values["verified"] == null) delay(100) }
                // Long enough for the navigation, the replacement's messages and the broker's replies.
                delay(8_000)
                println("route=$route stored=${storage.values.keys}")
                assertFalse(storage.values.containsKey("window_$route"), "$route: a replaced document reached the broker over the window")
                assertFalse(storage.values.containsKey("port_$route"), "$route: a replaced document bound a port of its own")
                session.close()
            }
        }

    /**
     * A reload is a new document in the same frame. The shell binds identity to the document it
     * verified, so the second one gets no channel: it never reads or writes the napplet's storage,
     * and the frame is torn down instead.
     */
    @Test
    fun aSecondDocumentInTheSameFrameGetsNoChannel() =
        runBlocking {
            val runtime = cefRuntime()
            val storage = RecordingStorage()
            val html =
                """
                <!doctype html><html><body><script>
                (async function () {
                  var loads = Number(await window.napplet.storage.getItem('loads') || '0') + 1;
                  await window.napplet.storage.setItem('loads', String(loads));
                  if (loads === 1) location.reload();
                })();
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            val session = session(html, storage, runtime, offscreen = OffscreenNappletView().apply { resize(320, 240, 1.0) })
            session.start()
            withTimeout(600_000) { session.state.first { it is CefNappletSession.State.Showing } }
            withTimeout(60_000) { while (storage.values["loads"] == null) delay(100) }
            delay(6_000)
            assertEquals("1", storage.values["loads"], "the reloaded document reached the broker as the napplet")
        }

    /**
     * A napplet gets one hello. A second one, or a NAP message sent at the window instead of the
     * port, ends the binding: nothing it sends after that reaches the broker.
     */
    @Test
    fun aSecondHelloEndsTheNappletsChannel() =
        runBlocking {
            val runtime = cefRuntime()
            val storage = RecordingStorage()
            val html =
                """
                <!doctype html><html><body><script>
                (async function () {
                  await window.napplet.storage.setItem('first', '1');
                  var c = new MessageChannel();
                  parent.postMessage({ type: 'napplet.hello' }, '*', [c.port2]);
                  c.port1.postMessage({ type: 'storage.set', id: 'f1', key: 'forged', value: '1' });
                  // Later than the hello: a port message and a window message travel on separate
                  // queues, so one sent alongside it may still land first.
                  setTimeout(function () { window.napplet.storage.setItem('after', '1'); }, 1000);
                })();
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            val session = session(html, storage, runtime, offscreen = OffscreenNappletView().apply { resize(320, 240, 1.0) })
            session.start()
            withTimeout(600_000) { session.state.first { it is CefNappletSession.State.Showing } }
            withTimeout(60_000) { while (storage.values["first"] == null) delay(100) }
            delay(5_000)
            assertFalse(storage.values.containsKey("forged"), "the second hello's port reached the broker")
            assertFalse(storage.values.containsKey("after"), "the first port still worked once the second hello arrived")
        }

    /** A site moves between its own pages; each page says hello and keeps the channel. */
    @Test
    fun aSiteKeepsItsChannelAcrossItsOwnPages() =
        runBlocking {
            val runtime = cefRuntime()
            val user = NostrSignerInternal(KeyPair())
            val author = NostrSignerInternal(KeyPair())
            val prompts = SitePrompts()

            fun page(
                name: String,
                next: String?,
            ) = """
                <!doctype html><html><body><script>
                (async function () {
                  var pubkey = await window.nostr.getPublicKey();
                  await fetch('$serverUrl/report?$name=' + pubkey, { mode: 'no-cors' }).catch(function () {});
                  ${if (next != null) "location.href = '$next';" else ""}
                })();
                </script></body></html>
                """.trimIndent().encodeToByteArray()
            show(site(mapOf("/index.html" to page("first", "/second.html"), "/second.html" to page("second", null)), runtime, user, author, prompts))

            withTimeout(60_000) { while (reports["second"] == null) delay(100) }
            assertEquals(user.pubKey, reports["first"])
            assertEquals(user.pubKey, reports["second"], "the second page reached the signer too")
        }

    private fun ImageBitmap.centerPixel(): Color = toPixelMap()[width / 2, height / 2]
}
