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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.NappletWebResources
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcFiles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcRuntime
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import com.vitorpamplona.amethyst.desktop.app.DesktopBrowser
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.OS
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.OffscreenCefBrowser
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.awt.Component
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * One open napplet or nSite window: its Chromium browser, the trusted shell it loads, and the bridge
 * between that shell and the shared [NappletBroker].
 *
 * The trust model is Android's (`nappletHost`), on CEF:
 * - The browser's top frame is only ever [NappletWebContract.SHELL_URL], served from memory with
 *   the shell CSP, built by the shared [NappletWebContract.shell].
 * - A [HostProfile.NAPPLET] runs as a `srcdoc` in a `sandbox="allow-scripts"` iframe (an opaque
 *   origin) with the locked app CSP (`connect-src 'none'`), from the sha256-verified artifact. Every
 *   request that isn't the shell is cancelled before it reaches the network.
 * - A [HostProfile.WEBSITE] runs at its own origin under `napplet.local`, one per site and per
 *   account, so its storage persists and stays its own. Every file it loads from that origin is a
 *   sha256-verified blob from its event (a client-routed page falls back to `/`); requests to other
 *   hosts go out through [DesktopNappletRoutes]' route (Tor or the open web, as the user chose for
 *   this site). `window.nostr` (NIP-07) reaches the broker through the same bridge.
 * - The bridge is CEF's message router, which Chromium injects into every frame. Only a query from
 *   the main frame at the shell URL is accepted, so the page's frames, which can see the function,
 *   get nothing from calling it.
 * - Neither the shell nor the page's own frame may navigate away; a link the user clicks opens in the
 *   system browser. Embeds inside a website (a video player) may load their own pages.
 * - Every request is decided by [NappletRequestRouter] and the broker, as on Android.
 */
class CefNappletSession(
    val launch: NappletLaunch,
    /** The account that opened it: the window keeps signing as this account, whoever is active later. */
    val accountPubKey: HexKey,
    /**
     * Whether a site's traffic and blobs go through Tor, from the user's choice for this site. A
     * napplet has no network of its own; its blobs follow the app's Tor setting.
     */
    val useTor: Boolean,
    /** The launching account, for live relay subscriptions; null leaves them unopened. */
    private val account: Account?,
    private val broker: NappletBroker,
    private val runtime: DesktopNappletRuntime,
    /**
     * Set for a napplet running inside a note: its browser has no window and paints into this view,
     * which the note draws and forwards input to. Null for a window of its own.
     */
    val offscreen: OffscreenNappletView? = null,
) {
    sealed interface State {
        data object Loading : State

        /** A site that goes through Tor, waiting for Tor to come up before anything is fetched. */
        data object WaitingForTor : State

        /** The page is up: in [component] for a window, or painting into [offscreen] when that is null. */
        data class Showing(
            val component: Component?,
        ) : State

        data class Failed(
            val reason: Failure,
        ) : State
    }

    enum class Failure {
        /** No server returned the signed files. */
        UNREACHABLE,

        /** Chromium did not download or start. */
        ENGINE,

        /** Tor is on and isn't running: nothing may leave until it is. */
        ROUTE_BLOCKED,
    }

    /** What the window's toolbar shows about the page. */
    data class Page(
        val isLoading: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val zoomPercent: Int = 100,
    )

    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val mutableDownload = MutableStateFlow<NappletDownloadProgress?>(null)

    /** The download the page waits on while [State.Loading], for the loading screen's bar. */
    val download: StateFlow<NappletDownloadProgress?> = mutableDownload.asStateFlow()

    private val mutablePage = MutableStateFlow(Page())
    val page: StateFlow<Page> = mutablePage.asStateFlow()

    /** Whether this window's traffic goes through Tor right now (a website on the open web can be forced). */
    val routedOverTor: StateFlow<Boolean> get() = runtime.routes.routedOverTor

    private val isWebsite = launch.profile == HostProfile.WEBSITE

    // A website's or webxdc app's origin is its own per account: this browser keeps one storage jar
    // for everyone, so the account goes into the origin instead of choosing a separate jar as Android does.
    private val appOrigin =
        NappletWebContract.appOrigin(
            NappletWebContract.appId(
                author = launch.identity.authorPubKey,
                identifier = launch.identity.identifier,
                partition = if (launch.profile.ownsOrigin) accountPubKey else null,
                profile = launch.profile,
            ),
            launch.profile,
        )

    private val blobsUseTor: Boolean get() = if (isWebsite) useTor else runtime.nappletBlobsUseTor()

    // Built once the route is known. Its client is the app's shared one, so it is never closed here.
    private lateinit var server: NappletSiteServer

    // The server's files, for close(): a webxdc archive still downloading stops with the window.
    @Volatile
    private var files: NappletFiles? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: CefClient? = null
    private var browser: CefBrowser? = null
    private var router: CefMessageRouter? = null

    @Volatile
    private var closed = false

    /** Whether [close] ran: the user stopped it, or the host did for an account that left. */
    val isClosed: Boolean get() = closed

    // Whether Chromium has made the browser it was asked for (it does so later, on its own thread).
    // Guarded by lifecycle.
    private var created = false

    // A close that came before the browser was made: it runs once Chromium has made it. Guarded by
    // lifecycle.
    private var pendingTeardown: (() -> Unit)? = null

    // Guards closed against the browser's creation, so close() and start() can't interleave.
    private val lifecycle = Any()

    // This session's own name for the query function. Chromium keeps one router per name for every
    // browser in a renderer, and the shells all share one: a window opened while a note's run closed
    // joined that run's router, which the close then removed, so the window's page could reach no one.
    private val queryFunction = QUERY_FUNCTION_PREFIX + UUID.randomUUID().toString().replace("-", "")

    // The object the shared shell expects at [NappletWebContract.BRIDGE_NAME]: `postMessage(String)`
    // out to the host, `onmessage({data})` in. The query function is captured and removed from the
    // shell's window, so nothing loaded later in the shell finds it there.
    private val bridgeScript =
        "<script>(function(){var q=window.$queryFunction;try{delete window.$queryFunction;}catch(_){}" +
            "window.${NappletWebContract.BRIDGE_NAME}={onmessage:null,postMessage:function(s){" +
            "q({request:String(s),persistent:false,onSuccess:function(){},onFailure:function(){}});}};})();</script>"

    // Payloads for the page, waiting for the next flush.
    private val outbox = ConcurrentLinkedQueue<String>()
    private val flushQueued = AtomicBoolean(false)

    fun start() {
        scope.launch {
            try {
                // With Tor on, nothing is fetched until Tor runs, so no request leaves early or around
                // it: a site's own traffic, or a napplet's blobs. Android fails closed the same way.
                val needsTor = blobsUseTor
                if (needsTor && !runtime.routes.torRunning) mutableState.value = State.WaitingForTor
                val routed =
                    if (isWebsite) {
                        runtime.routes.claim(this@CefNappletSession, useTor, runtime.torWaitMs)
                    } else {
                        !needsTor || runtime.routes.awaitTor(runtime.torWaitMs)
                    }
                // Closed while it waited: its claim was filed after close() released it.
                if (closed) {
                    if (isWebsite) runtime.routes.release(this@CefNappletSession)
                    return@launch
                }
                if (!routed) {
                    Log.w(TAG) { "Tor is not running; not opening ${launch.identity.coordinate}" }
                    // A window showing its failure has no traffic: its claim must not keep holding the
                    // route for the others ("Tor not ready" blocks every window).
                    if (isWebsite) runtime.routes.release(this@CefNappletSession)
                    mutableState.value = State.Failed(Failure.ROUTE_BLOCKED)
                    return@launch
                }
                mutableState.value = State.Loading
                val webxdc = launch.webxdc
                val blobs =
                    if (webxdc != null) {
                        WebxdcFiles(
                            runtime.webxdcBundles,
                            WebxdcFiles.downloadClient(runtime.httpClient(blobsUseTor)),
                            webxdc.url,
                            webxdc.hash,
                            launch.servers,
                            WebxdcRuntime.script(webxdc.selfAddr, webxdc.selfName, webxdc.isAppSender),
                        )
                    } else {
                        NappletBlobStore(
                            runtime.blobCache.forSite(NappletBlobCache.partitionOf(accountPubKey, launch.identity.authorPubKey, launch.identity.identifier)),
                            runtime.httpClient(blobsUseTor),
                            launch.paths,
                            launch.servers,
                        )
                    }
                files = blobs
                // Closed while the files were made: close() may have missed them.
                if (closed) blobs.close()
                server =
                    NappletSiteServer(
                        blobs = blobs,
                        shellHtml = NappletWebResources.shellHtml(),
                        shimJs = NappletWebResources.shimJs().decodeToString(),
                        appOrigin = appOrigin,
                        profile = launch.profile,
                        declaredDomains = launch.declaredDomains,
                        // A napplet's shell refuses to start it while WebRTC is still there: without the
                        // engine's guard extension, it would have a way out through the router.
                        shellHead = if (isWebsite) bridgeScript else WEBRTC_CHECK + bridgeScript,
                    )
                // The entry point must resolve before a window shows: a missing site is a clear
                // message, not a blank page.
                val watch = scope.launch { server.download.collect { mutableDownload.value = it } }
                val available =
                    try {
                        server.isAvailable(if (isWebsite) "/" else NappletSiteServer.INDEX_PATH)
                    } finally {
                        // Joined first: a collector mid-write on another thread would put its value
                        // back after this null.
                        withContext(NonCancellable) {
                            watch.cancelAndJoin()
                            mutableDownload.value = null
                        }
                    }
                if (!available) {
                    Log.w(TAG) { "No Blossom server returned ${launch.identity.coordinate}'s files" }
                    if (isWebsite) runtime.routes.release(this@CefNappletSession)
                    mutableState.value = State.Failed(Failure.UNREACHABLE)
                    return@launch
                }
                val app = runtime.engine.app()
                // Created and published under the lock close() takes, so a window closed meanwhile
                // never leaves a browser behind.
                val browser =
                    synchronized(lifecycle) {
                        if (closed) return@launch
                        val client = app.createClient()
                        this@CefNappletSession.client = client
                        installHandlers(client)
                        val created =
                            if (offscreen != null) {
                                OffscreenCefBrowser(client, NappletWebContract.SHELL_URL, null, OFFSCREEN_FRAME_RATE, offscreen).also {
                                    offscreen.browser = it
                                    it.createImmediately()
                                }
                            } else {
                                client.createBrowser(NappletWebContract.SHELL_URL, false, false)
                            }
                        created.also {
                            this@CefNappletSession.browser = it
                            runtime.attendance.set(this@CefNappletSession, true)
                        }
                    }
                // An off-screen browser's component is a placeholder that is never shown.
                mutableState.value = State.Showing(if (offscreen != null) null else browser.uiComponent)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "${launch.identity.coordinate} did not open", e)
                mutableState.value = State.Failed(Failure.ENGINE)
            }
        }
    }

    private fun installHandlers(client: CefClient) {
        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig(queryFunction, "${queryFunction}Cancel"))
        router.addHandler(
            object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser,
                    frame: CefFrame,
                    queryId: Long,
                    request: String,
                    persistent: Boolean,
                    callback: CefQueryCallback,
                ): Boolean {
                    if (!frame.isMain || frame.url != NappletWebContract.SHELL_URL) {
                        Log.w(TAG) { "Refused a bridge call from ${frame.url.take(64)} (not the shell)" }
                        callback.failure(403, "Not the napplet shell")
                        return true
                    }
                    callback.success("")
                    handle(request)
                    return true
                }
            },
            true,
        )
        client.addMessageRouter(router)
        this.router = router

        client.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean {
                    if (isInternal(request.url) || isLocal(request.url)) return false
                    // A website's embeds (frames inside its own frame) are ordinary web pages.
                    if (isWebsite && !frame.isMain && frame.parent?.isMain == false && isWebLink(request.url)) return false
                    // The shell and the page's own frame never leave; a link the user clicked opens in
                    // the system browser, and only then.
                    if (launch.profile.opensLinks && userGesture && isWebLink(request.url)) DesktopBrowser.open(request.url)
                    return true
                }

                override fun getResourceRequestHandler(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef,
                ): CefResourceRequestHandler? =
                    when {
                        isLocal(request.url) -> null
                        isInternal(request.url) -> internalHandler
                        // A website talks to the web through the router's route; a napplet has no network.
                        isWebsite -> null
                        else -> cancelHandler
                    }
            },
        )
        client.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onAfterCreated(browser: CefBrowser) {
                    // Closing a browser Chromium hasn't made yet does nothing, and blocks every close
                    // after it: a close that came first runs now, or the page would run on unseen.
                    val teardown =
                        synchronized(lifecycle) {
                            created = true
                            pendingTeardown.also { pendingTeardown = null }
                        }
                    teardown?.let { SwingUtilities.invokeLater(it) }
                }

                override fun onBeforePopup(
                    browser: CefBrowser,
                    frame: CefFrame,
                    targetUrl: String,
                    targetFrameName: String?,
                ): Boolean {
                    if (launch.profile.opensLinks && isWebLink(targetUrl)) DesktopBrowser.open(targetUrl)
                    return true
                }
            },
        )
        client.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    browser: CefBrowser,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean,
                ) {
                    mutablePage.update { it.copy(isLoading = isLoading, canGoBack = canGoBack, canGoForward = canGoForward) }
                    if (!isLoading && mutablePage.value.zoomPercent != 100) applyZoom()
                }

                // A missing blob, a verify miss or a request the posture refused, so a site's author can
                // see what broke (Android's host logs these to its console panel).
                override fun onLoadError(
                    browser: CefBrowser,
                    frame: CefFrame,
                    errorCode: CefLoadHandler.ErrorCode,
                    errorText: String?,
                    failedUrl: String,
                ) {
                    if (errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) {
                        Log.w(TAG) { "${launch.identity.coordinate}: $errorCode loading ${failedUrl.take(120)} ($errorText)" }
                    }
                }
            },
        )
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    browser: CefBrowser,
                    level: CefSettings.LogSeverity,
                    message: String,
                    source: String,
                    line: Int,
                ): Boolean {
                    Log.d(TAG) { "${launch.identity.coordinate} console [$level] $message ($source:$line)" }
                    if (message.contains(WEBRTC_PRESENT)) {
                        Log.w(TAG) { "The napplet guard did not load; not starting ${launch.identity.coordinate}" }
                        mutableState.value = State.Failed(Failure.ENGINE)
                    }
                    return false
                }
            },
        )
    }

    /** Cancels a napplet's off-origin request before it leaves the machine. */
    private val cancelHandler =
        object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
            ): Boolean = true
        }

    /** Serves the shell and the launch's verified files from [server]; anything else internal is a 404. */
    private val internalHandler =
        object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
            ): CefResourceHandler {
                // A document navigation accepts text/html; a sub-resource (js/css/img) does not.
                val acceptsHtml =
                    request.resourceType == CefRequest.ResourceType.RT_MAIN_FRAME ||
                        request.resourceType == CefRequest.ResourceType.RT_SUB_FRAME ||
                        request.getHeaderByName("Accept")?.contains(NappletSiteServer.MIME_HTML, ignoreCase = true) == true
                return ServerResourceHandler(request.method, request.url, acceptsHtml)
            }
        }

    /**
     * One response from [server], produced off the IO thread CEF asks on (a miss downloads). A
     * request the server leaves to the browser can't go anywhere from an internal origin, so it is
     * a 404 here.
     */
    private inner class ServerResourceHandler(
        private val method: String,
        private val url: String,
        private val acceptsHtml: Boolean,
    ) : CefResourceHandlerAdapter() {
        private var response = BytesResourceHandler(NappletSiteServer.notFound())

        override fun processRequest(
            request: CefRequest,
            callback: CefCallback,
        ): Boolean {
            scope.launch(Dispatchers.IO) {
                try {
                    server.serve(method, url, acceptsHtml)?.let { response = BytesResourceHandler(it) }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Could not serve $url", e)
                } finally {
                    callback.Continue()
                }
            }
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) = this.response.getResponseHeaders(response, responseLength, redirectUrl)

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean = response.readResponse(dataOut, bytesToRead, bytesRead, callback)

        // Chromium gave up on the request (the page navigated away): close the file being streamed.
        override fun cancel() = response.cancel()
    }

    private fun handle(payload: String) {
        if (closed) return
        val arrivedAt = runtime.liveSubscriptions.arrival()
        scope.launch {
            val id = runCatching { NappletProtocolJson.readId(payload) }.getOrNull()
            when (val outcome = NappletRequestRouter.route(broker, launch.identity, launch.declared, payload)) {
                is NappletRequestRouter.Outcome.Reply -> send(withId(outcome.payload, id))
                is NappletRequestRouter.Outcome.Push -> outcome.payloads.forEach(::send)
                is NappletRequestRouter.Outcome.OpenSubscription ->
                    runtime.liveSubscriptions.open(this@CefNappletSession, outcome.subId, outcome.filters, account, arrivedAt, outcome.relays, outcome.outbox, outcome.mayDecrypt, ::send)
                is NappletRequestRouter.Outcome.CloseSubscription -> runtime.liveSubscriptions.close(this@CefNappletSession, outcome.subId, arrivedAt)
                // The inc bus is not offered on desktop (INC is not a supported NAP domain).
                else -> {}
            }
        }
    }

    /** Tells a napplet that declared THEME about the app's current colors. */
    fun pushTheme(colors: NappletThemeColors) {
        if (NappletCapability.THEME in launch.declared) send(NappletProtocolJson.encodeThemeChanged(colors.background, colors.text, colors.primary))
    }

    fun reload() {
        browser?.reload()
    }

    fun stop() {
        browser?.stopLoad()
    }

    fun goBack() {
        browser?.goBack()
    }

    fun goForward() {
        browser?.goForward()
    }

    /** Highlights [text] on the page; [forward] steps through the matches. An empty text clears it. */
    fun find(
        text: String,
        forward: Boolean = true,
        findNext: Boolean = false,
    ) {
        val browser = browser ?: return
        if (text.isEmpty()) browser.stopFinding(true) else browser.find(text, forward, false, findNext)
    }

    fun stopFinding() {
        browser?.stopFinding(true)
    }

    /** Page zoom as a percentage of normal size, like Android's text zoom. */
    fun setZoom(percent: Int) {
        val clamped = percent.coerceIn(MIN_ZOOM, MAX_ZOOM)
        mutablePage.update { it.copy(zoomPercent = clamped) }
        applyZoom()
    }

    /**
     * Chromium keeps one zoom level per host, and every window's shell is on `napplet.local`: setting
     * it zoomed every window. CSS zoom on the shell's frame scales this window's page alone (the frame
     * keeps filling the window; the page lays out narrower at a higher density), and is set again
     * whenever the shell loads.
     */
    private fun applyZoom() {
        val factor = mutablePage.value.zoomPercent / 100.0
        // Read back at once: a window behind another isn't laid out on its own, so the page wouldn't see
        // its new size until the window came forward.
        browser?.executeJavaScript("var f=document.getElementById('app');f.style.zoom='$factor';f.getBoundingClientRect();", NappletWebContract.SHELL_URL, 0)
    }

    private fun withId(
        payload: String,
        id: String?,
    ): String {
        if (id == null) return payload
        val result = parseJsonObjectOrNull(payload) ?: return payload
        return result.withString("id", id).toString()
    }

    private fun send(payload: String) {
        if (closed) return
        outbox += payload
        if (flushQueued.compareAndSet(false, true)) scope.launch { flush() }
    }

    /**
     * Hands the page everything [send] queued, in order, in one script: a burst (a subscription's
     * stored events) costs one trip to the renderer instead of one each.
     */
    private fun flush() {
        // Cleared before draining, so a payload queued meanwhile schedules a flush of its own.
        flushQueued.set(false)
        // Held through the call, so two flushes can't hand the page their batches out of order.
        synchronized(outbox) {
            val batch = StringBuilder()
            while (true) {
                val payload = outbox.poll() ?: break
                if (batch.isNotEmpty()) batch.append(',')
                batch.append(JsonPrimitive(payload).toString())
            }
            if (batch.isEmpty()) return
            browser?.executeJavaScript(
                "(function(m){var b=window.${NappletWebContract.BRIDGE_NAME};for(var i=0;i<m.length;i++)b.onmessage&&b.onmessage({data:m[i]});})([$batch]);",
                NappletWebContract.SHELL_URL,
                0,
            )
        }
    }

    fun close() {
        val client: CefClient?
        val browser: CefBrowser?
        val router: CefMessageRouter?
        val made: Boolean
        synchronized(lifecycle) {
            if (closed) return
            closed = true
            client = this.client
            browser = this.browser
            router = this.router
            this.client = null
            this.browser = null
            this.router = null
            made = created
        }
        offscreen?.browser = null
        files?.close()
        runtime.liveSubscriptions.closeAllFor(this)
        runtime.attendance.forget(this)
        if (isWebsite) runtime.routes.release(this)
        scope.cancel()
        // The cache is shared, so it is trimmed as windows close.
        runtime.trimBlobCache()
        // On the AWT thread, where the browser's component lives: a logout closes windows from a
        // background coroutine, and tearing Chromium down there raced the window still painting it.
        val closeBrowser = {
            Log.d(TAG) { "Closing the browser of ${launch.identity.coordinate}" }
            runCatching {
                // Without this, Chromium only asks the browser's window to close, and waits for it.
                browser?.setCloseAllowed()
                browser?.close(true)
                if (client != null && router != null) client.removeMessageRouter(router)
                router?.dispose()
                client?.dispose()
            }.onFailure { Log.w(TAG, "Browser did not close cleanly", it) }
            Unit
        }
        val teardown = {
            runCatching {
                // The page goes first, whatever Chromium then does with the close: a closed window's
                // browser was seen living on unseen, its game still running and playing. Dropping the
                // app's frame from the trusted shell ends its document, timers and sound at once.
                browser?.executeJavaScript(DROP_APP_FRAME, NappletWebContract.SHELL_URL, 0)
            }.onFailure { Log.w(TAG, "Could not drop the app's frame", it) }
            val component = browser?.uiComponent
            if (component != null && component.isDisplayable) closeOnceDetached(component, closeBrowser) else closeBrowser()
        }
        if (browser != null && !made) {
            // Asked for, not made yet: onAfterCreated runs the teardown, unless it ran meanwhile.
            val now =
                synchronized(lifecycle) {
                    if (created) {
                        true
                    } else {
                        pendingTeardown = teardown
                        false
                    }
                }
            if (!now) return
        }
        if (SwingUtilities.isEventDispatchThread()) teardown() else SwingUtilities.invokeLater(teardown)
    }

    /**
     * Closes the browser only after its window has taken its component down. Taking a live browser's
     * component out of a window is JCEF's ordinary hide and reparent; doing it to a closed one is not:
     * JCEF's listener sets the native view's visibility without checking whether the browser closed,
     * so a window disposed after [CefBrowser.close] made macOS touch a view Chromium had already freed,
     * and the app crashed on Close. A window that never goes still has its browser closed, later.
     */
    private fun closeOnceDetached(
        component: Component,
        closeBrowser: () -> Unit,
    ) {
        val done = AtomicBoolean(false)
        val closeOnce = {
            if (done.compareAndSet(false, true)) {
                // JCEF hid the view by queueing a call to the main thread that holds the view's raw
                // pointer and never checks that the browser still exists. Closing frees the view, so
                // the close waits until the main thread has run that call.
                SwingUtilities.invokeLater { if (OS.isMacintosh()) MacMainThread.afterQueuedWork(closeBrowser) else closeBrowser() }
            }
        }
        component.addHierarchyListener(
            object : HierarchyListener {
                override fun hierarchyChanged(e: HierarchyEvent) {
                    if (e.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() == 0L || component.isDisplayable) return
                    component.removeHierarchyListener(this)
                    closeOnce()
                }
            },
        )
        Timer(DETACH_WAIT_MS) { closeOnce() }.apply { isRepeats = false }.start()
    }

    /** A [NappletSiteServer.Response], to Chromium, streamed: a large script or wasm file isn't loaded whole. */
    private class BytesResourceHandler(
        private val served: NappletSiteServer.Response,
    ) : CefResourceHandlerAdapter() {
        private var body: InputStream? = null

        override fun processRequest(
            request: CefRequest,
            callback: CefCallback,
        ): Boolean {
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            response.status = served.status
            response.statusText = served.reason
            response.mimeType = served.mime
            // JCEF's response has no charset of its own.
            response.setHeaderByName("Content-Type", served.contentType, true)
            served.headers.forEach { (name, value) -> response.setHeaderByName(name, value, true) }
            responseLength.set(served.length.toInt())
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            val input = body ?: runCatching { served.open() }.getOrNull()?.also { body = it }
            val n = input?.let { runCatching { it.read(dataOut, 0, bytesToRead) }.getOrDefault(-1) } ?: -1
            if (n <= 0) {
                cancel()
                bytesRead.set(0)
                return false
            }
            bytesRead.set(n)
            return true
        }

        override fun cancel() {
            runCatching { body?.close() }
            body = null
        }
    }

    companion object {
        /** Run in the shell: removes the app's frame, ending everything the app had running. */
        private const val DROP_APP_FRAME = "var f=document.getElementById('app');if(f)f.remove();"

        private const val TAG = "CefNappletSession"

        // How long a closing window may take to drop its browser before the browser closes anyway.
        private const val DETACH_WAIT_MS = 2_000

        // A note is a small picture: no need for more, and every frame is copied.
        private const val OFFSCREEN_FRAME_RATE = 30
        private const val QUERY_FUNCTION_PREFIX = "__amethystNappletQuery_"
        const val MIN_ZOOM = 50
        const val MAX_ZOOM = 300

        private const val WEBRTC_PRESENT = "napplet-guard: WebRTC is present"

        /** Stops the shell before it starts the napplet when the guard extension did not take WebRTC away. */
        private const val WEBRTC_CHECK =
            "<script>if(typeof RTCPeerConnection!=='undefined'||typeof webkitRTCPeerConnection!=='undefined'){" +
                "window.stop();console.error('$WEBRTC_PRESENT');throw new Error('$WEBRTC_PRESENT');}</script>"

        /**
         * Whether [url] is ours: https on the shell host or a napplet.local subdomain. Read from the
         * text, not `java.net.URI`, which refuses URLs Chromium sends as they are (`{`, `|` in a
         * query) and would send such a request for the site's own origin out to the network.
         */
        fun isInternal(url: String): Boolean {
            if (!url.startsWith("https://")) return false
            val authority =
                url
                    .substring(8)
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
            if ('@' in authority) return false
            return NappletWebContract.isInternalHost(authority.substringBefore(':').lowercase())
        }

        private fun isWebLink(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

        /** A napplet's srcdoc document and the data/blob URLs a page makes itself: none leave the machine. */
        private fun isLocal(url: String): Boolean = url == "about:srcdoc" || url == "about:blank" || url.startsWith("data:") || url.startsWith("blob:")
    }
}
