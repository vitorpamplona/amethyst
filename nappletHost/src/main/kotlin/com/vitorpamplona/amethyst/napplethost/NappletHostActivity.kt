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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
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
import android.widget.Button
import android.widget.FrameLayout
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
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.resolveRequiredCapabilities
import com.vitorpamplona.amethyst.napplethost.R
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executor
import com.vitorpamplona.amethyst.commons.R as CommonsR

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

    // The applet's stable identifier (the manifest `d` tag; empty for a root/replaceable applet).
    // Combined with [author] it gives the applet's per-launch-stable identity, used to derive its
    // own sandbox origin so its storage persists across launches and stays isolated from other applets.
    private var identifier: String = ""

    // Opaque token the broker resolves to this launch's trusted identity + declared capabilities.
    // The sandbox never carries its own coordinate, so a compromised :napplet process can't forge one.
    private var launchToken: String = ""

    // The host posture: a WEBSITE nSite (NIP-07 window.nostr + normal network) vs a locked NAPPLET.
    private var profile: HostProfile = HostProfile.NAPPLET

    // Per-site network choice: route this site's traffic through Tor (default) or over the open web.
    // Applied to both the WebView proxy and the blob-fetch client; toggled from the top-bar onion.
    private var useTor: Boolean = true

    // NAP domain strings the shell advertises to the applet in the shell.init handshake.
    private var declaredDomains: List<String> = emptyList()

    private var themeType: String = "SYSTEM"

    // Opaque per-account WebView storage-profile name (see NappletWebViewProfile). Null/blank when the
    // launcher didn't scope one, which lands on the shared default jar.
    private var webViewProfile: String? = null

    // Pre-localized capability labels for the "what it can access" sheet (resolved by the launcher).
    private var capabilityLabels: List<String> = emptyList()

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

    // Swaps between the loading screen, the applet WebView, and the "unavailable" screen.
    private val contentFrame by lazy { FrameLayout(this) }
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // The loading splash (monogram + spinner). Kept on top of the mounted WebView and removed only on
    // first paint, so there's never a blank/dark gap between the index probe and the shell's first frame.
    private var loadingView: View? = null

    // A thin determinate progress bar pinned to the top edge (browser-style), driven by the
    // WebChromeClient's onProgressChanged; hidden at 100%.
    private val topProgressBar by lazy { buildTopProgressBar() }

    // Bottom pull-up developer console: the page's console.log/warn/error plus any resource load errors.
    private var consolePanel: NappletConsolePanel? = null
    private var controlSheet: NappletControlSheet? = null

    // Set once the WebView has begun loading the shell, so a retry doesn't reload it.
    private var started = false

    // Back goes "page back" inside the applet's WebView history first; only when it can't go back
    // further does this disable itself and let the system back exit the sandbox to Amethyst.
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (this@NappletHostActivity::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    /** Keep the in-WebView back gesture enabled exactly while the applet has history to pop. */
    private fun syncBackState() {
        if (this::webView.isInitialized) backCallback.isEnabled = webView.canGoBack()
    }

    // True between onResume and onPause. Sent to the broker (foreground hold) on connect too, in case
    // the broker binds after this surface is already resumed (bindService is async).
    private var resumed = false

    // Renews the broker's foreground lease while resumed. If this process dies, the heartbeat stops and
    // the broker reaps the stale lease, so a crash can't pin the main process's network up forever.
    private var foregroundHeartbeat: Job? = null

    private val brokerConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                brokerMessenger = Messenger(service)
                pendingRequests.forEach { sendToBroker(it) }
                pendingRequests.clear()
                // If we're already foreground by the time the broker binds, report it now so the
                // main-process resource hold is acquired for this session.
                if (resumed) setBrokerForeground(true)
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
        // This isolated `:napplet` process skips app init (to stay key-free), so the compose-resources
        // Android context is never set here and `Res.readBytes` would crash — read the contract bytes
        // straight from the APK assets, where compose-resources packages them.
        fun readContractAsset(path: String): ByteArray = assets.open(NappletWebContract.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }
        val shellHtml = readContractAsset(NappletWebContract.SHELL_HTML_PATH)
        val shim = readContractAsset(NappletWebContract.SHIM_JS_PATH).decodeToString()
        val appOrigin = NappletWebContract.appOrigin(deriveAppId(author, identifier))
        // "Open web" for a site makes everything direct — both its blob fetches (here) and its live
        // web traffic (the WebView proxy, below). Tor (the default) routes both through the SOCKS port.
        val effectiveProxy = if (useTor) proxyPort else -1
        contentServer = NappletContentServer(paths, servers, effectiveProxy, cacheDir, shellHtml, shim, appOrigin, profile)

        // Create + warm the WebView NOW so its (slow, first-in-process) Chromium init runs on the main
        // thread concurrently with the index probe below (which runs on IO) — instead of serially after
        // it. Binding the broker early overlaps too. The WebView is attached once the probe succeeds.
        // Built from a context forced to the app theme so its content follows DARK/LIGHT regardless of the
        // device theme (WebView reads the context's theme, not the window's — see nightThemedContext).
        webView = WebView(nightThemedContext(this, themeType))
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before anything else.
        NappletWebViewProfile.apply(this, webView, webViewProfile)
        hardenWebView(webView)
        // Theme the WebView's pre-paint background to the app's so it doesn't flash white when the shell
        // mounts. This activity has a themed context, so it resolves the color locally (no IPC needed).
        webView.setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
        // Route the WebView's own (off-origin) traffic through Tor for an nSite, unless this site was
        // opted out to the open web. Set process-wide before any page navigation; the shell + blobs are
        // served from cache via shouldInterceptRequest, so only the site's external requests hit this.
        if (profile.exposesNetwork) applyWebViewProxy(effectiveProxy)
        // Origin-restricted bridge: only the trusted shell page (main frame) can reach native.
        WebViewCompat.addWebMessageListener(
            webView,
            NappletWebContract.BRIDGE_NAME,
            setOf(NappletWebContract.ORIGIN),
            ::onShellMessage,
        )
        // Bind the main-process broker by explicit class name (same APK) rather than a compile-time
        // class reference, so this sandbox module needs no dependency on :amethyst.
        bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)

        // Route the back gesture into the WebView's history first (see backCallback).
        onBackPressedDispatcher.addCallback(this, backCallback)

        // The applet titles itself, so instead of a full-width bar we hang a trusted top pull-down sheet
        // (the anti-phishing shield the applet can't draw over) over the content frame, which shows a
        // loading screen → the applet's WebView, or an "unavailable" screen.
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
                // Added last so the thin loading bar paints above the content (and over the grabber's top
                // edge); it's GONE except while loading, so it never obscures the trusted chrome.
                addView(topProgressBar)
            }
        setContentView(root)
        // Activities are edge-to-edge by default on recent Android; pad by the system bar and
        // display-cutout insets so neither the chrome nor the applet draws under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val applied = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            val bars = insets.getInsets(applied)
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            // Zero the insets we just turned into padding before they reach the child WebView: on
            // targetSdk 35+ the WebView auto-applies any insets it receives to its own web content, so
            // leaving them un-consumed padded the bottom a SECOND time — a band of the page's own
            // background above the navigation bar. Keep the other types (notably IME) flowing so the
            // applet's keyboard-aware resize still works.
            WindowInsetsCompat.Builder(insets).setInsets(applied, Insets.NONE).build()
        }

        probeAndMount()
    }

    /**
     * Probe whether the app/site's index resolves (downloading + verifying it, which also warms the
     * cache) — on IO, concurrently with the WebView init kicked off in [onCreate] — before showing the
     * WebView. The user sees a loading screen, then either the running app or a clear "unavailable"
     * screen with Retry, instead of a blank/white WebView.
     */
    private fun probeAndMount() {
        contentFrame.removeAllViews()
        loadingView = buildLoadingView().also { contentFrame.addView(it) }
        uiScope.launch {
            val available = withContext(Dispatchers.IO) { contentServer.resolve("/") is StaticSiteResolution.Resolved }
            if (available) {
                mountWebView()
            } else {
                contentFrame.removeAllViews()
                loadingView = null
                contentFrame.addView(buildErrorView { probeAndMount() })
            }
        }
    }

    private fun mountWebView() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        // Mount the WebView UNDER the loading splash (index 0) instead of replacing it: the shell + applet
        // bundle still take time to paint (seconds over Tor), and the WebView shows only its dark
        // colorBackground until then. The splash stays until the first frame paints (onPageCommitVisible),
        // so the user never sees a blank/black screen with no sign that anything is loading.
        contentFrame.addView(webView, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (!started) {
            started = true
            webView.loadUrl(NappletWebContract.SHELL_URL)
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::webView.isInitialized) {
            webView.onResume()
        }
        // Launching this :napplet-process surface backgrounded the main process; tell the broker to
        // hold the main process resumed (Tor/relays/AUTH) while this napplet/nSite is in front, and
        // keep renewing that lease so a crash here can't pin the network up forever.
        resumed = true
        startForegroundHeartbeat()
    }

    override fun onPause() {
        // Foreground-only: stop the applet's JS/timers in the background so it cannot fire a
        // sign/decrypt/pay request whose consent prompt would surface over (and be confused with)
        // Amethyst's own UI. Requests only happen while the user is looking at this napplet.
        if (this::webView.isInitialized) {
            // webView.onPause() pauses THIS WebView's JS/DOM (the security goal — a backgrounded napplet can't
            // fire a sign/decrypt/pay request). Do NOT call pauseTimers(): it's process-global and freezes
            // EVERY WebView in `:napplet`, including the embedded browser/napplet surfaces, which never resume.
            webView.onPause()
        }
        // No longer foreground: stop renewing and let the main process resume normal background scaling.
        resumed = false
        stopForegroundHeartbeat()
        setBrokerForeground(false)
        super.onPause()
    }

    /** Reports foreground=true immediately and then re-reports on a heartbeat to renew the broker lease. */
    private fun startForegroundHeartbeat() {
        foregroundHeartbeat?.cancel()
        foregroundHeartbeat =
            uiScope.launch {
                while (true) {
                    setBrokerForeground(true)
                    delay(FOREGROUND_HEARTBEAT_MS)
                }
            }
    }

    private fun stopForegroundHeartbeat() {
        foregroundHeartbeat?.cancel()
        foregroundHeartbeat = null
    }

    /** Reports this surface's foreground state to the broker so it can hold the main process resumed. */
    private fun setBrokerForeground(foreground: Boolean) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_FOREGROUND).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
                        putBoolean(NappletIpc.KEY_FOREGROUND, foreground)
                    }
            }
        // Before the broker binds, the surface isn't really up yet; the matching onPause(false) is a
        // no-op on the broker's empty map, so dropping a pre-bind report is harmless — the heartbeat
        // re-reports once connected (and onServiceConnected seeds it too).
        if (brokerMessenger != null) sendToBroker(msg)
    }

    override fun onDestroy() {
        uiScope.cancel()
        // unbind is in runCatching: if the index never resolved we never bound the broker.
        runCatching { unbindService(brokerConnection) }
        keyActions.clear()
        if (this::webView.isInitialized) {
            // Detach before destroy(): destroying an attached WebView corrupts the shared multiprocess
            // renderer/network state and breaks the other (embedded) WebViews in this `:napplet` process
            // (dead DNS, empty DOM reads, dead selection paint, broken IME). See NappletBrowserActivity.
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    /**
     * Intercepts hardware-key combos the applet bound via `keys.registerAction` and turns them into a
     * `keys.action` push — the applet never sees raw key events, only its own named action. Unmatched
     * keys fall through to the WebView (so the applet's own text inputs still work normally).
     *
     * RestrictedApi is a false positive: `Activity.dispatchKeyEvent` is a public framework
     * hook; lint flags it only because androidx.core's intermediate override carries a
     * library-group `@RestrictTo`. Unmatched keys still reach `super`, so androidx's
     * KeyEventDispatcher routing is preserved.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val actionId = keyActions.actionFor(event)
        if (actionId != null) {
            runCatching { bridgeReplyProxy?.postMessage(NappletProtocolJson.encodeKeysAction(actionId)) }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun readManifestExtras(): Boolean {
        val pathList = intent.getStringArrayListExtra(NappletHostContract.EXTRA_PATHS) ?: return false
        val hashList = intent.getStringArrayListExtra(NappletHostContract.EXTRA_HASHES) ?: return false
        if (pathList.size != hashList.size || pathList.isEmpty()) return false

        for (i in pathList.indices) paths.add(PathTag(pathList[i], hashList[i]))
        servers.addAll(intent.getStringArrayListExtra(NappletHostContract.EXTRA_SERVERS) ?: emptyList())
        author = intent.getStringExtra(NappletHostContract.EXTRA_AUTHOR).orEmpty()
        identifier = intent.getStringExtra(NappletHostContract.EXTRA_IDENTIFIER).orEmpty()
        profile = HostProfile.fromName(intent.getStringExtra(NappletHostContract.EXTRA_HOST_PROFILE))
        useTor = intent.getBooleanExtra(NappletHostContract.EXTRA_USE_TOR, true)
        title = intent.getStringExtra(NappletHostContract.EXTRA_TITLE).orEmpty()
        proxyPort = intent.getIntExtra(NappletHostContract.EXTRA_PROXY_PORT, -1)
        launchToken = intent.getStringExtra(NappletHostContract.EXTRA_LAUNCH_TOKEN).orEmpty()
        capabilityLabels = intent.getStringArrayListExtra(NappletHostContract.EXTRA_CAP_LABELS) ?: emptyList()
        themeType = intent.getStringExtra(NappletHostContract.EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" }
        webViewProfile = intent.getStringExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE)

        val requires = intent.getStringArrayListExtra(NappletHostContract.EXTRA_REQUIRES) ?: emptyList()
        val resolved = resolveRequiredCapabilities(requires)
        // shell is always available; the rest are the declared domains advertised to the applet in the
        // handshake. (The broker enforces the authoritative set from the launch token, not this list.)
        declaredDomains = (listOf("shell") + resolved.capabilities.map { it.name.lowercase() }).distinct()

        return author.isNotEmpty() && launchToken.isNotEmpty()
    }

    /**
     * Stable, unique, DNS-label-safe id for this applet's sandbox origin: a sha256 of
     * `author:identifier`, hex, truncated to 31 chars and letter-prefixed (`n`). The leading letter
     * avoids any numeric-host parsing quirk and keeps it ≤63 chars (a valid DNS label). The same
     * applet always derives the same id, so its origin — and therefore its localStorage/IndexedDB —
     * persists across launches; different applets derive different subdomains, so their storage is
     * isolated from one another.
     */
    private fun deriveAppId(
        author: String,
        identifier: String,
    ): String = "n" + sha256("$author:$identifier".encodeToByteArray()).toHexKey().take(31)

    private var title: String = ""

    @Suppress("SetJavaScriptEnabled")
    private fun hardenWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true // the applet needs JS; isolation comes from process + CSP + sandbox
            // DOM storage (localStorage/sessionStorage) is on because the applet runs on its OWN real,
            // per-applet origin (a napplet.local subdomain) — storage is scoped to that origin, isolated
            // from other applets and from the shell. SPAs need it at boot; without it they crash-loop.
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
        // Disable the overscroll stretch/glow: forcing a scroll past the content edge stretched the
        // WebView's output and exposed the shell document's background behind the applet iframe at the
        // seam (a stray white band at the bottom). The applet's own content still scrolls normally.
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        WebView.setWebContentsDebuggingEnabled(false)
        webView.webViewClient = NappletWebViewClient()
        webView.webChromeClient = NappletWebChromeClient()
    }

    /**
     * Routes this process's WebView traffic through the Tor SOCKS proxy when [port] > 0, else clears any
     * override so the site loads over the open web. Process-global (this `:napplet` process hosts only
     * applet/site WebViews) and best-effort: a device whose WebView can't honor a SOCKS proxy falls back
     * to direct — verified on-device, since SOCKS-over-WebView support varies by WebView version.
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

    /** Serves only the trusted shell and the manifest's verified blobs; everything else 404s. */
    private inner class NappletWebViewClient : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? = contentServer.serve(request)

        // The applet's in-page / in-iframe navigations (links + history.pushState) change the
        // WebView's back/forward list; keep the back gesture enabled exactly while it can pop.
        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) {
            syncBackState()
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            syncBackState()
        }

        // The shell has painted its first frame — drop the loading splash so the running app shows
        // through. Null-safe so a later in-app navigation/reload (splash already gone) is a no-op.
        override fun onPageCommitVisible(
            view: WebView,
            url: String,
        ) {
            loadingView?.let { contentFrame.removeView(it) }
            loadingView = null
        }

        // Surface failed resource fetches (a missing blob, a verify miss, an off-origin request the
        // default-deny CSP blocked) in the console so an nsite/napplet developer can see what broke.
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            logConsoleError(request, getString(R.string.napplet_console_load_error, error.errorCode, error.description?.toString().orEmpty()))
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            logConsoleError(request, getString(R.string.napplet_console_http_error, errorResponse.statusCode, errorResponse.reasonPhrase.orEmpty()))
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            // Internal hosts (the shell host and this applet's own per-applet subdomain): let the
            // WebView load them — they go through shouldInterceptRequest and are served from cache.
            if (NappletWebContract.isInternalHost(uri.host)) return false
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

    /** Drives the top loading bar and forwards the applet/site's `console.*` output to the console panel. */
    private inner class NappletWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(
            view: WebView,
            newProgress: Int,
        ) {
            updateLoadProgress(newProgress)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            val panel = consolePanel ?: return false
            panel.appendLog(consoleMessage.messageLevel(), consoleMessage.message(), consoleMessage.sourceId(), consoleMessage.lineNumber())
            controlSheet?.updateConsoleCount(panel.entryCount)
            return true
        }
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
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
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
                notifyIfSensitive(result)
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

    // ---- loading / unavailable screens ----

    /** A monogram tile (first letter of the title on a colored rounded square), matching the card. */
    private fun monogram(sizeDp: Int): TextView =
        TextView(this).apply {
            text = barTitle().trim().take(1).uppercase()
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimaryInverse))
            textSize = (sizeDp / 2.4f)
            gravity = Gravity.CENTER
            val bg =
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(18).toFloat()
                    setColor(resolveThemeColor(android.R.attr.colorPrimary))
                }
            background = bg
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    private fun centeredColumn(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            // Opaque so the splash/error screen fully covers the WebView it now overlays (mounted beneath
            // it until first paint) instead of letting the dark, not-yet-painted page show through.
            setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

    private fun buildLoadingView(): View =
        centeredColumn().apply {
            addView(monogram(72))
            addView(spacer(dp(20)))
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = barTitle()
                    setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                    textSize = 20f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(20)))
            addView(ProgressBar(this@NappletHostActivity))
        }

    private fun buildErrorView(onRetry: () -> Unit): View =
        centeredColumn().apply {
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = "⚠"
                    textSize = 44f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(12)))
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = getString(R.string.napplet_unavailable_title, barTitle())
                    setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                    textSize = 18f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(8)))
            addView(
                TextView(this@NappletHostActivity).apply {
                    text = getString(R.string.napplet_unavailable_subtitle)
                    setTextColor(resolveThemeColor(android.R.attr.textColorSecondary))
                    textSize = 14f
                    gravity = Gravity.CENTER
                },
            )
            addView(spacer(dp(20)))
            addView(
                Button(this@NappletHostActivity).apply {
                    text = getString(R.string.napplet_unavailable_retry)
                    setOnClickListener { onRetry() }
                },
            )
        }

    private fun spacer(heightPx: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, heightPx) }

    // ---- trusted sandbox chrome ----

    private fun barTitle(): String = title.ifBlank { getString(CommonsR.string.napplet_untitled) }

    /**
     * The trusted top pull-down sheet: a small grabber at the top edge (out of the corner where the app
     * shows its own avatar) that expands to the sandbox **shield**, the nSite network/Tor row (website
     * mode only, taps through to the confirm dialog), reload, and the "what it can access" sheet. The
     * applet can't draw over it. Mirrors the embedded tabs' Compose `TopControlSheet`.
     */
    private fun buildControlSheet(): View =
        NappletControlSheet(
            context = this,
            title = barTitle(),
            isSandbox = true,
            onReload = { if (this::webView.isInitialized) webView.reload() },
            // Website-mode nSites can re-route over Tor; switching rebuilds the session, so the row taps
            // through to a full relaunch rather than toggling inline.
            torInitiallyOn = if (profile.exposesNetwork && proxyPort > 0) useTor else null,
            onNetworkTap = if (profile.exposesNetwork && proxyPort > 0) ({ setNetworkMode(!useTor) }) else null,
            onInfo = { showAccessDialog() },
            onPermissions = { openPermissions() },
            onConsole = { show -> consolePanel?.setShowing(show) },
        ).also { controlSheet = it }

    /**
     * Ask the broker to open this napplet's editable permission screen. The sandbox can't state its own
     * coordinate, so we send only the launch token; the broker resolves it to the trusted coordinate and
     * launches the main activity at the Connected Apps detail.
     */
    private fun openPermissions() {
        val msg =
            Message.obtain(null, NappletIpc.MSG_OPEN_PERMISSIONS).apply {
                data = Bundle().apply { putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken) }
            }
        if (brokerMessenger != null) sendToBroker(msg)
    }

    private fun buildConsolePanel(): View =
        NappletConsolePanel(this).also {
            it.onClearCallback = { controlSheet?.updateConsoleCount(0) }
            consolePanel = it
        }

    /**
     * A thin determinate progress bar pinned to the top edge, like a browser's. Driven by
     * [NappletWebChromeClient.onProgressChanged]: visible while the shell + verified blobs load and gone
     * at 100%, so a slow load (e.g. a large bundle over Tor) shows progress instead of a blank dark WebView.
     */
    private fun buildTopProgressBar(): ProgressBar =
        ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = false
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(resolveThemeColor(android.R.attr.colorPrimary))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP)
        }

    /** Persists the new routing choice in the main process, then relaunches this screen to apply it. */
    private fun setNetworkMode(newUseTor: Boolean) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_NETWORK_MODE).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, launchToken)
                        putBoolean(NappletIpc.KEY_NETWORK_USE_TOR, newUseTor)
                    }
            }
        sendToBroker(msg)
        useTor = newUseTor
        intent.putExtra(NappletHostContract.EXTRA_USE_TOR, newUseTor)
        recreate()
    }

    /** Lists, in plain language, exactly which capabilities this napplet was launched with. */
    private fun showAccessDialog() {
        val body =
            if (capabilityLabels.isEmpty()) {
                getString(R.string.napplet_chrome_static_site)
            } else {
                capabilityLabels.joinToString("\n") { "•  $it" } + "\n\n" + getString(R.string.napplet_chrome_keys_safe)
            }
        AlertDialog
            .Builder(this)
            .setTitle(getString(R.string.napplet_chrome_access_title, barTitle()))
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Surfaces an "allow always" capability acting on the user's behalf, so a granted RELAY/UPLOAD/VALUE
     * op can never run completely silently. Read-only ops (identity/storage/resource) don't toast.
     */
    private fun notifyIfSensitive(result: JSONObject) {
        if (!result.optBoolean("ok")) return
        val message =
            when (result.optString("type")) {
                "relay.publish.result", "relay.publishEncrypted.result" -> getString(R.string.napplet_action_published, barTitle())
                "upload.upload.result" -> getString(R.string.napplet_action_uploaded, barTitle())
                "value.payInvoice.result" -> getString(R.string.napplet_action_paid, barTitle())
                else -> return
            }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "NappletHostActivity"

        /**
         * How often a resumed host renews its foreground lease with the broker. Comfortably shorter than
         * the broker's lease TTL so a couple of dropped/delayed beats don't expire a still-foreground
         * surface, while a dead process (heartbeat stopped) is reaped within the TTL.
         */
        const val FOREGROUND_HEARTBEAT_MS = 30_000L
    }
}
