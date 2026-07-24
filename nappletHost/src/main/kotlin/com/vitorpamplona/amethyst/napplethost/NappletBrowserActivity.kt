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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Full-screen **direct-WebView** browser for an arbitrary URL, running in the keyless `:napplet`
 * process. Unlike the embedded browser ([NappletBrowserService], which streams its surface to the main
 * app through SurfaceControlViewHost — a path that, on current Android, forwards taps but drops scroll/
 * zoom/keyboard gestures), this hosts the WebView **directly** in its own window, so scrolling, pinch
 * zoom, and the soft keyboard (`adjustResize`) all work natively. It stays just as keyless: the page JS
 * runs here, every NIP-07 `window.nostr` call is brokered + consent-gated in the main process per origin,
 * and the keys never leave it.
 *
 * Mirrors [NappletHostActivity]'s sandbox scaffolding (trusted chrome, loading screen, foreground hold)
 * but loads a live URL directly instead of serving verified blobs through a shell.
 */
class NappletBrowserActivity : ComponentActivity() {
    private lateinit var webView: WebView

    private var startUrl: String = "about:blank"
    private var proxyPort: Int = -1
    private var useTor: Boolean = true
    private var themeType: String = "SYSTEM"

    private val contentFrame by lazy { FrameLayout(this) }
    private var loadingView: View? = null
    private var resumed = false
    private var controlSheet: NappletControlSheet? = null
    private var consolePanel: NappletConsolePanel? = null

    // A thin determinate progress bar pinned to the top edge (browser-style), driven by the chrome
    // client's onProgressChanged; hidden at 100%.
    private val topProgressBar by lazy { buildTopProgressBar() }

    // Visit-history gating: only a clean main-frame load (no error) is recorded, so a misspelled/
    // unresolved address never enters history. Reset on each main-frame page start.
    private var pendingMainFrameUrl: String? = null
    private var mainFrameLoadFailed = false
    private var lastIconHost: String? = null

    // ---- broker bridge (per-origin NIP-07 tokens; identical to NappletBrowserService) ----
    private var brokerMessenger: Messenger? = null
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper(), ::onBrokerReply))
    private val pendingBrokerRequests = mutableListOf<Message>()
    private var bridgeReplyProxy: JavaScriptReplyProxy? = null
    private var fireSeq = 0
    private val originTokens = mutableMapOf<String, String>()
    private val pendingByOrigin = mutableMapOf<String, MutableList<Message>>()
    private val mintInFlight = mutableSetOf<String>()

    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (this@NappletBrowserActivity::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    private fun syncBackState() {
        if (this::webView.isInitialized) backCallback.isEnabled = webView.canGoBack()
    }

    private val brokerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                brokerMessenger = Messenger(service)
                pendingBrokerRequests.forEach { sendToBroker(it) }
                pendingBrokerRequests.clear()
                if (resumed) setBrokerForeground(true)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brokerMessenger = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startUrl = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: run {
            finish()
            return
        }
        proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, -1)
        useTor = intent.getBooleanExtra(EXTRA_USE_TOR, true)
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        themeType = intent.getStringExtra(EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" }

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Toast.makeText(this, getString(R.string.napplet_webview_too_old), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Build the WebView from a context forced to the app theme so its content follows DARK/LIGHT even when
        // the device theme differs (WebView reads the context's theme, not the window's — see nightThemedContext).
        webView = WebView(nightThemedContext(this, themeType))
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before anything else.
        NappletWebViewProfile.apply(this, webView, intent.getStringExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE))
        configureWebView(webView)
        webView.setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
        webView.dropSystemBarInsets()
        applyWebViewProxy(if (useTor) proxyPort else -1)

        // NIP-07 over the direct bridge (no shell): the shim talks to native at document start for every
        // origin; the broker scopes consent per visited origin.
        val shim = readContractAsset(NappletWebContract.SHIM_JS_PATH).decodeToString()
        WebViewCompat.addWebMessageListener(webView, NappletWebContract.BRIDGE_NAME, setOf("*"), ::onBridgeMessage)
        val startScript = "if (window.top === window) { window.__nappletDirectBridge = true; window.__nappletNip07 = true; }\n$shim"
        WebViewCompat.addDocumentStartJavaScript(webView, startScript, setOf("*"))

        bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
        onBackPressedDispatcher.addCallback(this, backCallback)

        val root =
            FrameLayout(this).apply {
                addView(contentFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(
                    buildControlSheet(),
                    FrameLayout
                        .LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP,
                        ),
                )
                addView(
                    buildConsolePanel(),
                    FrameLayout
                        .LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM,
                        ),
                )
                // Added last so the thin loading bar paints above the content (and over the grabber's top edge).
                addView(topProgressBar)
            }
        setContentView(root)
        // Pad by the system bars + cutout, but NOT the IME — windowSoftInputMode=adjustResize shrinks the
        // window when the keyboard shows, and the WebView (filling contentFrame) resizes so focused inputs
        // stay visible. Zero the consumed insets before they reach the WebView so it doesn't double-pad.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val applied = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(applied)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.Builder(insets).setInsets(applied, Insets.NONE).build()
        }

        contentFrame.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        loadingView = buildLoadingView().also { contentFrame.addView(it) }

        webView.loadUrl(startUrl)
    }

    // Renews the broker's foreground lease while resumed; without it the broker's watchdog would reap the
    // lease (tearing down Tor/relays) while the browser is still genuinely foreground.
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeat =
        object : Runnable {
            override fun run() {
                setBrokerForeground(true)
                heartbeatHandler.postDelayed(this, FOREGROUND_HEARTBEAT_MS)
            }
        }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) {
            webView.onResume()
        }
        resumed = true
        heartbeatHandler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

    override fun onPause() {
        if (this::webView.isInitialized) {
            // Only pause THIS activity's WebView (onPause is per-WebView). Do NOT call pauseTimers(): it is
            // process-global — it freezes JS/layout/parsing timers for EVERY WebView in `:napplet`, including
            // the embedded ones in NappletBrowserService, which have no resume of their own. That left the
            // embed frozen (dead page/connection) after returning from a full-screen excursion.
            webView.onPause()
        }
        resumed = false
        heartbeatHandler.removeCallbacks(heartbeat)
        setBrokerForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unbindService(brokerConnection) }
        if (this::webView.isInitialized) {
            // Detach from the view tree BEFORE destroy(). Destroying a WebView while it is still attached to
            // the window corrupts the SHARED multiprocess renderer/network state, which then breaks the OTHER
            // (embedded) WebViews living in this `:napplet` process: dead DNS (ERR_NAME_NOT_RESOLVED), DOM reads
            // returning empty (`value == ""` on a field that visibly shows text), dead selection-highlight paint,
            // and broken IME — all after a full-screen excursion returns to an embed. (`destroy()` requires the
            // view to be removed from the hierarchy first; see WebView.destroy() docs.)
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    /** Reports foreground state to the broker so the main process stays resumed (Tor/relays/AUTH). */
    private fun setBrokerForeground(foreground: Boolean) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_FOREGROUND).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, startUrl)
                        putBoolean(NappletIpc.KEY_FOREGROUND, foreground)
                    }
            }
        if (brokerMessenger != null) sendToBroker(msg)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView) {
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
        wv.webViewClient = BrowserClient()
        wv.webChromeClient = BrowserChromeClient()
    }

    /** Captures favicon and console output, and drives the top loading bar; all come from the WebChromeClient. */
    private inner class BrowserChromeClient : WebChromeClient() {
        override fun onProgressChanged(
            view: WebView,
            newProgress: Int,
        ) {
            updateLoadProgress(newProgress)
        }

        override fun onReceivedIcon(
            view: WebView,
            icon: Bitmap?,
        ) {
            if (icon == null || mainFrameLoadFailed) return
            val host = OmniboxInput.hostOf(view.url ?: return) ?: return
            // De-dupe: a page can fire this several times — store once per host per visit.
            if (host == lastIconHost) return
            lastIconHost = host
            recordIcon(host, icon)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            val panel = consolePanel ?: return false
            panel.appendLog(
                consoleMessage.messageLevel(),
                consoleMessage.message(),
                consoleMessage.sourceId(),
                consoleMessage.lineNumber(),
            )
            controlSheet?.updateConsoleCount(panel.entryCount)
            return true
        }
    }

    private inner class BrowserClient : WebViewClient() {
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
            favicon: Bitmap?,
        ) {
            // A fresh main-frame navigation: arm history gating and show the new address.
            pendingMainFrameUrl = url
            mainFrameLoadFailed = false
            // Re-arm favicon capture when the host changes, so a same-host in-page nav doesn't re-send.
            if (OmniboxInput.hostOf(url) != lastIconHost) lastIconHost = null
            controlSheet?.updateUrl(url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // A main-frame failure (DNS miss on a misspelled host, no connection, …) disqualifies this
            // navigation from history. Sub-resource errors are irrelevant to whether the page opened.
            if (request.isForMainFrame) mainFrameLoadFailed = true
            logConsoleError(request, getString(R.string.napplet_console_load_error, error.errorCode, error.description?.toString().orEmpty()))
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            logConsoleError(request, getString(R.string.napplet_console_http_error, errorResponse.statusCode, errorResponse.reasonPhrase.orEmpty()))
        }

        override fun onPageCommitVisible(
            view: WebView,
            url: String,
        ) {
            // The page has painted its first frame — drop the loading screen.
            loadingView?.let { contentFrame.removeView(it) }
            loadingView = null
            controlSheet?.updateUrl(url)
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) {
            syncBackState()
            controlSheet?.updateUrl(url)
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            syncBackState()
            controlSheet?.updateUrl(url)
            // Record only a clean http(s) main-frame load — never a typed-but-failed address.
            if (!mainFrameLoadFailed && (url.startsWith("https://") || url.startsWith("http://"))) {
                recordHistory(url, view.title)
                scheduleFaviconSniff(view, url)
            }
        }
    }

    /**
     * Second-chance favicon capture for pages `onReceivedIcon` never fires for (SVG-only declarations —
     * WebView does not rasterize those into the callback). Deliberately delayed so the WebView's own
     * raster path, which usually lands shortly after the page finishes, gets first claim on the host;
     * if it did, [lastIconHost] is already set and we skip out entirely.
     */
    private fun scheduleFaviconSniff(
        view: WebView,
        url: String,
    ) {
        val host = OmniboxInput.hostOf(url) ?: return
        view.postDelayed({
            if (mainFrameLoadFailed || host == lastIconHost || view.url != url) return@postDelayed
            NappletFaviconSniffer.capture(view) { sniffedHost, bytes ->
                if (sniffedHost == lastIconHost) return@capture
                lastIconHost = sniffedHost
                recordIconBytes(sniffedHost, bytes)
            }
        }, FAVICON_SNIFF_DELAY_MS)
    }

    /** Relays a successfully loaded page to the main-process broker for the device-local visit history. */
    private fun recordHistory(
        url: String,
        title: String?,
    ) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_RECORD_HISTORY).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_HISTORY_URL, url)
                        putString(NappletIpc.KEY_HISTORY_TITLE, title.orEmpty())
                    }
            }
        if (brokerMessenger != null) sendToBroker(msg) else pendingBrokerRequests.add(msg)
    }

    /** Scales [icon] down and relays it to the broker as the favicon for [host] (PNG bytes over IPC). */
    private fun recordIcon(
        host: String,
        icon: Bitmap,
    ) {
        val bytes =
            runCatching {
                val scaled =
                    if (icon.width > ICON_MAX_PX || icon.height > ICON_MAX_PX) {
                        icon.scale(ICON_MAX_PX, ICON_MAX_PX)
                    } else {
                        icon
                    }
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
        if (brokerMessenger != null) sendToBroker(msg) else pendingBrokerRequests.add(msg)
    }

    /** Loads a user-typed address from the in-page address bar, forcing Tor for `.onion` when available. */
    private fun loadAddress(text: String) {
        val resolved = OmniboxInput.resolve(text) ?: return
        if (resolved.forceTor && proxyPort > 0 && !useTor) {
            useTor = true
            applyWebViewProxy(proxyPort)
        }
        if (this::webView.isInitialized) webView.loadUrl(resolved.url)
    }

    // ---- bridge: page <-> native (mirror of NappletBrowserService.onBridgeMessage) ----

    private fun onBridgeMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        bridgeReplyProxy = replyProxy
        val raw = message.data ?: return
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return

        val scheme = sourceOrigin.scheme ?: return
        val host = sourceOrigin.host ?: return
        val origin = "$scheme://$host" + if (sourceOrigin.port > 0) ":${sourceOrigin.port}" else ""

        val id = envelope.optString("id").ifEmpty { "fire-${fireSeq++}" }
        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, raw)
                    }
            }

        val token = originTokens[origin]
        if (token != null) {
            msg.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
            if (brokerMessenger == null) pendingBrokerRequests.add(msg) else sendToBroker(msg)
        } else {
            pendingByOrigin.getOrPut(origin) { mutableListOf() }.add(msg)
            requestBrowserToken(origin)
        }
    }

    private fun requestBrowserToken(origin: String) {
        if (!mintInFlight.add(origin)) return
        val msg =
            Message.obtain(null, NappletIpc.MSG_MINT_BROWSER_TOKEN).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(NappletIpc.KEY_BROWSER_ORIGIN, origin) }
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

    private fun onBrokerReply(msg: Message): Boolean {
        val data = msg.data ?: return true
        when (msg.what) {
            NappletIpc.MSG_RESPONSE -> {
                val id = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                val result = runCatching { JSONObject(payload) }.getOrNull() ?: JSONObject()
                result.put("id", id)
                bridgeReplyProxy?.postMessage(result.toString())
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                bridgeReplyProxy?.postMessage(payload)
            }
            NappletIpc.MSG_BROWSER_TOKEN -> {
                val origin = data.getString(NappletIpc.KEY_BROWSER_ORIGIN) ?: return true
                val token = data.getString(NappletIpc.KEY_LAUNCH_TOKEN) ?: return true
                originTokens[origin] = token
                mintInFlight.remove(origin)
                pendingByOrigin.remove(origin)?.forEach { queued ->
                    queued.data.putString(NappletIpc.KEY_LAUNCH_TOKEN, token)
                    sendToBroker(queued)
                }
            }
            else -> return false
        }
        return true
    }

    /**
     * Routes WebView traffic through the Tor SOCKS proxy when [port] > 0, else clears the override.
     * Process-global (this `:napplet` process hosts only sandbox WebViews) and best-effort.
     */
    private fun applyWebViewProxy(port: Int) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val executor = Executor { it.run() }
        runCatching {
            if (port > 0) {
                val config = ProxyConfig.Builder().addProxyRule("socks5://127.0.0.1:$port").build()
                ProxyController.getInstance().setProxyOverride(config, executor) {}
            } else {
                ProxyController.getInstance().clearProxyOverride(executor) {}
            }
        }.onFailure { Log.w(TAG, "Failed to apply WebView proxy override", it) }
    }

    /** Persists the per-host Tor choice in the main process and re-applies it to the live WebView. */
    private fun setNetworkMode(newUseTor: Boolean) {
        useTor = newUseTor
        applyWebViewProxy(if (useTor) proxyPort else -1)
        if (this::webView.isInitialized) webView.reload()
        // Key the persisted choice on the host actually displayed (which may differ from startUrl after
        // in-page navigation), so the preference sticks to the right site.
        val liveUrl = if (this::webView.isInitialized) webView.url ?: startUrl else startUrl
        val host = runCatching { liveUrl.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_WEB_TOR).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_WEB_HOST, host)
                        putBoolean(NappletIpc.KEY_NETWORK_USE_TOR, newUseTor)
                    }
            }
        if (brokerMessenger != null) sendToBroker(msg)
    }

    // ---- trusted chrome + loading ----

    private var title: String = ""

    private fun barTitle(): String = title.ifBlank { runCatching { startUrl.toUri().host }.getOrNull() ?: getString(CommonsR.string.napplet_untitled) }

    /**
     * The top pull-down sheet: a small grabber at the top edge (out of the corner where a site shows its
     * own avatar) that expands to the Tor toggle (when Tor is available) and reload. The page can't draw
     * over it. Mirrors the embedded tabs' Compose `TopControlSheet`.
     */
    private fun buildControlSheet(): View =
        NappletControlSheet(
            context = this,
            title = barTitle(),
            isSandbox = false,
            onReload = { if (this::webView.isInitialized) webView.reload() },
            torInitiallyOn = if (proxyPort > 0) useTor else null,
            onToggleTor = { setNetworkMode(it) },
            onInfo = null,
            onPermissions = { openPermissions() },
            liveUrl = startUrl,
            onNavigate = { loadAddress(it) },
            onConsole = { show -> consolePanel?.setShowing(show) },
            isFavoriteInitially = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false),
            onFavoriteToggle = { url, _ -> sendFavoriteToggle(url) },
        ).also { controlSheet = it }

    /**
     * Ask the broker to open the editable permission screen for the site currently displayed. NIP-07 grants
     * for a plain browser are keyed per visited origin (`browser:<origin>`), so we send the live origin and
     * the broker launches the main activity at that Connected Apps detail.
     */
    private fun openPermissions() {
        val liveUrl = if (this::webView.isInitialized) webView.url ?: startUrl else startUrl
        val uri = runCatching { liveUrl.toUri() }.getOrNull() ?: return
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return
        val origin = "$scheme://$host" + if (uri.port > 0) ":${uri.port}" else ""
        val msg =
            Message.obtain(null, NappletIpc.MSG_OPEN_PERMISSIONS).apply {
                data = Bundle().apply { putString(NappletIpc.KEY_BROWSER_ORIGIN, origin) }
            }
        if (brokerMessenger != null) sendToBroker(msg) else pendingBrokerRequests.add(msg)
    }

    private fun sendFavoriteToggle(url: String) {
        val host =
            runCatching {
                android.net.Uri
                    .parse(url)
                    .host
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
        val msg =
            Message.obtain(null, NappletIpc.MSG_TOGGLE_WEB_FAVORITE).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_FAVORITE_URL, url)
                        putString(NappletIpc.KEY_FAVORITE_LABEL, host)
                    }
            }
        if (brokerMessenger != null) sendToBroker(msg) else pendingBrokerRequests.add(msg)
    }

    private fun buildConsolePanel(): View =
        NappletConsolePanel(this).also {
            it.onClearCallback = { controlSheet?.updateConsoleCount(0) }
            consolePanel = it
        }

    private fun buildLoadingView(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(
                TextView(this@NappletBrowserActivity).apply {
                    text = barTitle()
                    setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                    textSize = 18f
                    gravity = Gravity.CENTER
                },
            )
            addView(View(this@NappletBrowserActivity).apply { layoutParams = LinearLayout.LayoutParams(1, dp(20)) })
            addView(ProgressBar(this@NappletBrowserActivity))
        }

    /**
     * A thin determinate progress bar pinned to the top edge, like a browser's. Driven by
     * [BrowserChromeClient.onProgressChanged]: visible while the page loads and gone at 100%.
     */
    private fun buildTopProgressBar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = false
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(resolveThemeColor(android.R.attr.colorPrimary))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP)
        }

    /** Shows the thin top bar at [progress]% while loading, hiding it once the page is fully loaded. */
    private fun updateLoadProgress(progress: Int) {
        if (progress >= 100) {
            topProgressBar.visibility = View.GONE
        } else {
            topProgressBar.progress = progress
            topProgressBar.visibility = View.VISIBLE
        }
    }

    /** Appends a single ERROR line to the console panel and refreshes the chrome's unread count. */
    private fun logConsoleError(
        request: WebResourceRequest,
        message: String,
    ) {
        val panel = consolePanel ?: return
        panel.appendLog(ConsoleMessage.MessageLevel.ERROR, message, request.url?.toString().orEmpty(), 0)
        controlSheet?.updateConsoleCount(panel.entryCount)
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebContract.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    companion object {
        private const val TAG = "NappletBrowserActivity"

        /** How often a resumed browser renews its foreground lease (well under the broker's 90s TTL). */
        private const val FOREGROUND_HEARTBEAT_MS = 30_000L

        /** Max favicon edge (px) before sending over IPC — keeps the PNG tiny, well under the Binder limit. */
        private const val ICON_MAX_PX = 96

        /** Grace period after page-finish before the declared-icon sniff runs, so `onReceivedIcon` wins first. */
        private const val FAVICON_SNIFF_DELAY_MS = 1_200L

        private const val EXTRA_URL = "url"
        private const val EXTRA_PROXY_PORT = "proxyPort"
        private const val EXTRA_USE_TOR = "useTor"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THEME = "theme"
        private const val EXTRA_IS_FAVORITE = "isFavorite"

        fun intent(
            context: Context,
            url: String,
            proxyPort: Int,
            useTor: Boolean,
            title: String = "",
            theme: String = "SYSTEM",
            isFavorite: Boolean = false,
            webViewProfile: String? = null,
        ): Intent =
            Intent()
                .setClassName(context, "com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity")
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_PROXY_PORT, proxyPort)
                .putExtra(EXTRA_USE_TOR, useTor)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_THEME, theme)
                .putExtra(EXTRA_IS_FAVORITE, isFavorite)
                // Opaque per-account storage partition; shares the host contract's key so there is one
                // name for the concept across every WebView creation site.
                .putExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE, webViewProfile)
                // Distinct task identity per URL for documentLaunchMode=intoExisting.
                .setData(url.toUri())
    }
}
