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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.provider.toCoreLibInfo
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import org.json.JSONObject

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

    // The main-process client, kept to push URL/state updates that drive its address bar.
    private var clientMessenger: Messenger? = null

    // Session config captured at create time; consumed when the library opens the session (WebView built).
    private var pendingUrl: String = "about:blank"
    private var proxyPort: Int = -1
    private var useTor: Boolean = false

    // Amethyst's theme background (ARGB), passed from the main process; paints the WebView before the
    // page renders so it matches the app instead of flashing the WebView default white.
    private var bgColor: Int = android.graphics.Color.WHITE

    private var webView: WebView? = null

    // ---- broker bridge (identical trust model to NappletHostActivity) ----
    private var brokerMessenger: Messenger? = null
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper(), ::onBrokerReply))
    private val pendingBrokerRequests = mutableListOf<Message>()
    private var bridgeReplyProxy: JavaScriptReplyProxy? = null
    private var fireSeq = 0

    // Per visited origin: its broker-minted launch token, the requests queued until it arrives, and the
    // set of origins a mint is already in flight for — so NIP-07 consent is scoped per site.
    private val originTokens = mutableMapOf<String, String>()
    private val pendingByOrigin = mutableMapOf<String, MutableList<Message>>()
    private val mintInFlight = mutableSetOf<String>()

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
        runCatching { unbindService(brokerConnection) }
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun onClientMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletBrowserContract.MSG_CREATE_SESSION -> {
                val data = msg.data ?: return true
                clientMessenger = msg.replyTo
                pendingUrl = data.getString(NappletBrowserContract.KEY_URL)?.ifBlank { "about:blank" } ?: "about:blank"
                proxyPort = data.getInt(NappletBrowserContract.KEY_PROXY_PORT, -1)
                useTor = data.getBoolean(NappletBrowserContract.KEY_USE_TOR, false)
                bgColor = data.getInt(NappletBrowserContract.KEY_BG_COLOR, android.graphics.Color.WHITE)
                bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
                replyWithAdapter()
            }
            NappletBrowserContract.MSG_NAVIGATE -> webView?.loadUrl(normalizeUrl(msg.data?.getString(NappletBrowserContract.KEY_URL).orEmpty()))
            NappletBrowserContract.MSG_RELOAD -> webView?.reload()
            NappletBrowserContract.MSG_BACK -> webView?.let { if (it.canGoBack()) it.goBack() }
            NappletBrowserContract.MSG_SET_TOR -> {
                useTor = msg.data?.getBoolean(NappletBrowserContract.KEY_USE_TOR, false) ?: false
                applyWebViewProxy(if (useTor) proxyPort else -1)
                webView?.reload()
            }
            else -> return false
        }
        return true
    }

    /** Builds the SandboxedUiAdapter and ships its cross-process handle (coreLibInfo) to the client. */
    private fun replyWithAdapter() {
        val adapter = NappletBrowserUiAdapter(this)
        val coreLibInfo = adapter.toCoreLibInfo(this)
        val reply =
            Message.obtain(null, NappletBrowserContract.MSG_SESSION_READY).apply {
                data = Bundle().apply { putBundle(NappletBrowserContract.KEY_CORE_LIB_INFO, coreLibInfo) }
            }
        runCatching { clientMessenger?.send(reply) }
    }

    /**
     * Builds and configures the session's WebView (called by the adapter on the main thread when the
     * client attaches the surface). Injects the same NIP-07 shim [NappletHostActivity] uses, at document
     * start for every origin, reaching the broker directly (no shell). Loads the pending URL.
     */
    fun createBrowserWebView(context: Context): WebView {
        val wv = WebView(context)
        configureWebView(wv)
        // Theme the pre-load background so a blank/loading page shows Amethyst's background, not white.
        wv.setBackgroundColor(bgColor)
        // DIAGNOSTIC (scrolling): log whether touch input crosses the SurfaceControlViewHost boundary to
        // the remote WebView at all. Returns false so it never consumes — the WebView still scrolls.
        wv.setOnTouchListener { _, event ->
            Log.w(TAG, "DIAG browser WebView touch action=${event.actionMasked} x=${event.x} y=${event.y}")
            false
        }
        wv.dropSystemBarInsets()
        applyWebViewProxy(if (useTor) proxyPort else -1)
        val shim = readContractAsset(NappletWebContract.SHIM_JS_PATH).decodeToString()
        WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf("*"), ::onBridgeMessage)
        val startScript = "if (window.top === window) { window.__nappletDirectBridge = true; window.__nappletNip07 = true; }\n$shim"
        WebViewCompat.addDocumentStartJavaScript(wv, startScript, setOf("*"))
        webView = wv
        wv.loadUrl(pendingUrl)
        return wv
    }

    fun onSessionClosed() {
        webView?.destroy()
        webView = null
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
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
    }

    /** Loads live web pages in-WebView (http/https) and hands other schemes to the system on a user tap. */
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
            favicon: android.graphics.Bitmap?,
        ) = pushUrl(view)

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = pushUrl(view)

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            // DIAGNOSTIC (zoom): a responsive page rendering too large points to a density/viewport
            // mismatch from streaming the WebView through SurfaceControlViewHost. scale≈4 confirms 400%.
            val dm = view.resources.displayMetrics
            @Suppress("DEPRECATION")
            Log.w(TAG, "DIAG zoom: scale=${view.scale} density=${dm.density} dmWidthPx=${dm.widthPixels} webViewWidthPx=${view.width}")
            pushUrl(view)
        }
    }

    private fun pushUrl(view: WebView) {
        val url = view.url ?: return
        val message =
            Message.obtain(null, NappletBrowserContract.MSG_URL_CHANGED).apply {
                data =
                    Bundle().apply {
                        putString(NappletBrowserContract.KEY_URL, url)
                        putBoolean(NappletBrowserContract.KEY_CAN_GO_BACK, view.canGoBack())
                    }
            }
        runCatching { clientMessenger?.send(message) }
    }

    /**
     * Routes WebView traffic through the Tor SOCKS proxy when [port] > 0, else clears the override.
     * Process-global (this `:napplet` process hosts only sandbox WebViews) and best-effort.
     */
    private fun applyWebViewProxy(port: Int) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
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
                    .setProxyOverride(config, executor) {}
            } else {
                androidx.webkit.ProxyController
                    .getInstance()
                    .clearProxyOverride(executor) {}
            }
        }.onFailure { Log.w(TAG, "Failed to apply WebView proxy override", it) }
    }

    /**
     * A top-frame NIP-07 call from [sourceOrigin]. The origin is the trusted value the WebView reports
     * (the page can't forge it), so it keys consent; each origin uses its own broker-minted launch token.
     */
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

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebContract.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    /** Address-bar text → URL: keep an explicit scheme, prefix a bare domain, else DuckDuckGo search. */
    private fun normalizeUrl(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return "about:blank"
        if (text.contains("://")) return text
        if (!text.contains(' ') && text.contains('.')) return "https://$text"
        return "https://duckduckgo.com/?q=" + Uri.encode(text)
    }

    private companion object {
        private const val TAG = "NappletBrowserService"
    }
}
