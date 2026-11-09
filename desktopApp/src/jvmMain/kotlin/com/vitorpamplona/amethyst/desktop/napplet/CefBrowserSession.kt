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

import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.BrowserOriginPolicy
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import com.vitorpamplona.amethyst.desktop.app.DesktopBrowser
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
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.awt.Component
import java.util.concurrent.ConcurrentHashMap
import javax.swing.SwingUtilities
import kotlin.math.ln

/**
 * One in-app browser window: an ordinary web page in Chromium, with `window.nostr` (NIP-07) for the
 * pages that may have it, signed by the account that opened the window through the same consent the
 * napplets and nSites get.
 *
 * Desktop's counterpart of Android's `NappletBrowserActivity`:
 * - NIP-07 goes through [DesktopBrowserBridge] to the broker. Only the main frame may ask, only on an
 *   origin [BrowserOriginPolicy] allows (https, .onion, this machine), and each origin is its own app
 *   to the broker (`browser:<origin>`, the key Android's permission ledger uses), so a grant to one
 *   site is never another's.
 * - Every connection goes through [DesktopNappletRoutes], as an nSite's do: Tor or the open web, as
 *   the user chose for this host, and nothing while Tor is on and not running.
 * - Links in other schemes (`nostr:`, `lightning:`, `mailto:`) go to the system, on a click only.
 *   A page can't load napplet.local (the napplet hosts' origin), `file:` or Chromium's own pages.
 * - A new window the page opens (`target=_blank`, `window.open`) becomes another browser window, at
 *   most one a second.
 *
 * There is no renderer sandbox: see `commons/plans/2026-10-31-embed-security-review.md`.
 */
class CefBrowserSession(
    val startUrl: String,
    /** The account that opened it: the window keeps signing as this account, whoever is active later. */
    val accountPubKey: HexKey,
    /** Whether this window's traffic goes through Tor, from the user's choice for its host. */
    val useTor: Boolean,
    private val account: Account?,
    private val broker: NappletBroker,
    private val runtime: DesktopNappletRuntime,
    private val bridge: DesktopBrowserBridge,
    /** Opens a page this one asked for in a window of its own. */
    private val onPopup: (url: String) -> Unit = {},
    /** A page finished loading, for the browser's history. */
    private val onVisit: (url: String, title: String) -> Unit = { _, _ -> },
) {
    sealed interface State {
        data object Loading : State

        /** Its host goes through Tor, and it waits for Tor before anything is fetched. */
        data object WaitingForTor : State

        data class Showing(
            val component: Component,
        ) : State

        data class Failed(
            val reason: Failure,
        ) : State
    }

    enum class Failure {
        /** Chromium did not download or start. */
        ENGINE,

        /** Tor is on and isn't running: nothing may leave until it is. */
        ROUTE_BLOCKED,
    }

    /** What the window's toolbar shows about the page. */
    data class Page(
        val url: String,
        val title: String = "",
        val isLoading: Boolean = false,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val zoomPercent: Int = 100,
        /** Chromium found the page's certificate bad (the user may have gone past its warning). */
        val certificateError: Boolean = false,
    )

    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val mutablePage = MutableStateFlow(Page(startUrl))
    val page: StateFlow<Page> = mutablePage.asStateFlow()

    private val mutableApps = MutableStateFlow<Set<String>>(emptySet())

    /** Every `browser:<origin>` this window has asked the broker for, so its window shows their questions. */
    val apps: StateFlow<Set<String>> = mutableApps.asStateFlow()

    /** Whether this window's traffic goes through Tor right now (one on the open web can be forced). */
    val routedOverTor: StateFlow<Boolean> get() = runtime.routes.routedOverTor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: CefClient? = null
    private var browser: CefBrowser? = null

    @Volatile
    private var closed = false

    val isClosed: Boolean get() = closed

    private var created = false
    private var pendingTeardown: (() -> Unit)? = null
    private val lifecycle = Any()

    // The page on screen: its origin and the channel its pushes go to. Replaced as each document opens
    // its channel, and gone with it, which ends its live subscriptions.
    @Volatile
    private var document: BrowserDocument? = null

    // Non-persistent queries in flight, so a canceled one (the page went away) is answered no more.
    private val inFlight = ConcurrentHashMap<Long, Unit>()

    // Whether the main frame's last load failed: a failed address is not a visit.
    @Volatile
    private var mainFrameFailed = false

    private var lastPopupAt = 0L

    // Origins whose certificate Chromium refused in this window: the lock stays off for them, even
    // after the user goes past the warning.
    private val badCertificates = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        scope.launch {
            try {
                if (useTor && !runtime.routes.torRunning) mutableState.value = State.WaitingForTor
                val routed = runtime.routes.claim(this@CefBrowserSession, useTor, runtime.torWaitMs)
                if (closed) {
                    runtime.routes.release(this@CefBrowserSession)
                    return@launch
                }
                if (!routed) {
                    Log.w(TAG) { "Tor is not running; not opening a browser window" }
                    runtime.routes.release(this@CefBrowserSession)
                    mutableState.value = State.Failed(Failure.ROUTE_BLOCKED)
                    return@launch
                }
                mutableState.value = State.Loading
                val app = runtime.engine.app()
                val browser =
                    synchronized(lifecycle) {
                        if (closed) return@launch
                        val client = app.createClient()
                        this@CefBrowserSession.client = client
                        installHandlers(client)
                        client.createBrowser(startUrl, false, false).also { this@CefBrowserSession.browser = it }
                    }
                mutableState.value = State.Showing(browser.uiComponent)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "The browser window did not open", e)
                runtime.routes.release(this@CefBrowserSession)
                mutableState.value = State.Failed(Failure.ENGINE)
            }
        }
    }

    private fun installHandlers(client: CefClient) {
        client.addMessageRouter(bridge.router())
        client.addRequestHandler(
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean =
                    when (navigationOf(request.url, frame.isMain)) {
                        Navigation.LOAD -> false
                        Navigation.SYSTEM -> {
                            // Another app's link, on a click only: a page can't bounce the user out on load.
                            if (userGesture) DesktopBrowser.open(request.url)
                            true
                        }
                        Navigation.REFUSE -> true
                    }

                override fun getResourceRequestHandler(
                    browser: CefBrowser,
                    frame: CefFrame,
                    request: CefRequest,
                    isNavigation: Boolean,
                    isDownload: Boolean,
                    requestInitiator: String?,
                    disableDefaultHandling: BoolRef,
                ): CefResourceRequestHandler? = if (mayFetch(request.url)) null else cancelHandler

                // Chromium shows its own warning; this only keeps the address bar from claiming a lock.
                override fun onCertificateError(
                    browser: CefBrowser,
                    certError: CefLoadHandler.ErrorCode,
                    requestUrl: String,
                    callback: CefCallback,
                ): Boolean {
                    BrowserChrome.originOf(requestUrl)?.let { origin ->
                        badCertificates += origin
                        mutablePage.update { it.copy(certificateError = it.certificateError || BrowserChrome.originOf(it.url) == origin) }
                    }
                    return false
                }
            },
        )
        client.addLifeSpanHandler(
            object : CefLifeSpanHandlerAdapter() {
                override fun onAfterCreated(browser: CefBrowser) {
                    bridge.register(browser, this@CefBrowserSession)
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
                    when (navigationOf(targetUrl, isMainFrame = true)) {
                        Navigation.LOAD -> if (mayOpenPopup()) onPopup(targetUrl)
                        // A window.open of another app's link has no click to go with it here: refused.
                        Navigation.SYSTEM, Navigation.REFUSE -> Unit
                    }
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
                    if (isLoading) {
                        mainFrameFailed = false
                    } else {
                        val url = browser.url.orEmpty()
                        if (!mainFrameFailed && BrowserChrome.originOf(url) != null) onVisit(url, mutablePage.value.title)
                    }
                }

                override fun onLoadError(
                    browser: CefBrowser,
                    frame: CefFrame,
                    errorCode: CefLoadHandler.ErrorCode,
                    errorText: String?,
                    failedUrl: String,
                ) {
                    if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                    if (frame.isMain) mainFrameFailed = true
                    Log.d(TAG) { "$errorCode loading ${BrowserChrome.originOf(failedUrl) ?: "a page"} ($errorText)" }
                }
            },
        )
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onAddressChange(
                    browser: CefBrowser,
                    frame: CefFrame,
                    url: String,
                ) {
                    if (frame.isMain) mutablePage.update { it.copy(url = url, certificateError = BrowserChrome.originOf(url) in badCertificates) }
                }

                override fun onTitleChange(
                    browser: CefBrowser,
                    title: String?,
                ) {
                    mutablePage.update { it.copy(title = title.orEmpty()) }
                }
            },
        )
    }

    /** Cancels a request before it leaves the machine. */
    private val cancelHandler =
        object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(
                browser: CefBrowser,
                frame: CefFrame,
                request: CefRequest,
            ): Boolean = true
        }

    private fun mayOpenPopup(): Boolean =
        synchronized(lifecycle) {
            val now = System.currentTimeMillis()
            if (now - lastPopupAt < POPUP_INTERVAL_MS) return false
            lastPopupAt = now
            true
        }

    /** A query from [frame], through [DesktopBrowserBridge]. Always answers or keeps [callback]. */
    internal fun onQuery(
        frame: CefFrame,
        queryId: Long,
        request: String,
        persistent: Boolean,
        callback: CefQueryCallback,
    ) {
        if (closed) return callback.failure(410, "Closed")
        // Only the page itself, never a frame inside it, and only where a script on the path can't
        // speak for the site.
        if (!frame.isMain) return callback.failure(403, "Not the page")
        val origin = BrowserChrome.originOf(frame.url.orEmpty())
        if (origin == null || !BrowserOriginPolicy.offersNostr(origin)) return callback.failure(403, "No Nostr on this page")
        if (persistent) {
            if (request != DesktopBrowserBridge.CHANNEL_REQUEST) return callback.failure(400, "Unknown channel")
            openDocument(BrowserDocument(origin, queryId, callback))
            return
        }
        inFlight[queryId] = Unit
        handle(origin, queryId, request, callback)
    }

    internal fun onQueryCanceled(queryId: Long) {
        inFlight.remove(queryId)
        val current = document
        if (current != null && current.channelId == queryId) closeDocument(current)
    }

    private fun openDocument(next: BrowserDocument) {
        val previous = synchronized(lifecycle) { document.also { document = next } }
        previous?.let(::closeDocument)
        subscriptionAttendance.set(next, true)
    }

    private fun closeDocument(doc: BrowserDocument) {
        synchronized(lifecycle) { if (document === doc) document = null }
        doc.closed = true
        subscriptions.closeAllFor(doc)
        subscriptionAttendance.forget(doc)
    }

    private fun handle(
        origin: String,
        queryId: Long,
        payload: String,
        callback: CefQueryCallback,
    ) {
        val identity = identityOf(origin)
        mutableApps.update { it + identity.coordinate }
        val id = runCatching { NappletProtocolJson.readId(payload) }.getOrNull()
        val type = runCatching { NappletProtocolJson.readType(payload) }.getOrNull()
        val answer = { reply: String -> if (inFlight.remove(queryId) != null) runCatching { callback.success(reply) } }
        // A browser page declares neither: these only answer that they are unavailable.
        val edge = NappletRequestRouter.routeInc(DECLARED, payload, type) ?: NappletRequestRouter.routeIntent(DECLARED, payload, type)
        if (edge != null) {
            answer(if (edge is NappletRequestRouter.Outcome.Reply) withId(edge.payload, id) else "")
            return
        }
        // The document the request came from, if it is still the one on screen.
        val doc = document?.takeIf { it.origin == origin }
        val arrivedAt = subscriptions.arrival()
        scope.launch {
            try {
                when (val outcome = NappletRequestRouter.route(broker, identity, DECLARED, payload, null)) {
                    is NappletRequestRouter.Outcome.Reply -> answer(withId(outcome.payload, id))
                    is NappletRequestRouter.Outcome.Push -> {
                        answer("")
                        outcome.payloads.forEach { doc?.push(it) }
                    }
                    is NappletRequestRouter.Outcome.OpenSubscription -> {
                        answer("")
                        if (doc != null && !doc.closed) {
                            subscriptions.open(doc, outcome.subId, outcome.filters, account, arrivedAt, outcome.relays, outcome.outbox, outcome.mayDecrypt, doc::push)
                        }
                    }
                    is NappletRequestRouter.Outcome.CloseSubscription -> {
                        answer("")
                        doc?.let { subscriptions.close(it, outcome.subId, arrivedAt) }
                    }
                    else -> answer("")
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "A request from $origin failed", e)
                if (inFlight.remove(queryId) != null) runCatching { callback.failure(500, "Failed") }
            }
        }
    }

    private fun withId(
        payload: String,
        id: String?,
    ): String {
        if (id == null) return payload
        val result = parseJsonObjectOrNull(payload) ?: return payload
        return result.withString("id", id).toString()
    }

    /** Loads [url] in this window, when it is one a page may load. */
    fun load(url: String) {
        if (navigationOf(url, isMainFrame = true) != Navigation.LOAD) return
        browser?.loadURL(url)
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

    /** Page zoom, as Chrome's: Chromium keeps it per host, so other windows on the same host follow. */
    fun setZoom(percent: Int) {
        val clamped = percent.coerceIn(CefNappletSession.MIN_ZOOM, CefNappletSession.MAX_ZOOM)
        mutablePage.update { it.copy(zoomPercent = clamped) }
        browser?.zoomLevel = ln(clamped / 100.0) / ln(ZOOM_FACTOR_PER_LEVEL)
    }

    fun close() {
        val client: CefClient?
        val browser: CefBrowser?
        val made: Boolean
        synchronized(lifecycle) {
            if (closed) return
            closed = true
            client = this.client
            browser = this.browser
            this.client = null
            this.browser = null
            made = created
        }
        document?.let(::closeDocument)
        inFlight.clear()
        runtime.routes.release(this)
        scope.cancel()
        val teardown = {
            runCatching {
                browser?.let(bridge::unregister)
                browser?.setCloseAllowed()
                browser?.close(true)
                // The router is shared: it leaves this client before the client goes.
                client?.removeMessageRouter(bridge.router())
                client?.dispose()
            }.onFailure { Log.w(TAG, "Browser did not close cleanly", it) }
            Unit
        }
        if (browser != null && !made) {
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

    /** One document's channel: where its pushes go, until Chromium cancels it with the document. */
    class BrowserDocument(
        val origin: String,
        val channelId: Long,
        private val channel: CefQueryCallback,
    ) {
        @Volatile
        var closed = false

        fun push(payload: String) {
            if (!closed) runCatching { channel.success(payload) }
        }
    }

    private val subscriptionAttendance get() = runtime.browserAttendance
    private val subscriptions get() = runtime.browserSubscriptions

    enum class Navigation {
        /** A web page: load it here. */
        LOAD,

        /** Another app's link: the system opens it, on a click. */
        SYSTEM,

        /** Never: napplet.local, files, Chromium's own pages. */
        REFUSE,
    }

    companion object {
        private const val TAG = "CefBrowserSession"

        /** The broker's author for a web origin, as Android's broker names it. */
        const val BROWSER_AUTHOR = "browser"

        /** What a browser page may ask the broker for: NIP-07's set, as an nSite's. */
        val DECLARED: Set<NappletCapability> = NappletCapability.WEBSITE_CAPABILITIES

        private const val POPUP_INTERVAL_MS = 1_000L

        // Chromium's zoom level n is a scale of 1.2^n.
        private const val ZOOM_FACTOR_PER_LEVEL = 1.2

        /** The broker identity of a page at [origin]: one app per origin, as on Android. */
        fun identityOf(origin: String): NappletIdentity = NappletIdentity(authorPubKey = BROWSER_AUTHOR, identifier = origin)

        private val SYSTEM_SCHEMES = setOf("nostr", "lightning", "bitcoin", "mailto", "tel", "magnet", "cashu", "nostrconnect", "bunker")

        /**
         * What a browser window does with a navigation to [url]. Web pages load, except the napplet
         * hosts' own origin; the page's own data:, blob: and about: documents stay; a few app schemes
         * go to the system; everything else (file:, chrome:, view-source:) is refused.
         */
        fun navigationOf(
            url: String,
            isMainFrame: Boolean,
        ): Navigation {
            val scheme = url.substringBefore(':', "").lowercase()
            return when {
                scheme == "http" || scheme == "https" -> if (isInternalHost(url)) Navigation.REFUSE else Navigation.LOAD
                url == "about:blank" || url == "about:srcdoc" -> Navigation.LOAD
                // A page's own documents; a top-level data: page is a phishing tool Chrome blocks too.
                scheme == "blob" || (scheme == "data" && !isMainFrame) -> Navigation.LOAD
                scheme in SYSTEM_SCHEMES -> Navigation.SYSTEM
                else -> Navigation.REFUSE
            }
        }

        /** Whether a page's request for [url] may leave: the web, but never the napplet hosts' origin or a file. */
        fun mayFetch(url: String): Boolean {
            val scheme = url.substringBefore(':', "").lowercase()
            return when (scheme) {
                "http", "https" -> !isInternalHost(url)
                "data", "blob" -> true
                else -> false
            }
        }

        // napplet.local, by either scheme and whatever user info precedes it: no browser window serves
        // it, and none may reach it.
        private fun isInternalHost(url: String): Boolean {
            val authority =
                url
                    .substringAfter("://")
                    .substringBefore('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringAfterLast('@')
            val host = if (authority.startsWith('[')) authority.substringBefore(']') + "]" else authority.substringBefore(':')
            return NappletWebContract.isInternalHost(host.lowercase().trimEnd('.'))
        }
    }
}
