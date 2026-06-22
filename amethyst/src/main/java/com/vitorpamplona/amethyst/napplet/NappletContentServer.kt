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

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.BlobFetcher
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Serves the napplet sandbox's content over the internal `https://napplet.local` origin: the trusted
 * shell page, and the manifest's blobs — each **sha256-verified** by [StaticSiteResolver] before it
 * leaves this class. Everything else 404s. Blobs are fetched through the user's Tor proxy (the applet
 * has no direct network) and disk-cached; because they are content-addressed and re-verified on every
 * serve, a stale or poisoned cache entry can never be served.
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
    private val http = buildHttpClient(proxyPort, cacheDir)

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

    /**
     * Routes blob fetches through the user's Tor SOCKS proxy when one is active (port > 0), and
     * caches them on disk. Blobs are content-addressed (`<server>/<sha256>`) and therefore
     * immutable, so a long-lived forced cache is safe — and the resolver re-verifies every blob's
     * sha256 on the way out regardless, so a stale/poisoned cache entry can never be served.
     */
    private fun buildHttpClient(
        port: Int,
        cacheDir: File,
    ): OkHttpClient {
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
        private const val TAG = "NappletContentServer"
        private const val BLOB_CACHE_BYTES = 50L * 1024 * 1024
    }
}
