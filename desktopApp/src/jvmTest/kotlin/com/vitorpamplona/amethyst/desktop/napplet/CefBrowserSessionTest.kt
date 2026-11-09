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

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.desktop.napplet.CefBrowserSession.Navigation
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The in-app browser window. The tests that run a page need Chromium: run them with
 * AMETHYST_CEF_TEST=1 under a display, for example:
 *
 *   AMETHYST_CEF_TEST=1 xvfb-run -a ./gradlew :desktopApp:test --tests '*CefBrowserSessionTest*'
 */
class CefBrowserSessionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tmp = createTempDirectory("browser-session-test").toFile()
    private val pages = ConcurrentHashMap<String, String>()
    private val requested = ConcurrentHashMap.newKeySet<String>()
    private val reports = ConcurrentHashMap<String, String>()
    private val serverThreads = Executors.newCachedThreadPool()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            // Its own threads: a slow download must not hold up the page's other requests.
            executor = serverThreads
            createContext("/") { exchange ->
                requested += exchange.requestURI.path
                when (exchange.requestURI.path) {
                    "/big.bin" -> return@createContext streamBig(exchange)
                    "/drop.bin" -> return@createContext dropHalfway(exchange)
                }
                exchange.requestURI.rawQuery?.let { query ->
                    query.split('&').forEach { pair ->
                        reports[pair.substringBefore('=')] = URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
                    }
                }
                val body = pages[exchange.requestURI.path]?.encodeToByteArray()
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                if (body != null && exchange.requestURI.path.endsWith(".bin")) {
                    // A file to save, not to show.
                    exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                    exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"report.bin\"")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                } else if (body == null) {
                    exchange.sendResponseHeaders(404, -1)
                } else {
                    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            start()
        }
    private val port = server.address.port

    // A large file sent slowly, counting what left, and whether the browser hung up on it.
    private val bigSent = AtomicLong()

    @Volatile
    private var bigAborted = false

    private fun streamBig(exchange: HttpExchange) {
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"big.bin\"")
        try {
            exchange.sendResponseHeaders(200, BIG_SIZE)
            val chunk = ByteArray(BIG_CHUNK)
            exchange.responseBody.use { out ->
                while (bigSent.get() < BIG_SIZE) {
                    out.write(chunk)
                    bigSent.addAndGet(chunk.size.toLong())
                    Thread.sleep(10)
                }
            }
        } catch (e: IOException) {
            bigAborted = true
        } finally {
            exchange.close()
        }
    }

    // Promises a file and hangs up a little way in.
    private fun dropHalfway(exchange: HttpExchange) {
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"drop.bin\"")
        runCatching {
            exchange.sendResponseHeaders(200, BIG_SIZE)
            exchange.responseBody.write(ByteArray(BIG_CHUNK))
            exchange.responseBody.flush()
        }
        exchange.close()
    }

    private val sessions = mutableListOf<CefBrowserSession>()

    @After
    fun tearDown() {
        sessions.forEach { it.close() }
        server.stop(0)
        serverThreads.shutdownNow()
        scope.cancel()
        tmp.deleteRecursively()
    }

    private fun runtime(
        engine: DesktopCefEngine = DesktopCefEngine(File(tmp, "jcef"), File(tmp, "cache")),
        torPort: MutableStateFlow<Int?> = MutableStateFlow(null),
    ) = DesktopNappletRuntime(engine, NappletBlobCache(File(tmp, "blobs")), { OkHttpClient() }, scope, torPort, torWaitMs = 500)

    @Test
    fun webPagesLoadAndOtherAppsLinksGoToTheSystem() {
        assertEquals(Navigation.LOAD, CefBrowserSession.navigationOf("https://example.com/a?b#c", isMainFrame = true))
        assertEquals(Navigation.LOAD, CefBrowserSession.navigationOf("http://example.com/", isMainFrame = true))
        assertEquals(Navigation.LOAD, CefBrowserSession.navigationOf("about:blank", isMainFrame = true))
        assertEquals(Navigation.SYSTEM, CefBrowserSession.navigationOf("nostr:npub1abc", isMainFrame = true))
        assertEquals(Navigation.SYSTEM, CefBrowserSession.navigationOf("lightning:lnbc1", isMainFrame = true))
        assertEquals(Navigation.SYSTEM, CefBrowserSession.navigationOf("mailto:a@b.c", isMainFrame = true))
    }

    @Test
    fun aPageCannotReachTheNappletHostsOrTheMachinesFiles() {
        listOf(
            "https://napplet.local/__shell__",
            "https://abc.napplet.local/index.html",
            "http://napplet.local/__shell__",
            "https://user@napplet.local/__shell__",
            "https://NAPPLET.LOCAL./x",
            "https://x.xdc.napplet.local:443/",
            "file:///etc/passwd",
            "chrome://settings",
            "view-source:https://example.com",
            "javascript:alert(1)",
        ).forEach { url ->
            assertEquals(Navigation.REFUSE, CefBrowserSession.navigationOf(url, isMainFrame = true), url)
            assertFalse(CefBrowserSession.mayFetch(url), url)
        }
        // A top-level data: page is a phishing tool; inside a page it is the page's own.
        assertEquals(Navigation.REFUSE, CefBrowserSession.navigationOf("data:text/html,<p>hi", isMainFrame = true))
        assertEquals(Navigation.LOAD, CefBrowserSession.navigationOf("data:text/html,<p>hi", isMainFrame = false))
        assertTrue(CefBrowserSession.mayFetch("https://example.com/app.js"))
        assertTrue(CefBrowserSession.mayFetch("data:image/png;base64,AAAA"))
    }

    @Test
    fun eachOriginIsItsOwnAppToTheBroker() {
        assertEquals("browser:https://example.com", CefBrowserSession.identityOf("https://example.com").coordinate)
        assertTrue(CefBrowserSession.identityOf("https://a.example.com").coordinate != CefBrowserSession.identityOf("https://b.example.com").coordinate)
    }

    @Test
    fun theContentScriptRunsTheShimOnlyInATopFrameWithTheBridge() {
        val script = DesktopBrowserBridge.contentScript("/*SHIM*/")
        val gate = script.indexOf("window.top !== window")
        val bridge = script.indexOf("if (!found.length) return;")
        val shim = script.indexOf("/*SHIM*/")
        assertTrue(gate in 0 until bridge && bridge < shim, "the top-frame and bridge checks come before the shim")
        assertTrue("window.__nappletNip07 = true" in script)
        assertTrue("token + '\\n' + s" in script, "every request carries the document's token")
    }

    @Test
    fun pagesShareOnePopupBudget() {
        var now = 0L
        val gate = BrowserPopupGate(maxOpen = 2, minIntervalMs = 1_000, clock = { now })
        val first = assertNotNull(gate.tryOpen { "a" })
        assertNull(gate.tryOpen { "b" }, "a second popup within the interval, from any window, waits")
        now += 1_000
        assertNotNull(gate.tryOpen { "b" })
        now += 1_000
        assertNull(gate.tryOpen { "c" }, "no more than two page-opened windows at once")
        gate.closed(first)
        assertNotNull(gate.tryOpen { "c" }, "closing one frees its place")
        now += 1_000
        gate.closed("b")
        assertNull(gate.tryOpen<String> { null }, "a window that did not open")
        assertNotNull(gate.tryOpen { "d" }, "and used none of the budget")
    }

    @Test
    fun aTorWindowDoesNotOpenWhileTorIsDown() =
        runBlocking {
            val runtime = runtime()
            val session = CefBrowserSession("http://127.0.0.1:$port/", "page", "tester", useTor = true, account = null, broker = broker(NostrSignerInternal(KeyPair())), runtime = runtime)
            sessions += session
            session.start()
            val state = withTimeout(30_000) { session.state.first { it is CefBrowserSession.State.Showing || it is CefBrowserSession.State.Failed } }
            assertIs<CefBrowserSession.State.Failed>(state)
            assertEquals(CefBrowserSession.Failure.ROUTE_BLOCKED, state.reason)
            assertTrue(requested.isEmpty(), "nothing was fetched: $requested")
            // The failed window keeps no claim.
            assertTrue(runtime.routes.claim(Any(), useTor = false, timeoutMs = 0))
            assertEquals(DesktopSocksRouter.Route.Direct, runtime.engine.router.route)
        }

    private class Prompts {
        val connects = AtomicInteger()
        val signatures = AtomicInteger()
        val apps = ConcurrentHashMap.newKeySet<String>()
    }

    private fun broker(
        user: NostrSignerInternal,
        prompts: Prompts = Prompts(),
    ) = NappletBroker(
        signer = user,
        ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()) { user.pubKey },
        consentPrompt = NappletConsentPrompt { _, _, _ -> GrantState.ALLOW_ONCE },
        signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore()),
        nostrConnectPrompt =
            NostrConnectPrompt { identity, _ ->
                prompts.connects.incrementAndGet()
                prompts.apps += identity.coordinate
                AppConnectResult.Connected(AppSignerPolicy.PARANOID)
            },
        signerConsentPrompt =
            NostrSignerConsentPrompt { _, _, _ ->
                prompts.signatures.incrementAndGet()
                SignerOpGrant.AllowOnce
            },
    )

    @Test
    fun aWebPageSignsThroughNip07AndItsFramesCannot() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            val runtime = runtime(SharedCefTestEngine.engine)
            val user = NostrSignerInternal(KeyPair())
            val prompts = Prompts()
            // The frame is on another origin (localhost, not 127.0.0.1), as an ad or embed would be.
            val report = "function report(k, v) { return fetch('http://127.0.0.1:$port/report?' + k + '=' + encodeURIComponent(String(v))).catch(function () {}); }"
            pages["/frame.html"] =
                """
                <!doctype html><script>$report
                (async function () {
                  await report('frameNostr', typeof window.nostr);
                  var q = Object.getOwnPropertyNames(window).filter(function (n) { return n.indexOf('${DesktopBrowserBridge.QUERY_FUNCTION_PREFIX}') === 0 && n.slice(-6) !== 'Cancel'; }).map(function (n) { return window[n]; })[0];
                  if (typeof q !== 'function') { await report('frameCall', 'absent'); return; }
                  // A frame that calls the router itself is refused: only the page may ask.
                  q({ request: JSON.stringify({ type: 'identity.getPublicKey', id: 'f1' }), persistent: false,
                      onSuccess: function (r) { report('frameCall', 'answered:' + r); },
                      onFailure: function (code) { report('frameCall', 'refused'); } });
                })();
                </script>
                """.trimIndent()
            pages["/"] =
                """
                <!doctype html><html><head><title>NIP-07 page</title></head><body><p>PAGE</p>
                <iframe src="http://localhost:$port/frame.html"></iframe>
                <script>$report
                (async function () {
                  try {
                    // The page's own script calling the router without the document's token gets nothing.
                    var direct = Object.getOwnPropertyNames(window).filter(function (n) { return n.indexOf('${DesktopBrowserBridge.QUERY_FUNCTION_PREFIX}') === 0 && n.slice(-6) !== 'Cancel'; }).map(function (n) { return window[n]; })[0];
                    await new Promise(function (resolve) {
                      direct({ request: JSON.stringify({ type: 'identity.getPublicKey', id: 'd1' }), persistent: false,
                        onSuccess: function (r) { report('directCall', 'answered:' + r).then(resolve); },
                        onFailure: function () { report('directCall', 'refused').then(resolve); } });
                    });
                    await report('pubkey', await window.nostr.getPublicKey());
                    var signed = await window.nostr.signEvent({ kind: 1, created_at: 1700000000, tags: [], content: 'hello from the web' });
                    await report('signed', signed.pubkey + ':' + (signed.sig ? signed.sig.length : 0));
                  } catch (e) {
                    await report('error', (e && e.message) || e);
                  }
                  await new Promise(function (resolve) {
                    var ws = new WebSocket('ws://127.0.0.1:$port/ws-probe');
                    ws.onopen = ws.onerror = ws.onclose = function () { resolve(); };
                    setTimeout(resolve, 3000);
                  });
                  await report('done', 1);
                  // The napplet hosts' origin is not the web's to load.
                  try { await fetch('https://napplet.local/__shell__'); await report('shell', 'loaded'); } catch (e) { await report('shell', 'refused'); }
                })();
                </script></body></html>
                """.trimIndent()

            val visits = ConcurrentHashMap.newKeySet<String>()
            val session = show(runtime, user, prompts, "http://127.0.0.1:$port/", onVisit = { url -> visits += url })

            withTimeout(60_000) { while (reports["done"] == null || reports["shell"] == null || reports["frameCall"] == null) delay(100) }
            assertEquals(null, reports["error"], "the page ran without an error")
            assertEquals("refused", reports["directCall"], "a request without the document's token is refused")
            assertEquals(user.pubKey, reports["pubkey"], "window.nostr reached the signer")
            assertEquals("${user.pubKey}:128", reports["signed"], "and signed as the user")
            assertEquals(1, prompts.connects.get(), "the first NIP-07 call asked to connect")
            assertTrue(prompts.signatures.get() >= 1, "the signature asked first")
            assertEquals("undefined", reports["frameNostr"], "a frame inside the page gets no window.nostr")
            assertTrue(reports["frameCall"] in setOf("absent", "refused"), "and can't ask the broker itself: ${reports["frameCall"]}")
            assertEquals(setOf("browser:http://127.0.0.1:$port"), prompts.apps, "the connect prompt named the page's origin")
            assertEquals("refused", reports["shell"], "napplet.local is never served to a web page")
            assertTrue("/ws-probe" in requested, "a page's WebSocket reached the server: $requested")
            assertEquals(setOf("browser:http://127.0.0.1:$port"), session.apps.value, "consent is keyed on the page's origin")
            assertEquals("NIP-07 page", session.page.value.title)
            assertTrue(visits.any { it.startsWith("http://127.0.0.1:$port/") }, "the visit was recorded: $visits")
        }

    private suspend fun show(
        runtime: DesktopNappletRuntime,
        user: NostrSignerInternal,
        prompts: Prompts,
        url: String,
        onVisit: (String) -> Unit = {},
        onPopup: (CefBrowserSession, String) -> Unit = { _, _ -> },
    ): CefBrowserSession {
        val session =
            CefBrowserSession(
                url,
                "page-${sessions.size}",
                user.pubKey,
                useTor = false,
                account = null,
                broker = broker(user, prompts),
                runtime = runtime,
                onPopup = onPopup,
                onVisit = { visited, _ -> onVisit(visited) },
            )
        sessions += session
        // The screen gives the page its size; nothing else is needed for Chromium to paint off-screen.
        session.view.resize(960, 640, 1.0)
        session.start()
        val state = withTimeout(600_000) { session.state.first { it is CefBrowserSession.State.Showing || it is CefBrowserSession.State.Failed } }
        assertIs<CefBrowserSession.State.Showing>(state, "the page did not open: $state")
        return session
    }

    @Test
    fun aPagesNewWindowsBecomeScreenRequestsAndABlankOneDoesNot() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            val runtime = runtime(SharedCefTestEngine.engine)
            // A target=_blank link to a blank page, one to a web page, and window.open of a web page.
            pages["/opener.html"] =
                """
                <!doctype html><body style="margin:0">
                <a id="blank" href="about:blank" target="_blank" style="display:block;height:300px">blank</a>
                <a id="page" href="http://127.0.0.1:$port/popped.html" target="_blank" style="display:block;height:300px">page</a>
                <script>window.clicks = 0; document.addEventListener('click', function () { fetch('http://127.0.0.1:$port/report?clicks=' + (++window.clicks)); }, true);
                window.open('');
                fetch('http://127.0.0.1:$port/report?handle=' + (window.open('/scripted.html') === null ? 'none' : 'window'));</script>
                </body>
                """.trimIndent()
            val asked = ConcurrentHashMap.newKeySet<String>()
            // The host's budget is left out: every request is let through and recorded.
            val opener =
                show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/opener.html", onPopup = { page, url ->
                    asked += url
                    page.requestPopup(Route.WebPage(url, id = "popup"))
                })
            // Painted: the page is there to click.
            withTimeout(60_000) { opener.view.frame.first { it != null } }
            withTimeout(30_000) { while (opener.page.value.isLoading) delay(100) }

            // A click right after the first paint can come before the page takes input: each one is
            // sent again until the page counts it.
            clickUntil(opener, 100, 100) { reports["clicks"] == "1" }
            clickUntil(opener, 100, 450) { reports["clicks"] == "2" }
            val routes = withTimeout(30_000) { opener.popups.take(2).toList() }
            assertEquals(
                setOf("http://127.0.0.1:$port/scripted.html", "http://127.0.0.1:$port/popped.html"),
                routes.mapTo(HashSet()) { it.url },
                "window.open (resolved against the page) and the clicked link each ask for a screen",
            )
            assertEquals<Set<String>>(routes.mapTo(HashSet()) { it.url }, asked, "a blank one never does")
            assertEquals("none", reports["handle"], "window.open hands the page no window back")
        }

    @Test
    fun theWheelScrollsAPage() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            val runtime = runtime(SharedCefTestEngine.engine)
            pages["/long.html"] =
                """
                <!doctype html><body style="margin:0"><div style="height:6000px;background:linear-gradient(#fff,#000)"></div>
                <script>window.addEventListener('scroll', function () { fetch('http://127.0.0.1:$port/report?y=' + Math.round(window.scrollY)); });</script></body>
                """.trimIndent()
            val page = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/long.html")
            withTimeout(60_000) { page.view.frame.first { it != null } }
            withTimeout(30_000) { while (page.page.value.isLoading) delay(100) }
            // As the screen sends it: a wheel turn where the pointer is, repeated until the page moves.
            withTimeout(30_000) {
                while ((reports["y"]?.toIntOrNull() ?: 0) <= 0) {
                    SwingUtilities.invokeAndWait {
                        val source = Canvas()
                        val now = System.currentTimeMillis()
                        page.view.mouse(MouseEvent(source, MouseEvent.MOUSE_MOVED, now, 0, 200, 200, 0, false, MouseEvent.NOBUTTON), MouseEvent.MOUSE_MOVED, 200, 200)
                        page.view.wheel(MouseWheelEvent(source, MouseEvent.MOUSE_WHEEL, now, 0, 200, 200, 200, 200, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 1.0), 200, 200)
                    }
                    delay(500)
                }
            }
            assertTrue((reports["y"]?.toIntOrNull() ?: 0) >= 40, "a notch down scrolled the page down a line or more: ${reports["y"]}")

            // And a notch up brings it back up.
            val down = reports["y"]!!.toInt()
            withTimeout(30_000) {
                while ((reports["y"]?.toIntOrNull() ?: down) >= down) {
                    SwingUtilities.invokeAndWait {
                        val now = System.currentTimeMillis()
                        page.view.wheel(MouseWheelEvent(Canvas(), MouseEvent.MOUSE_WHEEL, now, 0, 200, 200, 200, 200, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1, -1.0), 200, 200)
                    }
                    delay(500)
                }
            }
        }

    @Test
    fun scrollbarsTakeTheAppsColorsInEveryFrameAndFollowItsTheme() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            val runtime = runtime(SharedCefTestEngine.engine)
            // Each document reports the track color it sees whenever it changes.
            val reporter = { name: String ->
                "<script>var last = ''; setInterval(function () { var c = getComputedStyle(document.documentElement).getPropertyValue('--amethyst-scrollbar-track').trim();" +
                    " if (c !== last) { last = c; fetch('http://127.0.0.1:$port/report?$name=' + encodeURIComponent(c)); } }, 100);</script>"
            }
            pages["/inner.html"] = "<!doctype html><body style=\"margin:0\"><div style=\"height:3000px\"></div>${reporter("frame")}</body>"
            pages["/themed.html"] =
                """
                <!doctype html><body style="margin:0;background:#fff">
                <iframe src="http://localhost:$port/inner.html" style="width:300px;height:200px;border:0;display:block"></iframe>
                <div style="height:6000px"></div>${reporter("top")}</body>
                """.trimIndent()
            PageScrollbars.update(NappletThemeColors("#102030", "#F0E0D0", "#000000"))
            val page = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/themed.html")
            withTimeout(30_000) { while (reports["top"] != "#102030" || reports["frame"] != "#102030") delay(100) }

            // The page's own scrollbar, at the right edge of what Chromium paints: the track below the
            // thumb in the app's background, the thumb in its text color, faded.
            suspend fun trackPixel(expected: Int): Int {
                var argb = 0
                withTimeout(30_000) {
                    while (true) {
                        val frame = page.view.frame.value
                        if (frame != null) {
                            argb = frame.toPixelMap()[frame.width - 4, frame.height - 20].toArgb()
                            if (argb == expected) break
                        }
                        delay(100)
                    }
                }
                return argb
            }
            assertEquals(0xFF102030.toInt(), trackPixel(0xFF102030.toInt()), "the track is the app's background")
            val pixels =
                page.view.frame.value!!
                    .toPixelMap()
            val thumb = pixels[pixels.width - 4, 20].toArgb()
            assertTrue(thumb != 0xFF102030.toInt() && thumb != 0xFFFFFFFF.toInt(), "the thumb is drawn over it: ${Integer.toHexString(thumb)}")
            // The embedded frame is another site (localhost, not 127.0.0.1), in a process of its own,
            // which the document-start script doesn't reach: its scrollbar is themed all the same.
            withTimeout(30_000) {
                while (page.view.frame.value!!
                        .toPixelMap()[296, 190]
                        .toArgb() != 0xFF102030.toInt()
                ) {
                    delay(100)
                }
            }

            // The app's theme changes: the open page's frames follow, the top and the embedded one.
            PageScrollbars.update(NappletThemeColors("#E0E8F0", "#203040", "#000000"))
            page.restyleScrollbars()
            withTimeout(30_000) { while (reports["top"] != "#E0E8F0" || reports["frame"] != "#E0E8F0") delay(100) }
            assertEquals(0xFFE0E8F0.toInt(), trackPixel(0xFFE0E8F0.toInt()), "and so does what it paints")
        }

    @Test
    fun aDownloadWaitsForTheUserAndSavesToDownloads() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            val home = File(tmp, "home")
            check(home.isDirectory || home.mkdirs()) { "Cannot create $home" }
            val realHome = System.getProperty("user.home")
            System.setProperty("user.home", home.absolutePath)
            try {
                val runtime = runtime(SharedCefTestEngine.engine)
                pages["/report.bin"] = "the report"
                pages["/get.html"] = "<!doctype html><body><script>location.href = '/report.bin';</script></body>"
                val downloads = File(home, "Downloads")

                suspend fun asked(page: CefBrowserSession): BrowserDownload =
                    withTimeout(30_000) { page.downloads.first { list -> list.any { it.state == BrowserDownload.State.ASKING } } }
                        .first { it.state == BrowserDownload.State.ASKING }

                // Asked, and nothing written while it waits.
                val first = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/get.html")
                val question = asked(first)
                assertEquals("report.bin", question.name)
                assertEquals("127.0.0.1", question.host)
                delay(500)
                assertTrue(downloads.listFiles().isNullOrEmpty(), "nothing is saved before the user says so: ${downloads.listFiles()?.toList()}")

                // Saved.
                first.saveDownload(question.id)
                val done = withTimeout(30_000) { first.downloads.first { list -> list.any { it.id == question.id && it.state == BrowserDownload.State.DONE } } }.first { it.id == question.id }
                assertEquals(File(downloads, "report.bin"), done.file)
                assertEquals("the report", File(downloads, "report.bin").readText())

                // Declined: nothing written, and it leaves the list.
                val second = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/get.html")
                val declined = asked(second)
                second.cancelDownload(declined.id)
                assertTrue(second.downloads.value.none { it.id == declined.id })
                delay(1_000)
                assertEquals(listOf("report.bin"), downloads.list()?.toList(), "a declined download leaves nothing behind")

                // The same name again: a name of its own.
                val third = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/get.html")
                val again = asked(third)
                third.saveDownload(again.id)
                withTimeout(30_000) { third.downloads.first { list -> list.any { it.id == again.id && it.state == BrowserDownload.State.DONE } } }
                assertEquals("the report", File(downloads, "report (1).bin").readText())
            } finally {
                System.setProperty("user.home", realHome)
            }
        }

    // Runs [block] with the user's home (where Downloads is) in a folder of the test's.
    private inline fun <T> withHome(block: (downloads: File) -> T): T {
        val home = File(tmp, "home-" + UUID.randomUUID())
        check(home.isDirectory || home.mkdirs()) { "Cannot create $home" }
        val realHome = System.getProperty("user.home")
        System.setProperty("user.home", home.absolutePath)
        return try {
            block(File(home, "Downloads"))
        } finally {
            System.setProperty("user.home", realHome)
        }
    }

    private suspend fun questionOf(page: CefBrowserSession): BrowserDownload =
        withTimeout(30_000) { page.downloads.first { list -> list.any { it.state == BrowserDownload.State.ASKING } } }
            .first { it.state == BrowserDownload.State.ASKING }

    @Test
    fun aDownloadIsHeldWhileAskedAndStopsWithItsPage() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            withHome { downloads ->
                val runtime = runtime(SharedCefTestEngine.engine)
                pages["/getbig.html"] = "<!doctype html><body><script>location.href = '/big.bin';</script></body>"
                val page = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/getbig.html")
                val question = questionOf(page)
                // Held: what left the server stops growing well short of the file.
                delay(3_000)
                val held = bigSent.get()
                delay(1_500)
                assertEquals(held, bigSent.get(), "nothing more is fetched while the user decides")
                assertTrue(held < BIG_SIZE / 2, "it was held early: $held of $BIG_SIZE bytes sent")

                // Saved: it moves again.
                page.saveDownload(question.id)
                withTimeout(30_000) { while (bigSent.get() <= held) delay(100) }

                // The page closes mid-download: the download stops, and leaves nothing.
                page.close()
                withTimeout(30_000) { while (!bigAborted) delay(100) }
                delay(1_000)
                assertTrue(downloads.listFiles().orEmpty().none { it.name.startsWith("big") }, "nothing of it stays: ${downloads.list()?.toList()}")
            }
        }

    @Test
    fun aDroppedDownloadStopsAndFreesItsName() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            withHome { downloads ->
                val runtime = runtime(SharedCefTestEngine.engine)
                pages["/getdrop.html"] = "<!doctype html><body><script>location.href = '/drop.bin';</script></body>"
                val page = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/getdrop.html")
                val question = questionOf(page)
                page.saveDownload(question.id)
                val ended = withTimeout(30_000) { page.downloads.first { list -> list.any { it.id == question.id && it.state.finished } } }.first { it.id == question.id }
                assertEquals(BrowserDownload.State.STOPPED, ended.state, "a download the server dropped is not done")
                val again = BrowserDownloads.reserve(downloads, "drop.bin")
                assertEquals("drop.bin", again.name, "its name is free again")
                BrowserDownloads.release(again)
            }
        }

    @Test
    fun aPageCannotStackMoreThanThreeQuestions() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            withHome {
                val runtime = runtime(SharedCefTestEngine.engine)
                pages["/report.bin"] = "the report"
                pages["/many.html"] =
                    """
                    <!doctype html><body><script>
                    for (var i = 0; i < 6; i++) { var a = document.createElement('a'); a.href = '/report.bin?i=' + i; a.download = 'r' + i + '.bin'; document.body.appendChild(a); a.click(); }
                    </script></body>
                    """.trimIndent()
                val page = show(runtime, NostrSignerInternal(KeyPair()), Prompts(), "http://127.0.0.1:$port/many.html")
                questionOf(page)
                delay(3_000)
                val asking = page.downloads.value.count { it.state == BrowserDownload.State.ASKING }
                // Chromium's own limit on automatic downloads lets the first through and blocks the
                // rest before they reach the page's handler; PageDownloadsTest covers the handler's cap.
                assertTrue(asking in 1..PageDownloads.MAX_QUESTIONS, "at most three questions wait at once: $asking")
            }
        }

    @Test
    fun eachAccountHasItsOwnCookieJar() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            val runtime = runtime(SharedCefTestEngine.engine)
            // Reports the cookie and localStorage it finds, then sets both.
            pages["/jar.html"] =
                """
                <!doctype html><body><script>
                var name = new URLSearchParams(location.search).get('n');
                var cookie = document.cookie.indexOf('jar=1') >= 0 ? 'cookie' : 'no-cookie';
                var stored = localStorage.getItem('jar') || 'no-storage';
                document.cookie = 'jar=1; max-age=3600'; localStorage.setItem('jar', '1');
                fetch('http://127.0.0.1:$port/report?' + name + '=' + cookie + ',' + stored);
                </script></body>
                """.trimIndent()
            val alice = NostrSignerInternal(KeyPair())
            val bob = NostrSignerInternal(KeyPair())

            suspend fun visit(
                user: NostrSignerInternal,
                name: String,
            ): String {
                show(runtime, user, Prompts(), "http://127.0.0.1:$port/jar.html?n=$name")
                return withTimeout(30_000) {
                    while (reports[name] == null) delay(100)
                    reports[name]!!
                }
            }
            assertEquals("no-cookie,no-storage", visit(alice, "alice1"), "a fresh jar")
            // It starts on a blank page while its scripts go in, which leaves nothing to go back to.
            withTimeout(30_000) {
                while (sessions
                        .last()
                        .page.value.isLoading
                ) {
                    delay(100)
                }
            }
            assertFalse(
                sessions
                    .last()
                    .page.value.canGoBack,
                "a new page has no history behind it",
            )
            assertEquals(
                "http://127.0.0.1:$port/jar.html?n=alice1",
                sessions
                    .last()
                    .page.value.url,
            )
            assertEquals("cookie,1", visit(alice, "alice2"), "the same account's next page shares it")
            assertEquals("no-cookie,no-storage", visit(bob, "bob1"), "another account's page finds nothing of it")
            assertEquals("cookie,1", visit(alice, "alice3"), "and the first account's is still there after")

            val engine = SharedCefTestEngine.engine
            assertTrue(engine.webContext(alice.pubKey, overTor = true) !== engine.webContext(alice.pubKey, overTor = false), "Tor pages keep a jar apart from the open web's")
            // The app switches to another account: the first's pages close, and its jars go with them.
            sessions.filter { it.accountPubKey == alice.pubKey }.forEach { it.close() }
            engine.retainWebContexts(bob.pubKey)
            assertEquals("no-cookie,no-storage", visit(alice, "alice4"), "an account switched away from starts over")
            assertEquals("cookie,1", visit(bob, "bob2"), "and the one switched to keeps its own")
        }

    private suspend fun clickUntil(
        session: CefBrowserSession,
        x: Int,
        y: Int,
        landed: () -> Boolean,
    ) {
        withTimeout(30_000) {
            while (!landed()) {
                click(session, x, y)
                repeat(10) { if (!landed()) delay(100) }
            }
        }
    }

    private fun click(
        session: CefBrowserSession,
        x: Int,
        y: Int,
    ) {
        // As the screen does, on the AWT thread: the pointer arrives, the page takes focus, then the
        // press and release.
        SwingUtilities.invokeAndWait {
            val source = Canvas()
            val now = System.currentTimeMillis()
            session.view.mouse(MouseEvent(source, MouseEvent.MOUSE_MOVED, now - 50, 0, x, y, 0, false, MouseEvent.NOBUTTON), MouseEvent.MOUSE_MOVED, x, y)
            session.view.focus(true)
            session.view.mouse(MouseEvent(source, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1), MouseEvent.MOUSE_PRESSED, x, y)
            session.view.mouse(MouseEvent(source, MouseEvent.MOUSE_RELEASED, now + 50, 0, x, y, 1, false, MouseEvent.BUTTON1), MouseEvent.MOUSE_RELEASED, x, y)
        }
    }

    @Test
    fun closingOneWindowLeavesTheOthersNip07Working() =
        runBlocking {
            assumeTrue("Set AMETHYST_CEF_TEST=1 (and run under a display) to run pages in Chromium", System.getenv("AMETHYST_CEF_TEST") == "1")
            val runtime = runtime(SharedCefTestEngine.engine)
            val user = NostrSignerInternal(KeyPair())
            pages["/probe.html"] =
                """
                <!doctype html><script>
                (async function () {
                  var name = new URLSearchParams(location.search).get('n');
                  var key = await window.nostr.getPublicKey().catch(function (e) { return 'error:' + e.message; });
                  fetch('http://127.0.0.1:$port/report?' + name + '=' + encodeURIComponent(key));
                })();
                </script>
                """.trimIndent()
            val first = show(runtime, user, Prompts(), "http://127.0.0.1:$port/probe.html?n=first")
            val second = show(runtime, user, Prompts(), "http://127.0.0.1:$port/probe.html?n=second")
            withTimeout(60_000) { while (reports["first"] == null || reports["second"] == null) delay(100) }
            assertEquals(user.pubKey, reports["first"])
            assertEquals(user.pubKey, reports["second"])

            first.close()
            delay(1_000)
            second.load("http://127.0.0.1:$port/probe.html?n=after")
            withTimeout(60_000) { while (reports["after"] == null) delay(100) }
            assertEquals(user.pubKey, reports["after"], "the other window still reaches the broker")
        }
}

// The large download: 24 MB in 64 KB pieces, ten milliseconds apart (a few seconds in all).
private const val BIG_SIZE = 24L * 1024 * 1024
private const val BIG_CHUNK = 64 * 1024
