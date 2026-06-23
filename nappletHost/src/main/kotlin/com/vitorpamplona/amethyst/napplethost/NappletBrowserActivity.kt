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
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import org.json.JSONObject
import java.util.concurrent.Executor

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

    private val contentFrame by lazy { FrameLayout(this) }
    private var loadingView: View? = null
    private var resumed = false

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

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Toast.makeText(this, getString(R.string.napplet_webview_too_old), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        webView = WebView(this)
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
                    buildFloatingChip(),
                    FrameLayout
                        .LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.END,
                        ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) },
                )
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

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
        resumed = true
        setBrokerForeground(true)
    }

    override fun onPause() {
        if (this::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
        resumed = false
        setBrokerForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        runCatching { unbindService(brokerConnection) }
        if (this::webView.isInitialized) webView.destroy()
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

        override fun onPageCommitVisible(
            view: WebView,
            url: String,
        ) {
            // The page has painted its first frame — drop the loading screen.
            loadingView?.let { contentFrame.removeView(it) }
            loadingView = null
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) = syncBackState()

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) = syncBackState()
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
        val host = runCatching { Uri.parse(startUrl).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
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

    private fun barTitle(): String = title.ifBlank { runCatching { Uri.parse(startUrl).host }.getOrNull() ?: getString(R.string.napplet_untitled) }

    /**
     * The floating control chip: a globe (trusted external-web marker) that expands on tap to the Tor
     * toggle (when Tor is available) and reload. The page can't draw over it.
     */
    private fun buildFloatingChip(): View {
        val onSurface = resolveThemeColor(android.R.attr.textColorPrimary)
        val dimmed = resolveThemeColor(android.R.attr.textColorSecondary)

        val actions =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
            }
        if (proxyPort > 0) {
            actions.addView(
                ImageView(this).apply {
                    setImageResource(R.drawable.ic_tor)
                    setColorFilter(if (useTor) onSurface else dimmed)
                    alpha = if (useTor) 1f else 0.4f
                    setPadding(dp(8), 0, dp(8), 0)
                    isClickable = true
                    setOnClickListener { setNetworkMode(!useTor) }
                    contentDescription = getString(if (useTor) R.string.napplet_net_tor_desc else R.string.napplet_net_open_desc)
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(22))
                },
            )
        }
        actions.addView(chipGlyph("↻", onSurface, getString(R.string.napplet_chrome_reload)) { if (this::webView.isInitialized) webView.reload() })

        val marker =
            TextView(this).apply {
                text = "🌐" // globe
                textSize = 16f
                setPadding(dp(4), dp(2), dp(4), dp(2))
                isClickable = true
                contentDescription = barTitle()
                setOnClickListener {
                    actions.visibility = if (actions.visibility == View.GONE) View.VISIBLE else View.GONE
                }
            }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            elevation = dp(4).toFloat()
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(resolveThemeColor(android.R.attr.colorBackground))
                }
            addView(marker)
            addView(actions)
        }
    }

    private fun chipGlyph(
        glyph: String,
        color: Int,
        desc: String,
        onClick: () -> Unit,
    ): TextView =
        TextView(this).apply {
            text = glyph
            setTextColor(color)
            textSize = 18f
            setPadding(dp(8), 0, dp(8), 0)
            isClickable = true
            contentDescription = desc
            setOnClickListener { onClick() }
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

    private fun resolveThemeColor(attr: Int): Int {
        val tv = android.util.TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebContract.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    companion object {
        private const val TAG = "NappletBrowserActivity"
        private const val EXTRA_URL = "url"
        private const val EXTRA_PROXY_PORT = "proxyPort"
        private const val EXTRA_USE_TOR = "useTor"
        private const val EXTRA_TITLE = "title"

        fun intent(
            context: Context,
            url: String,
            proxyPort: Int,
            useTor: Boolean,
            title: String = "",
        ): Intent =
            Intent()
                .setClassName(context, "com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity")
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_PROXY_PORT, proxyPort)
                .putExtra(EXTRA_USE_TOR, useTor)
                .putExtra(EXTRA_TITLE, title)
                // Distinct task identity per URL for documentLaunchMode=intoExisting.
                .setData(Uri.parse(url))
    }
}
