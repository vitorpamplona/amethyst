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
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
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
    ) {
        var webView: WebView? = null
        var bridgeReplyProxy: JavaScriptReplyProxy? = null
        var fireSeq = 0

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
                        url = data.getString(NappletBrowserContract.KEY_URL)?.ifBlank { "about:blank" } ?: "about:blank",
                        proxyPort = data.getInt(NappletBrowserContract.KEY_PROXY_PORT, -1),
                        useTor = data.getBoolean(NappletBrowserContract.KEY_USE_TOR, false),
                        bgColor = data.getInt(NappletBrowserContract.KEY_BG_COLOR, android.graphics.Color.WHITE),
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
            else -> return false
        }
        return true
    }

    /** Builds the SandboxedUiAdapter for [tab] and ships its cross-process handle (coreLibInfo) to the client. */
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
        val wv = WebView(context)
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
        ) = pushUrl(tab, view)

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = pushUrl(tab, view)

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) = pushUrl(tab, view)
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
    private fun normalizeUrl(input: String): String = OmniboxInput.resolve(input)?.url ?: "about:blank"

    private companion object {
        private const val TAG = "NappletBrowserService"
    }
}
