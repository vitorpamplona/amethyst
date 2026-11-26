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
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeColors
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.NappletWebResources
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.blobs.OkHttpNappletHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.PaperFiles
import com.vitorpamplona.amethyst.commons.napplet.fs.NappletFilePort
import com.vitorpamplona.amethyst.commons.napplet.openStream
import com.vitorpamplona.amethyst.commons.napplet.session.NappletGuard
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLinkOpener
import com.vitorpamplona.amethyst.commons.napplet.session.NappletNavigationPolicy
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRequestKind
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoutePort
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionServices
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.amethyst.commons.napplet.session.NappletWebEngine
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcFiles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcRuntime
import com.vitorpamplona.amethyst.desktop.app.DesktopBrowser
import com.vitorpamplona.amethyst.desktop.app.DesktopDeepLinks
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * One open napplet or nSite window on Chromium: the CEF adapter ([NappletWebEngine]) of the shared
 * [NappletSessionCore], which decides everything that isn't the engine's: the route, the files, the
 * guards, which requests and navigations the frames may make, the bridge to the broker, attendance
 * and the teardown order (see its KDoc for the trust model, which is Android's).
 *
 * What stays here is Chromium's own:
 * - The bridge is CEF's message router, which Chromium injects into every frame, under a random name
 *   this session's shell captures and deletes ([bridgeHead]); the core accepts only a query from the
 *   main frame at the shell URL.
 * - The WebRTC and cookie guards are the engine's extension ([DesktopCefEngine]) plus its
 *   `disable_non_proxied_udp` preference, which a site on Tor relies on; the shell's in-page check
 *   refuses to run when the extension did not load.
 * - Requests are served from the core's server through CEF resource handlers; a front page's art
 *   from [DesktopNappletRuntime.paperArt].
 * - Off-screen painting, frame rates, zoom, find, scrollbars, and closing only after the window let
 *   go of the browser's component.
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
    account: Account?,
    broker: NappletBroker,
    private val runtime: DesktopNappletRuntime,
    /**
     * Set for an app drawn by Compose (a napplet inside a note, or an app as a screen the navigation
     * layout places): its browser has no window and paints into this view, which the note or the
     * screen draws and forwards input to. Null for a window of its own.
     */
    val offscreen: OffscreenNappletView? = null,
    /** Frames a second an [offscreen] page paints: a note's small picture needs fewer than a screen. */
    private val frameRate: Int = OFFSCREEN_FRAME_RATE,
    /** The [Route.NostrApp] coordinate this app is the screen of; null for a window or a note. */
    val routeId: String? = null,
    /** Where NAP-FS picks and file bytes go: the system's file dialog, unless a test scripts them. */
    filePort: NappletFilePort = DesktopNappletFilePort(),
) {
    /** What the window's toolbar shows about the page. */
    data class Page(
        val isLoading: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val zoomPercent: Int = 100,
    )

    // This browser keeps one storage jar for everyone, so the account goes into a site's origin instead
    // of choosing a separate jar as Android does.
    private val plan = NappletLaunchPlan.of(launch, accountPubKey, useTor, runtime.nappletBlobsUseTor(), sharedStorageJar = true)

    private val core: NappletSessionCore<CefNappletSession> =
        NappletSessionCore(
            owner = this,
            launch = launch,
            accountPubKey = accountPubKey,
            plan = plan,
            account = account,
            broker = broker,
            services = NappletSessionServices(runtime.attendance, runtime.liveSubscriptions, runtime.incBus) { runtime.intents },
            route = Routes(),
            files = ::filesFor,
            shellHtml = { NappletWebResources.shellHtml() },
            shimJs = { NappletWebResources.shimJs().decodeToString() },
            links = Links,
            filePort = filePort,
            // A window is looked at for as long as it is open.
            attendedWhenShown = true,
            // The cache is shared, so it is trimmed as windows close.
            onClosed = runtime::trimBlobCache,
        ).also { it.engine = Engine() }

    val state: StateFlow<NappletSessionState> get() = core.state

    /** The download the page waits on while [NappletSessionState.Loading], for the loading screen's bar. */
    val download: StateFlow<NappletDownloadProgress?> get() = core.download

    private val mutablePage = MutableStateFlow(Page())
    val page: StateFlow<Page> = mutablePage.asStateFlow()

    /** Whether this window's traffic goes through Tor right now (a website on the open web can be forced). */
    val routedOverTor: StateFlow<Boolean> get() = runtime.routes.routedOverTor

    /** Whether [close] ran: the user stopped it, or the host did for an account that left. */
    val isClosed: Boolean get() = core.isClosed

    /** The browser's component, for a window of its own once it shows; null for an [offscreen] page. */
    val component: Component? get() = if (offscreen != null) null else browser?.uiComponent

    // For the front page's art, read or downloaded off the thread CEF asks on.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: CefClient? = null
    private var browser: CefBrowser? = null
    private var router: CefMessageRouter? = null

    // The engine's half of closing: set under lifecycle, with the browser's creation.
    @Volatile
    private var closed = false

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

    fun start() = core.start()

    private fun filesFor(plan: NappletLaunchPlan): NappletFiles {
        val webxdc = launch.webxdc
        val paper = launch.paper
        return if (paper != null) {
            PaperFiles(paper)
        } else if (webxdc != null) {
            WebxdcFiles(
                runtime.webxdcBundles,
                WebxdcFiles.downloadClient(runtime.httpClient(plan.blobsUseTor)),
                webxdc.url,
                webxdc.hash,
                launch.servers,
                WebxdcRuntime.script(webxdc.selfAddr, webxdc.selfName, webxdc.isAppSender),
            )
        } else {
            NappletBlobStore(
                runtime.blobCache.forSite(NappletBlobCache.partitionOf(accountPubKey, launch.identity.authorPubKey, launch.identity.identifier)),
                OkHttpNappletHttp(runtime.httpClient(plan.blobsUseTor)),
                launch.paths,
                launch.servers,
            )
        }
    }

    /** The window's route on [DesktopNappletRoutes]: one SOCKS route for every window, Tor always winning. */
    private inner class Routes : NappletRoutePort {
        override val torRunning: Boolean get() = runtime.routes.torRunning

        override suspend fun claim(useTor: Boolean): Boolean = runtime.routes.claim(this@CefNappletSession, useTor, runtime.torWaitMs)

        override suspend fun awaitTor(): Boolean = runtime.routes.awaitTor(runtime.torWaitMs)

        override fun release() = runtime.routes.release(this@CefNappletSession)
    }

    /**
     * A front page's own links (a citation, a byline, a stream) open in Amethyst, through the same queue
     * as an OS-opened nostr: link; everything else goes to the system browser.
     */
    private object Links : NappletLinkOpener {
        override fun openExternal(url: String) {
            DesktopBrowser.open(url)
        }

        override fun openNostr(uri: String) {
            DesktopDeepLinks.offer(uri)
        }
    }

    private inner class Engine : NappletWebEngine {
        // The engine's extension covers every napplet.local frame and every webxdc app, and its
        // `disable_non_proxied_udp` preference keeps a site's WebRTC on the proxy. The shell's check
        // (in the head the plan builds) refuses to run when the extension didn't load.
        override fun installGuard(guard: NappletGuard): Boolean = true

        // The object the shared shell expects at [NappletWebContract.BRIDGE_NAME]: `postMessage(String)`
        // out to the host, `onmessage({data})` in. The query function is captured and removed from the
        // shell's window, so nothing loaded later in the shell finds it there.
        override fun bridgeHead(): String =
            "<script>(function(){var q=window.$queryFunction;try{delete window.$queryFunction;}catch(_){}" +
                "window.${NappletWebContract.BRIDGE_NAME}={onmessage:null,postMessage:function(s){" +
                "q({request:String(s),persistent:false,onSuccess:function(){},onFailure:function(){}});}};})();</script>"

        override suspend fun loadShell(shellUrl: String): Boolean {
            val app = runtime.engine.app()
            // Created and published under the lock close() takes, so a window closed meanwhile never
            // leaves a browser behind.
            synchronized(lifecycle) {
                if (closed) return false
                val client = app.createClient()
                this@CefNappletSession.client = client
                installHandlers(client)
                val made =
                    if (offscreen != null) {
                        OffscreenCefBrowser(client, shellUrl, null, frameRate, offscreen).also {
                            offscreen.browser = it
                            it.createImmediately()
                        }
                    } else {
                        client.createBrowser(shellUrl, false, false)
                    }
                browser = made
                if (offscreen != null && !visible) made.setWindowlessFrameRate(HIDDEN_FRAME_RATE)
            }
            return true
        }

        override fun evaluateInShell(js: String) {
            browser?.executeJavaScript(js, NappletWebContract.SHELL_URL, 0)
        }

        override fun close() = closeBrowser()
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
                    if (core.onShellMessage(request, frame.isMain, frame.url)) {
                        callback.success("")
                    } else {
                        callback.failure(403, "Not the napplet shell")
                    }
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
                ): Boolean = !core.onNavigation(request.url, NappletNavigationPolicy.frameOf(frame.isMain, frame.parent?.isMain), userGesture)

                override fun getResourceRequestHandler(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef,
                ): CefResourceRequestHandler? =
                    when (core.classifyRequest(request.url, isImage = request.resourceType == CefRequest.ResourceType.RT_IMAGE)) {
                        NappletRequestKind.LOCAL, NappletRequestKind.NETWORK -> null
                        NappletRequestKind.SERVE -> internalHandler
                        NappletRequestKind.PAPER_ART -> paperArtHandler
                        NappletRequestKind.CANCEL -> cancelHandler
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
                    core.onPopup(targetUrl)
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

                // The app's colors on this frame's scrollbars, as it starts and once it has loaded.
                override fun onLoadStart(
                    browser: CefBrowser,
                    frame: CefFrame,
                    transitionType: CefRequest.TransitionType,
                ) = PageScrollbars.apply(frame)

                override fun onLoadEnd(
                    browser: CefBrowser,
                    frame: CefFrame,
                    httpStatusCode: Int,
                ) {
                    PageScrollbars.apply(frame)
                    // An nSite's next page starts unfocused even while it holds the keyboard.
                    if (frame.isMain) offscreen?.refocus()
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
                    // A link the reader clicked in a front page, which Chromium would not open itself.
                    if (launch.profile == HostProfile.PAPER) {
                        PaperLinkReports.parse(message, source)?.let { url ->
                            core.openLink(url)
                            return true
                        }
                    }
                    Log.d(TAG) { "${launch.identity.coordinate} console [$level] $message ($source:$line)" }
                    core.onConsoleMessage(message)
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

    /** Serves the shell and the launch's verified files from the core's server; anything else internal is a 404. */
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
                // Read now: the request is CEF's only for the length of this call.
                val method = request.method
                val url = request.url
                val mainFrame = request.resourceType == CefRequest.ResourceType.RT_MAIN_FRAME
                return AsyncResourceHandler(url) { core.serve(method, url, acceptsHtml, mainFrame) }
            }
        }

    /** Serves a front page's picture from [DesktopNappletRuntime.paperArt], downloading it on a miss. */
    private val paperArtHandler =
        object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
            ): CefResourceHandler {
                val url = request.url
                // With Tor on it is fetched only while Tor runs, the way the page's own traffic was: never around it.
                return AsyncResourceHandler(url) {
                    val bytes =
                        runtime.paperArt.get(url)
                            ?: if (useTor && !runtime.routes.torRunning) null else runtime.paperArt.load(url, runtime.httpClient(useTor))
                    // The app keeps it; Chromium wouldn't anyway.
                    bytes?.let { NappletSiteServer.Response(200, "OK", PaperArtCache.mimeOf(it), null, mapOf("Cache-Control" to "no-store"), it) }
                }
            }
        }

    /**
     * One response, produced off the IO thread CEF asks on (a miss downloads). Null, or a failure, is a
     * 404: a request from an internal origin can't go anywhere else.
     */
    private inner class AsyncResourceHandler(
        private val url: String,
        private val produce: suspend () -> NappletSiteServer.Response?,
    ) : CefResourceHandlerAdapter() {
        private var response = BytesResourceHandler(NappletSiteServer.notFound())

        override fun processRequest(
            request: CefRequest,
            callback: CefCallback,
        ): Boolean {
            ioScope.launch {
                try {
                    produce()?.let { response = BytesResourceHandler(it) }
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

    /** The app's current colors: on every frame's scrollbars, and to a napplet that declared THEME. */
    fun pushTheme(colors: NappletThemeColors) {
        browser?.let(PageScrollbars::apply)
        core.pushTheme(colors)
    }

    fun reload() {
        browser?.reload()
    }

    @Volatile
    private var visible = true

    /**
     * Whether an [offscreen] page's screen is on show. Out of sight (a tab behind another, a screen
     * under the one on top) it paints a frame a second instead of its [frameRate]: it keeps running.
     */
    fun setVisible(visible: Boolean) {
        this.visible = visible
        if (offscreen == null) return
        if (visible) {
            slowDown.stop()
            browser?.setWindowlessFrameRate(frameRate)
            offscreen.shown()
        } else {
            slowDown.restart()
        }
    }

    // Out of sight, the page slows to [HIDDEN_FRAME_RATE] a moment later, not at once: what it was
    // told as it left (its size put back, see [OffscreenNappletView.leave]) is painted at full rate,
    // so the frame waiting for the reader's return is the right one. At once, that frame came up to
    // two seconds later, and a quick return showed the one before it. AWT thread, like [setVisible].
    private val slowDown: Timer =
        Timer(HIDE_SLOWDOWN_MS) { if (!visible && !closed) browser?.setWindowlessFrameRate(HIDDEN_FRAME_RATE) }.apply { isRepeats = false }

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

    /** Closes the window's launch: the core releases its bridge, files and route, then [closeBrowser] runs. */
    fun close() = core.close()

    private fun closeBrowser() {
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
        ioScope.cancel()
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
                browser?.executeJavaScript(NappletWebContract.DROP_APP_FRAME_JS, NappletWebContract.SHELL_URL, 0)
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
            val input = body ?: runCatching { served.openStream() }.getOrNull()?.also { body = it }
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
        private const val TAG = "CefNappletSession"

        // How long a closing window may take to drop its browser before the browser closes anyway.
        private const val DETACH_WAIT_MS = 2_000

        // A note is a small picture: no need for more, and every frame is copied.
        private const val OFFSCREEN_FRAME_RATE = 30

        /** What an [offscreen] app paints as a screen of its own, like any app screen. */
        const val SCREEN_FRAME_RATE = 60
        private const val HIDDEN_FRAME_RATE = 1

        /** How long a hidden page keeps its full frame rate: see [slowDown]. */
        private const val HIDE_SLOWDOWN_MS = 1000
        private const val QUERY_FUNCTION_PREFIX = "__amethystNappletQuery_"
        const val MIN_ZOOM = 50
        const val MAX_ZOOM = 300
    }
}
