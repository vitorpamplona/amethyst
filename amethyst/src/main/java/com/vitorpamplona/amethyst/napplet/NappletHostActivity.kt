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
package com.vitorpamplona.amethyst.napplet

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * Hosts a napplet/nsite WebView in the isolated `:napplet` process — a process that holds **no**
 * account state, signer, or keys. It:
 *
 * 1. serves the trusted shell page and the manifest's **already-verified** blobs through
 *    [WebViewClient.shouldInterceptRequest] (no `file://`/`content://`, default-deny CSP with
 *    `connect-src 'none'` so the applet has no direct network), and
 * 2. relays the applet's `window.napplet.*` capability calls — applet → shell (postMessage) →
 *    native (origin-restricted [WebViewCompat.addWebMessageListener]) → main-process broker
 *    (Messenger) → back — without ever interpreting them itself.
 *
 * Even a full WebView/renderer escape into this process yields no secret: the keys live only in
 * the main process, and every brokered operation is still gated by user consent there.
 */
class NappletHostActivity : ComponentActivity() {
    private lateinit var webView: WebView

    private val paths = mutableListOf<PathTag>()
    private val servers = mutableListOf<String>()
    private var author: String = ""
    private var identifier: String = ""
    private var aggregateHash: String? = null

    // Capability names (comma-separated) the manifest declared; the broker refuses anything else.
    private var declared: String = ""

    // NAP domain strings the shell advertises to the applet in the shell.init handshake.
    private var declaredDomains: List<String> = emptyList()

    // Correlation id for id-less fire-and-forget messages so they still reach the broker.
    private var fireSeq = 0

    private var proxyPort: Int = -1

    // The resource edge (shell + verified blobs); built in onCreate once the manifest is parsed.
    private lateinit var contentServer: NappletContentServer

    // Messenger to the main-process broker, bound lazily; requests queue until connected.
    private var brokerMessenger: Messenger? = null
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper(), ::onBrokerReply))
    private val pendingRequests = mutableListOf<Message>()
    private var bridgeReplyProxy: JavaScriptReplyProxy? = null

    // Keyboard/command actions the applet bound via keys.registerAction; matched in dispatchKeyEvent.
    private val keyActions = NappletKeyActions()

    private val brokerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                brokerMessenger = Messenger(service)
                pendingRequests.forEach { sendToBroker(it) }
                pendingRequests.clear()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                brokerMessenger = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!readManifestExtras()) {
            Toast.makeText(this, getString(R.string.napplet_invalid), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Toast.makeText(this, getString(R.string.napplet_webview_too_old), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // The shell page + shim are the shared web contract (commons composeResources); load them
        // once up front so the WebView worker threads that serve them never block on resource I/O.
        val (shellHtml, shim) = runBlocking { NappletWebContract.shellHtml() to NappletWebContract.shimJs().decodeToString() }
        contentServer = NappletContentServer(paths, servers, proxyPort, cacheDir, shellHtml, shim)

        webView = WebView(this)
        setContentView(webView)
        // Activities are edge-to-edge by default on recent Android; pad the WebView by the system bar
        // and display-cutout insets so the applet's own content isn't drawn under the status/nav bars.
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        hardenWebView(webView)

        // Origin-restricted bridge: only the trusted shell page (main frame) can reach native.
        WebViewCompat.addWebMessageListener(
            webView,
            NappletWebContract.BRIDGE_NAME,
            setOf(NappletWebContract.ORIGIN),
            ::onShellMessage,
        )

        bindService(Intent(this, NappletBrokerService::class.java), brokerConnection, BIND_AUTO_CREATE)

        webView.loadUrl(NappletWebContract.SHELL_URL)
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onPause() {
        // Foreground-only: stop the applet's JS/timers in the background so it cannot fire a
        // sign/decrypt/pay request whose consent prompt would surface over (and be confused with)
        // Amethyst's own UI. Requests only happen while the user is looking at this napplet.
        if (this::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unbindService(brokerConnection) }
        keyActions.clear()
        if (this::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    /**
     * Intercepts hardware-key combos the applet bound via `keys.registerAction` and turns them into a
     * `keys.action` push — the applet never sees raw key events, only its own named action. Unmatched
     * keys fall through to the WebView (so the applet's own text inputs still work normally).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val actionId = keyActions.actionFor(event)
        if (actionId != null) {
            runCatching { bridgeReplyProxy?.postMessage(NappletProtocolJson.encodeKeysAction(actionId)) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun readManifestExtras(): Boolean {
        val pathList = intent.getStringArrayListExtra(NappletLauncher.EXTRA_PATHS) ?: return false
        val hashList = intent.getStringArrayListExtra(NappletLauncher.EXTRA_HASHES) ?: return false
        if (pathList.size != hashList.size || pathList.isEmpty()) return false

        for (i in pathList.indices) paths.add(PathTag(pathList[i], hashList[i]))
        servers.addAll(intent.getStringArrayListExtra(NappletLauncher.EXTRA_SERVERS) ?: emptyList())
        author = intent.getStringExtra(NappletLauncher.EXTRA_AUTHOR).orEmpty()
        identifier = intent.getStringExtra(NappletLauncher.EXTRA_IDENTIFIER).orEmpty()
        aggregateHash = intent.getStringExtra(NappletLauncher.EXTRA_AGGREGATE_HASH)
        title = intent.getStringExtra(NappletLauncher.EXTRA_TITLE).orEmpty()
        proxyPort = intent.getIntExtra(NappletLauncher.EXTRA_PROXY_PORT, -1)

        val requires = intent.getStringArrayListExtra(NappletLauncher.EXTRA_REQUIRES) ?: emptyList()
        val resolved = resolveRequiredCapabilities(requires)
        declared = resolved.capabilities.joinToString(",") { it.name }
        // shell is always available; the rest are the declared domains the broker will honor.
        declaredDomains = (listOf("shell") + resolved.capabilities.map { it.name.lowercase() }).distinct()

        return author.isNotEmpty()
    }

    private var title: String = ""

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true // the applet needs JS; isolation comes from process + CSP + sandbox
            domStorageEnabled = false
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
        WebView.setWebContentsDebuggingEnabled(false)
        webView.webViewClient = NappletWebViewClient()
    }

    /** Serves only the trusted shell and the manifest's verified blobs; everything else 404s. */
    private inner class NappletWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = contentServer.serve(request)

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            // Internal origin: let the WebView load it (it goes through shouldInterceptRequest).
            if (uri.host == NappletWebContract.HOST) return false
            // An external link the user actually tapped is handed to the system browser. A user
            // gesture is required so a hostile site can't auto-redirect to spam-open the browser,
            // and only http(s) is honored so it can't fire arbitrary intent schemes.
            if (request.hasGesture() && (uri.scheme == "https" || uri.scheme == "http")) {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            // Never navigate the sandbox WebView away from our internal origin.
            return true
        }
    }

    // ---- bridge: shell <-> native ----

    private fun onShellMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!isMainFrame) return // only the trusted shell, never a sub-frame
        bridgeReplyProxy = replyProxy

        val raw = message.data ?: return
        // The applet sends a full upstream envelope {type, id, ...}; we forward it verbatim and
        // correlate on its id. The broker reads `type` to decode and to build the .result reply.
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return

        // Shell handshake: the SDK posts `shell.ready` (no id) and answers shell.supports() locally
        // from the `shell.init` environment we send back here.
        if (envelope.optString("type") == "shell.ready") {
            runCatching { replyProxy.postMessage(NappletProtocolJson.encodeShellInit(declaredDomains, declaredDomains)) }
            return
        }

        // Unbind a keyboard action as soon as the applet drops it (the broker's Done reply carries no
        // actionId, so the binding is removed here from the envelope itself).
        if (envelope.optString("type") == "keys.unregisterAction") {
            envelope.optString("actionId").takeIf { it.isNotEmpty() }?.let { keyActions.unregister(it) }
        }

        // Fire-and-forget messages (inc.emit, keys.unregisterAction) have no id; synthesize one so
        // they still reach the broker. Any reply is harmless — the applet has nothing to correlate.
        val id = envelope.optString("id").ifEmpty { "fire-${fireSeq++}" }

        val msg =
            Message.obtain(null, NappletIpc.MSG_REQUEST).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_PAYLOAD, raw)
                        putString(NappletIpc.KEY_AUTHOR, author)
                        putString(NappletIpc.KEY_IDENTIFIER, identifier)
                        putString(NappletIpc.KEY_AGGREGATE_HASH, aggregateHash)
                        putString(NappletIpc.KEY_DECLARED, declared)
                    }
            }

        val messenger = brokerMessenger
        if (messenger == null) {
            pendingRequests.add(msg)
        } else {
            sendToBroker(msg)
        }
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

                // payload is the broker's {type:"...result", ok, ...}; inject the correlation id for the shim.
                val result = runCatching { JSONObject(payload) }.getOrNull() ?: JSONObject()
                result.put("id", id)
                // The broker authorized a keyboard action: bind the honored key combo so dispatchKeyEvent
                // can fire it. Only ok'd registrations bind (a denied KEYS request never reaches here).
                if (result.optString("type") == "keys.registerAction.result" && result.optBoolean("ok")) {
                    val actionId = result.optString("actionId")
                    if (actionId.isNotEmpty()) keyActions.register(actionId, result.optString("binding").ifEmpty { null })
                }
                bridgeReplyProxy?.postMessage(result.toString())
            }
            // A subscription push (relay.event/relay.eose) is keyed by subId, not a request id; forward verbatim.
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                bridgeReplyProxy?.postMessage(payload)
            }
            else -> return false
        }
        return true
    }

    companion object {
        private const val TAG = "NappletHostActivity"
    }
}
