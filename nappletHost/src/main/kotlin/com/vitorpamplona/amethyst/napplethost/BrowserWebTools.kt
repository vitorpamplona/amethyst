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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslCertificate
import android.os.Build
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.quartz.utils.Log
import java.net.URISyntaxException
import java.text.DateFormat
import java.util.WeakHashMap
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * WebView-side behaviours shared by the full-screen browser ([NappletBrowserActivity]) and the embedded
 * one ([NappletBrowserService]), so the two surfaces act the same: non-web schemes, desktop mode, text
 * size, the out-of-scope "back to app" walk, site-data clearing, page info, copy and share.
 *
 * Everything here runs in the keyless `:napplet` process and touches only the WebView it is given.
 */
object BrowserWebTools {
    private const val TAG = "BrowserWebTools"

    // ---- setup ----

    /**
     * The settings every browser WebView gets (full-screen, embedded, and popups), so a page behaves the
     * same wherever it opens. New windows are enabled — `onCreateWindow` turns them into new browser
     * windows — but `window.open()` still needs a user gesture (Blink's popup blocker, as in Chrome).
     * Geolocation is enabled at the WebView level; every request still goes through the per-site prompt.
     */
    @Suppress("SetJavaScriptEnabled")
    fun applyBrowserSettings(webView: WebView) {
        webView.settings.apply {
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
            setSupportMultipleWindows(true)
            setGeolocationEnabled(true)
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
    }

    /**
     * The document-start script for a browser WebView: the direct-bridge flags, the NIP-07 [shimJs], and
     * [BrowserExtrasScript]. [imeProxy] is set only for the embedded surface, which has no native keyboard.
     */
    fun browserStartScript(
        shimJs: String,
        imeProxy: Boolean,
    ): String {
        val flags = if (imeProxy) " window.__nappletImeProxy = true;" else ""
        return "if (window.top === window) { window.__nappletDirectBridge = true; window.__nappletNip07 = true;$flags }\n$shimJs\n${BrowserExtrasScript.JS}"
    }

    // ---- non-web schemes ----

    /**
     * Handles a navigation to a non-http(s) [uri] the way Chrome does: `intent:` URIs are parsed (component
     * and selector stripped so a page can't aim at a private activity) and fall back to their
     * `browser_fallback_url` in-page when no app takes them; other schemes (`mailto:`, `tel:`, `geo:`,
     * `nostr:`, …) go to the system. Only acts on a user gesture, like Chrome, so a page can't bounce the
     * user into another app on load. Always consumes the navigation.
     */
    fun openExternal(
        context: Context,
        uri: Uri,
        hasGesture: Boolean,
        loadInPage: (String) -> Unit,
    ): Boolean {
        if (!hasGesture) return true
        val intent =
            if (uri.scheme.equals("intent", ignoreCase = true)) {
                parseIntentUri(uri.toString()) ?: return true
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = intent.getStringExtra("browser_fallback_url")
            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                loadInPage(fallback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open ${uri.scheme} link", e)
        }
        return true
    }

    /** Parses an `intent:` URI with the same hardening Chrome applies. Null when it's malformed. */
    fun parseIntentUri(uri: String): Intent? =
        try {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                // A web page may only ask for something any app could handle: never an explicit
                // component, never a selector (which could smuggle one in).
                component = null
                selector = null
                // No grants of our own content to whatever answers.
                flags = flags and
                    (
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    ).inv()
            }
        } catch (_: URISyntaxException) {
            null
        }

    // ---- desktop site / text size ----

    private val mobileUserAgents = WeakHashMap<WebView, String>()

    fun isDesktopMode(webView: WebView): Boolean = mobileUserAgents.containsKey(webView)

    /** Switches [webView] between its own mobile UA and [BrowserChrome.desktopUserAgent], then reloads. */
    fun setDesktopMode(
        webView: WebView,
        desktop: Boolean,
    ) {
        if (desktop == isDesktopMode(webView)) return
        val settings = webView.settings
        if (desktop) {
            val mobile = settings.userAgentString
            mobileUserAgents[webView] = mobile
            settings.userAgentString = BrowserChrome.desktopUserAgent(mobile)
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = mobileUserAgents.remove(webView)
        }
        webView.reload()
    }

    fun setTextZoom(
        webView: WebView,
        percent: Int,
    ) {
        webView.settings.textZoom = percent.coerceIn(BrowserChrome.TEXT_ZOOM_STEPS.first(), BrowserChrome.TEXT_ZOOM_STEPS.last())
    }

    // ---- scope ----

    /**
     * Chrome's out-of-scope bar ✕: step back to the most recent history entry on [startUrl]'s origin, or
     * reload [startUrl] when there is none.
     */
    fun backToScope(
        webView: WebView,
        startUrl: String,
    ) {
        val home = BrowserChrome.originOf(startUrl) ?: return
        val history = webView.copyBackForwardList()
        for (i in history.currentIndex - 1 downTo 0) {
            if (BrowserChrome.originOf(history.getItemAtIndex(i).url).equals(home, ignoreCase = true)) {
                webView.goBackOrForward(i - history.currentIndex)
                return
            }
        }
        webView.loadUrl(startUrl)
    }

    // ---- storage ----

    /** The cookie jar of [webView]'s own storage profile (the per-account one), or the default jar. */
    fun cookieManager(webView: WebView): CookieManager =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.getProfile(webView).cookieManager }.getOrNull() ?: CookieManager.getInstance()
        } else {
            CookieManager.getInstance()
        }

    private fun webStorage(webView: WebView): WebStorage =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.getProfile(webView).webStorage }.getOrNull() ?: WebStorage.getInstance()
        } else {
            WebStorage.getInstance()
        }

    /**
     * Clears what the site behind [url] stored in [webView]'s profile — its origin's web storage (local
     * storage, IndexedDB, cache storage, service workers) and its cookies — then reloads it logged out.
     * Other sites and other accounts are untouched.
     */
    fun clearSiteData(
        context: Context,
        webView: WebView,
        url: String,
    ) {
        val origin = BrowserChrome.originOf(url) ?: return
        runCatching { webStorage(webView).deleteOrigin(origin) }
        val cookies = cookieManager(webView)
        val names =
            cookies
                .getCookie(url)
                .orEmpty()
                .split(';')
                .mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotEmpty) }
        val host = BrowserChrome.displayHost(url)
        // A cookie can be scoped to the host or to any parent domain; expire it on each so it really goes.
        val domains = host.split('.').let { parts -> (0 until (parts.size - 1).coerceAtLeast(1)).map { parts.drop(it).joinToString(".") } }
        names.forEach { name ->
            cookies.setCookie(url, "$name=; Max-Age=0; Path=/")
            domains.forEach { domain -> cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$domain") }
        }
        cookies.flush()
        Toast.makeText(context, CommonsR.string.browser_site_data_cleared, Toast.LENGTH_SHORT).show()
        webView.reload()
    }

    // ---- page info ----

    /** The paragraphs of the page-info sheet for the page in [webView]. */
    fun pageInfo(
        context: Context,
        webView: WebView,
        torOn: Boolean?,
    ): String {
        val url = webView.url.orEmpty()
        val lines = mutableListOf<String>()
        lines +=
            context.getString(
                if (url.startsWith("https://", ignoreCase = true)) CommonsR.string.browser_page_info_https else CommonsR.string.browser_page_info_http,
            )
        if (torOn != null) {
            lines += context.getString(if (torOn) CommonsR.string.browser_page_info_tor else CommonsR.string.browser_page_info_open_web)
        }
        webView.certificate?.let { lines += certificateLine(context, it) }
        return lines.joinToString("\n\n")
    }

    private fun certificateLine(
        context: Context,
        cert: SslCertificate,
    ): String {
        val to = cert.issuedTo?.cName?.takeIf { it.isNotBlank() } ?: cert.issuedTo?.oName.orEmpty()
        val by = cert.issuedBy?.oName?.takeIf { it.isNotBlank() } ?: cert.issuedBy?.cName.orEmpty()
        val until = cert.validNotAfterDate?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(it) }.orEmpty()
        return context.getString(CommonsR.string.browser_page_info_certificate, to, by, until)
    }

    // ---- copy / share / other browser ----

    fun copyToClipboard(
        context: Context,
        text: String,
    ) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
        // Android 13+ shows its own clipboard confirmation; a toast on top of it would be noise.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, CommonsR.string.browser_link_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private var lastShareAt = 0L

    /**
     * Opens the Android share sheet for a page or a `navigator.share()` call. Throttled: a page can post
     * share requests straight to the bridge (bypassing the polyfill's user-activation check), so at most
     * one sheet per [SHARE_COOLDOWN_MS] is honoured.
     */
    fun share(
        context: Context,
        title: String?,
        text: String?,
        url: String?,
    ) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastShareAt < SHARE_COOLDOWN_MS) return
        lastShareAt = now
        val body = listOfNotNull(text?.takeIf { it.isNotBlank() }, url?.takeIf { it.isNotBlank() }).joinToString("\n")
        if (body.isEmpty()) return
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
                title?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            }
        val chooser = Intent.createChooser(send, context.getString(CommonsR.string.browser_share_chooser))
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }.onFailure { Log.w(TAG, "Share failed", it) }
    }

    /**
     * Hands [url] to a browser other than Amethyst — the escape hatch for sites that refuse embedded
     * browsers (Google sign-in, some banks). Our own activities are excluded from the chooser.
     */
    fun openInOtherBrowser(
        context: Context,
        url: String,
    ) {
        val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        val chooser =
            Intent.createChooser(view, null).apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownBrowsableComponents(context, view))
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, CommonsR.string.browser_no_other_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun ownBrowsableComponents(
        context: Context,
        intent: Intent,
    ): Array<ComponentName> =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager
                .queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName == context.packageName }
                .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
                .toTypedArray()
        }.getOrDefault(emptyArray())

    private const val SHARE_COOLDOWN_MS = 1_000L
}
