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
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BorderLayout
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFrame
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
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requested += exchange.requestURI.path
                exchange.requestURI.rawQuery?.let { query ->
                    query.split('&').forEach { pair ->
                        reports[pair.substringBefore('=')] = URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8)
                    }
                }
                val body = pages[exchange.requestURI.path]?.encodeToByteArray()
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                if (body == null) {
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
    private val sessions = mutableListOf<CefBrowserSession>()
    private val frames = mutableListOf<JFrame>()

    @After
    fun tearDown() {
        sessions.forEach { it.close() }
        SwingUtilities.invokeAndWait { frames.forEach { it.dispose() } }
        server.stop(0)
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
        val bridge = script.indexOf("typeof q !== 'function'")
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
            val session = CefBrowserSession("http://127.0.0.1:$port/", "tester", useTor = true, account = null, broker = broker(NostrSignerInternal(KeyPair())), runtime = runtime, bridge = runtime.browserBridge)
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
                  var q = window.${DesktopBrowserBridge.QUERY_FUNCTION};
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
                    var direct = window.${DesktopBrowserBridge.QUERY_FUNCTION};
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
            val session = show(runtime, user, prompts, "http://127.0.0.1:$port/") { url -> visits += url }

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
    ): CefBrowserSession {
        val session =
            CefBrowserSession(
                url,
                user.pubKey,
                useTor = false,
                account = null,
                broker = broker(user, prompts),
                runtime = runtime,
                bridge = runtime.browserBridge,
                onVisit = { visited, _ -> onVisit(visited) },
            )
        sessions += session
        session.start()
        val state = withTimeout(600_000) { session.state.first { it is CefBrowserSession.State.Showing || it is CefBrowserSession.State.Failed } }
        assertIs<CefBrowserSession.State.Showing>(state, "the page did not open: $state")
        SwingUtilities.invokeAndWait {
            frames +=
                JFrame("browser").apply {
                    contentPane.add(state.component, BorderLayout.CENTER)
                    setBounds(0, 0, 960, 640)
                    isVisible = true
                }
        }
        return session
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
