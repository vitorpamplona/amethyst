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

import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.BlobFetcher
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.GENERIC_CONTENT_TYPE
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.guessStaticContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.resolvePath
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.sniffContentType
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Serves the napplet sandbox's content over the internal `https://napplet.local` origin: the trusted
 * shell page, and the manifest's blobs — each **sha256-verified** by [StaticSiteResolver] before it
 * leaves this class. Everything else 404s. Blobs come from the shared content-addressed
 * [NappletBlobCache] (warmed by the prefetcher when the card was on screen, so opening is instant);
 * a cache miss falls back to a Tor-routed download that re-fills the cache. Because blobs are
 * content-addressed and re-verified on every serve, a stale or poisoned cache entry can never be served.
 *
 * This is the host's resource edge, kept separate from the Activity lifecycle and the broker bridge:
 * given a [WebResourceRequest] it returns the [WebResourceResponse] (with the right CSP headers) or
 * null to defer to the WebView.
 */
class NappletContentServer(
    private val paths: List<PathTag>,
    private val servers: List<String>,
    proxyPort: Int,
    cacheDir: File,
    private val shellHtmlBytes: ByteArray,
    private val shimJs: String,
    // The applet's own per-applet origin (a distinct napplet.local subdomain). The shell is on
    // NappletWebContract.ORIGIN; app blobs are served here so the applet has a real, isolated origin.
    private val appOrigin: String,
    // The host posture: a WEBSITE nSite is a normal web app — NIP-07 window.nostr provider, normal
    // network (no app CSP; off-origin requests defer to the WebView), unlike a locked NAPPLET.
    private val profile: HostProfile = HostProfile.NAPPLET,
    // Exact NAP domains the trusted main process authorized for this launch. The injected prelude
    // projects only these objects; absence is the NIP-5D capability-availability signal.
    private val declaredDomains: List<String> = emptyList(),
    // Embedded surfaces (a windowless Service) can't host the soft keyboard, so the shim installs the
    // IME proxy agent that relays the focused field to the host's keyboard. The full-screen Activity
    // host has a native keyboard and leaves this false.
    private val imeProxy: Boolean = false,
) {
    private val cache = NappletBlobCache(NappletBlobCache.dirFor(cacheDir))
    private val http = NappletBlobHttp.client(proxyPort)

    private val fetch: BlobFetcher = { url ->
        val hash = url.substringAfterLast('/').lowercase()
        cache.get(hash) ?: NappletBlobHttp.download(http, url)?.also { cache.put(hash, it) }
    }

    /**
     * Fast path for a cache hit: the blob is already on disk under its sha256 (the cache verified it on
     * write and addresses it by hash), so we serve it without re-hashing — skipping the resolver's
     * per-serve sha256 over the whole blob, which is the dominant CPU cost for large JS bundles. Returns
     * null on a miss, so the caller falls back to the verifying network resolve.
     */
    private fun cacheHit(requestPath: String): StaticSiteResolution.Resolved? {
        val match = paths.resolvePath(requestPath) ?: return null
        val bytes = cache.get(match.hash.lowercase()) ?: return null
        val byExtension = guessStaticContentType(match.path)
        val contentType = if (byExtension == GENERIC_CONTENT_TYPE) sniffContentType(bytes) ?: byExtension else byExtension
        return StaticSiteResolution.Resolved(match.path, match.hash, contentType, bytes, server = CACHE_SERVER)
    }

    /** Cache-first resolution: serve a verified-on-write CAS hit directly, else fall back to the resolver. */
    private fun resolveCacheFirst(requestPath: String): StaticSiteResolution = cacheHit(requestPath) ?: runBlocking { StaticSiteResolver.resolve(requestPath, paths, servers, fetch) }

    /**
     * Resolves [requestPath] to a verified blob (or PathNotInManifest / Unresolvable). Used by the host
     * to probe availability for the loading screen before showing the WebView. Warms the cache as a
     * side effect, so the subsequent WebView request serves from disk.
     */
    fun resolve(requestPath: String): StaticSiteResolution = resolveCacheFirst(requestPath)

    /**
     * Releases the per-instance OkHttp client (its dispatcher thread pool + connection pool) and cancels
     * any in-flight blob fetch. A new content server is built per embedded tab, so without this each
     * opened-then-closed napplet/nSite tab would leak OkHttp threads and keep-alive connections.
     */
    fun close() {
        runCatching {
            http.dispatcher.cancelAll()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    /**
     * Serves the trusted shell (on the shell origin) or a verified app blob (on the per-applet
     * [appOrigin]); 404s anything else, and returns null (defer to the WebView) for non-GET requests.
     */
    fun serve(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!request.method.equals("GET", ignoreCase = true)) return null

        if (url == NappletWebContract.SHELL_URL) return serveShell()
        if (url == appOrigin || url.startsWith("$appOrigin/")) {
            // A document navigation accepts text/html; a sub-resource (js/css/img) does not.
            val acceptsHtml = request.requestHeaders["Accept"]?.contains(MIME_HTML, ignoreCase = true) == true
            return serveAppResource(url, acceptsHtml)
        }
        // Off-origin: a locked napplet 404s (connect-src 'none' means it shouldn't ask). An nSite in
        // website mode is a normal web app — defer to the WebView so it can load external resources.
        return if (profile.allowsOffOrigin) null else notFound()
    }

    private fun serveShell(): WebResourceResponse {
        val sandbox: String
        val frameSource: String
        val bootstrap: String

        if (profile == HostProfile.NAPPLET) {
            // NIP-5D identity is bound to the exact verified bytes that execute. Resolve the sole
            // /index.html blob, inject host-owned policy/prelude outside its aggregate hash, then
            // hand those bytes to the opaque-origin child via srcdoc (never a navigated src).
            val resolution = resolveCacheFirst("/index.html")
            if (resolution !is StaticSiteResolution.Resolved) return notFound()
            val encoded = Base64.encodeToString(injectShim(resolution.bytes), Base64.NO_WRAP)
            sandbox = "allow-scripts"
            frameSource = "'self'"
            bootstrap =
                "var b=atob('$encoded'),u=new Uint8Array(b.length);" +
                "for(var j=0;j<b.length;j++)u[j]=b.charCodeAt(j);" +
                "iframe.srcdoc=new TextDecoder('utf-8').decode(u);"
        } else {
            // Amethyst's NIP-5A website posture is intentionally unchanged: a dedicated real origin,
            // normal website storage/network, and NIP-07 behind the existing consent broker.
            sandbox = "allow-scripts allow-same-origin"
            frameSource = appOrigin
            bootstrap = "iframe.src = '$appOrigin/';"
        }

        val html =
            shellHtmlBytes
                .decodeToString()
                .replace(NappletWebContract.APP_ORIGIN_PLACEHOLDER, appOrigin)
                .replace(NappletWebContract.APP_SANDBOX_PLACEHOLDER, sandbox)
                .replace(NappletWebContract.APP_BOOTSTRAP_PLACEHOLDER, bootstrap)
                .encodeToByteArray()
        return WebResourceResponse(
            MIME_HTML,
            "utf-8",
            200,
            "OK",
            mapOf("Content-Security-Policy" to NappletWebContract.shellCsp(frameSource)),
            ByteArrayInputStream(html),
        )
    }

    private fun serveAppResource(
        url: String,
        acceptsHtml: Boolean,
    ): WebResourceResponse {
        val requestPath =
            url
                .removePrefix(appOrigin)
                .substringBefore('?')
                .substringBefore('#')
                .ifEmpty { "/" }

        var resolution = resolveCacheFirst(requestPath)

        // SPA fallback: a document navigation (Accept: text/html) to a route that isn't in the
        // manifest falls back to the verified index.html, so client-side-routed sites survive deep
        // links and refreshes. Missing sub-resources (js/css/images) still 404 — they don't accept
        // html — so a broken asset never silently returns the page.
        if (resolution !is StaticSiteResolution.Resolved && acceptsHtml && requestPath != "/") {
            resolution = resolveCacheFirst("/")
        }

        if (resolution !is StaticSiteResolution.Resolved) return notFound()

        val (mime, charset) = splitContentType(resolution.contentType)
        val isHtml = mime.equals(MIME_HTML, ignoreCase = true)
        val bytes = if (isHtml) injectShim(resolution.bytes) else resolution.bytes

        // Locked napplets get the strict app CSP (connect-src 'none', etc.). An nSite in website mode
        // is a normal web app: no app CSP, so it can talk to relays (wss) and load external resources.
        val headers = profile.appCsp?.let { mapOf("Content-Security-Policy" to it) } ?: emptyMap()
        return WebResourceResponse(
            mime,
            charset,
            200,
            "OK",
            headers,
            ByteArrayInputStream(bytes),
        )
    }

    /** Inserts host policy and the explicit-domain `window.napplet` prelude before authored code. */
    private fun injectShim(html: ByteArray): ByteArray =
        NappletWebContract.injectPrelude(
            html = html,
            shimJs = shimJs,
            declaredDomains = declaredDomains,
            locked = profile == HostProfile.NAPPLET,
            injectNip07 = profile.injectsNip07,
            imeProxy = imeProxy,
        )

    private fun notFound(): WebResourceResponse = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun splitContentType(contentType: String): Pair<String, String> {
        val mime = contentType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
        val charset =
            contentType.substringAfter("charset=", "").trim().ifEmpty { null }
                ?: if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "utf-8" else ""
        return mime to charset
    }

    companion object {
        // Marker "server" for a Resolved served from the local content-addressed cache.
        private const val CACHE_SERVER = "cache"
        private const val MIME_HTML = "text/html"
    }
}
