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
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
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
    private var clientMessenger: Messenger? = null

    private val paths = mutableListOf<PathTag>()
    private val servers = mutableListOf<String>()
    private var author = ""
    private var identifier = ""
    private var launchToken = ""
    private var websiteMode = false
    private var useTor = true
    private var proxyPort = -1
    private var bgColor = android.graphics.Color.WHITE
    private var declaredDomains: List<String> = emptyList()

    private lateinit var contentServer: NappletContentServer

    private var webView: WebView? = null

    // ---- broker bridge (identical trust model to NappletHostActivity: one launch token) ----
    private var brokerMessenger: Messenger? = null
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper(), ::onBrokerReply))
    private val pendingBrokerRequests = mutableListOf<Message>()
    private var bridgeReplyProxy: JavaScriptReplyProxy? = null
    private var fireSeq = 0

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
            NappletEmbedContract.MSG_CREATE_SESSION -> {
                if (!readParams(msg)) return true
                clientMessenger = msg.replyTo
                bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
                replyWithAdapter()
            }
            NappletEmbedContract.MSG_BACK -> webView?.let { if (it.canGoBack()) it.goBack() }
            NappletEmbedContract.MSG_RELOAD -> webView?.reload()
            NappletEmbedContract.MSG_PAUSE ->
                webView?.let {
                    it.onPause()
                    it.pauseTimers()
                }
            NappletEmbedContract.MSG_RESUME ->
                webView?.let {
                    it.onResume()
                    it.resumeTimers()
                }
            else -> return false
        }
        return true
    }

    private fun readParams(msg: Message): Boolean {
        val data = msg.data ?: return false
        val pathList = data.getStringArrayList(NappletHostContract.EXTRA_PATHS) ?: return false
        val hashList = data.getStringArrayList(NappletHostContract.EXTRA_HASHES) ?: return false
        if (pathList.size != hashList.size || pathList.isEmpty()) return false
        for (i in pathList.indices) paths.add(PathTag(pathList[i], hashList[i]))
        servers.addAll(data.getStringArrayList(NappletHostContract.EXTRA_SERVERS) ?: emptyList())
        author = data.getString(NappletHostContract.EXTRA_AUTHOR).orEmpty()
        identifier = data.getString(NappletHostContract.EXTRA_IDENTIFIER).orEmpty()
        websiteMode = data.getBoolean(NappletHostContract.EXTRA_WEBSITE_MODE, false)
        useTor = data.getBoolean(NappletHostContract.EXTRA_USE_TOR, true)
        proxyPort = data.getInt(NappletHostContract.EXTRA_PROXY_PORT, -1)
        bgColor = data.getInt(NappletHostContract.EXTRA_BG_COLOR, android.graphics.Color.WHITE)
        launchToken = data.getString(NappletHostContract.EXTRA_LAUNCH_TOKEN).orEmpty()

        val requires = data.getStringArrayList(NappletHostContract.EXTRA_REQUIRES) ?: emptyList()
        declaredDomains = (listOf("shell") + resolveRequiredCapabilities(requires).capabilities.map { it.name.lowercase() }).distinct()

        return author.isNotEmpty() && launchToken.isNotEmpty()
    }

    /** Builds the SandboxedUiAdapter and ships its cross-process handle (coreLibInfo) to the client. */
    private fun replyWithAdapter() {
        val adapter = NappletHostUiAdapter(this)
        val coreLibInfo = adapter.toCoreLibInfo(this)
        val reply =
            Message.obtain(null, NappletEmbedContract.MSG_SESSION_READY).apply {
                data = Bundle().apply { putBundle(NappletEmbedContract.KEY_CORE_LIB_INFO, coreLibInfo) }
            }
        runCatching { clientMessenger?.send(reply) }
    }

    /**
     * Builds the session WebView (called by the adapter on the main thread when the client attaches the
     * surface). Mirrors [NappletHostActivity]: serves the shell + verified blobs through the content
     * server, installs the origin-restricted shell bridge, loads the trusted shell URL.
     */
    fun createHostWebView(context: Context): WebView {
        val shellHtml = readContractAsset(NappletWebContract.SHELL_HTML_PATH)
        val shim = readContractAsset(NappletWebContract.SHIM_JS_PATH).decodeToString()
        val appOrigin = NappletWebContract.appOrigin(deriveAppId(author, identifier))
        val effectiveProxy = if (useTor) proxyPort else -1
        contentServer = NappletContentServer(paths, servers, effectiveProxy, cacheDir, shellHtml, shim, appOrigin, websiteMode)

        val wv = WebView(context)
        hardenWebView(wv)
        // Theme the pre-load background so the shell/app loading shows Amethyst's background, not white.
        wv.setBackgroundColor(bgColor)
        wv.dropSystemBarInsets()
        if (websiteMode) applyWebViewProxy(effectiveProxy)
        WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf(NappletWebContract.ORIGIN), ::onShellMessage)
        webView = wv
        wv.loadUrl(NappletWebContract.SHELL_URL)
        return wv
    }

    fun onSessionClosed() {
        webView?.destroy()
        webView = null
    }

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView(wv: WebView) {
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
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                safeBrowsingEnabled = true
            }
        }
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        WebView.setWebContentsDebuggingEnabled(false)
        wv.webViewClient = HostClient()
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
    private inner class HostClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = contentServer.serve(request)

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = pushState(view)

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) = pushState(view)

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

    private fun pushState(view: WebView) {
        val message =
            Message.obtain(null, NappletEmbedContract.MSG_STATE).apply {
                data = Bundle().apply { putBoolean(NappletEmbedContract.KEY_CAN_GO_BACK, view.canGoBack()) }
            }
        runCatching { clientMessenger?.send(message) }
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
        bridgeReplyProxy = replyProxy

        val raw = message.data ?: return
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return

        if (envelope.optString("type") == "shell.ready") {
            runCatching { replyProxy.postMessage(NappletProtocolJson.encodeShellInit(declaredDomains, declaredDomains)) }
            return
        }

        val id = envelope.optString("id").ifEmpty { "fire-${fireSeq++}" }
        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, raw)
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
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

    private fun onBrokerReply(msg: Message): Boolean {
        val data = msg.data ?: return true
        when (msg.what) {
            NappletIpc.MSG_RESPONSE -> {
                val id = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return true
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                val result = runCatching { JSONObject(payload) }.getOrNull() ?: JSONObject()
                result.put("id", id)
                notifyIfSensitive(result)
                bridgeReplyProxy?.postMessage(result.toString())
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                bridgeReplyProxy?.postMessage(payload)
            }
            else -> return false
        }
        return true
    }

    /** Pushes a notice to the main process for a granted "allow always" sensitive op, so it can toast. */
    private fun notifyIfSensitive(result: JSONObject) {
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
        runCatching { clientMessenger?.send(message) }
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
