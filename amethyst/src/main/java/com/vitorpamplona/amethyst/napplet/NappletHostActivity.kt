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
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.BlobFetcher
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy

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

    private var proxyPort: Int = -1
    private val http by lazy { buildHttpClient(proxyPort) }
    private val fetch: BlobFetcher = { url ->
        try {
            http
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build(),
                ).execute()
                .use { r ->
                    if (r.isSuccessful) r.body.bytes() else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "Blob fetch failed for $url", e)
            null
        }
    }

    // Messenger to the main-process broker, bound lazily; requests queue until connected.
    private var brokerMessenger: Messenger? = null
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper(), ::onBrokerReply))
    private val pendingRequests = mutableListOf<Message>()
    private var bridgeReplyProxy: JavaScriptReplyProxy? = null

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

        webView = WebView(this)
        setContentView(webView)
        hardenWebView(webView)

        // Origin-restricted bridge: only the trusted shell page (main frame) can reach native.
        WebViewCompat.addWebMessageListener(
            webView,
            BRIDGE_NAME,
            setOf(ORIGIN),
            ::onShellMessage,
        )

        bindService(Intent(this, NappletBrokerService::class.java), brokerConnection, BIND_AUTO_CREATE)

        webView.loadUrl(SHELL_URL)
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
        if (this::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
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
        declared = resolveRequiredCapabilities(requires).capabilities.joinToString(",") { it.name }

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
        ): WebResourceResponse? {
            val url = request.url.toString()
            if (!request.method.equals("GET", ignoreCase = true)) return null
            if (!url.startsWith(ORIGIN)) return notFound()

            if (url == SHELL_URL) return serveShell()
            if (url == APP_BASE || url.startsWith(APP_BASE)) {
                // A document navigation accepts text/html; a sub-resource (js/css/img) does not.
                val acceptsHtml = request.requestHeaders["Accept"]?.contains("text/html", ignoreCase = true) == true
                return serveAppResource(url, acceptsHtml)
            }
            return notFound()
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            // Internal origin: let the WebView load it (it goes through shouldInterceptRequest).
            if (uri.host == HOST) return false
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

    private fun serveShell(): WebResourceResponse {
        val bytes = assets.open("napplet/shell.html").use { it.readBytes() }
        return WebResourceResponse(
            "text/html",
            "utf-8",
            200,
            "OK",
            mapOf("Content-Security-Policy" to SHELL_CSP),
            ByteArrayInputStream(bytes),
        )
    }

    private fun serveAppResource(
        url: String,
        acceptsHtml: Boolean,
    ): WebResourceResponse {
        val requestPath =
            url
                .removePrefix(APP_BASE)
                .substringBefore('?')
                .substringBefore('#')
                .let { if (it.isEmpty()) "/" else "/$it" }

        var resolution = runBlocking { StaticSiteResolver.resolve(requestPath, paths, servers, fetch) }

        // SPA fallback: a document navigation (Accept: text/html) to a route that isn't in the
        // manifest falls back to the verified index.html, so client-side-routed sites survive deep
        // links and refreshes. Missing sub-resources (js/css/images) still 404 — they don't accept
        // html — so a broken asset never silently returns the page.
        if (resolution !is StaticSiteResolution.Resolved && acceptsHtml && requestPath != "/") {
            resolution = runBlocking { StaticSiteResolver.resolve("/", paths, servers, fetch) }
        }

        if (resolution !is StaticSiteResolution.Resolved) return notFound()

        val (mime, charset) = splitContentType(resolution.contentType)
        val isHtml = mime.equals("text/html", ignoreCase = true)
        val bytes = if (isHtml) injectShim(resolution.bytes) else resolution.bytes

        return WebResourceResponse(
            mime,
            charset,
            200,
            "OK",
            mapOf("Content-Security-Policy" to APP_CSP),
            ByteArrayInputStream(bytes),
        )
    }

    /** Inserts the `window.napplet` client shim into the applet's HTML document. */
    private fun injectShim(html: ByteArray): ByteArray {
        val text = html.decodeToString()
        val script = "<script>$SHIM_JS</script>"
        val headIdx = text.indexOf("<head", ignoreCase = true)
        val injected =
            when {
                headIdx >= 0 -> {
                    val close = text.indexOf('>', headIdx)
                    if (close >= 0) text.substring(0, close + 1) + script + text.substring(close + 1) else script + text
                }
                else -> script + text
            }
        return injected.encodeToByteArray()
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
        val id = envelope.optString("id").ifEmpty { return }

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

    /**
     * Routes blob fetches through the user's Tor SOCKS proxy when one is active (port > 0), and
     * caches them on disk. Blobs are content-addressed (`<server>/<sha256>`) and therefore
     * immutable, so a long-lived forced cache is safe — and the resolver re-verifies every blob's
     * sha256 on the way out regardless, so a stale/poisoned cache entry can never be served.
     */
    private fun buildHttpClient(port: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (port > 0) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
        }
        runCatching {
            builder.cache(Cache(File(cacheDir, "napplet-blobs"), BLOB_CACHE_BYTES))
            builder.addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.isSuccessful) {
                    response
                        .newBuilder()
                        .header("Cache-Control", "public, max-age=31536000, immutable")
                        .removeHeader("Pragma")
                        .build()
                } else {
                    response
                }
            }
        }
        return builder.build()
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun splitContentType(contentType: String): Pair<String, String> {
        val mime = contentType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
        val charset =
            contentType.substringAfter("charset=", "").trim().ifEmpty { null }
                ?: if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "utf-8" else ""
        return mime to charset
    }

    companion object {
        private const val TAG = "NappletHostActivity"
        private const val BLOB_CACHE_BYTES = 50L * 1024 * 1024
        private const val HOST = "napplet.local"
        private const val ORIGIN = "https://napplet.local"
        private const val SHELL_URL = "$ORIGIN/__shell__"
        private const val APP_BASE = "$ORIGIN/app/"
        private const val BRIDGE_NAME = "__nappletBridge"

        private const val SHELL_CSP =
            "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
                "frame-src https://napplet.local; base-uri 'none'; form-action 'none'"

        // 'self' does not match an opaque (sandboxed) origin, so the host is listed explicitly.
        // connect-src 'none' is the key lever: the applet gets no direct network.
        private const val APP_CSP =
            "default-src 'self' https://napplet.local; " +
                "script-src 'self' https://napplet.local 'unsafe-inline'; " +
                "style-src 'self' https://napplet.local 'unsafe-inline'; " +
                "img-src 'self' https://napplet.local data: blob:; " +
                "font-src 'self' https://napplet.local data:; " +
                "media-src 'self' https://napplet.local blob: data:; " +
                "connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'self'; form-action 'none'"

        // Namespaced window.napplet.* matching the upstream @napplet/web SDK, over the
        // {type:"domain.action", id} envelope. The applet's own code (built against that SDK)
        // therefore runs unchanged on this shell.
        private const val SHIM_JS = """
(function(){
  if (window.__nappletShimInstalled) return; window.__nappletShimInstalled = true;
  var seq = 0, pending = {}, subs = {};
  function send(env){ env.id = env.id || ('r' + (seq++)); parent.postMessage(JSON.stringify(env), '*'); return env.id; }
  function call(type, fields){
    return new Promise(function(resolve, reject){
      var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k];
      var id = send(env);
      pending[id] = { resolve: resolve, reject: reject };
    });
  }
  // Fire-and-forget (no .result awaited), used for subscribe/unsubscribe.
  function post(type, fields){ var env = { type: type }; if (fields) for (var k in fields) env[k] = fields[k]; send(env); }
  window.addEventListener('message', function(e){
    if (e.source !== parent) return;
    var msg; if (typeof e.data === 'string') { try { msg = JSON.parse(e.data); } catch (_) { return; } } else { msg = e.data; }
    if (!msg) return;
    // Subscription pushes are keyed by subId, not a request id.
    if (msg.type === 'relay.event' || msg.type === 'relay.eose') {
      var sub = subs[msg.subId]; if (!sub) return;
      if (msg.type === 'relay.event') { if (sub.onEvent) sub.onEvent(msg.event); }
      else { if (sub.onEose) sub.onEose(); }
      return;
    }
    if (!msg.id) return;
    var p = pending[msg.id]; if (!p) return; delete pending[msg.id];
    if (msg.ok) p.resolve(msg);
    else { var err = new Error(msg.reason || msg.operation || msg.error || 'napplet error'); err.napplet = msg; p.reject(err); }
  });
  function field(promise, name){ return promise.then(function(m){ return m[name]; }); }
  function normFilters(filters){ return Array.isArray(filters) ? { filters: filters } : { filter: filters || {} }; }
  function bytesToB64(bytes){ var u = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes); var s=''; for (var i=0;i<u.length;i++) s+=String.fromCharCode(u[i]); return btoa(s); }
  var actionSeq = 0;
  var napplet = {
    shell: {
      // supports() is synchronous in @napplet/shim; we expose a sync proxy backed by an async check.
      supports: function(domain, protocol){ return field(call('shell.supports', { domain: domain, protocol: protocol }), 'supported'); },
      ready: function(){ return Promise.resolve({}); },
      onReady: function(cb){ if (typeof cb === 'function') cb({}); return { close: function(){} }; },
      services: []
    },
    identity: {
      getPublicKey: function(){ return field(call('identity.getPublicKey'), 'pubkey'); },
      getProfile: function(){ return field(call('identity.getProfile'), 'profile'); },
      getRelays: function(){ return field(call('identity.getRelays'), 'relays'); },
      getFollows: function(){ return field(call('identity.getFollows'), 'pubkeys'); },
      getMutes: function(){ return field(call('identity.getMutes'), 'pubkeys'); },
      getBlocked: function(){ return field(call('identity.getBlocked'), 'pubkeys'); },
      getList: function(listType){ return field(call('identity.getList', { listType: listType }), 'entries'); },
      getZaps: function(){ return field(call('identity.getZaps'), 'zaps'); },
      getBadges: function(){ return field(call('identity.getBadges'), 'badges'); },
      // Live identity-change push is a follow-up; onChanged is a no-op subscription for now.
      onChanged: function(handler){ return { close: function(){} }; }
    },
    // keys = keyboard / command action binding (NOT signing). Signing is shell-only via relay.publish.
    // Not yet wired to the host keyboard, so these are client-side no-ops that keep applets from crashing.
    keys: {
      registerAction: function(action){ return Promise.resolve({ actionId: 'a' + (actionSeq++) }); },
      unregisterAction: function(actionId){},
      onAction: function(actionId, cb){ return { close: function(){} }; }
    },
    relay: {
      // publish takes an UNSIGNED template (carried in the `event` field per @napplet/shim); the
      // shell signs it and resolves to the signed event.
      publish: function(template, options){ return field(call('relay.publish', { event: template, options: options }), 'event'); },
      publishEncrypted: function(template, recipient, encryption){ return field(call('relay.publishEncrypted', { event: template, recipient: recipient, encryption: encryption || 'nip44' }), 'event'); },
      query: function(filters){ return field(call('relay.query', normFilters(filters)), 'events'); },
      // subscribe is push-based: the shell sends relay.event/relay.eose keyed by subId. Today it
      // delivers the initial matches then EOSE; a live tail is a follow-up.
      subscribe: function(filters, onEvent, onEose, options){
        var subId = 's' + (seq++);
        subs[subId] = { onEvent: onEvent, onEose: onEose };
        var env = normFilters(filters); env.subId = subId;
        post('relay.subscribe', env);
        return { close: function(){ delete subs[subId]; post('relay.close', { subId: subId }); } };
      }
    },
    storage: {
      // SDK method names are getItem/setItem/removeItem/keys; the wire types are storage.get/set/remove/keys.
      getItem: function(key){ return field(call('storage.get', { key: key }), 'value'); },
      setItem: function(key, value){ return call('storage.set', { key: key, value: value }).then(function(){}); },
      removeItem: function(key){ return call('storage.remove', { key: key }).then(function(){}); },
      keys: function(){ return field(call('storage.keys'), 'keys'); }
    },
    // value.payInvoice is an Amethyst-specific extension (not part of @napplet/shim).
    value: {
      payInvoice: function(invoice){ return field(call('value.payInvoice', { invoice: invoice }), 'preimage'); }
    },
    resource: {
      // The shell rebuilds the Blob from the host's base64 before this resolves.
      bytes: function(url){ return field(call('resource.bytes', { url: url }), 'blob'); },
      bytesAsObjectURL: function(url){ return field(call('resource.bytes', { url: url }), 'blob').then(function(blob){ return URL.createObjectURL(blob); }); }
    },
    // upload.blob is an Amethyst-specific extension (not part of @napplet/shim).
    upload: {
      blob: function(bytes, contentType){ return field(call('upload', { bytes: bytesToB64(bytes), contentType: contentType }), 'url'); }
    }
  };
  window.napplet = Object.freeze(napplet);
})();
"""
    }
}
