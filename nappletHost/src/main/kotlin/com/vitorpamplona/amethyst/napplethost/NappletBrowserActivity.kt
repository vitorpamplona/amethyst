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

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.TypedValue
import android.view.ContextMenu
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission.Decision
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.stringOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import com.vitorpamplona.quartz.utils.Log
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * Full-screen **direct-WebView** browser for an arbitrary URL, running in the keyless `:napplet`
 * process — Amethyst's equivalent of an installed Chrome PWA window. Unlike the embedded browser
 * ([NappletBrowserService], which streams its surface to the main app through SurfaceControlViewHost — a
 * path that, on current Android, forwards taps but drops scroll/zoom/keyboard gestures), this hosts the
 * WebView **directly** in its own window, so scrolling, pinch zoom, and the soft keyboard all work natively
 * — the window insets the content for the IME itself (see [applyFullScreenHostInsets]). It stays just as
 * keyless: the page JS runs here, every NIP-07 `window.nostr` call is brokered + consent-gated in the main
 * process per origin, and the keys never leave it.
 *
 * PWA behaviours, beyond the page itself: its own task in Recents titled, iconed and coloured after the
 * site ([updateTaskDescription]); system bars tinted with the page's `theme-color`; the top pill
 * ([NappletControlSheet], laid out by [BrowserChrome]); JS dialogs; new windows (`_blank` / `window.open`)
 * as new browser windows with `opener` intact ([BrowserPopups]); downloads; HTML fullscreen video;
 * camera / microphone / location behind a per-site prompt; find in page; long-press link and image menus;
 * Web Share; and recovery from a renderer crash.
 */
class NappletBrowserActivity : ComponentActivity() {
    private var webView: WebView? = null

    private var startUrl: String = "about:blank"
    private var proxyPort: Int = -1
    private var useTor: Boolean = true
    private var themeType: String = "SYSTEM"
    private var webViewProfile: String? = null

    // Key for this window's foreground lease with the broker; stable for the Activity's life.
    private var leaseKey: String = ""

    private val contentFrame by lazy { FrameLayout(this) }
    private var root: FrameLayout? = null
    private var loadingView: View? = null
    private var crashView: View? = null
    private var resumed = false
    private var controlSheet: NappletControlSheet? = null
    private var consolePanel: NappletConsolePanel? = null
    private var findBar: BrowserFindBar? = null
    private var consoleShowing = false

    // A thin determinate progress bar pinned to the top edge (browser-style), driven by the chrome
    // client's onProgressChanged; hidden at 100%.
    private val topProgressBar by lazy { buildTopProgressBar() }

    // The NIP-07 shim + browser extras, injected at document start in this window and in its popups.
    private var shimJs: String = ""

    // Visit-history gating: only a clean main-frame load (no error) is recorded, so a misspelled/
    // unresolved address never enters history. Reset on each main-frame page start.
    private var pendingMainFrameUrl: String? = null
    private var mainFrameLoadFailed = false
    private var lastIconHost: String? = null

    // The last URL whose pin state was asked of the broker, so the several page callbacks that report the
    // same address don't each trigger a round-trip.
    private var lastFavoriteQueryUrl: String? = null

    // What Recents shows for this task: the page's title, favicon and theme colour.
    private var pageTitle: String? = null
    private var pageIcon: Bitmap? = null
    private var themeColor: Int? = null

    // HTML fullscreen (a video's fullscreen button): the view WebView hands us, drawn over the whole window.
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Page-originated alert/confirm/prompt/beforeunload, labelled with the page's origin.
    private val jsDialogs by lazy { BrowserJsDialogs(this) }

    // ---- HTML file input (`<input type="file">`) ----
    // Registered as a field so it is in place before onCreate returns, which is what
    // registerForActivityResult requires. This activity hosts its WebView directly, so it can run the
    // picker itself; the embedded surfaces have no Activity and route theirs through the main process.
    private val pendingFileChooser = PendingFileChooser()

    private val fileChooserLauncher = WebFileChooserLauncher(this) { uris -> pendingFileChooser.deliver(uris) }

    // ---- site permissions (camera / microphone / location) ----
    private val sitePermissionQueries = mutableMapOf<Long, (Map<BrowserSitePermission, Decision>) -> Unit>()
    private var sitePermissionSeq = 0L
    private var permissionPrompt: AlertDialog? = null
    private var pendingRuntimeGrant: ((Map<String, Boolean>) -> Unit)? = null
    private val runtimePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            pendingRuntimeGrant?.invoke(result)
            pendingRuntimeGrant = null
        }

    // ---- broker bridge (per-origin NIP-07 tokens; identical to NappletBrowserService) ----
    private var brokerMessenger: Messenger? = null

    /**
     * Reply channel handed to the broker. It MUST NOT hold this Activity strongly.
     *
     * A [Messenger] sent over IPC is a binder: while the main process holds it, ART keeps a **JNI global
     * reference** to the backing [Handler] here in `:napplet`. A `Handler(looper, ::onBrokerReply)` makes
     * the Activity the handler's `mCallback` (a bound method reference captures `this`), so that one
     * retained binder pinned Activity → PhoneWindow → DecorView → WebView past `onDestroy`, and no GC in
     * this process could ever reclaim it — only killing the process could. Measured: one full-screen page
     * opened and closed left a destroyed Activity plus its WebView alive through repeated forced GCs.
     *
     * Holding the Activity weakly severs that chain at the source, so even a broker that never processes
     * [NappletIpc.MSG_RELEASE_CLIENT] (see [onDestroy]) cannot leak a surface. Messages arriving after
     * destruction are dropped, which is correct: there is nothing left to deliver them to.
     */
    private val replyMessenger = Messenger(WeakBrokerReplyHandler(this))

    private class WeakBrokerReplyHandler(
        activity: NappletBrowserActivity,
    ) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            ref.get()?.onBrokerReply(msg)
        }
    }

    private val pendingBrokerRequests = mutableListOf<Message>()
    private var bridgeReplyProxy: JavaScriptReplyProxy? = null
    private var fireSeq = 0
    private val originTokens = mutableMapOf<String, String>()
    private val pendingByOrigin = mutableMapOf<String, MutableList<Message>>()
    private val mintInFlight = mutableSetOf<String>()

    /**
     * Back walks out of fullscreen video, then the find bar, then the page's history, then leaves. Enabled
     * only while one of those applies, so the system back (and its predictive animation) otherwise acts
     * on the window itself.
     */
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val wv = webView
                when {
                    customView != null -> exitFullscreen()
                    findBar?.isShowing == true -> findBar?.hide()
                    wv != null && wv.canGoBack() -> wv.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
                syncBackState()
            }
        }

    private fun syncBackState() {
        backCallback.isEnabled = customView != null || findBar?.isShowing == true || webView?.canGoBack() == true
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

        // A new window a page opened: its WebView already exists (built inside the opener's onCreateWindow).
        val popupToken = intent.getStringExtra(EXTRA_POPUP_TOKEN)
        val popup = BrowserPopups.take(popupToken)
        if (popupToken != null && popup == null) {
            finish()
            return
        }

        if (popup != null) {
            proxyPort = popup.proxyPort
            useTor = popup.useTor
            themeType = popup.themeType
            webViewProfile = popup.webViewProfile
            leaseKey = "popup:$popupToken"
        } else {
            startUrl = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: run {
                finish()
                return
            }
            proxyPort = intent.getIntExtra(EXTRA_PROXY_PORT, -1)
            useTor = intent.getBooleanExtra(EXTRA_USE_TOR, true)
            themeType = intent.getStringExtra(EXTRA_THEME).orEmpty().ifBlank { "SYSTEM" }
            webViewProfile = intent.getStringExtra(NappletHostContract.EXTRA_WEBVIEW_PROFILE)
            leaseKey = startUrl
        }
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Toast.makeText(this, getString(R.string.napplet_webview_too_old), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        shimJs = readContractAsset(NappletWebContract.SHIM_JS_PATH).decodeToString()
        applyWebViewProxy(if (useTor) proxyPort else -1)

        bindService(Intent().setClassName(this, NappletHostContract.BROKER_SERVICE_CLASS), brokerConnection, BIND_AUTO_CREATE)
        onBackPressedDispatcher.addCallback(this, backCallback)

        val findBar = BrowserFindBar(this, { webView }) { syncBackState() }.also { this.findBar = it }
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
                addView(contentFrame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(buildControlSheet(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                addView(buildConsolePanel(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
                addView(findBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
                // Added last so the thin loading bar paints above the content (and over the grabber's top edge).
                addView(topProgressBar)
            }
        this.root = root
        setContentView(root)
        // Pad by the system bars + cutout AND the IME: on an edge-to-edge window (enforced for targetSdk
        // 35+ on Android 15+) windowSoftInputMode=adjustResize no longer shrinks the window, so without
        // this the keyboard covers the bottom of the page. See applyFullScreenHostInsets. The root's own
        // background shows through that padding, which is how the page's theme-color tints the bars.
        root.applyFullScreenHostInsets()

        val wv = buildWebView(popup)
        contentFrame.addView(wv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (popup == null) {
            loadingView = buildLoadingView().also { contentFrame.addView(it) }
            wv.loadUrl(startUrl)
        } else {
            wv.url?.let { if (it.isNotBlank() && it != "about:blank") startUrl = it }
        }
        updateTaskDescription()
    }

    /**
     * Builds (or, for a popup, adopts) this window's WebView and wires every client and bridge. Also used
     * to rebuild after a renderer crash.
     */
    private fun buildWebView(popup: BrowserPopups.Pending? = null): WebView {
        val wv: WebView
        if (popup != null) {
            wv = popup.webView
            // The popup was built on a placeholder context; point it at this Activity now.
            popup.context.baseContext = nightThemedContext(this, themeType)
        } else {
            // Build the WebView from a context forced to the app theme so its content follows DARK/LIGHT
            // even when the device theme differs (WebView reads the context's theme, not the window's).
            wv = WebView(nightThemedContext(this, themeType))
            // FIRST touch after construction: setProfile throws once the WebView has loaded content (or
            // its profile has otherwise been used), so the storage partition must be chosen first.
            NappletWebViewProfile.apply(this, wv, webViewProfile)
        }
        BrowserWebTools.applyBrowserSettings(wv)
        wv.webViewClient = BrowserClient()
        wv.webChromeClient = BrowserChromeClient()
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            BrowserDownloads.download(this, url, userAgent, contentDisposition, mimeType, BrowserWebTools.cookieManager(wv).getCookie(url), if (useTor) proxyPort else -1)
        }
        wv.setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
        wv.dropSystemBarInsets()
        registerForContextMenu(wv)

        if (popup != null) {
            popup.adopt(::onBridgeMessage)
        } else {
            // NIP-07 over the direct bridge (no shell): the shim talks to native at document start for
            // every origin; the broker scopes consent per visited origin.
            WebViewCompat.addWebMessageListener(wv, NappletWebContract.BRIDGE_NAME, setOf("*"), ::onBridgeMessage)
            WebViewCompat.addDocumentStartJavaScript(wv, BrowserWebTools.browserStartScript(shimJs, imeProxy = false), setOf("*"))
        }
        webView = wv
        return wv
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
        webView?.onResume()
        resumed = true
        heartbeatHandler.removeCallbacks(heartbeat)
        heartbeat.run()
    }

    override fun onPause() {
        // Only pause THIS activity's WebView (onPause is per-WebView). Do NOT call pauseTimers(): it is
        // process-global — it freezes JS/layout/parsing timers for EVERY WebView in `:napplet`, including
        // the embedded ones in NappletBrowserService, which have no resume of their own. That left the
        // embed frozen (dead page/connection) after returning from a full-screen excursion.
        webView?.onPause()
        resumed = false
        heartbeatHandler.removeCallbacks(heartbeat)
        setBrokerForeground(false)
        super.onPause()
    }

    override fun onDestroy() {
        // Tell the broker to drop every reference to our reply Messenger BEFORE unbinding — a retained
        // Messenger is a binder, and it would pin this Activity (and its WebView) in `:napplet` for the
        // life of the process. `unbindService` alone does not release it. See [replyMessenger].
        releaseFromBroker()
        runCatching { unbindService(brokerConnection) }
        // A picker still up when the browser is torn down would otherwise leave its callback unanswered.
        pendingFileChooser.cancel()
        fileChooserLauncher.teardown()
        // A dialog still up would leak its window and leave the page's JS blocked on an unanswered result.
        jsDialogs.dismiss()
        permissionPrompt?.dismiss()
        customViewCallback?.onCustomViewHidden()
        destroyWebView()
        super.onDestroy()
    }

    /**
     * Detaches, then destroys, the WebView. Destroying a WebView while it is still attached to the window
     * corrupts the SHARED multiprocess renderer/network state, which then breaks the OTHER (embedded)
     * WebViews living in this `:napplet` process: dead DNS (ERR_NAME_NOT_RESOLVED), DOM reads returning
     * empty, dead selection-highlight paint, and broken IME — all after a full-screen excursion returns to
     * an embed. (`destroy()` requires the view to be removed from the hierarchy first.)
     */
    private fun destroyWebView() {
        val wv = webView ?: return
        webView = null
        unregisterForContextMenu(wv)
        wv.stopLoading()
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.destroy()
    }

    /** Reports foreground state to the broker so the main process stays resumed (Tor/relays/AUTH). */
    private fun setBrokerForeground(foreground: Boolean) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_FOREGROUND).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_LAUNCH_TOKEN, leaseKey)
                        putBoolean(NappletIpc.KEY_FOREGROUND, foreground)
                    }
            }
        if (brokerMessenger != null) sendToBroker(msg)
    }

    /**
     * Asks the broker to drop every reference it holds to [replyMessenger] (inc-bus subscriptions and this
     * surface's foreground lease). Sent directly rather than through [sendToBroker] because that queues
     * when the broker is unbound — and we are being destroyed, so a queued release would never be sent.
     */
    private fun releaseFromBroker() {
        val broker = brokerMessenger ?: return
        val msg =
            Message.obtain(null, NappletIpc.MSG_RELEASE_CLIENT).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(NappletIpc.KEY_LAUNCH_TOKEN, leaseKey) }
            }
        runCatching { broker.send(msg) }
    }

    private fun currentUrl(): String = webView?.url?.takeIf { it.isNotBlank() } ?: controlSheet?.state?.url?.takeIf { it.isNotBlank() } ?: startUrl

    /** Captures favicon, title and console output, and hosts every page-initiated UI. */
    private inner class BrowserChromeClient : WebChromeClient() {
        /**
         * Without this override the base implementation returns false and WebView shows nothing at all, so
         * every `<input type="file">` in the browser is a dead tap. Always returns true: we take ownership
         * of the callback, and [showFileChooser] guarantees it is answered on every path.
         */
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = showFileChooser(filePathCallback, fileChooserParams)

        override fun onProgressChanged(
            view: WebView,
            newProgress: Int,
        ) {
            updateLoadProgress(newProgress)
        }

        override fun onReceivedTitle(
            view: WebView,
            title: String?,
        ) {
            pageTitle = title?.trim()?.takeIf { it.isNotEmpty() && it != view.url }
            controlSheet?.updateTitle(title)
            updateTaskDescription()
        }

        override fun onReceivedIcon(
            view: WebView,
            icon: Bitmap?,
        ) {
            if (icon == null || mainFrameLoadFailed) return
            pageIcon = icon
            updateTaskDescription()
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

        // The framework's own JS dialogs only appear when the WebView's context IS an Activity
        // (`JsDialogHelper.canShowAlertDialog`), and this one is built from [nightThemedContext] — a
        // configuration context, not the Activity — so without these overrides every alert() was silently
        // dismissed, confirm() always answered false and prompt() null. [jsDialogs] shows them itself.
        override fun onJsAlert(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = jsDialogs.alert(url, message, result)

        override fun onJsConfirm(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = jsDialogs.confirm(url, message, result)

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean = jsDialogs.prompt(url, message, defaultValue, result)

        override fun onJsBeforeUnload(
            view: WebView,
            url: String?,
            message: String?,
            result: JsResult,
        ): Boolean = jsDialogs.beforeUnload(result)

        /**
         * A `_blank` link or a user-initiated `window.open()`: open it as a new browser window, like a new
         * Chrome tab. Without a user gesture it's refused (Chrome's popup blocker).
         */
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            if (!isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val (token, child) = BrowserPopups.create(this@NappletBrowserActivity, shimJs, proxyPort, useTor, themeType, webViewProfile)
            transport.webView = child
            resultMsg.sendToTarget()
            startActivity(popupIntent(this@NappletBrowserActivity, token))
            return true
        }

        /** `window.close()` from a window a page opened: close this window. */
        override fun onCloseWindow(window: WebView) {
            if (window === webView) finish()
        }

        override fun onPermissionRequest(request: PermissionRequest) = handlePermissionRequest(request)

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            permissionPrompt?.dismiss()
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            val siteOrigin = BrowserChrome.originOf(origin) ?: return callback.invoke(origin, false, false)
            resolveSitePermissions(siteOrigin, listOf(BrowserSitePermission.LOCATION)) { granted ->
                // Never let WebView remember it: the answer lives in the main-process registry.
                callback.invoke(origin, BrowserSitePermission.LOCATION in granted, false)
            }
        }

        override fun onGeolocationPermissionsHidePrompt() {
            permissionPrompt?.dismiss()
        }

        override fun onShowCustomView(
            view: View,
            callback: CustomViewCallback,
        ) = enterFullscreen(view, callback)

        override fun onHideCustomView() = exitFullscreen()
    }

    /**
     * Opens the system picker for a page's file input and routes the pick back to it. Returns true
     * unconditionally: the callback is ours from here on, and it is delivered on every path — a real
     * pick, a cancel, or a device with no app that can return a file (the input is released with null so
     * the user can tap it again after installing one).
     */
    private fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        pendingFileChooser.start(filePathCallback)
        fileChooserLauncher.launch(
            acceptTypes = params.acceptTypes?.toList().orEmpty(),
            allowMultiple = NappletFileChooser.allowsMultiple(params.mode),
            captureEnabled = params.isCaptureEnabled,
            pageTitle = params.title,
        )
        return true
    }

    private inner class BrowserClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return false
            return BrowserWebTools.openExternal(this@NappletBrowserActivity, uri, request.hasGesture()) { view.loadUrl(it) }
        }

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            // A fresh main-frame navigation: arm history gating and show the new address.
            pendingMainFrameUrl = url
            mainFrameLoadFailed = false
            // A window a page opened takes its first real page as its home ("scope").
            if (startUrl == "about:blank" && url.startsWith("http")) startUrl = url
            // Re-arm favicon capture when the host changes, so a same-host in-page nav doesn't re-send.
            if (OmniboxInput.hostOf(url) != lastIconHost) {
                lastIconHost = null
                pageIcon = null
                pageTitle = null
                themeColor = null
                applyThemeColor(null)
            }
            // Chrome scopes "block this page's dialogs" to the page: a new main-frame load lifts it.
            jsDialogs.onMainFrameNavigation()
            controlSheet?.setLoading(true)
            showUrl(url)
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
            showUrl(url)
        }

        override fun doUpdateVisitedHistory(
            view: WebView,
            url: String,
            isReload: Boolean,
        ) {
            syncNavigation(view)
            showUrl(url)
        }

        override fun onPageFinished(
            view: WebView,
            url: String,
        ) {
            syncNavigation(view)
            controlSheet?.setLoading(false)
            showUrl(url)
            // Record only a clean http(s) main-frame load — never a typed-but-failed address.
            if (!mainFrameLoadFailed && (url.startsWith("https://") || url.startsWith("http://"))) {
                recordHistory(url, view.title)
                scheduleFaviconSniff(view, url)
            }
        }

        /**
         * The renderer died (crashed or was killed for memory). Every WebView in `:napplet` shares one
         * renderer, and returning false here would kill the whole process — taking every embedded tab with
         * it. So drop just this WebView and offer a reload, like Chrome's "Aw, Snap!".
         */
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            if (view !== webView) {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                return true
            }
            val lastUrl = view.url?.takeIf { it.startsWith("http") } ?: startUrl
            Log.w(TAG) { "Renderer gone (crashed=${detail.didCrash()}); offering a reload of $lastUrl" }
            exitFullscreen()
            destroyWebView()
            showCrashView(lastUrl)
            return true
        }
    }

    private fun syncNavigation(view: WebView) {
        syncBackState()
        controlSheet?.setNavigation(view.canGoBack(), view.canGoForward())
    }

    /**
     * Pushes the displayed [url] into the chrome and, when it is a different page, asks the broker whether
     * it is pinned — the registry lives in the main process, so the star can't know on its own.
     */
    private fun showUrl(url: String) {
        controlSheet?.updateUrl(url)
        if (url == lastFavoriteQueryUrl) return
        lastFavoriteQueryUrl = url
        val msg =
            Message.obtain(null, NappletIpc.MSG_QUERY_WEB_FAVORITE).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(NappletIpc.KEY_FAVORITE_URL, url) }
            }
        queueToBroker(msg)
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
            if (view !== webView || mainFrameLoadFailed || host == lastIconHost || view.url != url) return@postDelayed
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
        queueToBroker(msg)
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
        queueToBroker(msg)
    }

    /** Loads a user-typed address from "Edit address", forcing Tor for `.onion` when available. */
    private fun loadAddress(text: String) {
        val resolved = OmniboxInput.resolve(text) ?: return
        if (resolved.forceTor && proxyPort > 0 && !useTor) {
            useTor = true
            applyWebViewProxy(proxyPort)
            controlSheet?.setTor(true)
        }
        webView?.loadUrl(resolved.url)
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
        val envelope = parseJsonObjectOrNull(raw) ?: return

        // Browser conveniences (share, theme colour, blob downloads) are handled here, never brokered.
        if (envelope.stringOrNull("type").orEmpty().startsWith("browser.")) {
            onBrowserMessage(envelope)
            return
        }

        val scheme = sourceOrigin.scheme ?: return
        val host = sourceOrigin.host ?: return
        val origin = "$scheme://$host" + if (sourceOrigin.port > 0) ":${sourceOrigin.port}" else ""

        val id = envelope.stringOrNull("id").orEmpty().ifEmpty { "fire-${fireSeq++}" }
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
            queueToBroker(msg)
        } else {
            pendingByOrigin.getOrPut(origin) { mutableListOf() }.add(msg)
            requestBrowserToken(origin)
        }
    }

    /** A `browser.*` message from [BrowserExtrasScript]. */
    private fun onBrowserMessage(envelope: JsonObject) {
        when (envelope.stringOrNull("type")) {
            "browser.share" ->
                if (resumed) {
                    BrowserWebTools.share(this, envelope.stringOrNull("title"), envelope.stringOrNull("text"), envelope.stringOrNull("url"))
                }
            "browser.themeColor" -> {
                themeColor = BrowserChrome.parseCssRgb(envelope.stringOrNull("color"))
                applyThemeColor(themeColor)
                updateTaskDescription()
            }
            "browser.download" -> {
                val data = envelope.stringOrNull("data") ?: return
                if (data.startsWith("data:") && data.length <= BrowserDownloads.MAX_INLINE_BYTES / 3 * 4 + 256) {
                    BrowserDownloads.saveDataUrl(this, data, envelope.stringOrNull("name"))
                }
            }
        }
    }

    private fun requestBrowserToken(origin: String) {
        if (!mintInFlight.add(origin)) return
        val msg =
            Message.obtain(null, NappletIpc.MSG_MINT_BROWSER_TOKEN).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(NappletIpc.KEY_BROWSER_ORIGIN, origin) }
            }
        queueToBroker(msg)
    }

    /** Sends now when the broker is bound, else queues until it is. */
    private fun queueToBroker(msg: Message) {
        if (brokerMessenger != null) sendToBroker(msg) else pendingBrokerRequests.add(msg)
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
                val result = (parseJsonObjectOrNull(payload) ?: JsonObject(emptyMap())).withString("id", id)
                bridgeReplyProxy?.postMessage(result.toString())
            }
            NappletIpc.MSG_PUSH -> {
                val payload = data.getString(NappletIpc.KEY_PAYLOAD) ?: return true
                bridgeReplyProxy?.postMessage(payload)
            }
            NappletIpc.MSG_WEB_FAVORITE_STATE -> {
                val url = data.getString(NappletIpc.KEY_FAVORITE_URL) ?: return true
                controlSheet?.setFavorite(url, data.getBoolean(NappletIpc.KEY_FAVORITE_IS_FAVORITE, false))
            }
            NappletIpc.MSG_SITE_PERMISSIONS -> {
                val callback = sitePermissionQueries.remove(data.getLong(NappletIpc.KEY_REQUEST_ID)) ?: return true
                callback(
                    BrowserSitePermission.entries.associateWith { permission ->
                        runCatching { Decision.valueOf(data.getString(NappletIpc.KEY_SITE_PERMISSION_PREFIX + permission.key).orEmpty()) }.getOrDefault(Decision.ASK)
                    },
                )
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

    // ---- site permissions ----

    private fun handlePermissionRequest(request: PermissionRequest) {
        val origin = BrowserChrome.originOf(request.origin.toString()) ?: return request.deny()
        val wanted = request.resources.mapNotNull(::sitePermissionFor).distinct()
        if (wanted.isEmpty()) return request.deny()
        resolveSitePermissions(origin, wanted) { granted ->
            val resources = request.resources.filter { sitePermissionFor(it) in granted }.toTypedArray()
            if (resources.isEmpty()) request.deny() else request.grant(resources)
        }
    }

    private fun sitePermissionFor(resource: String): BrowserSitePermission? =
        when (resource) {
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> BrowserSitePermission.CAMERA
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> BrowserSitePermission.MICROPHONE
            else -> null
        }

    /**
     * Decides [wanted] for [origin]: the user's remembered answers first (kept per origin in the main
     * process — the same on Tor and the open web), a prompt for anything never answered, and finally
     * Android's own runtime permission for whatever was allowed. [done] receives what is granted.
     */
    private fun resolveSitePermissions(
        origin: String,
        wanted: List<BrowserSitePermission>,
        done: (Set<BrowserSitePermission>) -> Unit,
    ) {
        querySitePermissions(origin) { decisions ->
            val allowed = wanted.filter { decisions[it] == Decision.ALLOW }.toSet()
            val ask = wanted.filter { decisions[it] == Decision.ASK }
            if (ask.isEmpty()) {
                ensureRuntimePermissions(allowed, done)
            } else {
                showPermissionPrompt(origin, ask) { allow ->
                    // null = dismissed without an answer: deny this once, remember nothing.
                    if (allow != null) ask.forEach { rememberSitePermission(origin, it, if (allow) Decision.ALLOW else Decision.BLOCK) }
                    ensureRuntimePermissions(if (allow == true) allowed + ask else allowed, done)
                }
            }
        }
    }

    private fun querySitePermissions(
        origin: String,
        callback: (Map<BrowserSitePermission, Decision>) -> Unit,
    ) {
        val id = ++sitePermissionSeq
        sitePermissionQueries[id] = callback
        val msg =
            Message.obtain(null, NappletIpc.MSG_QUERY_SITE_PERMISSIONS).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putLong(NappletIpc.KEY_REQUEST_ID, id)
                        putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                    }
            }
        queueToBroker(msg)
    }

    private fun rememberSitePermission(
        origin: String,
        permission: BrowserSitePermission,
        decision: Decision,
    ) {
        val msg =
            Message.obtain(null, NappletIpc.MSG_SET_SITE_PERMISSION).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_BROWSER_ORIGIN, origin)
                        putString(NappletIpc.KEY_SITE_PERMISSION, permission.key)
                        putString(NappletIpc.KEY_SITE_DECISION, decision.name)
                    }
            }
        queueToBroker(msg)
    }

    /**
     * Chrome's permission bubble: "<host> wants to — Use your camera — Block / Allow". [answer] gets true
     * (allow), false (block), or null when the prompt went away unanswered.
     */
    private fun showPermissionPrompt(
        origin: String,
        permissions: List<BrowserSitePermission>,
        answer: (Boolean?) -> Unit,
    ) {
        if (isFinishing || isDestroyed || permissionPrompt != null) {
            answer(null)
            return
        }
        var answered = false
        val lines =
            permissions.joinToString("\n") {
                "• " +
                    getString(
                        when (it) {
                            BrowserSitePermission.CAMERA -> CommonsR.string.browser_permission_camera
                            BrowserSitePermission.MICROPHONE -> CommonsR.string.browser_permission_microphone
                            BrowserSitePermission.LOCATION -> CommonsR.string.browser_permission_location
                        },
                    )
            }
        permissionPrompt =
            AlertDialog
                .Builder(this)
                .setTitle(getString(CommonsR.string.browser_permission_title, BrowserChrome.displayHost(origin)))
                .setMessage(lines)
                .setPositiveButton(CommonsR.string.browser_permission_allow) { _, _ ->
                    answered = true
                    answer(true)
                }.setNegativeButton(CommonsR.string.browser_permission_block) { _, _ ->
                    answered = true
                    answer(false)
                }.setOnDismissListener {
                    permissionPrompt = null
                    // Dismissed without choosing (back, the page cancelling): deny this time, remember nothing.
                    if (!answered) {
                        answered = true
                        answer(null)
                    }
                }.show()
    }

    /** Requests Android's runtime permission for each allowed site permission that lacks it. */
    private fun ensureRuntimePermissions(
        allowed: Set<BrowserSitePermission>,
        done: (Set<BrowserSitePermission>) -> Unit,
    ) {
        val needed = allowed.associateWith(::androidPermissionFor)
        val missing = needed.values.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.distinct()
        if (missing.isEmpty()) return done(allowed)
        if (pendingRuntimeGrant != null) return done(allowed - needed.filterValues { it in missing }.keys)
        pendingRuntimeGrant = { result ->
            val granted = allowed.filter { ContextCompat.checkSelfPermission(this, needed.getValue(it)) == PackageManager.PERMISSION_GRANTED }.toSet()
            if (granted.size < allowed.size) Toast.makeText(this, CommonsR.string.browser_permission_system_denied, Toast.LENGTH_LONG).show()
            done(granted)
        }
        runtimePermissionLauncher.launch(missing.toTypedArray())
    }

    private fun androidPermissionFor(permission: BrowserSitePermission): String =
        when (permission) {
            BrowserSitePermission.CAMERA -> Manifest.permission.CAMERA
            BrowserSitePermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
            BrowserSitePermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        }

    // ---- fullscreen video ----

    private fun enterFullscreen(
        view: View,
        callback: WebChromeClient.CustomViewCallback,
    ) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        view.setBackgroundColor(Color.BLACK)
        // Over the whole window, outside the inset root, so the video really covers the screen.
        (window.decorView as? FrameLayout)?.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        syncBackState()
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        customView = null
        (window.decorView as? FrameLayout)?.removeView(view)
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        val callback = customViewCallback
        customViewCallback = null
        callback?.onCustomViewHidden()
        syncBackState()
    }

    // ---- theme colour + Recents ----

    /**
     * Tints the system-bar areas with the page's `theme-color` (the root's padding shows through behind
     * the transparent bars), and flips the bar icons to stay legible on it. Null restores the app theme.
     */
    private fun applyThemeColor(color: Int?) {
        val root = root ?: return
        val background = color ?: resolveThemeColor(android.R.attr.colorBackground)
        root.setBackgroundColor(background)
        val light = ColorUtils.calculateLuminance(background) > 0.5
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** Makes this task look like an installed app in Recents: the site's title, icon and colour. */
    @Suppress("DEPRECATION")
    private fun updateTaskDescription() {
        val label = pageTitle ?: title.ifBlank { null } ?: BrowserChrome.displayHost(currentUrl())
        val color = themeColor ?: 0
        val description =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityManager.TaskDescription
                    .Builder()
                    .setLabel(label)
                    .apply { pageIcon?.let { setIcon(Icon.createWithBitmap(it)) } }
                    .apply { if (color != 0) setPrimaryColor(color) }
                    .build()
            } else {
                ActivityManager.TaskDescription(label, pageIcon, color)
            }
        runCatching { setTaskDescription(description) }
    }

    // ---- long-press menu ----

    override fun onCreateContextMenu(
        menu: ContextMenu,
        v: View,
        menuInfo: ContextMenu.ContextMenuInfo?,
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val wv = v as? WebView ?: return
        val hit = wv.hitTestResult
        val extra = hit.extra?.takeIf { it.isNotBlank() } ?: return
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> addLinkItems(menu, extra)
            WebView.HitTestResult.IMAGE_TYPE -> addImageItems(menu, extra)
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // An image inside a link: the hit gives the image; ask the page for the link's href.
                val href = Handler(Looper.getMainLooper()).obtainMessage()
                wv.requestFocusNodeHref(href)
                href.data
                    .getString("url")
                    ?.takeIf { it.startsWith("http") }
                    ?.let { addLinkItems(menu, it) }
                addImageItems(menu, extra)
            }
        }
    }

    private fun addLinkItems(
        menu: ContextMenu,
        link: String,
    ) {
        if (menu.size() == 0) menu.setHeaderTitle(link)
        if (link.startsWith("http")) {
            menu.add(CommonsR.string.browser_ctx_open_new_window).setOnMenuItemClickListener {
                startActivity(newWindowIntent(link))
                true
            }
        }
        menu.add(CommonsR.string.browser_ctx_copy_link).setOnMenuItemClickListener {
            BrowserWebTools.copyToClipboard(this, link)
            true
        }
        menu.add(CommonsR.string.browser_ctx_share_link).setOnMenuItemClickListener {
            BrowserWebTools.share(this, null, null, link)
            true
        }
    }

    private fun addImageItems(
        menu: ContextMenu,
        image: String,
    ) {
        if (menu.size() == 0) menu.setHeaderTitle(image.take(80))
        if (image.startsWith("http") || image.startsWith("data:")) {
            menu.add(CommonsR.string.browser_ctx_download_image).setOnMenuItemClickListener {
                val wv = webView
                val cookie = wv?.let { BrowserWebTools.cookieManager(it).getCookie(image) }
                BrowserDownloads.download(this, image, wv?.settings?.userAgentString, null, null, cookie, if (useTor) proxyPort else -1)
                true
            }
        }
        if (image.startsWith("http")) {
            menu.add(CommonsR.string.browser_ctx_copy_image_link).setOnMenuItemClickListener {
                BrowserWebTools.copyToClipboard(this, image)
                true
            }
        }
    }

    /** A plain new browser window for [url], sharing this one's route, theme and account storage. */
    private fun newWindowIntent(url: String): Intent = intent(this, url, proxyPort, useTor, theme = themeType, webViewProfile = webViewProfile).addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)

    // ---- network ----

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
        webView?.reload()
        controlSheet?.setTor(useTor)
        // Key the persisted choice on the host actually displayed (which may differ from startUrl after
        // in-page navigation), so the preference sticks to the right site.
        val host = runCatching { currentUrl().toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
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
     * The top pull-down pill: a small grabber at the top edge (out of the corner where a site shows its
     * own avatar) that expands to the Chrome-PWA-style menu laid out by [BrowserChrome].
     */
    private fun buildControlSheet(): View =
        NappletControlSheet(
            context = this,
            initialState =
                BrowserChrome.State(
                    surface = BrowserChrome.Surface.WEB,
                    presentation = BrowserChrome.Presentation.FULL_SCREEN,
                    url = startUrl,
                    startUrl = startUrl,
                    torOn = if (proxyPort > 0) useTor else null,
                ),
            title = barTitle(),
            listener = sheetListener,
            isFavoriteInitially = intent.getBooleanExtra(EXTRA_IS_FAVORITE, false),
        ).also { controlSheet = it }

    private val sheetListener =
        object : NappletControlSheet.Listener {
            override fun onAction(action: Action) {
                val wv = webView
                when (action) {
                    Action.BACK -> wv?.goBack()
                    Action.FORWARD -> wv?.goForward()
                    Action.RELOAD -> wv?.reload()
                    Action.STOP -> wv?.stopLoading()
                    Action.FAVORITE -> sendFavoriteToggle(currentUrl(), controlSheet?.wantsFavorite() ?: true)
                    Action.SHARE -> BrowserWebTools.share(this@NappletBrowserActivity, pageTitle, null, currentUrl())
                    Action.BACK_TO_APP -> wv?.let { BrowserWebTools.backToScope(it, startUrl) }
                    Action.COPY_LINK -> BrowserWebTools.copyToClipboard(this@NappletBrowserActivity, currentUrl())
                    Action.EDIT_ADDRESS -> Unit
                    Action.FIND_IN_PAGE -> {
                        setConsoleShowing(false)
                        findBar?.show()
                        syncBackState()
                    }
                    Action.TEXT_SIZE -> Unit
                    Action.DESKTOP_SITE ->
                        wv?.let {
                            val desktop = !BrowserWebTools.isDesktopMode(it)
                            BrowserWebTools.setDesktopMode(it, desktop)
                            controlSheet?.setDesktopSite(desktop)
                        }
                    Action.ADD_TO_HOME_SCREEN -> addToHomeScreen()
                    Action.OPEN_IN_BROWSER_APP -> BrowserWebTools.openInOtherBrowser(this@NappletBrowserActivity, currentUrl())
                    Action.OPEN_FULL_SCREEN -> Unit
                    Action.TOR -> setNetworkMode(!useTor)
                    Action.ACCESS_INFO -> Unit
                    Action.SITE_SETTINGS -> openPermissions()
                    Action.CONSOLE -> setConsoleShowing(!consoleShowing)
                }
            }

            override fun onNavigate(text: String) = loadAddress(text)

            override fun onTextZoom(percent: Int) {
                webView?.let { BrowserWebTools.setTextZoom(it, percent) }
            }

            override fun onOriginTap() = showPageInfo()

            override fun onClose() = finish()
        }

    private fun setConsoleShowing(showing: Boolean) {
        consoleShowing = showing
        if (showing) findBar?.hide()
        consolePanel?.setShowing(showing)
        controlSheet?.setConsoleShowing(showing)
    }

    /** Chrome's page-info sheet: connection, Tor, certificate, then site settings and clearing its data. */
    private fun showPageInfo() {
        val wv = webView ?: return
        val url = currentUrl()
        AlertDialog
            .Builder(this)
            .setTitle(BrowserChrome.displayHost(url))
            .setMessage(BrowserWebTools.pageInfo(this, wv, if (proxyPort > 0) useTor else null))
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(CommonsR.string.browser_page_info_permissions) { _, _ -> openPermissions() }
            .setNegativeButton(CommonsR.string.browser_page_info_clear_data) { _, _ ->
                webView?.let { BrowserWebTools.clearSiteData(this, it, url) }
            }.show()
    }

    /** Asks the main process to pin a launcher shortcut that reopens this page in Amethyst's browser. */
    private fun addToHomeScreen() {
        val url = currentUrl()
        val msg =
            Message.obtain(null, NappletIpc.MSG_ADD_TO_HOME_SCREEN).apply {
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_FAVORITE_URL, url)
                        putString(NappletIpc.KEY_FAVORITE_LABEL, pageTitle ?: BrowserChrome.displayHost(url))
                    }
            }
        queueToBroker(msg)
    }

    /**
     * Ask the broker to open the editable permission screen for the site currently displayed. NIP-07 grants
     * for a plain browser are keyed per visited origin (`browser:<origin>`), so we send the live origin and
     * the broker launches the main activity at that Connected Apps detail.
     */
    private fun openPermissions() {
        val uri = runCatching { currentUrl().toUri() }.getOrNull() ?: return
        val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return
        val origin = "$scheme://$host" + if (uri.port > 0) ":${uri.port}" else ""
        val msg =
            Message.obtain(null, NappletIpc.MSG_OPEN_PERMISSIONS).apply {
                data = Bundle().apply { putString(NappletIpc.KEY_BROWSER_ORIGIN, origin) }
            }
        queueToBroker(msg)
    }

    /**
     * Pins or unpins [url] in the main-process registry. Sends the state the user asked for rather than a
     * flip, and takes the broker's confirmed state back through [NappletIpc.MSG_WEB_FAVORITE_STATE].
     */
    private fun sendFavoriteToggle(
        url: String,
        isFavorite: Boolean,
    ) {
        val label = pageTitle ?: runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
        val msg =
            Message.obtain(null, NappletIpc.MSG_TOGGLE_WEB_FAVORITE).apply {
                replyTo = replyMessenger
                data =
                    Bundle().apply {
                        putString(NappletIpc.KEY_FAVORITE_URL, url)
                        putString(NappletIpc.KEY_FAVORITE_LABEL, label)
                        putBoolean(NappletIpc.KEY_FAVORITE_IS_FAVORITE, isFavorite)
                    }
            }
        queueToBroker(msg)
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

    /** Chrome's "Aw, Snap!": the page's renderer died; offer to load [url] again in a fresh WebView. */
    private fun showCrashView(url: String) {
        crashView?.let { contentFrame.removeView(it) }
        crashView =
            LinearLayout(this)
                .apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(resolveThemeColor(android.R.attr.colorBackground))
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    addView(
                        TextView(this@NappletBrowserActivity).apply {
                            text = getString(CommonsR.string.browser_renderer_gone)
                            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
                            textSize = 18f
                            gravity = Gravity.CENTER
                        },
                    )
                    addView(
                        Button(this@NappletBrowserActivity).apply {
                            text = getString(CommonsR.string.browser_renderer_gone_reload)
                            setOnClickListener {
                                crashView?.let { contentFrame.removeView(it) }
                                crashView = null
                                val wv = buildWebView()
                                contentFrame.addView(wv, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                                wv.loadUrl(url)
                            }
                        },
                    )
                }.also { contentFrame.addView(it) }
        syncBackState()
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
            controlSheet?.setLoading(false)
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
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun readContractAsset(path: String): ByteArray = assets.open(NappletWebContract.RESOURCE_ASSET_ROOT + path).use { it.readBytes() }

    companion object {
        private const val TAG = "NappletBrowserActivity"
        private const val ACTIVITY_CLASS = "com.vitorpamplona.amethyst.napplethost.NappletBrowserActivity"

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
        private const val EXTRA_POPUP_TOKEN = "popupToken"

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
                .setClassName(context, ACTIVITY_CLASS)
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

        /**
         * Opens, as its own task, a window a page asked for; [token] names the popup WebView parked in
         * [BrowserPopups]. Usable from the embedded browser's Service as well as from an Activity.
         */
        fun popupIntent(
            context: Context,
            token: String,
        ): Intent =
            Intent()
                .setClassName(context, ACTIVITY_CLASS)
                .putExtra(EXTRA_POPUP_TOKEN, token)
                .setData("amethyst-window://$token".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
}
