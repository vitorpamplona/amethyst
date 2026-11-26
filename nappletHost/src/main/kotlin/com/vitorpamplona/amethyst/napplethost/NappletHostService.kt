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
package com.vitorpamplona.amethyst.napplethost

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.privacysandbox.ui.provider.toCoreLibInfo
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.session.NappletFailure
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionState
import com.vitorpamplona.amethyst.commons.util.booleanOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.json.JsonObject

/**
 * Provider for an **embedded** nsite/napplet tab — the in-app-tab counterpart of [NappletHostActivity].
 * Runs in the keyless `:napplet` process: each tab is a [NappletSandboxSession] (the shared session core
 * over a WebView, with the main-process broker as its trusted half), exposed to the main app as a
 * `SandboxedUiAdapter` so only pixels + input cross the process boundary — never the keys, which live
 * only in the main process where every brokered op is still consent-gated.
 *
 * Shares [NappletHostActivity]'s session; differs only in being a windowless Service whose surface the
 * main app embeds (vs. an Activity that owns the screen). The trusted chrome, the loading bar and the
 * errors are drawn by the main process around the surface. Requires API 30+ (SurfaceControlViewHost).
 */
@RequiresApi(Build.VERSION_CODES.R)
class NappletHostService : Service() {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onClientMessage))

    /**
     * Per-embedded-tab state. A single NappletHostService instance is shared by every embedded
     * napplet/nSite tab (they bind the same Intent), so each tab's launch, session and client live here,
     * keyed by [sessionId]. Each session has its OWN reply Messenger, so broker responses AND unsolicited
     * pushes come back to the right one (the broker echoes replyTo).
     */
    private inner class NappletTab(
        val sessionId: String,
        var clientMessenger: Messenger?,
        val config: NappletSandboxLaunch,
        // Tor's port, re-read by the client on a retry: it may have come up (or moved) since the tab was made.
        var proxyPort: Int,
        val bgColor: Int,
    ) {
        // The launch running in the surface; replaced by a retry once it failed.
        var session: NappletSandboxSession? = null

        // The session's root view; a session that failed is rebuilt inside it on retry.
        var container: FrameLayout? = null

        // The client's last pause/resume. A parked tab is paused before its WebView exists (the WebView is
        // only built when the surface opens), so the flag is applied to every WebView built for the tab.
        var paused = false

        // Whether the user is looking at this napplet (NappletEmbedContract.MSG_SET_ATTENDED). Requests that act
        // for the user (publish, pay, upload…) made while they aren't — or while the page is paused — are held
        // by the session and sent when they're back: pausing the WebView doesn't stop JavaScript. Unattended
        // until the client says otherwise: it sends its state right after every create.
        var attended = false

        val mayAct: Boolean get() = attended && !paused

        // The in-flight `<input type="file">` pick for this surface. The picker itself runs in the main
        // process (this provider is windowless), so the callback waits here for MSG_FILE_CHOOSER_RESULT.
        val fileChooser = PendingFileChooser()

        // Last main-frame error state, pushed to the client so it can show an error/retry overlay over the
        // surface (the embedded surface has no error page of its own).
        var loadFailed = false

        // The user's text size, re-applied when a retry builds a fresh WebView.
        var textZoom = BrowserChrome.DEFAULT_TEXT_ZOOM

        fun liveWebView(): WebView? = session?.engine?.takeIf { !it.isGone }?.webView

        /**
         * Sends [msg] to this tab's client stamped with the session it belongs to, so the client can drop what
         * a session it has since replaced still had in flight (a late file-chooser request, a stale
         * SESSION_READY that would re-arm its view with a dead adapter). Returns whether it was delivered.
         */
        fun toClient(msg: Message): Boolean {
            val client = clientMessenger ?: return false
            msg.data.putString(NappletEmbedContract.KEY_SESSION_ID, sessionId)
            return runCatching { client.send(msg) }.isSuccess
        }
    }

    private val tabs = mutableMapOf<String, NappletTab>()

    // ---- broker bridge: ONE binding shared by all tabs; per-message replyTo scopes the routing ----
    private var brokerMessenger: Messenger? = null
    private var brokerBound = false
    private val pendingBrokerRequests = mutableListOf<Message>()

    private val brokerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                brokerMessenger = Messenger(service)
                pendingBrokerRequests.forEach { sendToBroker(it) }
                pendingBrokerRequests.clear()
                // A broker that restarted knows nothing about who is watching.
                tabs.values.forEach { it.session?.onBrokerConnected() }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brokerMessenger = null
            }
        }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onDestroy() {
        // Release before unbinding, while the broker can still hear it.
        tabs.values.toList().forEach(::closeTab)
        tabs.clear()
        if (brokerBound) {
            runCatching { unbindService(brokerConnection) }
            brokerBound = false
        }
        super.onDestroy()
    }

    private fun tabFor(msg: Message): NappletTab? = msg.data?.getString(NappletEmbedContract.KEY_SESSION_ID)?.let { tabs[it] }

    private fun onClientMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletEmbedContract.MSG_CREATE_SESSION -> {
                val tab = buildTab(msg) ?: return true
                // A re-sent create for an id that is still live must not strand the old tab's WebView, files
                // and broker state with nothing left to close them.
                tabs[tab.sessionId]?.let(::closeTab)
                tabs[tab.sessionId] = tab
                // Bind the broker once; a re-sent MSG_CREATE_SESSION must not leak a second binding.
                if (!brokerBound) {
                    brokerBound = bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
                }
                replyWithAdapter(tab)
            }
            NappletEmbedContract.MSG_BACK -> tabFor(msg)?.liveWebView()?.let { if (it.canGoBack()) it.goBack() }
            NappletEmbedContract.MSG_RELOAD -> {
                val tab = tabFor(msg) ?: return true
                // The client re-reads Tor's port on a retry: it may have come up (or moved) since the tab was made.
                msg.data
                    ?.getInt(NappletHostContract.EXTRA_PROXY_PORT, 0)
                    ?.takeIf { it != 0 }
                    ?.let { tab.proxyPort = it }
                val container = tab.container
                val session = tab.session
                // A launch that failed (no route, no files, a dead renderer) starts over in the same surface.
                if ((session == null || session.isFailed) && container != null) {
                    val wv = createHostWebView(container.context, tab.sessionId, container)
                    container.addView(wv, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                } else {
                    tab.liveWebView()?.reload()
                }
            }
            // onPause()/onResume() are per-WebView (pause/resume THIS surface's JS/DOM). Do NOT call
            // pauseTimers()/resumeTimers(): they are process-global and would freeze/thaw every WebView in
            // `:napplet` (the browser embed + other napplets), whose lifecycles are independent of this one.
            NappletEmbedContract.MSG_CLOSE_SESSION -> tabFor(msg)?.let(::closeTab)
            NappletEmbedContract.MSG_PAUSE ->
                tabFor(msg)?.let {
                    it.paused = true
                    it.liveWebView()?.onPause()
                    it.session?.setAttended(it.mayAct)
                }
            NappletEmbedContract.MSG_RESUME ->
                tabFor(msg)?.let {
                    it.paused = false
                    it.liveWebView()?.onResume()
                    it.session?.setAttended(it.mayAct)
                }
            NappletEmbedContract.MSG_SET_ATTENDED ->
                tabFor(msg)?.let {
                    it.attended = msg.data?.getBoolean(NappletEmbedContract.KEY_ATTENDED, false) ?: false
                    it.session?.setAttended(it.mayAct)
                }
            NappletEmbedContract.MSG_IME_OP -> {
                val tab = tabFor(msg) ?: return true
                val payload = msg.data?.getString(NappletEmbedContract.KEY_IME_PAYLOAD) ?: return true
                // The proxy can belong to a page that has already gone away; that must not crash the sandbox.
                tab.session?.postToShell(payload)
            }
            NappletEmbedContract.MSG_MAGNIFIER_REQUEST -> onMagnifierRequest(msg)
            NappletEmbedContract.MSG_FIND -> {
                val wv = tabFor(msg)?.liveWebView() ?: return true
                val query = msg.data?.getString(NappletEmbedContract.KEY_FIND_QUERY).orEmpty()
                if (query.isEmpty()) wv.clearMatches() else wv.findAllAsync(query)
            }
            NappletEmbedContract.MSG_FIND_NEXT -> tabFor(msg)?.liveWebView()?.findNext(msg.data?.getBoolean(NappletEmbedContract.KEY_FIND_FORWARD, true) ?: true)
            NappletEmbedContract.MSG_SET_TEXT_ZOOM -> {
                val tab = tabFor(msg) ?: return true
                tab.textZoom = msg.data?.getInt(NappletEmbedContract.KEY_TEXT_ZOOM, tab.textZoom) ?: tab.textZoom
                tab.liveWebView()?.let { BrowserWebTools.setTextZoom(it, tab.textZoom) }
            }
            NappletEmbedContract.MSG_FILE_CHOOSER_RESULT -> {
                val tab = tabFor(msg) ?: return true
                val data = msg.data ?: return true
                val id = data.getLong(NappletEmbedContract.KEY_FILE_CHOOSER_ID)
                // Absent array = the user cancelled; PendingFileChooser turns that into the null the page
                // needs to see so its file input becomes tappable again.
                val uris = data.getStringArray(NappletEmbedContract.KEY_FILE_CHOOSER_URIS)?.map(String::toUri)
                tab.fileChooser.deliver(id, uris?.toTypedArray())
            }
            else -> return false
        }
        return true
    }

    private fun buildTab(msg: Message): NappletTab? {
        val data = msg.data ?: return null
        val sessionId = data.getString(NappletEmbedContract.KEY_SESSION_ID) ?: return null
        val config = NappletSandboxLaunch.from(data) ?: return null
        return NappletTab(
            sessionId = sessionId,
            clientMessenger = msg.replyTo,
            config = config,
            proxyPort = config.proxyPort,
            bgColor = data.getInt(NappletHostContract.EXTRA_BG_COLOR, android.graphics.Color.WHITE),
        )
    }

    /**
     * Draw a zoomed slice of the live WebView (the loupe content) and ship it back as a PNG. The WebView is a
     * real in-window view here in the provider, so its software draw renders real DOM pixels — unlike host-side
     * `PixelCopy` of the sandbox surface. Mirror of the browser path. Runs on the main looper (`WebView.draw`).
     */
    private fun onMagnifierRequest(msg: Message) {
        val tab = tabFor(msg) ?: return
        val wv = tab.liveWebView() ?: return
        val data = msg.data ?: return
        val reqT = data.getLong(NappletEmbedContract.KEY_MAG_REQ_T)
        MagnifierCapture.capture(
            webView = wv,
            bgColor = tab.bgColor,
            cx = data.getFloat(NappletEmbedContract.KEY_MAG_X),
            cy = data.getFloat(NappletEmbedContract.KEY_MAG_Y),
            boxW = data.getInt(NappletEmbedContract.KEY_MAG_BOX_W, 150).coerceIn(16, 1024),
            boxH = data.getInt(NappletEmbedContract.KEY_MAG_BOX_H, 84).coerceIn(16, 1024),
            zoom = data.getFloat(NappletEmbedContract.KEY_MAG_ZOOM, 1.6f).coerceIn(1f, 4f),
        ) { bytes, outW, outH, captureMs ->
            val reply =
                Message.obtain(null, NappletEmbedContract.MSG_MAGNIFIER_FRAME).apply {
                    this.data =
                        Bundle().apply {
                            putByteArray(NappletEmbedContract.KEY_MAG_BYTES, bytes)
                            putInt(NappletEmbedContract.KEY_MAG_W, outW)
                            putInt(NappletEmbedContract.KEY_MAG_H, outH)
                            putDouble(NappletEmbedContract.KEY_MAG_CAPTURE_MS, captureMs)
                            putLong(NappletEmbedContract.KEY_MAG_REQ_T, reqT)
                        }
                }
            tab.toClient(reply)
        }
    }

    /** Builds the SandboxedUiAdapter for [tab] and ships its cross-process handle (coreLibInfo) to the client. */
    @Suppress("DEPRECATION") // androidx.privacysandbox.ui toCoreLibInfo is deprecated with no successor.
    private fun replyWithAdapter(tab: NappletTab) {
        val adapter = NappletHostUiAdapter(this, tab.sessionId)
        val coreLibInfo = adapter.toCoreLibInfo(this)
        val reply =
            Message.obtain(null, NappletEmbedContract.MSG_SESSION_READY).apply {
                data = Bundle().apply { putBundle(NappletEmbedContract.KEY_CORE_LIB_INFO, coreLibInfo) }
            }
        tab.toClient(reply)
    }

    /**
     * Builds the session WebView (called by the adapter on the main thread when the client attaches the
     * surface, and on a retry). The launch is a [NappletSandboxSession], the same as the full-screen host's.
     */
    fun createHostWebView(
        context: Context,
        sessionId: String,
        container: FrameLayout,
    ): WebView {
        // The session may have been closed between MSG_CREATE_SESSION and this posted call — fail rather
        // than build a WebView that no tab tracks (it would leak).
        val tab = tabs[sessionId] ?: error("No napplet tab for session $sessionId")
        // A session re-opened on this tab (the client's view detached and re-attached) before the old one's
        // close landed, or a retry: the old launch goes first (its close will be ignored, see onSessionClosed).
        tab.session?.let { closeSession(tab, it) }
        tab.session = null
        tab.container = container
        tab.loadFailed = false
        val session =
            NappletSandboxSession(
                context = this,
                config = NappletSandboxLaunch(tab.config.launch, tab.config.launchToken, tab.config.useTor, tab.proxyPort, tab.config.themeType, tab.config.webViewProfile, tab.config.capabilityLabels),
                themedContext = nightThemedContext(context, tab.config.themeType),
                client = HostClient(tab),
                chrome = HostChromeClient(tab),
                toBroker = { if (brokerMessenger == null) pendingBrokerRequests.add(it) else sendToBroker(it) },
                toBoundBroker = { if (brokerMessenger != null) sendToBroker(it) },
                host = TabHost(tab),
                imeProxy = true,
            )
        tab.session = session
        val wv = session.webView
        // Theme the pre-load background so the shell/app loading shows Amethyst's background, not white.
        wv.setBackgroundColor(tab.bgColor)
        wv.dropSystemBarInsets()
        wv.setFindListener { active, total, _ -> pushFindResult(tab, active, total) }
        if (tab.textZoom != BrowserChrome.DEFAULT_TEXT_ZOOM) BrowserWebTools.setTextZoom(wv, tab.textZoom)
        if (tab.paused) wv.onPause()
        session.setAttended(tab.mayAct)
        session.start()
        return wv
    }

    /** What the tab's client draws, from the shared core's state. */
    private inner class TabHost(
        private val tab: NappletTab,
    ) : NappletSandboxSession.Host {
        private fun isCurrent(): Boolean = tabs[tab.sessionId] === tab

        override fun onState(state: NappletSessionState) {
            if (!isCurrent()) return
            // The client words a run inside a note from it, as every host does.
            tab.toClient(Message.obtain(null, NappletEmbedContract.MSG_SESSION_STATE).apply { data = Bundle().also { NappletEmbedContract.putSessionState(it, state) } })
            when (state) {
                // The files are being fetched (a webxdc archive can take a while): the client shows its spinner.
                NappletSessionState.Loading, NappletSessionState.WaitingForTor -> pushLoadState(tab, isLoading = true)
                NappletSessionState.Showing -> {}
                is NappletSessionState.Failed ->
                    when (state.reason) {
                        // Fail closed: Tor wanted and not running, or a WebView that can't take WebRTC away.
                        // The client's Retry sends the port once Tor is up.
                        NappletFailure.ROUTE_BLOCKED, NappletFailure.ENGINE -> reportRouteBlocked(tab)
                        // No server returned the files: the client's retry builds the tab again, with a new download.
                        NappletFailure.UNREACHABLE -> {
                            tab.loadFailed = true
                            pushLoadState(tab, isLoading = false)
                        }
                        // The view is gone; the client's MSG_RELOAD rebuilds it in the same surface.
                        NappletFailure.RENDERER_GONE -> {
                            tab.loadFailed = true
                            pushLoadState(tab, isLoading = false, rendererGone = true)
                        }
                    }
            }
        }

        override fun onDownload(progress: NappletDownloadProgress?) {
            if (isCurrent()) pushDownload(tab, progress)
        }

        override fun onPageMessage(
            type: String?,
            envelope: JsonObject,
            raw: String,
        ): Boolean {
            // IME events aren't brokered — the main app hosts the keyboard. Relay the envelope to the client.
            if (type?.startsWith("ime.") != true) return false
            val reply =
                Message.obtain(null, NappletEmbedContract.MSG_IME_EVENT).apply {
                    data = Bundle().apply { putString(NappletEmbedContract.KEY_IME_PAYLOAD, raw) }
                }
            tab.toClient(reply)
            return true
        }

        override fun onReply(result: JsonObject) {
            if (isCurrent()) notifyIfSensitive(tab, result)
        }

        override fun onRoute(usesTor: Boolean) {
            if (isCurrent()) {
                tab.toClient(Message.obtain(null, NappletEmbedContract.MSG_ROUTE).apply { data = Bundle().apply { putBoolean(NappletEmbedContract.KEY_ROUTE_TOR, usesTor) } })
            }
        }
    }

    /** A session closed: drop the tab, release its resources, and destroy its own WebView (never a sibling's). */
    fun onSessionClosed(
        sessionId: String,
        container: FrameLayout,
    ) {
        // Only the session that currently owns the tab may close it. A late close from a session that was
        // already replaced by a re-open would otherwise reap the live one — its WebView destroyed under a
        // client that had just been told the session opened, leaving the surface black for good.
        tabs[sessionId]?.takeIf { it.container === container }?.let(::closeTab)
    }

    /** Drops [tab] and everything it holds (its session: WebView, files, broker state, proxy claim). */
    private fun closeTab(tab: NappletTab) {
        // By identity: a replaced tab closing late must not take its replacement (same id) with it.
        tabs.remove(tab.sessionId, tab)
        // Release a picker still waiting on this surface before its WebView goes away.
        tab.fileChooser.cancel()
        tab.session?.let { closeSession(tab, it) }
        tab.session = null
        tab.container = null
    }

    /** Ends [session]: what it still had queued for the broker is dropped, and the broker forgets it. */
    private fun closeSession(
        tab: NappletTab,
        session: NappletSandboxSession,
    ) {
        pendingBrokerRequests.removeAll { it.replyTo == session.replyMessenger }
        (session.webView.parent as? ViewGroup)?.takeIf { it === tab.container }?.removeView(session.webView)
        session.close()
    }

    /**
     * The applet's file picker. Console output and the loading bar are drawn by the main process from
     * [NappletEmbedContract.MSG_LOAD_STATE]; without a chrome client WebView silently ignores every
     * `<input type="file">`.
     */
    private inner class HostChromeClient(
        private val tab: NappletTab,
    ) : NappletWebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = requestFileChooser(tab, filePathCallback, fileChooserParams)

        override fun onConsole(consoleMessage: ConsoleMessage): Boolean {
            pushConsoleLog(tab, consoleMessage.messageLevel().name, consoleMessage.message(), consoleMessage.sourceId(), consoleMessage.lineNumber())
            return true
        }

        // Setting a chrome client at all is what opts this WebView into the default JS-dialog handling,
        // and this one is built from a Service context — there is no window token to attach a dialog to,
        // and an applet's alert() must not be able to draw over the main app's trusted chrome anyway.
        // Dismiss all three so the page's JS resumes instead of blocking on a dialog that never appears.
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }
    }

    /**
     * Asks the client (main process) to run the picker for [tab] and holds the callback until the
     * matching [NappletEmbedContract.MSG_FILE_CHOOSER_RESULT] arrives. A surface with no client to ask
     * releases the input immediately rather than leaving it stuck on a reply that can never come.
     *
     * The user names the one file that crosses into the sandbox by picking it, so this needs no
     * capability of its own — same user-mediated grant a browser gives a page.
     */
    private fun requestFileChooser(
        tab: NappletTab,
        filePathCallback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        val client = tab.clientMessenger
        if (client == null) {
            filePathCallback.onReceiveValue(null)
            return true
        }
        val id = tab.fileChooser.start(filePathCallback)
        val msg =
            Message.obtain(null, NappletEmbedContract.MSG_FILE_CHOOSER_REQUEST).apply {
                data =
                    Bundle().apply {
                        putLong(NappletEmbedContract.KEY_FILE_CHOOSER_ID, id)
                        putStringArray(NappletEmbedContract.KEY_FILE_CHOOSER_ACCEPT, params.acceptTypes ?: emptyArray())
                        putBoolean(NappletEmbedContract.KEY_FILE_CHOOSER_MULTIPLE, NappletFileChooser.allowsMultiple(params.mode))
                        putBoolean(NappletEmbedContract.KEY_FILE_CHOOSER_CAPTURE, params.isCaptureEnabled)
                        putString(NappletEmbedContract.KEY_FILE_CHOOSER_TITLE, params.title?.toString())
                    }
            }
        if (!tab.toClient(msg)) tab.fileChooser.cancel()
        return true
    }

    /** Tells the client about loads, history and errors; the security answers are the session's. */
    private inner class HostClient(
        private val tab: NappletTab,
    ) : NappletWebViewClient() {
        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            // A new main-frame navigation cleared any prior error.
            tab.loadFailed = false
            pushLoadState(tab, isLoading = true)
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = pushState(tab, view)

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            pushState(tab, view)
            pushLoadState(tab, isLoading = false)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            pushConsoleLog(tab, ConsoleMessage.MessageLevel.ERROR.name, getString(R.string.napplet_console_load_error, error.errorCode, error.description?.toString().orEmpty()), request.url?.toString().orEmpty(), 0)
            // Only a main-frame failure blanks the applet; a missing sub-resource is irrelevant to whether
            // it opened.
            if (!request.isForMainFrame) return
            tab.loadFailed = true
            pushLoadState(tab, isLoading = false)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            pushConsoleLog(tab, ConsoleMessage.MessageLevel.ERROR.name, getString(R.string.napplet_console_http_error, errorResponse.statusCode, errorResponse.reasonPhrase.orEmpty()), request.url?.toString().orEmpty(), 0)
        }
    }

    private fun pushState(
        tab: NappletTab,
        view: WebView,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_STATE).apply {
                data = Bundle().apply { putBoolean(NappletEmbedContract.KEY_CAN_GO_BACK, view.canGoBack()) }
            }
        tab.toClient(message)
    }

    private fun pushFindResult(
        tab: NappletTab,
        active: Int,
        total: Int,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_FIND_RESULT).apply {
                data =
                    Bundle().apply {
                        putInt(NappletEmbedContract.KEY_FIND_ACTIVE, active)
                        putInt(NappletEmbedContract.KEY_FIND_TOTAL, total)
                    }
            }
        tab.toClient(message)
    }

    private fun pushConsoleLog(
        tab: NappletTab,
        level: String,
        text: String,
        source: String,
        line: Int,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_CONSOLE_LOG).apply {
                data =
                    Bundle().apply {
                        putString(NappletEmbedContract.KEY_CONSOLE_LEVEL, level)
                        putString(NappletEmbedContract.KEY_CONSOLE_MESSAGE, text)
                        putString(NappletEmbedContract.KEY_CONSOLE_SOURCE, source)
                        putInt(NappletEmbedContract.KEY_CONSOLE_LINE, line)
                    }
            }
        tab.toClient(message)
    }

    /** Tells the client whether a main-frame load is in flight and whether it failed, so it can overlay a spinner/retry. */
    private fun pushLoadState(
        tab: NappletTab,
        isLoading: Boolean,
        rendererGone: Boolean = false,
        routeBlocked: Boolean = false,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_LOAD_STATE).apply {
                data =
                    Bundle().apply {
                        putBoolean(NappletEmbedContract.KEY_IS_LOADING, isLoading)
                        putBoolean(NappletEmbedContract.KEY_LOAD_FAILED, tab.loadFailed)
                        putBoolean(NappletEmbedContract.KEY_RENDERER_GONE, rendererGone)
                        putBoolean(NappletEmbedContract.KEY_ROUTE_BLOCKED, routeBlocked)
                    }
            }
        tab.toClient(message)
    }

    /** Tells the client how far the tab's download has come, or (null) that it ended, for its loading bar. */
    private fun pushDownload(
        tab: NappletTab,
        download: NappletDownloadProgress?,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_DOWNLOAD).apply {
                data =
                    Bundle().apply {
                        if (download != null) {
                            putLong(NappletEmbedContract.KEY_DOWNLOAD_BYTES, download.bytes)
                            putLong(NappletEmbedContract.KEY_DOWNLOAD_TOTAL, download.total ?: -1)
                        }
                    }
            }
        tab.toClient(message)
    }

    /** The applet wasn't loaded because its route can't be honored: tell the client, which shows the error. */
    private fun reportRouteBlocked(tab: NappletTab) {
        if (tabs[tab.sessionId] !== tab) return
        // Whatever WebView the tab has never loaded the applet: the retry builds a new session.
        tab.loadFailed = true
        pushLoadState(tab, isLoading = false, routeBlocked = true)
    }

    private fun sendToBroker(msg: Message) {
        try {
            brokerMessenger?.send(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver request to broker", e)
        }
    }

    /** Pushes a notice to the main process for a granted "allow always" sensitive op, so it can toast. */
    private fun notifyIfSensitive(
        tab: NappletTab,
        result: JsonObject,
    ) {
        if (result.booleanOrNull("ok") != true) return
        val notice =
            when (result.stringOrNull("type")) {
                "relay.publish.result", "relay.publishEncrypted.result" -> NappletEmbedContract.NOTICE_PUBLISHED
                "upload.upload.result" -> NappletEmbedContract.NOTICE_UPLOADED
                "value.payInvoice.result" -> NappletEmbedContract.NOTICE_PAID
                else -> return
            }
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_NOTICE).apply {
                data = Bundle().apply { putString(NappletEmbedContract.KEY_NOTICE, notice) }
            }
        tab.toClient(message)
    }

    private companion object {
        private const val TAG = "NappletHostService"
    }
}
