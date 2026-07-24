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
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.privacysandbox.ui.provider.toCoreLibInfo
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.sha256.sha256
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor

/**
 * Provider for an **embedded** nsite/napplet tab — the in-app-tab counterpart of [NappletHostActivity].
 * Runs in the keyless `:napplet` process: it serves the trusted shell + the manifest's already-verified
 * blobs (via [NappletContentServer]), relays the applet's `window.napplet.*` calls to the main-process
 * broker (with this launch's single token), and exposes the WebView to the main app as a
 * `SandboxedUiAdapter` so only pixels + input cross the process boundary — never the keys, which live
 * only in the main process where every brokered op is still consent-gated.
 *
 * Shares [NappletHostActivity]'s trust model and resource edge; differs only in being a windowless
 * Service whose surface the main app embeds (vs. an Activity that owns the screen). The trusted chrome
 * is drawn by the main process around the surface. Requires API 30+ (SurfaceControlViewHost).
 */
@RequiresApi(Build.VERSION_CODES.R)
class NappletHostService : Service() {
    private val incoming = Messenger(Handler(Looper.getMainLooper(), ::onClientMessage))

    /**
     * Per-embedded-tab state. A single NappletHostService instance is shared by every embedded
     * napplet/nSite tab (they bind the same Intent), so each tab's config, content server, WebView, and
     * broker bridge live here, keyed by [sessionId]. Each tab has its OWN [replyMessenger] so broker
     * responses AND unsolicited pushes come back tagged to the right tab (the broker echoes replyTo).
     */
    private inner class NappletTab(
        val sessionId: String,
        var clientMessenger: Messenger?,
        val paths: List<PathTag>,
        val servers: List<String>,
        val author: String,
        val identifier: String,
        val launchToken: String,
        val profile: HostProfile,
        val useTor: Boolean,
        val proxyPort: Int,
        val bgColor: Int,
        val themeType: String,
        // Opaque per-account WebView storage-profile name (see NappletWebViewProfile).
        val webViewProfile: String?,
        val declaredDomains: List<String>,
    ) {
        // Read on the WebView worker thread in shouldInterceptRequest; written on the main thread in
        // createHostWebView — @Volatile gives the happens-before so the worker never sees a stale null.
        @Volatile var contentServer: NappletContentServer? = null
        var webView: WebView? = null
        var bridgeReplyProxy: JavaScriptReplyProxy? = null
        var fireSeq = 0

        // Last main-frame error state, pushed to the client so it can show an error/retry overlay over the
        // surface (the embedded surface has no error page of its own).
        var loadFailed = false
        val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { onBrokerReply(this, it) })
    }

    private val tabs = mutableMapOf<String, NappletTab>()

    // The shell HTML and shim never change; read+decode them once instead of per tab on the main thread.
    private val shellHtml: ByteArray by lazy { readContractAsset(NappletWebContract.SHELL_HTML_PATH) }
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
        tabs.values.forEach {
            it.contentServer?.close()
            it.webView?.destroy()
        }
        tabs.clear()
        super.onDestroy()
    }

    private fun tabFor(msg: Message): NappletTab? = msg.data?.getString(NappletEmbedContract.KEY_SESSION_ID)?.let { tabs[it] }

    private fun tabFor(view: WebView): NappletTab? = tabs.values.firstOrNull { it.webView === view }

    private fun onClientMessage(msg: Message): Boolean {
        when (msg.what) {
            NappletEmbedContract.MSG_CREATE_SESSION -> {
                val tab = buildTab(msg) ?: return true
                tabs[tab.sessionId] = tab
                // Bind the broker once; a re-sent MSG_CREATE_SESSION must not leak a second binding.
                if (!brokerBound) {
                    brokerBound = bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
                }
                replyWithAdapter(tab)
            }
            NappletEmbedContract.MSG_BACK -> tabFor(msg)?.webView?.let { if (it.canGoBack()) it.goBack() }
            NappletEmbedContract.MSG_RELOAD -> tabFor(msg)?.webView?.reload()
            // onPause()/onResume() are per-WebView (pause/resume THIS surface's JS/DOM). Do NOT call
            // pauseTimers()/resumeTimers(): they are process-global and would freeze/thaw every WebView in
            // `:napplet` (the browser embed + other napplets), whose lifecycles are independent of this one.
            NappletEmbedContract.MSG_PAUSE -> tabFor(msg)?.webView?.onPause()
            NappletEmbedContract.MSG_RESUME -> tabFor(msg)?.webView?.onResume()
            NappletEmbedContract.MSG_IME_OP -> {
                val tab = tabFor(msg) ?: return true
                val payload = msg.data?.getString(NappletEmbedContract.KEY_IME_PAYLOAD) ?: return true
                tab.bridgeReplyProxy?.postMessage(payload)
            }
            NappletEmbedContract.MSG_MAGNIFIER_REQUEST -> onMagnifierRequest(msg)
            else -> return false
        }
        return true
    }

    private fun buildTab(msg: Message): NappletTab? {
        val data = msg.data ?: return null
        val sessionId = data.getString(NappletEmbedContract.KEY_SESSION_ID) ?: return null
        val pathList = data.getStringArrayList(NappletHostContract.EXTRA_PATHS) ?: return null
        val hashList = data.getStringArrayList(NappletHostContract.EXTRA_HASHES) ?: return null
        if (pathList.size != hashList.size || pathList.isEmpty()) return null
        val author = data.getString(NappletHostContract.EXTRA_AUTHOR).orEmpty()
        val launchToken = data.getString(NappletHostContract.EXTRA_LAUNCH_TOKEN).orEmpty()
        if (author.isEmpty() || launchToken.isEmpty()) return null

        val requires = data.getStringArrayList(NappletHostContract.EXTRA_REQUIRES) ?: emptyList()
        val declaredDomains = (listOf("shell") + resolveRequiredCapabilities(requires).capabilities.map { it.name.lowercase() }).distinct()

        val tab =
            NappletTab(
                sessionId = sessionId,
                clientMessenger = msg.replyTo,
                paths = pathList.indices.map { PathTag(pathList[it], hashList[it]) },
                servers = data.getStringArrayList(NappletHostContract.EXTRA_SERVERS) ?: emptyList(),
                author = author,
                identifier = data.getString(NappletHostContract.EXTRA_IDENTIFIER).orEmpty(),
                launchToken = launchToken,
                profile = HostProfile.fromName(data.getString(NappletHostContract.EXTRA_HOST_PROFILE)),
                useTor = data.getBoolean(NappletHostContract.EXTRA_USE_TOR, true),
                proxyPort = data.getInt(NappletHostContract.EXTRA_PROXY_PORT, -1),
                bgColor = data.getInt(NappletHostContract.EXTRA_BG_COLOR, android.graphics.Color.WHITE),
                themeType = data.getString(NappletHostContract.EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" },
                webViewProfile = data.getString(NappletHostContract.EXTRA_WEBVIEW_PROFILE),
                declaredDomains = declaredDomains,
            )
        return tab
    }

    /**
     * Draw a zoomed slice of the live WebView (the loupe content) and ship it back as a PNG. The WebView is a
     * real in-window view here in the provider, so its software draw renders real DOM pixels — unlike host-side
     * `PixelCopy` of the sandbox surface. Mirror of the browser path. Runs on the main looper (`WebView.draw`).
     */
    private fun onMagnifierRequest(msg: Message) {
        val tab = tabFor(msg) ?: return
        val wv = tab.webView ?: return
        val data = msg.data ?: return
        val cx = data.getFloat(NappletEmbedContract.KEY_MAG_X)
        val cy = data.getFloat(NappletEmbedContract.KEY_MAG_Y)
        val boxW = data.getInt(NappletEmbedContract.KEY_MAG_BOX_W, 150).coerceIn(16, 1024)
        val boxH = data.getInt(NappletEmbedContract.KEY_MAG_BOX_H, 84).coerceIn(16, 1024)
        val zoom = data.getFloat(NappletEmbedContract.KEY_MAG_ZOOM, 1.6f).coerceIn(1f, 4f)
        val reqT = data.getLong(NappletEmbedContract.KEY_MAG_REQ_T)

        val outW = (boxW * zoom).toInt().coerceAtLeast(1)
        val outH = (boxH * zoom).toInt().coerceAtLeast(1)
        val t0 = SystemClock.elapsedRealtimeNanos()
        val bitmap = createBitmap(outW, outH)
        val canvas = Canvas(bitmap)
        canvas.drawColor(tab.bgColor)
        canvas.scale(zoom, zoom)
        canvas.translate(-(cx - boxW / 2f), -(cy - boxH / 2f))
        wv.draw(canvas)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val bytes = baos.toByteArray()
        bitmap.recycle()
        val captureMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0

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
        runCatching { tab.clientMessenger?.send(reply) }
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
        runCatching { tab.clientMessenger?.send(reply) }
    }

    /**
     * Builds the session WebView (called by the adapter on the main thread when the client attaches the
     * surface). Mirrors [NappletHostActivity]: serves the shell + verified blobs through the content
     * server, installs the origin-restricted shell bridge, loads the trusted shell URL.
     */
    fun createHostWebView(
        context: Context,
        sessionId: String,
    ): WebView {
        // The session may have been closed between MSG_CREATE_SESSION and this posted call — fail rather
        // than build a WebView that no tab tracks (it would leak).
        val tab = tabs[sessionId] ?: error("No napplet tab for session $sessionId")
        val wv = WebView(nightThemedContext(context, tab.themeType))
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before the
        // hardening/bridge setup and the loadUrl below.
        NappletWebViewProfile.apply(context, wv, tab.webViewProfile)
        val appOrigin = NappletWebContract.appOrigin(deriveAppId(tab.author, tab.identifier))
        val effectiveProxy = if (tab.useTor) tab.proxyPort else -1
        tab.contentServer = NappletContentServer(tab.paths, tab.servers, effectiveProxy, cacheDir, shellHtml, shimJs, appOrigin, tab.profile, imeProxy = true)

        hardenWebView(wv, tab)
        // Theme the pre-load background so the shell/app loading shows Amethyst's background, not white.
        wv.setBackgroundColor(tab.bgColor)
        wv.dropSystemBarInsets()
        if (tab.profile.exposesNetwork) applyWebViewProxy(effectiveProxy)
        WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf(NappletWebContract.ORIGIN), ::onShellMessage)
        tab.webView = wv
        wv.loadUrl(NappletWebContract.SHELL_URL)
        return wv
    }

    /** A session closed: drop the tab, release its resources, and destroy its own WebView (never a sibling's). */
    fun onSessionClosed(sessionId: String) {
        val tab = tabs.remove(sessionId) ?: return
        tab.bridgeReplyProxy = null
        tab.contentServer?.close()
        tab.contentServer = null
        tab.webView?.destroy()
        tab.webView = null
    }

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView(
        wv: WebView,
        tab: NappletTab,
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
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                safeBrowsingEnabled = true
            }
        }
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        WebView.setWebContentsDebuggingEnabled(false)
        wv.webViewClient = HostClient(tab)
    }

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

    /** Serves only the trusted shell and the manifest's verified blobs; external links go to the system. */
    private inner class HostClient(
        private val tab: NappletTab,
    ) : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = tab.contentServer?.serve(request)

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: android.graphics.Bitmap?,
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
            // Only a main-frame failure blanks the applet; a missing sub-resource is irrelevant to whether
            // it opened.
            if (!request.isForMainFrame) return
            tab.loadFailed = true
            pushLoadState(tab, isLoading = false)
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            if (NappletWebContract.isInternalHost(uri.host)) return false
            if (request.hasGesture() && (uri.scheme == "https" || uri.scheme == "http")) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return true
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
        runCatching { tab.clientMessenger?.send(message) }
    }

    /** Tells the client whether a main-frame load is in flight and whether it failed, so it can overlay a spinner/retry. */
    private fun pushLoadState(
        tab: NappletTab,
        isLoading: Boolean,
    ) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_LOAD_STATE).apply {
                data =
                    Bundle().apply {
                        putBoolean(NappletEmbedContract.KEY_IS_LOADING, isLoading)
                        putBoolean(NappletEmbedContract.KEY_LOAD_FAILED, tab.loadFailed)
                    }
            }
        runCatching { tab.clientMessenger?.send(message) }
    }

    // ---- bridge: shell <-> native (mirror of NappletHostActivity.onShellMessage) ----

    private fun onShellMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return
        val tab = tabFor(view) ?: return
        tab.bridgeReplyProxy = replyProxy

        val raw = message.data ?: return
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return

        if (envelope.optString("type") == "shell.ready") {
            runCatching { replyProxy.postMessage(NappletProtocolJson.encodeShellInit(tab.declaredDomains, tab.declaredDomains)) }
            return
        }

        // IME events aren't brokered — the main app hosts the keyboard. Relay the envelope to the client.
        if (envelope.optString("type").startsWith("ime.")) {
            val reply =
                Message.obtain(null, NappletEmbedContract.MSG_IME_EVENT).apply {
                    data = Bundle().apply { putString(NappletEmbedContract.KEY_IME_PAYLOAD, raw) }
                }
            runCatching { tab.clientMessenger?.send(reply) }
            return
        }

        val id = envelope.optString("id").ifEmpty { "fire-${tab.fireSeq++}" }
        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = tab.replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, raw)
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, tab.launchToken)
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
        tab: NappletTab,
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
                notifyIfSensitive(tab, result)
                runCatching { tab.bridgeReplyProxy?.postMessage(result.toString()) }
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                runCatching { tab.bridgeReplyProxy?.postMessage(payload) }
            }
            else -> return false
        }
        return true
    }

    /** Pushes a notice to the main process for a granted "allow always" sensitive op, so it can toast. */
    private fun notifyIfSensitive(
        tab: NappletTab,
        result: JSONObject,
    ) {
        if (!result.optBoolean("ok")) return
        val notice =
            when (result.optString("type")) {
                "relay.publish.result", "relay.publishEncrypted.result" -> NappletEmbedContract.NOTICE_PUBLISHED
                "upload.upload.result" -> NappletEmbedContract.NOTICE_UPLOADED
                "value.payInvoice.result" -> NappletEmbedContract.NOTICE_PAID
                else -> return
            }
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_NOTICE).apply {
                data = Bundle().apply { putString(NappletEmbedContract.KEY_NOTICE, notice) }
            }
        runCatching { tab.clientMessenger?.send(message) }
    }

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebContract.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    private fun deriveAppId(
        author: String,
        identifier: String,
    ): String = "n" + sha256("$author:$identifier".encodeToByteArray()).toHexKey().take(31)

    private companion object {
        private const val TAG = "NappletHostService"
    }
}
