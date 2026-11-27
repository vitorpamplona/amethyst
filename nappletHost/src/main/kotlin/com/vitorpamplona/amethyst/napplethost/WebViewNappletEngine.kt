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
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.napplet.NappletProxyClaims
import com.vitorpamplona.amethyst.commons.napplet.NappletSiteServer
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.amethyst.commons.napplet.openStream
import com.vitorpamplona.amethyst.commons.napplet.session.NappletBridgeGate
import com.vitorpamplona.amethyst.commons.napplet.session.NappletFrame
import com.vitorpamplona.amethyst.commons.napplet.session.NappletGuard
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRequestKind
import com.vitorpamplona.amethyst.commons.napplet.session.NappletRoutePort
import com.vitorpamplona.amethyst.commons.napplet.session.NappletSessionCore
import com.vitorpamplona.amethyst.commons.napplet.session.NappletWebEngine
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.coroutines.resume

/**
 * The Android WebView adapter ([NappletWebEngine]) of the shared [NappletSessionCore], used by both the
 * full-screen [NappletHostActivity] and the embedded [NappletHostService]. It owns what is the WebView's
 * own: its storage profile, the hardening, the document-start guards, the origin-restricted bridge
 * listener and the answers to the WebView's questions, each of which the core decides:
 * [shouldInterceptRequest][NappletWebViewClient.shouldInterceptRequest] by
 * [NappletSessionCore.classifyRequest] and [NappletSessionCore.serve],
 * [shouldOverrideUrlLoading][NappletWebViewClient.shouldOverrideUrlLoading] by
 * [NappletSessionCore.onNavigation], a dead renderer by [NappletSessionCore.onRendererGone].
 *
 * Main thread only, except [installGuard] (it only records the guard) and the request answers (WebView
 * worker threads).
 */
class WebViewNappletEngine(
    /** A context forced to the app theme, so the WebView's content follows it (see nightThemedContext). */
    themedContext: Context,
    webViewProfile: String?,
    /** What the host draws around the page; its callbacks run on the main thread. */
    private val client: NappletWebViewClient,
    private val chrome: NappletWebChromeClient,
    /** A shell message past the bridge gate, and the proxy that answers its document. */
    private val onBridgeMessage: (raw: String, isMainFrame: Boolean, origin: String, replyProxy: JavaScriptReplyProxy) -> Unit,
    /** Right before the shell loads, on the main thread: the host mounts the view. */
    private val onLoadShell: (WebView) -> Unit,
) : NappletWebEngine {
    private val main = Handler(Looper.getMainLooper())

    val webView: WebView = WebView(themedContext)

    /** Set once the renderer died and the view was destroyed under it, or [close] ran. */
    var isGone: Boolean = false
        private set

    // The guards the core asked for, installed on the main thread right before the shell loads.
    private val wantedGuards = mutableListOf<NappletGuard>()

    init {
        // FIRST touch after construction: setProfile throws once the WebView has loaded content (or its
        // profile has otherwise been used), so the storage partition must be chosen before anything else.
        NappletWebViewProfile.apply(themedContext, webView, webViewProfile)
        harden(webView)
        webView.webViewClient = client
        webView.webChromeClient = chrome
        // Origin-restricted bridge: only the trusted shell page (main frame) can reach native.
        WebViewCompat.addWebMessageListener(webView, NappletWebContract.BRIDGE_NAME, setOf(NappletWebContract.ORIGIN), ::onShellMessage)
    }

    private fun onShellMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        val raw = message.data ?: return
        val origin = originOf(sourceOrigin)
        if (!NappletBridgeGate.acceptsOrigin(isMainFrame, origin)) return
        onBridgeMessage(raw, isMainFrame, origin, replyProxy)
    }

    override fun installGuard(guard: NappletGuard): Boolean {
        if (!NappletWebRtcGuard.isSupported()) return false
        synchronized(wantedGuards) { wantedGuards += guard }
        return true
    }

    override suspend fun loadShell(shellUrl: String): Boolean =
        withContext(Dispatchers.Main) {
            if (isGone) return@withContext false
            synchronized(wantedGuards) { wantedGuards.toList().also { wantedGuards.clear() } }.forEach { guard ->
                when (guard) {
                    NappletGuard.WEBRTC -> if (!NappletWebRtcGuard.install(webView)) return@withContext false
                    NappletGuard.COOKIES -> NappletWebRtcGuard.installCookieGuard(webView)
                }
            }
            onLoadShell(webView)
            webView.loadUrl(shellUrl)
            true
        }

    override fun evaluateInShell(js: String) {
        onMain { if (!isGone) webView.evaluateJavascript(js, null) }
    }

    /** The renderer died: the view is dropped (every WebView here shares the renderer) and never touched again. */
    internal fun onGone(view: WebView) {
        isGone = true
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    override fun close() {
        onMain {
            if (isGone) return@onMain
            isGone = true
            // Detach before destroy(): destroying an attached WebView corrupts the shared multiprocess
            // renderer/network state and breaks the other (embedded) WebViews in this `:napplet` process
            // (dead DNS, empty DOM reads, dead selection paint, broken IME). See NappletBrowserActivity.
            webView.stopLoading()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        private const val TAG = "WebViewNappletEngine"

        /** Whether this WebView has what every launch needs: the origin-restricted bridge listener. */
        fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

        /** `scheme://host[:port]` of the origin the WebView reported for the calling frame. */
        fun originOf(origin: Uri): String {
            val port = if (origin.port == -1) "" else ":${origin.port}"
            return "${origin.scheme}://${origin.host}$port"
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun harden(webView: WebView) {
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
        }

        internal fun notFound(): WebResourceResponse = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", mapOf("X-Content-Type-Options" to "nosniff"), ByteArrayInputStream(ByteArray(0)))

        internal fun toWebResponse(
            url: String,
            response: NappletSiteServer.Response,
        ): WebResourceResponse {
            // The file may be trimmed away before it opens; an exception here would crash the WebView.
            val body =
                try {
                    response.openStream()
                } catch (e: IOException) {
                    Log.w(TAG, "Could not open $url", e)
                    return WebResourceResponse("text/plain", "utf-8", 502, "Bad Gateway", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            return WebResourceResponse(response.mime, response.charset, response.status, response.reason, response.headers, body)
        }
    }
}

/**
 * The WebView's client for a launch. The security answers are final and come from the shared core; a
 * host overrides the rest (its loading screen, back state, console).
 */
open class NappletWebViewClient : WebViewClient() {
    internal lateinit var core: NappletSessionCore
    internal lateinit var engine: WebViewNappletEngine

    /** Serves the trusted shell and the launch's verified files; a launch with no network gets nothing else. */
    final override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val url = request.url.toString()
        return when (core.classifyRequest(url, isImage = false)) {
            NappletRequestKind.SERVE -> {
                // A document navigation accepts text/html; a sub-resource (js/css/img) does not.
                val acceptsHtml = request.requestHeaders["Accept"]?.contains(NappletSiteServer.MIME_HTML, ignoreCase = true) == true
                // From an internal origin a request can't go anywhere else: what isn't served is a 404.
                runBlocking { core.serve(request.method, url, acceptsHtml, mainFrameNavigation = request.isForMainFrame) }
                    ?.let { WebViewNappletEngine.toWebResponse(url, it) }
                    ?: WebViewNappletEngine.notFound()
            }
            NappletRequestKind.CANCEL -> WebViewNappletEngine.notFound()
            // A page's own documents, and a site's traffic through the claimed route.
            NappletRequestKind.LOCAL, NappletRequestKind.NETWORK, NappletRequestKind.PAPER_ART -> null
        }
    }

    /**
     * The shell and the app's frame never leave the internal origins; a link the user tapped opens outside.
     * This WebView names no parent frame, so every frame below the shell counts as the app's own.
     */
    final override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = !core.onNavigation(request.url.toString(), if (request.isForMainFrame) NappletFrame.SHELL else NappletFrame.APP, request.hasGesture())

    /**
     * The renderer died. Every WebView in `:napplet` shares one renderer and an unhandled crash kills the
     * whole process (every embedded tab too), so only this WebView is dropped; the core closes the bridge
     * and the host offers to start over.
     */
    final override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        Log.w("NappletWebViewClient") { "Renderer gone (crashed=${detail.didCrash()})" }
        onBeforeRendererGone(view)
        engine.onGone(view)
        core.onRendererGone()
        return true
    }

    /** Before a dead renderer's view is dropped: the host closes what drew over it. */
    open fun onBeforeRendererGone(view: WebView) {}
}

/** The WebView's chrome client for a launch: the shell's refusal to run unguarded reaches the core first. */
open class NappletWebChromeClient : WebChromeClient() {
    internal lateinit var core: NappletSessionCore

    final override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        core.onConsoleMessage(consoleMessage.message().orEmpty())
        return onConsole(consoleMessage)
    }

    /** A console line for the host's panel; true when it was handled. */
    open fun onConsole(consoleMessage: ConsoleMessage): Boolean = false
}

/**
 * The route of one launch ([NappletRoutePort]) over the `:napplet` process's one WebView proxy: a claim
 * filed with [WebViewProxyPolicy], which arbitrates every surface's wish (Tor always wins) and fails closed.
 * The sandbox can't wait for Tor to start: it learns the port from the launcher, and a retry brings a new one.
 */
class WebViewNappletRoute(
    private val owner: Any,
    private val proxyPort: Int,
) : NappletRoutePort {
    private val main = Handler(Looper.getMainLooper())

    override val torRunning: Boolean get() = proxyPort > 0

    override suspend fun claim(useTor: Boolean): Boolean {
        // Fail closed: Tor is on but its port isn't known, so nothing may go out.
        if (useTor && proxyPort <= 0) return false
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                WebViewProxyPolicy.claim(
                    owner = owner,
                    torPort = if (useTor) proxyPort else NappletProxyClaims.NO_PROXY,
                    onFailed = { if (cont.isActive) cont.resume(false) },
                ) { if (cont.isActive) cont.resume(true) }
            }
        }
    }

    override suspend fun awaitTor(): Boolean = proxyPort > 0

    override fun release() {
        if (Looper.myLooper() == Looper.getMainLooper()) WebViewProxyPolicy.release(owner) else main.post { WebViewProxyPolicy.release(owner) }
    }
}
