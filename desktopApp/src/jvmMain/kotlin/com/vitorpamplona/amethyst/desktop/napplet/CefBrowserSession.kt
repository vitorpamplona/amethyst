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
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.BrowserOriginPolicy
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletRequestRouter
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.desktop.app.DesktopBrowser
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefDevToolsClient
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.OffscreenCefBrowser
import org.cef.callback.CefBeforeDownloadCallback
import org.cef.callback.CefCallback
import org.cef.callback.CefDownloadItem
import org.cef.callback.CefDownloadItemCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefDownloadHandler
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * One in-app browser page: an ordinary web page in Chromium, with `window.nostr` (NIP-07) for the
 * pages that may have it, signed by the account that opened it through the same consent the
 * napplets and nSites get. It is the content of a [Route.WebPage] screen, which the navigation
 * layout places (stack, deck column, tab or window), so Chromium paints it off-screen ([view]) and
 * the screen draws it; the screen's ViewModel owns it, so it lives as long as the screen does.
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
 * - A new window the page opens (`target=_blank`, `window.open`) becomes another page screen, within
 *   the one popup budget every page shares ([BrowserPopupGate]).
 *
 * There is no renderer sandbox: see `commons/plans/2026-10-31-embed-security-review.md`.
 */
class CefBrowserSession(
    val startUrl: String,
    /** The [Route.WebPage] this page is the screen of. */
    val routeId: String,
    /** The account that opened it: the window keeps signing as this account, whoever is active later. */
    val accountPubKey: HexKey,
    /** Whether this window's traffic goes through Tor, from the user's choice for its host. */
    val useTor: Boolean,
    private val account: Account?,
    private val broker: NappletBroker,
    private val runtime: DesktopNappletRuntime,
    /**
     * A page asked for a new window ([url] is always a web page). The host decides whether one opens:
     * it holds the one budget every browser window shares, so a page can't multiply windows.
     */
    private val onPopup: (opener: CefBrowserSession, url: String) -> Unit = { _, _ -> },
    /** A page finished loading, for the browser's history. Called off Chromium's threads. */
    private val onVisit: (url: String, title: String) -> Unit = { _, _ -> },
    /** The page asked the broker as `browser:<origin>` [app]: this window shows that app's questions. */
    private val onAsk: (asker: CefBrowserSession, app: String) -> Unit = { _, _ -> },
    /** The user changed the zoom of [host]: Chromium applied it to every window on that host. */
    private val onZoom: (host: String, percent: Int) -> Unit = { _, _ -> },
) {
    sealed interface State {
        data object Loading : State

        /** Its host goes through Tor, and it waits for Tor before anything is fetched. */
        data object WaitingForTor : State

        /** The page is up, painting into [view]. */
        data object Showing : State

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

    /** The page's frames and input: the screen draws it, so it layers with the rest of the app. */
    val view = OffscreenNappletView()

    // Buffered, not a SharedFlow: a popup asked for while the screen is out of sight (nobody collecting)
    // waits for it instead of being dropped.
    private val popupQueue = Channel<Route.WebPage>(POPUP_QUEUE)

    /** Windows this page opened that the host let through: the screen opens each as a screen of its own. */
    val popups: Flow<Route.WebPage> = popupQueue.receiveAsFlow()

    /** Every `browser:<origin>` this window has asked the broker for, so its window shows their questions. */
    val apps: StateFlow<Set<String>> = mutableApps.asStateFlow()

    /** Whether this window's traffic goes through Tor right now (one on the open web can be forced). */
    val routedOverTor: StateFlow<Boolean> get() = runtime.routes.routedOverTor

    private val mutableDownloads = MutableStateFlow<List<BrowserDownload>>(emptyList())

    /**
     * Files the page started to download, oldest first: each waits for the user to save it
     * ([saveDownload]) or not ([cancelDownload]), then shows its progress until dismissed.
     */
    val downloads: StateFlow<List<BrowserDownload>> = mutableDownloads.asStateFlow()

    // Chromium's answers, held for the user: where to save each one, and how to stop it.
    private val downloadAnswers = ConcurrentHashMap<Int, CefBeforeDownloadCallback>()
    private val downloadControls = ConcurrentHashMap<Int, CefDownloadItemCallback>()
    private val declinedDownloads = ConcurrentHashMap.newKeySet<Int>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var client: CefClient? = null
    private var router: CefMessageRouter? = null
    private var browser: CefBrowser? = null
    private var devTools: CefDevToolsClient? = null

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

    // Whether the page on screen has reported its own title yet, so a visit isn't filed under the
    // previous page's.
    private val titleFresh = MutableStateFlow(false)

    // Origins whose certificate Chromium refused in this window: the lock stays off for them, even
    // after the user goes past the warning.
    private val badCertificates = ConcurrentHashMap.newKeySet<String>()

    private fun hadBadCertificate(url: String) = BrowserChrome.originOf(url)?.let { it in badCertificates } == true

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
                        // Blank, so nothing loads before the page's policies and scripts are in (prepare).
                        OffscreenCefBrowser(client, PLACEHOLDER_URL, runtime.engine.webContext(accountPubKey, useTor), FRAME_RATE, view).also {
                            this@CefBrowserSession.browser = it
                            view.browser = it
                            it.createImmediately()
                        }
                    }
                if (!visible) browser.setWindowlessFrameRate(HIDDEN_FRAME_RATE)
                mutableState.value = State.Showing
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.w(TAG, "The browser window did not open", e)
                runtime.routes.release(this@CefBrowserSession)
                mutableState.value = State.Failed(Failure.ENGINE)
            }
        }
    }

    /**
     * Before the page loads, on Chromium's UI thread: the account context's policies, then the
     * scripts every document runs first ([DesktopCefEngine.webPageScripts]), registered on a DevTools
     * session the page keeps (they last as long as it). A policy Chromium refuses, or scripts that
     * didn't register, fail the page.
     *
     * The page starts on a blank document ([PLACEHOLDER_URL]): DevTools needs a renderer to talk to,
     * and the address must not load before the scripts are in. Once they are and the blank page is
     * up, the blank page replaces itself with the address ([leavePlaceholder]), so it leaves no entry
     * in the page's history to go back to.
     */
    private fun prepare(browser: CefBrowser) {
        val refused = runtime.engine.applyPolicies(browser.requestContext)
        if (refused.isNotEmpty()) {
            mutableState.value = State.Failed(Failure.ENGINE)
            return
        }
        val devTools = browser.devToolsClient
        synchronized(lifecycle) {
            if (closed) return
            this.devTools = devTools
        }
        // All sent from here, in order: a DevTools call made from inside another's result trips
        // Chromium's observer re-entrancy check and takes the browser process down.
        val sent =
            listOf(devTools.executeDevToolsMethod("Page.enable")) +
                runtime.engine.webPageScripts.map { script ->
                    devTools.executeDevToolsMethod("Page.addScriptToEvaluateOnNewDocument", buildJsonObject { put("source", script) }.toString())
                }
        CompletableFuture.allOf(*sent.toTypedArray()).whenComplete { _, error ->
            if (error != null) {
                Log.w(TAG, "The page's scripts did not register", error)
                if (!closed) mutableState.value = State.Failed(Failure.ENGINE)
            } else {
                leavePlaceholder(browser)
            }
        }
    }

    // Two things the blank page waits for: its scripts registered, and itself loaded.
    private val placeholderWaits = AtomicInteger(2)
    private val placeholderLoaded = AtomicBoolean(false)

    private fun leavePlaceholder(browser: CefBrowser) {
        if (placeholderWaits.decrementAndGet() != 0 || closed) return
        browser.mainFrame.executeJavaScript("location.replace(${JsonPrimitive(startUrl)});", PLACEHOLDER_URL, 0)
    }

    private fun installHandlers(client: CefClient) {
        val router = DesktopBrowserBridge.newRouter(queries)
        this.router = router
        client.addMessageRouter(router)
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

                // A link opened in a new tab (Ctrl-click): a screen of its own, as a popup.
                override fun onOpenURLFromTab(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    targetUrl: String?,
                    userGesture: Boolean,
                ): Boolean {
                    if (userGesture && targetUrl != null) requestWindow(targetUrl)
                    return true
                }

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
                    val teardown =
                        synchronized(lifecycle) {
                            created = true
                            pendingTeardown.also { pendingTeardown = null }
                        }
                    if (teardown != null) SwingUtilities.invokeLater(teardown) else prepare(browser)
                }

                override fun onBeforePopup(
                    browser: CefBrowser,
                    frame: CefFrame,
                    targetUrl: String,
                    targetFrameName: String?,
                ): Boolean {
                    // Only a web page becomes a window. A blank or blob: popup is one the opener means to
                    // write into, which a separate window can't take (it has no window.opener), and a
                    // window.open of another app's link has no click to go with it here.
                    requestWindow(targetUrl)
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
                        syncZoom(browser)
                        val url = browser.url.orEmpty()
                        if (!mainFrameFailed && BrowserChrome.originOf(url) != null) recordVisit(url)
                    }
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
                    if (frame.isMain && frame.url == PLACEHOLDER_URL && !placeholderLoaded.getAndSet(true)) leavePlaceholder(browser)
                    PageScrollbars.apply(frame)
                }

                override fun onLoadError(
                    browser: CefBrowser,
                    frame: CefFrame,
                    errorCode: CefLoadHandler.ErrorCode,
                    errorText: String?,
                    failedUrl: String,
                ) {
                    if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                    if (frame.isMain) {
                        mainFrameFailed = true
                        // Chromium's error and certificate pages report no address of their own: the
                        // bar shows the address that failed, and whether its certificate did.
                        if (BrowserChrome.originOf(failedUrl) != null) {
                            mutablePage.update { it.copy(url = failedUrl, certificateError = hadBadCertificate(failedUrl)) }
                        }
                    }
                    Log.d(TAG) { "$errorCode loading ${BrowserChrome.originOf(failedUrl) ?: "a page"} ($errorText)" }
                }
            },
        )
        client.addDownloadHandler(
            object : CefDownloadHandler {
                // Nothing is written yet: the user says where it goes, or that it doesn't.
                override fun onBeforeDownload(
                    browser: CefBrowser,
                    item: CefDownloadItem,
                    suggestedName: String,
                    callback: CefBeforeDownloadCallback,
                ): Boolean {
                    val id = item.id
                    // A page can start downloads without end; past a few unanswered, the rest are
                    // refused without asking.
                    if (downloadAnswers.size >= MAX_DOWNLOAD_QUESTIONS) {
                        declinedDownloads += id
                        return true
                    }
                    downloadAnswers[id] = callback
                    val download =
                        BrowserDownload(
                            id = id,
                            name = BrowserDownloads.safeName(suggestedName),
                            host = BrowserChrome.displayHost(item.url.orEmpty()),
                            totalBytes = item.totalBytes.takeIf { it > 0 } ?: -1,
                            state = BrowserDownload.State.ASKING,
                        )
                    mutableDownloads.update { list -> (list.filter { it.id != id } + download).takeLast(MAX_DOWNLOADS_SHOWN) }
                    return true
                }

                override fun onDownloadUpdated(
                    browser: CefBrowser,
                    item: CefDownloadItem,
                    callback: CefDownloadItemCallback,
                ) {
                    val id = item.id
                    val ended = item.isComplete || !item.isInProgress
                    if (id in declinedDownloads) {
                        if (ended) declinedDownloads -= id else callback.cancel()
                        return
                    }
                    if (ended) downloadControls.remove(id) else downloadControls[id] = callback
                    val percent = item.percentComplete.coerceIn(0, 100)
                    val total = item.totalBytes.takeIf { it > 0 } ?: -1
                    mutableDownloads.update { list ->
                        list.map { download ->
                            when {
                                download.id != id -> download
                                // Chromium reports on it while it waits for the user: only the size.
                                download.state == BrowserDownload.State.ASKING -> download.copy(totalBytes = total)
                                item.isComplete -> download.copy(state = BrowserDownload.State.DONE, percent = 100, totalBytes = total)
                                ended -> download.copy(state = BrowserDownload.State.STOPPED)
                                else -> download.copy(percent = percent, totalBytes = total)
                            }
                        }
                    }
                    if (ended) {
                        mutableDownloads.value
                            .find { it.id == id }
                            ?.file
                            ?.let(BrowserDownloads::release)
                    }
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
                    // The blank page it starts on, while prepare() sets it up, isn't the page.
                    if (!frame.isMain || url == PLACEHOLDER_URL) return
                    val previous = mutablePage.value.url
                    mutablePage.update { it.copy(url = url, certificateError = hadBadCertificate(url)) }
                    if (url != previous) titleFresh.value = false
                    // Another host may have its own zoom.
                    if (OmniboxInput.hostOf(url) != OmniboxInput.hostOf(previous)) syncZoom(browser)
                }

                override fun onTitleChange(
                    browser: CefBrowser,
                    title: String?,
                ) {
                    if (browser.url == PLACEHOLDER_URL) return
                    mutablePage.update { it.copy(title = title.orEmpty()) }
                    titleFresh.value = true
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

    /**
     * Files the visit once the page has said what it is called (or soon after, if it never does), off
     * Chromium's thread: the history rewrites its whole list on each one.
     */
    private fun recordVisit(url: String) {
        scope.launch {
            withTimeoutOrNull(TITLE_WAIT_MS) { titleFresh.first { it } }
            val page = mutablePage.value
            onVisit(url, if (titleFresh.value && page.url == url) page.title else "")
        }
    }

    /** Reads the zoom Chromium applies to this page's host: it is per host, not per window. */
    private fun syncZoom(browser: CefBrowser) {
        val level = runCatching { browser.zoomLevel }.getOrNull() ?: return
        refreshZoom((ZOOM_FACTOR_PER_LEVEL.pow(level) * 100).roundToInt())
    }

    /** Whether this window's page is on [host]. */
    fun isOnHost(host: String): Boolean = OmniboxInput.hostOf(mutablePage.value.url).equals(host, ignoreCase = true)

    /** The zoom of this page's host changed in another window. */
    fun refreshZoom(percent: Int) {
        mutablePage.update { it.copy(zoomPercent = percent.coerceIn(CefNappletSession.MIN_ZOOM, CefNappletSession.MAX_ZOOM)) }
    }

    private val queries =
        object : DesktopBrowserBridge.Handler {
            override fun onQuery(
                frame: CefFrame,
                queryId: Long,
                request: String,
                persistent: Boolean,
                callback: CefQueryCallback,
            ) = this@CefBrowserSession.onQuery(frame, queryId, request, persistent, callback)

            override fun onQueryCanceled(queryId: Long) = this@CefBrowserSession.onQueryCanceled(queryId)
        }

    /** A query from [frame], through [DesktopBrowserBridge]. Always answers or keeps [callback]. */
    private fun onQuery(
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
            // The document is named once, as it opens its channel, when the frame can only be showing
            // it. Its first message is its token, which each of its requests carries.
            val doc = BrowserDocument(origin, queryId, UUID.randomUUID().toString(), callback)
            openDocument(doc)
            runCatching { callback.success(doc.token) }
            return
        }
        // A request belongs to the document whose token it carries, judged by that document's origin:
        // never by where the frame is by the time it is read. One from a page already navigating away
        // (whose token is gone, or whose frame now shows another site) is refused.
        val token = request.substringBefore('\n', "")
        val doc = document
        if (doc == null || doc.closed || token != doc.token) return callback.failure(403, "Unknown document")
        if (origin != doc.origin) return callback.failure(403, "The page moved")
        val payload = request.substringAfter('\n')
        if (payload.startsWith(OPEN_WINDOW)) {
            requestWindow(payload.removePrefix(OPEN_WINDOW))
            return callback.success("")
        }
        inFlight[queryId] = Unit
        handle(doc, queryId, payload, callback)
    }

    private fun onQueryCanceled(queryId: Long) {
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
        doc: BrowserDocument,
        queryId: Long,
        payload: String,
        callback: CefQueryCallback,
    ) {
        val origin = doc.origin
        val identity = identityOf(origin)
        if (identity.coordinate !in mutableApps.value) mutableApps.update { it + identity.coordinate }
        onAsk(this, identity.coordinate)
        val id = runCatching { NappletProtocolJson.readId(payload) }.getOrNull()
        val type = runCatching { NappletProtocolJson.readType(payload) }.getOrNull()
        val answer = { reply: String -> if (inFlight.remove(queryId) != null) runCatching { callback.success(reply) } }
        // A browser page declares neither: these only answer that they are unavailable.
        val edge = NappletRequestRouter.routeInc(DECLARED, payload, type) ?: NappletRequestRouter.routeIntent(DECLARED, payload, type)
        if (edge != null) {
            answer(if (edge is NappletRequestRouter.Outcome.Reply) withReplyId(edge.payload, id) else "")
            return
        }
        val arrivedAt = subscriptions.arrival()
        scope.launch {
            try {
                when (val outcome = NappletRequestRouter.route(broker, identity, DECLARED, payload, null)) {
                    is NappletRequestRouter.Outcome.Reply -> answer(withReplyId(outcome.payload, id))
                    is NappletRequestRouter.Outcome.Push -> {
                        answer("")
                        outcome.payloads.forEach(doc::push)
                    }
                    is NappletRequestRouter.Outcome.OpenSubscription -> {
                        answer("")
                        if (!doc.closed) {
                            subscriptions.open(doc, outcome.subId, outcome.filters, account, arrivedAt, outcome.relays, outcome.outbox, outcome.mayDecrypt, doc::push)
                        }
                    }
                    is NappletRequestRouter.Outcome.CloseSubscription -> {
                        answer("")
                        subscriptions.close(doc, outcome.subId, arrivedAt)
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

    /** Loads [url] in this window, when it is one a page may load. */
    fun load(url: String) {
        if (navigationOf(url, isMainFrame = true) != Navigation.LOAD) return
        browser?.loadURL(url)
    }

    /** The app's current colors on every frame's scrollbars ([PageScrollbars.update] set them). */
    fun restyleScrollbars() {
        browser?.let(PageScrollbars::apply)
    }

    /** The user said yes to download [id]: it goes to the Downloads folder, under a name of its own. */
    fun saveDownload(id: Int) {
        val callback = downloadAnswers.remove(id) ?: return
        val download = mutableDownloads.value.find { it.id == id } ?: return
        val target = BrowserDownloads.reserve(BrowserDownloads.folder(), download.name)
        mutableDownloads.update { list -> list.map { if (it.id == id) it.copy(state = BrowserDownload.State.DOWNLOADING, file = target) else it } }
        callback.Continue(target.absolutePath, false)
    }

    /** The user said no to download [id], or stopped it: nothing of it stays. */
    fun cancelDownload(id: Int) {
        if (downloadAnswers.remove(id) != null) {
            // Chromium cancels it on its next report.
            declinedDownloads += id
            mutableDownloads.update { list -> list.filter { it.id != id } }
        }
        downloadControls.remove(id)?.cancel()
    }

    /** Takes a finished download off the list. */
    fun dismissDownload(id: Int) {
        val finished = setOf(BrowserDownload.State.DONE, BrowserDownload.State.STOPPED)
        mutableDownloads.update { list -> list.filter { it.id != id || it.state !in finished } }
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

    /**
     * The page asked for a new window at [url]: only a web page becomes one, and only as the host's
     * budget allows. Chromium refuses an off-screen page's own new windows before asking, so the
     * content script asks over the bridge ([OPEN_WINDOW]) for `window.open` and `target=_blank` links.
     */
    private fun requestWindow(url: String) {
        if (closed) return
        if (navigationOf(url, isMainFrame = true) == Navigation.LOAD && BrowserChrome.originOf(url) != null) onPopup(this, url)
    }

    /** The host let a window this page asked for open: the screen opens [route]. */
    fun requestPopup(route: Route.WebPage) {
        popupQueue.trySend(route)
    }

    @Volatile
    private var visible = true

    /**
     * Whether the screen is on show. Out of sight (a tab behind another, a screen under the one on
     * top) the page paints a frame a second instead of [FRAME_RATE]: it keeps running, unseen.
     */
    fun setVisible(visible: Boolean) {
        this.visible = visible
        browser?.setWindowlessFrameRate(if (visible) FRAME_RATE else HIDDEN_FRAME_RATE)
    }

    fun stopFinding() {
        browser?.stopFinding(true)
    }

    /** Page zoom, as Chrome's: Chromium keeps it per host, so other windows on the same host follow. */
    fun setZoom(percent: Int) {
        val clamped = percent.coerceIn(CefNappletSession.MIN_ZOOM, CefNappletSession.MAX_ZOOM)
        mutablePage.update { it.copy(zoomPercent = clamped) }
        browser?.zoomLevel = ln(clamped / 100.0) / ln(ZOOM_FACTOR_PER_LEVEL)
        OmniboxInput.hostOf(mutablePage.value.url)?.let { onZoom(it.lowercase(), clamped) }
    }

    fun close() {
        val client: CefClient?
        val browser: CefBrowser?
        val router: CefMessageRouter?
        val devTools: CefDevToolsClient?
        val made: Boolean
        synchronized(lifecycle) {
            if (closed) return
            closed = true
            client = this.client
            browser = this.browser
            router = this.router
            devTools = this.devTools
            this.devTools = null
            this.client = null
            this.browser = null
            this.router = null
            made = created
        }
        view.browser = null
        document?.let(::closeDocument)
        inFlight.clear()
        // Unanswered downloads are answered no; Chromium stops the rest with the page.
        declinedDownloads += downloadAnswers.keys
        downloadAnswers.clear()
        mutableDownloads.value.forEach { download -> download.file?.takeIf { download.state == BrowserDownload.State.DOWNLOADING }?.let(BrowserDownloads::release) }
        runtime.routes.release(this)
        scope.cancel()
        popupQueue.close()
        // Each step on its own: one that throws must not leave the shared router on a client, or the
        // client alive.
        val teardown = {
            fun step(
                name: String,
                block: () -> Unit,
            ) = runCatching(block).onFailure { Log.w(TAG, "Browser did not $name cleanly", it) }
            step("close DevTools") { devTools?.close() }
            step("close") {
                browser?.setCloseAllowed()
                browser?.close(true)
            }
            step("release the router") {
                if (client != null && router != null) client.removeMessageRouter(router)
                router?.dispose()
            }
            step("dispose") { client?.dispose() }
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
        /** Minted by the host as the channel opens; the document's requests carry it. */
        val token: String,
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

        /** A request the content script sends for a new window, followed by its address. */
        const val OPEN_WINDOW = "__amethyst.open:"

        /** The broker's author for a web origin, as Android's broker names it. */
        const val BROWSER_AUTHOR = "browser"

        /** What a browser page may ask the broker for: NIP-07's set, as an nSite's. */
        val DECLARED: Set<NappletCapability> = NappletCapability.WEBSITE_CAPABILITIES

        // A screen's page paints like any app screen; one out of sight barely at all.
        private const val FRAME_RATE = 60

        /** Downloads a page may have waiting for an answer at once; more are refused unasked. */
        private const val MAX_DOWNLOAD_QUESTIONS = 3

        /** Downloads the screen lists at once, newest kept. */
        private const val MAX_DOWNLOADS_SHOWN = 5

        /** What a page starts on, so DevTools has a renderer to set it up in before its address loads. */
        private const val PLACEHOLDER_URL = "about:blank"
        private const val HIDDEN_FRAME_RATE = 1

        // Popups waiting for the screen; the host's budget keeps it at a handful anyway.
        private const val POPUP_QUEUE = 8

        // How long a finished page may take to name itself before its visit is filed without a title.
        private const val TITLE_WAIT_MS = 1_500L

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
        private fun isInternalHost(url: String): Boolean = NappletWebContract.isInternalHost(OmniboxInput.hostOf(url)?.lowercase()?.trimEnd('.'))
    }
}
