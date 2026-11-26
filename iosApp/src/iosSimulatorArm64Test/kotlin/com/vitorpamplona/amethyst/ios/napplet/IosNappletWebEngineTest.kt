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
package com.vitorpamplona.amethyst.ios.napplet

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletIncBus
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLinkOpener
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRequestPolicy
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoutePort
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionServices
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.WebKit.WKContentRuleList
import platform.WebKit.WKContentWorld
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.posix.AF_INET
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.fcntl
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import kotlin.concurrent.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [IosNappletWebEngine] in a real WKWebView, driven headless in the test executable (it runs WebKit
 * on a booted simulator: `-Pamethyst.ios.testSimulator=<udid>`). The test thread is the main thread,
 * so it pumps the main run loop while it waits.
 *
 * The napplet under test reports what it sees through the bridge itself (`storage.set` to a broker
 * whose storage the test reads), so every report is also a round trip through the core.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosNappletWebEngineTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attendance = NappletAttendance<String>()
    private val services = NappletSessionServices(attendance, NappletLiveSubscriptions(scope, attendance), NappletIncBus<String>()) { null }
    private val listener = Listener()

    @AfterTest
    fun tearDown() {
        listener.stop()
        scope.cancel()
    }

    @Test
    fun aNappletLoadsOverTheSchemeTalksThroughTheCoreAndReachesNoNetwork() {
        val reports = Reports()
        val port = listener.port
        val opened = open(probeNapplet(port), reports)
        opened.core.start()

        assertTrue(pumpUntil(20.0) { reports["pk"] != null }, "no round trip; reports so far: ${reports.all()}")
        // identity.getPublicKey went page -> shell -> native -> core -> broker and back, and the reply
        // then came back again as a storage.set.
        assertTrue(reports["pk"]!!.contains(signer.pubKey), reports["pk"])
        assertEquals(NappletSessionState.Showing, opened.core.state.value)
        // Assigning the napplet's srcdoc leaves no history to go back through: back closes the screen
        // instead of taking the frame back to a blank page (which would end the napplet).
        assertEquals(
            false,
            opened.engine.webView.value
                ?.canGoBack,
            "the napplet left history behind",
        )

        // The guards: gone in the napplet's frame and in a frame it made itself, where only the
        // engine's document-start script reaches; the shell's too.
        assertEquals("undefined", reports["rtc"])
        assertTrue(pumpUntil(5.0) { reports["nestedRtc"] != null }, "the nested frame never reported")
        assertEquals("undefined", reports["nestedRtc"])
        assertEquals("undefined", evaluate(opened.engine, "typeof RTCPeerConnection"))
        // The bridge's handler is in its own world: no page world sees it, the shell's included.
        assertEquals("undefined", reports["handler"])
        assertEquals("undefined", evaluate(opened.engine, "typeof (window.webkit&&window.webkit.messageHandlers&&window.webkit.messageHandlers.${IosNappletWebEngine.HANDLER_NAME})"))

        // Nothing left the device: the fetch failed, and the image, the WebSocket and the fetch never
        // reached the listener they were aimed at.
        assertTrue(pumpUntil(10.0) { reports["fetch"] != null }, "the fetch never settled")
        assertEquals("blocked", reports["fetch"])
        pumpUntil(1.0) { false }
        assertEquals(0, listener.accepted(), "a request reached the network")

        // The shell can't read itself again: its CSP refuses the fetch before the scheme handler, which
        // serves it only to the navigation that loaded it, would answer 404.
        val again = evaluateAsync(opened.engine, "fetch('${NappletWebContract.NAPPLET_SCHEME.shellUrl}').then(function(r){return String(r.status);},function(e){return 'err';})")
        assertTrue(again == "err" || again == "404", "the shell was served again: $again")

        opened.core.close()
        assertTrue(pumpUntil(5.0) { opened.engine.webView.value == null })
    }

    @Test
    fun aSecondDocumentInTheFrameLosesTheChannel() {
        val reports = Reports()
        val opened = open(reloadingNapplet(), reports)
        opened.core.start()

        assertTrue(pumpUntil(20.0) { reports["first"] != null }, "the first document never reported: ${reports.all()}")
        // The napplet reloads itself after its first report; the shell drops the frame instead of
        // binding the new document.
        assertTrue(pumpUntil(10.0) { evaluate(opened.engine, "String(document.getElementById('app')===null)") == "true" }, "the frame stayed")
        pumpUntil(1.0) { false }
        assertNull(reports["second"], "the reloaded document reached the bridge")
        opened.core.close()
    }

    @Test
    fun theRequestFilterAloneKeepsAPageOffTheNetwork() {
        val rules =
            run {
                var compiled: Any? = null
                var done = false
                scope.launch {
                    compiled = IosNappletWebEngine.compileContentRules(NappletRequestPolicy.contentRuleListJson(NappletRequestPolicy.contentRules(HostProfile.NAPPLET, "napplet://x.napplet.local", NappletWebContract.NAPPLET_SCHEME)))
                    done = true
                }
                assertTrue(pumpUntil(10.0) { done })
                compiled as? WKContentRuleList
            }
        assertNotNull(rules, "the rule list did not compile")

        // A page with no CSP at all: with the list, nothing it asks for gets out.
        val page = "<html><body><script>fetch('http://127.0.0.1:${listener.port}/f').catch(function(){});var i=new Image();i.src='http://127.0.0.1:${listener.port}/i';</script></body></html>"
        val filtered = WKWebView(CGRectZero.readValue(), WKWebViewConfiguration().apply { websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore() })
        filtered.configuration.userContentController.addContentRuleList(rules)
        filtered.loadHTMLString(page, null)
        pumpUntil(3.0) { false }
        assertEquals(0, listener.accepted(), "the filter let a request out")

        // The same page without it does reach the listener: the check above can see a leak.
        val open = WKWebView(CGRectZero.readValue(), WKWebViewConfiguration().apply { websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore() })
        open.loadHTMLString(page, null)
        assertTrue(pumpUntil(10.0) { listener.accepted() > 0 }, "the control page never reached the listener")
    }

    // ---- the napplets ----

    // Stands in for shim.js: makes the port and says hello, as the real shim does before the napplet's
    // own scripts run.
    private val shim = "(function(){var c=new MessageChannel();window.__napPort=c.port1;window.parent.postMessage({type:'napplet.hello'},'*',[c.port2]);})();"

    private fun probeNapplet(port: Int) =
        """
        <!doctype html><html><head></head><body><script>
        (function(){
          var p=window.__napPort,n=0;
          function report(k,v){p.postMessage({type:'storage.set',id:'r'+(n++),key:k,value:String(v)});}
          p.onmessage=function(e){var d=e.data;if(d&&d.id==='pk')report('pk',JSON.stringify(d));};
          report('rtc',typeof RTCPeerConnection);
          // A frame of its own, which the napplet's prelude doesn't reach: it says what it sees.
          window.addEventListener('message',function(e){if(e.data&&e.data.nested)report('nestedRtc',e.data.nested);});
          var f=document.createElement('iframe');
          f.srcdoc='<script>parent.postMessage({nested:typeof RTCPeerConnection},"*")<\/script>';
          document.body.appendChild(f);
          report('handler',typeof (window.webkit&&window.webkit.messageHandlers&&window.webkit.messageHandlers.${IosNappletWebEngine.HANDLER_NAME}));
          fetch('http://127.0.0.1:$port/f').then(function(){report('fetch','ok');},function(){report('fetch','blocked');});
          var i=new Image();i.src='http://127.0.0.1:$port/i';
          try{new WebSocket('ws://127.0.0.1:$port/w');}catch(e){}
          p.postMessage({type:'identity.getPublicKey',id:'pk'});
        })();
        </script></body></html>
        """.trimIndent()

    private fun reloadingNapplet() =
        """
        <!doctype html><html><head></head><body><script>
        (function(){
          var p=window.__napPort;
          if(window.name==='again'){p.postMessage({type:'storage.set',id:'s',key:'second',value:'1'});return;}
          p.postMessage({type:'storage.set',id:'f',key:'first',value:'1'});
          setTimeout(function(){window.name='again';location.reload();},300);
        })();
        </script></body></html>
        """.trimIndent()

    // ---- the harness ----

    private class Opened(
        val core: NappletSessionCore<String>,
        val engine: IosNappletWebEngine,
    )

    private fun open(
        html: String,
        reports: Reports,
    ): Opened {
        val launch = NappletLaunch(NappletIdentity("aa".repeat(32), "probe"), HostProfile.NAPPLET, NappletCapability.entries.toSet(), emptyList(), "Probe", emptyList(), emptyList())
        val plan = NappletLaunchPlan.of(launch, "bb".repeat(32), useTor = false, appletBlobsUseTor = false, sharedStorageJar = false, origins = NappletWebContract.NAPPLET_SCHEME)
        val engine = IosNappletWebEngine(plan, dataStore = { WKWebsiteDataStore.nonPersistentDataStore() }, inspectable = false)
        val broker = NappletBroker(signer, NappletPermissionLedger(InMemoryNappletPermissionStore()), AllowOnce, relay = NoRelay, storage = reports)
        val shell = NSString.stringWithContentsOfFile(NappletTestFiles.SHELL_HTML, NSUTF8StringEncoding, null) ?: error("no shell at ${NappletTestFiles.SHELL_HTML}")
        val core =
            NappletSessionCore(
                owner = "probe-" + html.hashCode(),
                launch = launch,
                accountPubKey = "bb".repeat(32),
                plan = plan,
                account = null,
                broker = broker,
                services = services,
                route = NoRoute,
                files = { OneFile(html) },
                shellHtml = { shell.encodeToByteArray() },
                shimJs = { shim },
                links = NoLinks,
                filePort = null,
                attendedWhenShown = true,
            )
        engine.core = core
        core.engine = engine
        return Opened(core, engine)
    }

    private fun evaluate(
        engine: IosNappletWebEngine,
        js: String,
    ): String? {
        var result: String? = null
        var done = false
        engine.webView.value?.evaluateJavaScript(js, null, WKContentWorld.pageWorld) { value, _ ->
            result = value?.toString()
            done = true
        } ?: return null
        pumpUntil(5.0) { done }
        return result
    }

    private fun evaluateAsync(
        engine: IosNappletWebEngine,
        promise: String,
    ): String? {
        var result: String? = null
        var done = false
        val view = engine.webView.value ?: return null
        view.callAsyncJavaScript("return await ($promise);", emptyMap<Any?, Any?>(), null, WKContentWorld.pageWorld) { value, _ ->
            result = value?.toString()
            done = true
        }
        pumpUntil(5.0) { done }
        return result
    }

    private fun pumpUntil(
        seconds: Double,
        condition: () -> Boolean,
    ): Boolean {
        val end = NSDate().timeIntervalSince1970 + seconds
        while (!condition() && NSDate().timeIntervalSince1970 < end) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02))
        }
        return condition()
    }

    /** What the napplet stored, by key, whatever its storage namespace. */
    private class Reports : NappletStorage {
        private val data = AtomicReference(emptyMap<String, String>())

        operator fun get(key: String): String? = data.value[key]

        fun all(): Map<String, String> = data.value

        override suspend fun get(
            coordinate: String,
            key: String,
        ): String? = data.value[key]

        override suspend fun set(
            coordinate: String,
            key: String,
            value: String,
        ) {
            while (true) {
                val current = data.value
                if (data.compareAndSet(current, current + (key to value))) return
            }
        }

        override suspend fun remove(
            coordinate: String,
            key: String,
        ) {}

        override suspend fun keys(coordinate: String): List<String> = data.value.keys.toList()
    }

    private object AllowOnce : NappletConsentPrompt {
        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState = GrantState.ALLOW_ONCE
    }

    private object NoRelay : NappletRelayGateway {
        override suspend fun publish(event: Event): List<String> = emptyList()

        override suspend fun query(filters: List<Filter>): List<Event> = emptyList()
    }

    private object NoRoute : NappletRoutePort {
        override val torRunning: Boolean = true

        override suspend fun claim(useTor: Boolean): Boolean = false

        override suspend fun awaitTor(): Boolean = true

        override fun release() {}
    }

    private object NoLinks : NappletLinkOpener {
        override fun openExternal(url: String) {}

        override fun openNostr(uri: String) {}
    }

    private class OneFile(
        private val html: String,
    ) : NappletFiles {
        private fun blob() = NappletBlobStore.Blob("/index.html", "00".repeat(32), "text/html", file = null, bytes = html.encodeToByteArray())

        override suspend fun isAvailable(requestPath: String): Boolean = true

        override suspend fun find(requestPath: String): NappletBlobStore.Blob? = if (requestPath == "/" || requestPath == "/index.html") blob() else null

        override fun isListed(requestPath: String): Boolean = requestPath == "/index.html"
    }

    /** A TCP listener on 127.0.0.1 that counts the connections it is offered: the network the page must not reach. */
    private class Listener {
        private val fd = socket(AF_INET, SOCK_STREAM, 0)
        private var count = 0
        val port: Int

        init {
            port =
                memScoped {
                    val addr = alloc<sockaddr_in>()
                    addr.sin_len = sizeOf<sockaddr_in>().convert()
                    addr.sin_family = AF_INET.convert()
                    addr.sin_port = 0u
                    addr.sin_addr.s_addr = 0x0100007Fu
                    check(bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0) { "bind" }
                    check(listen(fd, 16) == 0) { "listen" }
                    val length = alloc<socklen_tVar>()
                    length.value = sizeOf<sockaddr_in>().convert()
                    getsockname(fd, addr.ptr.reinterpret(), length.ptr)
                    val raw = addr.sin_port.toInt()
                    ((raw and 0xff) shl 8) or ((raw shr 8) and 0xff)
                }
            fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) or O_NONBLOCK)
        }

        /** Connections offered so far. */
        fun accepted(): Int {
            while (true) {
                val client = accept(fd, null, null)
                if (client < 0) return count
                count++
                close(client)
            }
        }

        fun stop() {
            close(fd)
        }
    }
}
