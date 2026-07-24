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
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.privacysandbox.ui.provider.toCoreLibInfo
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Provider for the **embedded** in-app browser. Runs in the keyless `:napplet` process: it hosts the
 * live-URL WebView and exposes it to the main app as a `SandboxedUiAdapter` (the main app renders it
 * inside a `SandboxedSdkView`, so only pixels + input cross the process boundary — never the WebView's
 * JS context, cookies, or the NIP-07 bridge). Keys still live only in the main process; every NIP-07
 * `window.nostr` call is brokered and consent-gated there, per visited origin.
 *
 * Shares the keyless-sandbox trust model of [NappletHostActivity] (the verified-blob nSite/napplet
 * host), but as a windowless Service rendering an arbitrary live URL, so the surface can be embedded in
 * the main activity rather than taking over the screen. Requires API 30+ (SurfaceControlViewHost); the
 * feature is hidden below that.
 */
@RequiresApi(Build.VERSION_CODES.R)
class NappletBrowserService : Service() {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onClientMessage))

    /**
     * Everything that belongs to ONE embedded browser surface. A single service instance is shared by
     * every tab (they bind the same Intent), so all per-surface state is collected here and keyed by
     * [sessionId] in [tabs]. Each tab gets its own [replyMessenger]: the broker echoes `replyTo` on both
     * responses and unsolicited relay pushes, so this is what scopes NIP-07 traffic back to the right
     * surface without any id rewriting.
     */
    private inner class BrowserTab(
        val sessionId: String,
        var clientMessenger: Messenger?,
        val url: String,
        val proxyPort: Int,
        var useTor: Boolean,
        val bgColor: Int,
        val themeType: String,
        // Opaque per-account WebView storage-profile name (see NappletWebViewProfile).
        val webViewProfile: String?,
    ) {
        var webView: WebView? = null
        var bridgeReplyProxy: JavaScriptReplyProxy? = null
        var fireSeq = 0

        // Last main-frame error state, pushed to the client so it can show an error/retry overlay over
        // the surface (the embedded surface has no error page of its own).
        var loadFailed = false

        // Host whose favicon this tab already relayed. A page fires the icon callbacks several times, and
        // both capture paths (WebView raster + declared-icon sniff) can land for the same visit, so this
        // keeps it to one relay per host per visit. Re-armed when the host changes.
        var lastIconHost: String? = null

        // Per visited origin: its broker-minted launch token, the requests queued until it arrives, and
        // the origins a mint is already in flight for — so NIP-07 consent is scoped per site, per tab.
        val originTokens = mutableMapOf<String, String>()
        val pendingByOrigin = mutableMapOf<String, MutableList<Message>>()
        val mintInFlight = mutableSetOf<String>()

        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { onBrokerReply(this, it) })
    }

    private val tabs = mutableMapOf<String, BrowserTab>()

    // The shim never changes; read+decode it once instead of per tab on the main thread.
    private val shimJs: String by lazy { readContractAsset(NappletWebContract.SHIM_JS_PATH).decodeToString() }

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
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brokerMessenger = null
            }
        }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onDestroy() {
        if (brokerBound) {
            runCatching { unbindService(brokerConnection) }
            brokerBound = false
        }
        tabs.values.forEach { it.webView?.destroy() }
        tabs.clear()
        super.onDestroy()
    }

    private fun tabFor(msg: Message): BrowserTab? = msg.data?.getString(NappletBrowserContract.KEY_SESSION_ID)?.let { tabs[it] }

    private fun onClientMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletBrowserContract.MSG_CREATE_SESSION -> {
                val data = msg.data ?: return true
                val sessionId = data.getString(NappletBrowserContract.KEY_SESSION_ID) ?: return true
                val tab =
                    BrowserTab(
                        sessionId = sessionId,
                        clientMessenger = msg.replyTo,
                        url = data.getString(NappletBrowserContract.KEY_URL)?.ifBlank { ABOUT_BLANK } ?: ABOUT_BLANK,
                        proxyPort = data.getInt(NappletBrowserContract.KEY_PROXY_PORT, -1),
                        useTor = data.getBoolean(NappletBrowserContract.KEY_USE_TOR, false),
                        bgColor = data.getInt(NappletBrowserContract.KEY_BG_COLOR, android.graphics.Color.WHITE),
                        themeType = data.getString(NappletBrowserContract.KEY_THEME).orEmpty().ifBlank { "SYSTEM" },
                        webViewProfile = data.getString(NappletBrowserContract.KEY_WEBVIEW_PROFILE),
                    )
                tabs[sessionId] = tab
                // Bind the broker once; a re-sent MSG_CREATE_SESSION (e.g. client reconnect) must not
                // leak a second binding.
                if (!brokerBound) {
                    brokerBound = bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
                }
                replyWithAdapter(tab)
            }
            NappletBrowserContract.MSG_NAVIGATE -> tabFor(msg)?.webView?.loadUrl(normalizeUrl(msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty()))
            NappletBrowserContract.MSG_RELOAD -> tabFor(msg)?.webView?.reload()
            NappletBrowserContract.MSG_BACK -> tabFor(msg)?.webView?.let { if (it.canGoBack()) it.goBack() }
            NappletBrowserContract.MSG_IME_OP -> {
                val tab = tabFor(msg) ?: return true
                val payload = msg.data?.getString(NappletBrowserContract.KEY_IME_PAYLOAD) ?: return true
                tab.bridgeReplyProxy?.postMessage(payload)
            }
            NappletBrowserContract.MSG_SET_TOR -> {
                val tab = tabFor(msg) ?: return true
                tab.useTor = msg.data?.getBoolean(NappletBrowserContract.KEY_USE_TOR, false) ?: false
                // Reload only after the proxy override actually applies — setProxyOverride is async, so
                // reloading immediately would re-fetch through the old route. NB: the override is
                // process-global (Android has no per-WebView proxy), so it affects every tab; we only
                // reload the one the user toggled.
                applyWebViewProxy(if (tab.useTor) tab.proxyPort else -1) { tab.webView?.reload() }
            }
            NappletBrowserContract.MSG_MAGNIFIER_REQUEST -> onMagnifierRequest(msg)
            else -> return false
        }
        return true
    }

    // One reusable output bitmap per tab would be ideal, but loupe size is fixed per drag; createBitmap each
    // frame is cheap next to the draw. Source rect is in view px (== surface px, the SCVH is 1:1).
    private fun onMagnifierRequest(msg: Message) {
        val tab = tabFor(msg) ?: return
        val wv = tab.webView ?: return
        val data = msg.data ?: return
        val cx = data.getFloat(NappletBrowserContract.KEY_MAG_X)
        val cy = data.getFloat(NappletBrowserContract.KEY_MAG_Y)
        val boxW = data.getInt(NappletBrowserContract.KEY_MAG_BOX_W, 150).coerceIn(16, 1024)
        val boxH = data.getInt(NappletBrowserContract.KEY_MAG_BOX_H, 84).coerceIn(16, 1024)
        val zoom = data.getFloat(NappletBrowserContract.KEY_MAG_ZOOM, 1.6f).coerceIn(1f, 4f)
        val reqT = data.getLong(NappletBrowserContract.KEY_MAG_REQ_T)

        val outW = (boxW * zoom).toInt().coerceAtLeast(1)
        val outH = (boxH * zoom).toInt().coerceAtLeast(1)
        val t0 = SystemClock.elapsedRealtimeNanos()
        val bitmap = createBitmap(outW, outH)
        val canvas = Canvas(bitmap)
        canvas.drawColor(tab.bgColor)
        // Map the source rect (centered on cx,cy in view px) into the zoomed output bitmap.
        canvas.scale(zoom, zoom)
        canvas.translate(-(cx - boxW / 2f), -(cy - boxH / 2f))
        wv.draw(canvas)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val bytes = baos.toByteArray()
        bitmap.recycle()
        val captureMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0

        val reply =
            Message.obtain(null, NappletBrowserContract.MSG_MAGNIFIER_FRAME).apply {
                this.data =
                    Bundle().apply {
                        putByteArray(NappletBrowserContract.KEY_MAG_BYTES, bytes)
                        putInt(NappletBrowserContract.KEY_MAG_W, outW)
                        putInt(NappletBrowserContract.KEY_MAG_H, outH)
                        putDouble(NappletBrowserContract.KEY_MAG_CAPTURE_MS, captureMs)
                        putLong(NappletBrowserContract.KEY_MAG_REQ_T, reqT)
                    }
            }
        runCatching { tab.clientMessenger?.send(reply) }
    }

    /** Builds the SandboxedUiAdapter for [tab] and ships its cross-process handle (coreLibInfo) to the client. */
    @Suppress("DEPRECATION") // androidx.privacysandbox.ui toCoreLibInfo is deprecated with no successor.
    private fun replyWithAdapter(tab: BrowserTab) {
        val adapter = NappletBrowserUiAdapter(this, tab.sessionId)
        val coreLibInfo = adapter.toCoreLibInfo(this)
        val reply =
            Message.obtain(null, NappletBrowserContract.MSG_SESSION_READY).apply {
                data = Bundle().apply { putBundle(NappletBrowserContract.KEY_CORE_LIB_INFO, coreLibInfo) }
            }
        runCatching { tab.clientMessenger?.send(reply) }
    }

    /**
     * Builds and configures [sessionId]'s WebView (called by the adapter on the main thread when the
     * client attaches the surface). Injects the same NIP-07 shim [NappletHostActivity] uses, at document
     * start for every origin, reaching the broker directly (no shell). Loads the tab's URL.
     */
    fun createBrowserWebView(
        context: Context,
        sessionId: String,
    ): WebView {
        // The session may have been closed between MSG_CREATE_SESSION and this posted call — fail rather
        // than build a WebView that no tab tracks (it would leak).
        val tab = tabs[sessionId] ?: error("No browser tab for session $sessionId")
        val wv = WebView(nightThemedContext(context, tab.themeType))
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before the
        // configure/bridge setup and the loadUrl below.
        NappletWebViewProfile.apply(context, wv, tab.webViewProfile)
        configureWebView(wv, tab)
        // Theme the pre-load background so a blank/loading page shows Amethyst's background, not white.
        wv.setBackgroundColor(tab.bgColor)
        wv.dropSystemBarInsets()
        applyWebViewProxy(if (tab.useTor) tab.proxyPort else -1)
        WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf("*")) { view, message, sourceOrigin, isMainFrame, replyProxy ->
            onBridgeMessage(tab, view, message, sourceOrigin, isMainFrame, replyProxy)
        }
        // __nappletImeProxy: this is the EMBEDDED surface (no native keyboard), so install the IME agent
        // that relays the focused field to the host's keyboard. The full-screen browser activity sets the
        // direct bridge but NOT this flag (it has a real WebView window with a native keyboard).
        val startScript = "if (window.top === window) { window.__nappletDirectBridge = true; window.__nappletNip07 = true; window.__nappletImeProxy = true; }\n$shimJs"
        WebViewCompat.addDocumentStartJavaScript(wv, startScript, setOf("*"))
        tab.webView = wv
        wv.loadUrl(tab.url)
        return wv
    }

    /** A session closed: drop the tab and destroy its own WebView (never a sibling's). */
    fun onSessionClosed(sessionId: String) {
        val tab = tabs.remove(sessionId) ?: return
        tab.bridgeReplyProxy = null
        tab.webView?.destroy()
        tab.webView = null
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(
        wv: WebView,
        tab: BrowserTab?,
    ) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                safeBrowsingEnabled = true
            }
        }
        WebView.setWebContentsDebuggingEnabled(false)
        wv.webViewClient = BrowserClient(tab)
        wv.webChromeClient = BrowserChromeClient(tab)
    }

    private inner class BrowserChromeClient(
        private val tab: BrowserTab?,
    ) : WebChromeClient() {
        /**
         * The embedded surface captures favicons just like the full-screen browser does — a site pinned
         * to a tab but never opened full-screen would otherwise never contribute an icon at all.
         */
        override fun onReceivedIcon(
            view: WebView,
            icon: Bitmap?,
        ) {
            val tab = tab ?: return
            if (icon == null || tab.loadFailed) return
            val host = OmniboxInput.hostOf(view.url ?: return) ?: return
            if (host == tab.lastIconHost) return
            tab.lastIconHost = host
            recordIcon(host, icon)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (tab == null) return false
            pushConsoleLog(
                tab,
                consoleMessage.messageLevel().name,
                consoleMessage.message(),
                consoleMessage.sourceId(),
                consoleMessage.lineNumber(),
            )
            return true
        }
    }

    private fun pushConsoleLog(
        tab: BrowserTab,
        level: String,
        message: String,
        source: String,
        line: Int,
    ) {
        val msg =
            Message.obtain(null, NappletBrowserContract.MSG_CONSOLE_LOG).apply {
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_CONSOLE_LEVEL, level)
                        putString(NappletBrowserContract.KEY_CONSOLE_MESSAGE, message)
                        putString(NappletBrowserContract.KEY_CONSOLE_SOURCE, source)
                        putInt(NappletBrowserContract.KEY_CONSOLE_LINE, line)
                    }
            }
        runCatching { tab.clientMessenger?.send(msg) }
    }

    /** Loads live web pages in-WebView (http/https) and hands other schemes to the system on a user tap. */
    private inner class BrowserClient(
        private val tab: BrowserTab?,
    ) : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
            if (request.hasGesture()) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return true
        }

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: android.graphics.Bitmap?,
        ) {
            // A new main-frame navigation cleared any prior error.
            tab?.loadFailed = false
            // Re-arm favicon capture when the host changes, so a same-host in-page nav doesn't re-send.
            if (tab != null && OmniboxInput.hostOf(url) != tab.lastIconHost) tab.lastIconHost = null
            pushUrl(tab, view)
            pushLoadState(tab, view, isLoading = true)
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = pushUrl(tab, view)

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            pushUrl(tab, view)
            pushLoadState(tab, view, isLoading = false)
            if (url.startsWith("https://") || url.startsWith("http://")) scheduleFaviconSniff(tab, view, url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // Only a main-frame failure blanks the page; sub-resource errors (a missing image, a blocked
            // tracker) are irrelevant to whether the app opened.
            if (!request.isForMainFrame) return
            tab?.loadFailed = true
            pushLoadState(tab, view, isLoading = false)
        }
    }

    /** Tells the client whether a main-frame load is in flight and whether it failed, so it can overlay a spinner/retry. */
    private fun pushLoadState(
        tab: BrowserTab?,
        view: WebView,
        isLoading: Boolean,
    ) {
        val message =
            Message.obtain(null, NappletBrowserContract.MSG_LOAD_STATE).apply {
                data =
                    Bundle().apply {
                        putBoolean(NappletBrowserContract.KEY_IS_LOADING, isLoading)
                        putBoolean(NappletBrowserContract.KEY_LOAD_FAILED, tab?.loadFailed ?: false)
                        putString(NappletBrowserContract.KEY_URL, view.url.orEmpty())
                    }
            }
        runCatching { tab?.clientMessenger?.send(message) }
    }

    private fun pushUrl(
        tab: BrowserTab?,
        view: WebView,
    ) {
        val url = view.url ?: return
        val message =
            Message.obtain(null, NappletBrowserContract.MSG_URL_CHANGED).apply {
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_URL, url)
                        putBoolean(NappletBrowserContract.KEY_CAN_GO_BACK, view.canGoBack())
                    }
            }
        runCatching { tab?.clientMessenger?.send(message) }
    }

    /**
     * Routes WebView traffic through the Tor SOCKS proxy when [port] > 0, else clears the override.
     * [onApplied] runs on the main thread once the override is in effect (the WebKit callback is async,
     * so callers that reload must wait for it). Process-global (this `:napplet` process hosts only
     * sandbox WebViews) and best-effort.
     */
    private fun applyWebViewProxy(
        port: Int,
        onApplied: () -> Unit = {},
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            onApplied()
            return
        }
        val executor = java.util.concurrent.Executor { it.run() }
        runCatching {
            if (port > 0) {
                val config =
                    androidx.webkit.ProxyConfig
                        .Builder()
                        .addProxyRule("socks5://127.0.0.1:$port")
                        .build()
                androidx.webkit.ProxyController
                    .getInstance()
                    .setProxyOverride(config, executor) { onApplied() }
            } else {
                androidx.webkit.ProxyController
                    .getInstance()
                    .clearProxyOverride(executor) { onApplied() }
            }
        }.onFailure {
            Log.w(TAG, "Failed to apply WebView proxy override", it)
            onApplied()
        }
    }

    /**
     * A top-frame NIP-07 call from [sourceOrigin]. The origin is the trusted value the WebView reports
     * (the page can't forge it), so it keys consent; each origin uses its own broker-minted launch token.
     */
    private fun onBridgeMessage(
        tab: BrowserTab,
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        tab.bridgeReplyProxy = replyProxy
        val raw = message.data ?: return
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return

        // IME events aren't brokered — the main app hosts the keyboard. Relay the envelope to the client.
        if (envelope.optString("type").startsWith("ime.")) {
            val reply =
                Message.obtain(null, NappletBrowserContract.MSG_IME_EVENT).apply {
                    data = Bundle().apply { putString(NappletBrowserContract.KEY_IME_PAYLOAD, raw) }
                }
            runCatching { tab.clientMessenger?.send(reply) }
            return
        }

        val scheme = sourceOrigin.scheme ?: return
        val host = sourceOrigin.host ?: return
        val origin = "$scheme://$host" + if (sourceOrigin.port > 0) ":${sourceOrigin.port}" else ""

        val id = envelope.optString("id").ifEmpty { "fire-${tab.fireSeq++}" }
        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = tab.replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, raw)
                    }
            }

        val token = tab.originTokens[origin]
        if (token != null) {
            msg.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
            if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
        } else {
            tab.pendingByOrigin.getOrPut(origin) { mutableListOf() }.add(msg)
            requestBrowserToken(tab, origin)
        }
    }

    private fun requestBrowserToken(
        tab: BrowserTab,
        origin: String,
    ) {
        if (!tab.mintInFlight.add(origin)) return
        val msg =
            Message.obtain(null, NappletIpc.MSG_MINT_BROWSER_TOKEN).apply {
                replyTo = tab.replyMessenger
                data = Bundle().apply { putString(NappletIpc.KEY_BROWSER_ORIGIN, origin) }
            }
        if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
    }

    /**
     * Second-chance favicon capture for pages `onReceivedIcon` never fires for (SVG-only declarations —
     * WebView does not rasterize those into the callback). Delayed so the WebView's own raster path,
     * which usually lands just after page-finish, gets first claim on the host.
     */
    private fun scheduleFaviconSniff(
        tab: BrowserTab?,
        view: WebView,
        url: String,
    ) {
        if (tab == null) return
        val host = OmniboxInput.hostOf(url) ?: return
        view.postDelayed({
            if (tabs[tab.sessionId] !== tab || tab.loadFailed || host == tab.lastIconHost || view.url != url) return@postDelayed
            NappletFaviconSniffer.capture(view) { sniffedHost, bytes ->
                if (sniffedHost == tab.lastIconHost) return@capture
                tab.lastIconHost = sniffedHost
                recordIconBytes(sniffedHost, bytes)
            }
        }, FAVICON_SNIFF_DELAY_MS)
    }

    /** Scales [icon] down and relays it to the broker as the favicon for [host] (PNG bytes over IPC). */
    private fun recordIcon(
        host: String,
        icon: Bitmap,
    ) {
        val bytes =
            runCatching {
                val scaled = if (icon.width > ICON_MAX_PX || icon.height > ICON_MAX_PX) icon.scale(ICON_MAX_PX, ICON_MAX_PX) else icon
                ByteArrayOutputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
            }.getOrNull() ?: return
        recordIconBytes(host, bytes)
    }

    /** Relays already-encoded icon bytes (PNG/ICO/… or SVG source) to the broker as [host]'s favicon. */
    private fun recordIconBytes(
        host: String,
        bytes: ByteArray,
    ) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_RECORD_ICON).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_ICON_HOST, host)
                        putByteArray(NappletIpc.KEY_ICON_BYTES, bytes)
                    }
            }
        if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
    }

    private fun sendToBroker(msg: Message) {
        try {
            brokerMessenger?.send(msg)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver request to broker", e)
        }
    }

    /** Broker reply for [tab] — delivered to the tab's own reply Messenger, so it's already scoped. */
    private fun onBrokerReply(
        tab: BrowserTab,
        msg: Message,
    ): Boolean {
        // The reply may arrive after the tab closed (its WebView/page is gone) — drop it so we never
        // postMessage to a dead JavaScriptReplyProxy.
        if (tabs[tab.sessionId] !== tab) return true
        val data = msg.data ?: return true
        when (msg.what) {
            NappletIpc.MSG_RESPONSE -> {
                val id = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                val result = runCatching { JSONObject(payload) }.getOrNull() ?: JSONObject()
                result.put("id", id)
                runCatching { tab.bridgeReplyProxy?.postMessage(result.toString()) }
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                runCatching { tab.bridgeReplyProxy?.postMessage(payload) }
            }
            NappletIpc.MSG_BROWSER_TOKEN -> {
                val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN) ?: return true
                val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN) ?: return true
                tab.originTokens[origin] = token
                tab.mintInFlight.remove(origin)
                tab.pendingByOrigin.remove(origin)?.forEach { queued ->
                    queued.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
                    sendToBroker(queued)
                }
            }
            else -> return false
        }
        return true
    }

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebContract.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    /** Address-bar text → URL via the shared [OmniboxInput] rules (bare domain → https, else search). */
    private fun normalizeUrl(input: String): String = OmniboxInput.resolve(input)?.url ?: ABOUT_BLANK

    private companion object {
        private const val TAG = "NappletBrowserService"
        private const val ABOUT_BLANK = "about:blank"

        /** Max favicon edge (px) before sending over IPC — keeps the PNG tiny, well under the Binder limit. */
        private const val ICON_MAX_PX = 96

        /** Grace period after page-finish before the declared-icon sniff runs, so `onReceivedIcon` wins first. */
        private const val FAVICON_SNIFF_DELAY_MS = 1_200L
    }
}
