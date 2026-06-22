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
     * Serves the trusted shell or a verified app blob for a GET to our origin; 404s anything else on
     * the origin, and returns null (defer to the WebView) for non-GET or off-origin requests.
     */
    fun serve(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (!request.method.equals("GET", ignoreCase = true)) return null
        if (!url.startsWith(NappletWebContract.ORIGIN)) return notFound()

        if (url == NappletWebContract.SHELL_URL) return serveShell()
        if (url == NappletWebContract.APP_BASE || url.startsWith(NappletWebContract.APP_BASE)) {
            // A document navigation accepts text/html; a sub-resource (js/css/img) does not.
            val acceptsHtml = request.requestHeaders["Accept"]?.contains("text/html", ignoreCase = true) == true
            return serveAppResource(url, acceptsHtml)
        }
        return notFound()
    }

    private fun serveShell(): WebResourceResponse =
        WebResourceResponse(
            "text/html",
            "utf-8",
            200,
            "OK",
            mapOf("Content-Security-Policy" to NappletWebContract.SHELL_CSP),
            ByteArrayInputStream(shellHtmlBytes),
        )

    private fun serveAppResource(
        url: String,
        acceptsHtml: Boolean,
    ): WebResourceResponse {
        val requestPath =
            url
                .removePrefix(NappletWebContract.APP_BASE)
                .substringBefore('?')
                .substringBefore('#')
                .let { if (it.isEmpty()) "/" else "/$it" }

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
        val isHtml = mime.equals("text/html", ignoreCase = true)
        val bytes = if (isHtml) injectShim(resolution.bytes) else resolution.bytes

        return WebResourceResponse(
            mime,
            charset,
            200,
            "OK",
            mapOf("Content-Security-Policy" to NappletWebContract.APP_CSP),
            ByteArrayInputStream(bytes),
        )
    }

    /** Inserts the `window.napplet` client shim into the applet's HTML document. */
    private fun injectShim(html: ByteArray): ByteArray {
        val text = html.decodeToString()
        val script = "<script>$shimJs</script>"
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
    }
}
