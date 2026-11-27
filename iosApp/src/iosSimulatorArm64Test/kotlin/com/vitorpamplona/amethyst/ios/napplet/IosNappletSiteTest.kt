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
import com.vitorpamplona.amethyst.commons.napplet.WebxdcLaunch
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.session.NappletFailure
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLinkOpener
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRequestPolicy
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoute
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionServices
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.posix.AF_INET
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.O_NONBLOCK
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.fcntl
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval
import kotlin.concurrent.AtomicReference
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * nSites and webxdc apps in a real WKWebView, on the real per-account stores and their route
 * ([IosNappletStores], [IosNappletStoreRoute]), with a fake SOCKS5 listener standing in for Arti: it
 * records where each connection asked to go, then refuses it, so nothing reaches the internet through
 * it. Runs on a booted simulator (`-Pamethyst.ios.testSimulator=<udid>`), pumping the main run loop.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosNappletSiteTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("09").hexToByteArray()))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attendance = NappletAttendance<String>()
    private val services = NappletSessionServices(attendance, NappletLiveSubscriptions(scope, attendance), NappletIncBus<String>()) { null }
    private val socks = Probe(socks = true)
    private val socks2 = Probe(socks = true)
    private val plain = Probe(socks = false)

    // Each test's own account, so its store and route are its own; removed at the end.
    private val account =
        Random
            .nextLong()
            .toULong()
            .toString(16)
            .padStart(16, '0')
            .repeat(4)

    @Volatile
    private var torPort: Int? = socks.port
    private val stores = IosNappletStores { torPort }
    private val opened = mutableListOf<Opened>()

    @AfterTest
    fun tearDown() {
        opened.forEach { it.core.close() }
        // The web views go first; then the store can be removed (WebKit refuses one still in use).
        var removed: Boolean? = null
        scope.launch(Dispatchers.Main) { removed = stores.remove(account) }
        pumpUntil(15.0) { removed != null }
        socks.stop()
        socks2.stop()
        plain.stop()
        scope.cancel()
        // Asserted in its own test: a failure here would hide the test's own.
        if (removed != true) Log.w("NSITE") { "the store of $account was not removed" }
    }

    @Test
    fun aRemovedAccountsSiteStorageIsGone() {
        // The site keeps something in its storage, and finds it the next time it opens.
        val first = Reports()
        val site = open(HostProfile.WEBSITE, storagePage(), first, useTor = false)
        site.core.start()
        assertTrue(pumpUntil(20.0) { first["before"] != null }, "the page never reported: ${first.all()}")
        assertEquals("null", first["before"])
        site.core.close()
        val again = Reports()
        open(HostProfile.WEBSITE, storagePage(), again, useTor = false).core.start()
        assertTrue(pumpUntil(20.0) { again["before"] != null }, "the page never reported: ${again.all()}")
        assertEquals("kept", again["before"], "the store did not keep the site's storage")
        opened.forEach { it.core.close() }
        opened.clear()

        // The account goes: its store is removed, or wiped where WebKit still holds it.
        var removed: Boolean? = null
        scope.launch(Dispatchers.Main) { removed = stores.remove(account) }
        assertTrue(pumpUntil(30.0) { removed != null })
        assertEquals(true, removed, "the store was neither removed nor wiped")
        val after = Reports()
        open(HostProfile.WEBSITE, storagePage(), after, useTor = false).core.start()
        assertTrue(pumpUntil(20.0) { after["before"] != null }, "the page never reported: ${after.all()}")
        assertEquals("null", after["before"], "the removed account's storage came back")
    }

    @Test
    fun aSiteOnTorReachesTheWebOnlyThroughTheSocksListener() {
        val reports = Reports()
        val site = open(HostProfile.WEBSITE, sitePage(), reports, useTor = true)
        site.core.start()
        assertTrue(pumpUntil(20.0) { reports["https"] != null && reports["lan"] != null }, "the page never reported: ${reports.all()}")
        pumpUntil(3.0) { false }
        socks.poll()
        plain.poll()
        Log.w("NSITE") { "tor: socks=${socks.targets()} plain=${plain.targets()} reports=${reports.all()}" }
        assertEquals(NappletRoute.Tor(socks.port), stores.routeOf(account))
        // The page's own fetch went to the proxy by name (the exit resolves it), never direct.
        assertTrue("check.torproject.org:443" in socks.targets(), "no CONNECT for the site's fetch: ${socks.targets()}")
        assertTrue(socks.targets().none { it.startsWith("192.168.") }, "a LAN address reached the proxy: ${socks.targets()}")
        assertEquals(emptyList(), plain.targets(), "something went around the proxy")
        // WebRTC is gone from a site on Tor (Android's guard matrix).
        assertEquals("undefined", reports["rtc"])
    }

    @Test
    fun aSiteOnTheOpenWebNeverTouchesTheSocksListener() {
        val reports = Reports()
        val site = open(HostProfile.WEBSITE, sitePage(), reports, useTor = false)
        site.core.start()
        assertTrue(pumpUntil(20.0) { reports["https"] != null && reports["lan"] != null }, "the page never reported: ${reports.all()}")
        pumpUntil(3.0) { false }
        socks.poll()
        plain.poll()
        Log.w("NSITE") { "direct: socks=${socks.targets()} plain=${plain.targets()} reports=${reports.all()}" }
        assertEquals(NappletRoute.Direct, stores.routeOf(account))
        assertEquals(emptyList(), socks.targets(), "a direct site went through the proxy")
        // The LAN rule refused the IP literal, and the store's router the public name that resolves to
        // loopback: the loopback listener saw no connection at all.
        assertEquals(NappletRoute.Direct, stores.routerOf(account)?.route)
        assertTrue(pumpUntil(10.0) { reports["rebind"] != null }, "the rebind fetch never settled: ${reports.all()}")
        assertEquals("err", reports["rebind"])
        assertEquals(emptyList(), plain.targets(), "the local network was reached: ${plain.targets()}")
        // The open web still works through the router.
        assertEquals("ok", reports["https"])
    }

    @Test
    fun torWantedWithoutAPortMakesNoWebViewAndFailsClosed() {
        torPort = null
        val reports = Reports()
        val site =
            open(HostProfile.WEBSITE, sitePage(), reports, useTor = true, awaitPort = {
                delay(1_500)
                false
            })
        site.core.start()
        assertTrue(pumpUntil(5.0) { site.core.state.value == NappletSessionState.WaitingForTor }, "state ${site.core.state.value}")
        assertNull(site.engine.webView.value, "a web view was made while Tor was down")
        assertEquals(NappletRoute.Blocked, stores.routeOf(account), "the store was not blocked")
        assertTrue(pumpUntil(5.0) { site.core.state.value is NappletSessionState.Failed }, "state ${site.core.state.value}")
        assertEquals(NappletSessionState.Failed(NappletFailure.ROUTE_BLOCKED), site.core.state.value)
        assertNull(site.engine.webView.value)
        socks.poll()
        plain.poll()
        assertEquals(emptyList(), socks.targets() + plain.targets())
    }

    @Test
    fun aTorRestartRePointsTheStoreAndTorGoingAwayBlocksIt() {
        val reports = Reports()
        val site = open(HostProfile.WEBSITE, sitePage(), reports, useTor = true)
        site.core.start()
        assertTrue(pumpUntil(20.0) { reports["https"] != null }, "the page never reported: ${reports.all()}")

        // Tor comes back on another port: the live store follows it.
        torPort = socks2.port
        stores.reapplyAll()
        assertEquals(NappletRoute.Tor(socks2.port), stores.routeOf(account))
        evaluate(site.engine, "document.getElementById('app').contentWindow.postMessage('again','*')")
        assertTrue(pumpUntil(10.0) { socks2.poll() > 0 }, "the re-pointed store never reached the new port")
        assertTrue(socks2.targets().any { it.startsWith("again.torproject.org") }, "${socks2.targets()}")

        // Tor goes away: the store's router refuses everything, and the page's next load fails, never direct.
        torPort = null
        stores.reapplyAll()
        assertEquals(NappletRoute.Blocked, stores.routeOf(account))
        val before = socks.poll() + socks2.poll()
        evaluate(site.engine, "document.getElementById('app').contentWindow.postMessage('again','*')")
        pumpUntil(4.0) { false }
        assertEquals(before, socks.poll() + socks2.poll(), "a blocked store reached a proxy")
        assertTrue(plain.targets().isEmpty(), "a blocked store went direct")
    }

    @Test
    fun aWebxdcAppHasNoNetwork() {
        val files = OneFile(webxdcPage())
        val app = open(HostProfile.WEBXDC, "", Reports(), useTor = false, files = files)
        app.core.start()
        assertTrue(pumpUntil(20.0) { "/ran" in files.asked() }, "the app never ran: ${files.asked()}")
        pumpUntil(4.0) { false }
        socks.poll()
        plain.poll()
        assertEquals(emptyList(), socks.targets(), "webxdc reached the proxy")
        assertEquals(emptyList(), plain.targets(), "webxdc reached the network")
        // Not even to another internal host: only its own origin answered.
        assertTrue(files.asked().none { it.contains("other") }, "${files.asked()}")
    }

    @Test
    fun aSitesFrameThatLeavesItsOriginCantSayHello() {
        val reports = Reports()
        // No shim: the site doesn't say hello, its frame is navigated to a document of another origin,
        // which then does. The shell accepts a site's hello only from the site's own origin.
        val site = open(HostProfile.WEBSITE, foreignHelloPage(), reports, useTor = false, shim = "")
        site.core.start()
        assertTrue(pumpUntil(15.0) { site.core.state.value == NappletSessionState.Showing })
        pumpUntil(5.0) { reports["foreign"] != null }
        assertNull(reports["foreign"], "a foreign document took the site's channel")
    }

    @Test
    fun aSiteCantFrameOrLoadAnotherAppsOrigin() {
        val reports = Reports()
        val other = NappletWebContract.NAPPLET_SCHEME.appOrigin("other", HostProfile.WEBSITE)
        val files = OneFile(otherOriginPage(other))
        val site = open(HostProfile.WEBSITE, "", reports, useTor = false, files = files)
        site.core.start()
        assertTrue(pumpUntil(20.0) { reports["own"] != null && reports["other"] != null }, "the page never reported: ${reports.all()}")
        // Its own picture loads; the same file at another app's origin is not served (only this
        // launch's host is), and a frame there gets nothing either.
        assertEquals("ok", reports["own"])
        assertEquals("err", reports["other"])
        assertTrue(files.asked().count { it == "/probe.gif" } == 1, "the other origin's request reached the files: ${files.asked()}")
    }

    @Test
    fun theSiteRequestFilterCompiles() {
        var compiled: Any? = null
        var done = false
        scope.launch {
            compiled = IosNappletWebEngine.compileContentRules(NappletRequestPolicy.contentRuleListJson(NappletRequestPolicy.contentRules(HostProfile.WEBSITE, "napplet://n1.napplet.local", NappletWebContract.NAPPLET_SCHEME)))
            done = true
        }
        assertTrue(pumpUntil(10.0) { done })
        assertTrue(compiled != null, "WebKit refused the site's request filter")
    }

    // ---- the pages ----

    private val shim = "(function(){var c=new MessageChannel();window.__napPort=c.port1;window.parent.postMessage({type:'napplet.hello'},'*',[c.port2]);})();"

    /** Reports through the bridge what it reached; another fetch on each 'again' message. */
    private fun sitePage() =
        """
        <!doctype html><html><head></head><body><script>
        (function(){
          var p=window.__napPort,n=0;
          function report(k,v){p.postMessage({type:'storage.set',id:'r'+(n++),key:k,value:String(v)});}
          function settle(k,promise){promise.then(function(){report(k,'ok');},function(e){report(k,'err');});}
          report('rtc',typeof RTCPeerConnection);
          settle('https',fetch('https://check.torproject.org/api/ip',{mode:'no-cors'}));
          // Mixed content (O3): plain http from a napplet:// page, to a name and to an onion.
          settle('http',fetch('http://localtest.me:${plain.port}/mixed',{mode:'no-cors'}));
          settle('onionFetch',fetch('http://amethystprobeonionfetch.onion/api',{mode:'no-cors'}));
          var i=new Image();i.onload=function(){report('onionImg','ok');};i.onerror=function(){report('onionImg','err');};
          i.src='http://amethystprobeonionimage.onion/p.png';
          var s=document.createElement('script');s.onload=function(){report('httpScript','ok');};s.onerror=function(){report('httpScript','err');};
          s.src='http://amethystprobescript.example/x.js';document.head.appendChild(s);
          // The local network (row 19): an IP literal of the loopback listener, and a public name that
          // resolves to it (localtest.me is 127.0.0.1 in public DNS), which only the router can refuse.
          settle('lan',fetch('http://127.0.0.1:${plain.port}/lan',{mode:'no-cors'}));
          settle('rebind',fetch('https://localtest.me:${plain.port}/rebind',{mode:'no-cors'}));
          // Speculative connections (row 20).
          var l=document.createElement('link');l.rel='preconnect';l.href='https://preconnect-probe.torproject.org';document.head.appendChild(l);
          var d=document.createElement('link');d.rel='dns-prefetch';d.href='//dnsprefetch-probe.torproject.org';document.head.appendChild(d);
          // Service workers (O4).
          report('sw',typeof navigator.serviceWorker);
          if(navigator.serviceWorker){navigator.serviceWorker.register('/sw.js').then(function(){report('swReg','ok');},function(e){report('swReg','err:'+e.name+': '+e.message);});}
          window.addEventListener('message',function(e){if(e.data==='again')fetch('https://again.torproject.org/'+Date.now(),{mode:'no-cors'}).catch(function(){});});
        })();
        </script></body></html>
        """.trimIndent()

    private fun otherOriginPage(other: String) =
        """
        <!doctype html><html><head></head><body><script>
        var p=window.__napPort,n=0;
        function report(k,v){p.postMessage({type:'storage.set',id:'r'+(n++),key:k,value:String(v)});}
        function probe(k,src){var i=new Image();i.onload=function(){report(k,'ok');};i.onerror=function(){report(k,'err');};i.src=src;}
        probe('own',location.origin+'/probe.gif');
        probe('other','$other/probe.gif');
        var f=document.createElement('iframe');f.src='$other/index.html';document.body.appendChild(f);
        </script></body></html>
        """.trimIndent()

    private fun storagePage() =
        """
        <!doctype html><html><head></head><body><script>
        var p=window.__napPort;var v=localStorage.getItem('k');localStorage.setItem('k','kept');
        p.postMessage({type:'storage.set',id:'s',key:'before',value:String(v)});
        </script></body></html>
        """.trimIndent()

    private fun webxdcPage() =
        """
        <!doctype html><html><head></head><body><script>
        fetch('/ran').catch(function(){});
        fetch('http://localtest.me:${plain.port}/xdc',{mode:'no-cors'}).catch(function(){});
        fetch('https://check.torproject.org/api/ip',{mode:'no-cors'}).catch(function(){});
        fetch('http://127.0.0.1:${plain.port}/xdc-lan',{mode:'no-cors'}).catch(function(){});
        fetch('${NappletWebContract.NAPPLET_SCHEME.appOrigin("other", HostProfile.WEBXDC)}/other',{mode:'no-cors'}).catch(function(){});
        var i=new Image();i.src='http://localtest.me:${plain.port}/xdc-img';
        try{new WebSocket('ws://localtest.me:${plain.port}/xdc-ws');}catch(e){}
        </script></body></html>
        """.trimIndent()

    private fun foreignHelloPage() =
        """
        <!doctype html><html><head></head><body><script>
        location.href='data:text/html,'+encodeURIComponent('<script>var c=new MessageChannel();parent.postMessage({type:"napplet.hello"},"*",[c.port2]);c.port1.postMessage({type:"storage.set",id:"x",key:"foreign",value:"1"});<\/script>');
        </script></body></html>
        """.trimIndent()

    // ---- the harness ----

    private class Opened(
        val core: NappletSessionCore,
        val engine: IosNappletWebEngine,
    )

    private fun open(
        profile: HostProfile,
        html: String,
        reports: Reports,
        useTor: Boolean,
        awaitPort: suspend () -> Boolean = { torPort != null },
        files: OneFile = OneFile(html),
        shim: String = this.shim,
    ): Opened {
        val webxdc = if (profile == HostProfile.WEBXDC) WebxdcLaunch("https://files.example/app.xdc", null, "addr", "Me", isAppSender = false) else null
        val launch = NappletLaunch(NappletIdentity("aa".repeat(32), "site-${profile.name.lowercase()}"), profile, NappletCapability.entries.toSet(), emptyList(), "Site", emptyList(), emptyList(), webxdc)
        val plan = NappletLaunchPlan.of(launch, account, useTor = useTor, appletBlobsUseTor = false, sharedStorageJar = false, origins = NappletWebContract.NAPPLET_SCHEME)
        val engine = IosNappletWebEngine(plan, dataStore = { stores.storeFor(account) }, inspectable = false)
        val broker = NappletBroker(signer, NappletPermissionLedger(InMemoryNappletPermissionStore()), AllowOnce, relay = NoRelay, storage = reports)
        val shell = NSString.stringWithContentsOfFile(NappletTestFiles.SHELL_HTML, NSUTF8StringEncoding, null) ?: error("no shell at ${NappletTestFiles.SHELL_HTML}")
        val route = IosNappletStoreRoute(stores, account, plan.usesNetwork, { torPort }, awaitPort, { true }, { true })
        val core =
            NappletSessionCore.inProcess(
                owner = "site-" + Random.nextLong(),
                launch = launch,
                accountPubKey = account,
                plan = plan,
                account = null,
                broker = broker,
                services = services,
                route = route,
                files = { files },
                shellHtml = { shell.encodeToByteArray() },
                shimJs = { shim },
                links = NoLinks,
                filePort = null,
                attendedWhenShown = true,
            )
        engine.core = core
        core.engine = engine
        return Opened(core, engine).also { opened += it }
    }

    private fun evaluate(
        engine: IosNappletWebEngine,
        js: String,
    ) {
        var done = false
        engine.webView.value?.evaluateJavaScript(js) { _, _ -> done = true } ?: return
        pumpUntil(5.0) { done }
    }

    private fun pumpUntil(
        seconds: Double,
        condition: () -> Boolean,
    ): Boolean {
        val end = NSDate().timeIntervalSince1970 + seconds
        while (!condition() && NSDate().timeIntervalSince1970 < end) {
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.02))
            // The probes answer as connections come, or the page's requests would hang on them.
            socks.poll()
            socks2.poll()
            plain.poll()
        }
        return condition()
    }

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

    private object NoLinks : NappletLinkOpener {
        override fun openExternal(url: String) {}

        override fun openNostr(uri: String) {}
    }

    /** The page at `/` and `/index.html`, a service worker at `/sw.js`; records every path asked for. */
    private class OneFile(
        private val html: String,
    ) : NappletFiles {
        private val paths = AtomicReference(emptyList<String>())

        fun asked(): List<String> = paths.value

        override suspend fun isAvailable(requestPath: String): Boolean = true

        override suspend fun find(requestPath: String): NappletBlobStore.Blob? {
            while (true) {
                val current = paths.value
                if (paths.compareAndSet(current, current + requestPath)) break
            }
            return when (requestPath) {
                "/", "/index.html" -> NappletBlobStore.Blob("/index.html", "00".repeat(32), "text/html", file = null, bytes = html.encodeToByteArray())
                "/sw.js" -> NappletBlobStore.Blob("/sw.js", "00".repeat(32), "text/javascript", file = null, bytes = "self.addEventListener('fetch',function(){});".encodeToByteArray())
                "/probe.gif" -> NappletBlobStore.Blob("/probe.gif", "00".repeat(32), "image/gif", file = null, bytes = TINY_GIF)
                else -> null
            }
        }

        override fun isListed(requestPath: String): Boolean = false
    }

    private companion object {
        /** A 1x1 transparent GIF. */
        val TINY_GIF =
            byteArrayOf(
                0x47,
                0x49,
                0x46,
                0x38,
                0x39,
                0x61,
                0x01,
                0x00,
                0x01,
                0x00,
                0x80.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0x21,
                0xF9.toByte(),
                0x04,
                0x01,
                0x00,
                0x00,
                0x00,
                0x00,
                0x2C,
                0x00,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x01,
                0x00,
                0x00,
                0x02,
                0x02,
                0x44,
                0x01,
                0x00,
                0x3B,
            )
    }

    /**
     * A TCP listener on 127.0.0.1. As a SOCKS5 proxy ([socks]) it reads each client's CONNECT, records
     * `host:port`, and refuses it; otherwise it records each connection's HTTP request line.
     */
    private class Probe(
        private val socks: Boolean,
    ) {
        private val fd = socket(AF_INET, SOCK_STREAM, 0)
        private val seen = mutableListOf<String>()
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
                    check(listen(fd, 32) == 0) { "listen" }
                    val length = alloc<socklen_tVar>()
                    length.value = sizeOf<sockaddr_in>().convert()
                    getsockname(fd, addr.ptr.reinterpret(), length.ptr)
                    val raw = addr.sin_port.toInt()
                    ((raw and 0xff) shl 8) or ((raw shr 8) and 0xff)
                }
            fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) or O_NONBLOCK)
        }

        /** Takes the connections waiting; returns how many there have been. */
        fun poll(): Int {
            while (true) {
                val client = accept(fd, null, null)
                if (client < 0) return seen.size
                fcntl(client, F_SETFL, fcntl(client, F_GETFL) and O_NONBLOCK.inv())
                memScoped {
                    val tv = alloc<timeval>()
                    tv.tv_sec = 2
                    tv.tv_usec = 0
                    setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())
                }
                seen += if (socks) socksTarget(client) else requestLine(client)
                close(client)
            }
        }

        fun targets(): List<String> = seen.toList()

        private fun read(
            client: Int,
            size: Int,
        ): ByteArray {
            val buffer = ByteArray(size)
            val n = buffer.usePinned { recv(client, it.addressOf(0), size.convert(), 0) }
            return if (n <= 0) ByteArray(0) else buffer.copyOf(n.toInt())
        }

        private fun write(
            client: Int,
            bytes: ByteArray,
        ) {
            bytes.usePinned { send(client, it.addressOf(0), bytes.size.convert(), 0) }
        }

        private fun requestLine(client: Int): String = read(client, 1024).decodeToString().substringBefore("\r\n").ifEmpty { "(no request)" }

        private fun socksTarget(client: Int): String {
            val greeting = read(client, 64)
            if (greeting.isEmpty() || greeting[0] != 5.toByte()) return "(not socks5)"
            write(client, byteArrayOf(5, 0))
            val request = read(client, 512)
            if (request.size < 7) return "(no request)"
            val (host, at) =
                when (request[3].toInt()) {
                    1 -> (0 until 4).joinToString(".") { (request[4 + it].toInt() and 0xff).toString() } to 8
                    3 -> {
                        val length = request[4].toInt() and 0xff
                        request.copyOfRange(5, 5 + length).decodeToString() to 5 + length
                    }
                    4 -> "[ipv6]" to 20
                    else -> "(atyp ${request[3]})" to request.size - 2
                }
            val port = ((request[at].toInt() and 0xff) shl 8) or (request[at + 1].toInt() and 0xff)
            // Connection refused: nothing goes on from here.
            write(client, byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
            return "$host:$port"
        }

        fun stop() {
            close(fd)
        }
    }
}
