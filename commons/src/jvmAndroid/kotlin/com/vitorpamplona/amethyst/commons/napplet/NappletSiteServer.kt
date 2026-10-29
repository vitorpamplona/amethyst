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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag

/**
 * What a host serves for one napplet or nSite, whatever its browser: the trusted shell on
 * [NappletWebContract.SHELL_URL] and the launch's verified files on its own [appOrigin]. Android's
 * WebView and the desktop's Chromium only translate requests in and [Response]s out, so the rules
 * below can't drift between them.
 *
 * - A NIP-5D napplet's shell carries its sole `/index.html` as an opaque-origin `srcdoc`, with the
 *   prelude injected outside its aggregate hash; its requests anywhere else are 404s.
 * - A NIP-5A site's shell frames [appOrigin]. Every file there is a verified blob from its event;
 *   a document navigation to a path the event doesn't list falls back to `/`, so client-side routes
 *   survive deep links and reloads, while a missing script or image stays a 404. HTML gets the
 *   prelude with NIP-07. Requests to other hosts are the browser's to send ([serve] returns null).
 */
class NappletSiteServer(
    private val paths: List<PathTag>,
    private val servers: List<String>,
    private val blobs: NappletBlobStore,
    private val shellHtml: ByteArray,
    private val shimJs: String,
    private val appOrigin: String,
    private val profile: HostProfile,
    // Exact NAP domains the trusted side authorized for this launch. The prelude projects only these
    // objects; absence is the NIP-5D capability-availability signal.
    private val declaredDomains: List<String>,
    // A surface with no keyboard of its own (Android's windowless embedded tabs) relays the focused
    // field to the host's keyboard through the shim.
    private val imeProxy: Boolean = false,
    // Markup the host adds to the shell's head, before anything else runs there (the desktop's bridge).
    private val shellHead: String = "",
) {
    /** One HTTP response, for the host to hand its browser. */
    class Response(
        val status: Int,
        val reason: String,
        val mime: String,
        /** Null when the body has no text encoding. */
        val charset: String?,
        val headers: Map<String, String>,
        val body: ByteArray,
    ) {
        /** [mime] with its charset, for a `Content-Type` header. */
        val contentType: String get() = if (charset != null) "$mime; charset=$charset" else mime
    }

    /**
     * Resolves [requestPath] to a verified blob (or why not). The hosts call it to probe the entry
     * point before showing anything, which also warms the cache for the page's own request.
     */
    suspend fun resolve(requestPath: String): StaticSiteResolution = blobs.resolve(requestPath, paths, servers)

    /**
     * The response for a request to [url], or null when the browser should handle it itself (a
     * request that isn't a GET, or a site's request to another host). [acceptsHtml] is whether the
     * request is a document navigation.
     */
    suspend fun serve(
        method: String,
        url: String,
        acceptsHtml: Boolean,
    ): Response? {
        if (!method.equals("GET", ignoreCase = true)) return null
        if (url == NappletWebContract.SHELL_URL) return shell()
        if (url == appOrigin || url.startsWith("$appOrigin/")) return appResource(url, acceptsHtml)
        // A napplet has no network (its CSP is connect-src 'none'); a site is a normal web app.
        return if (profile.allowsOffOrigin) null else notFound()
    }

    private suspend fun shell(): Response {
        val artifact =
            if (profile == HostProfile.NAPPLET) {
                val entry = resolve(INDEX_PATH) as? StaticSiteResolution.Resolved ?: return notFound()
                injectPrelude(entry.bytes)
            } else {
                null
            }
        val page = NappletWebContract.shell(shellHtml, profile, appOrigin, artifact)
        val html =
            if (shellHead.isEmpty()) {
                page.html
            } else {
                page.html
                    .decodeToString()
                    .replaceFirst("<head>", "<head>$shellHead")
                    .encodeToByteArray()
            }
        return Response(200, "OK", MIME_HTML, "utf-8", mapOf("Content-Security-Policy" to page.csp) + NO_STORE, html)
    }

    private suspend fun appResource(
        url: String,
        acceptsHtml: Boolean,
    ): Response {
        val requestPath =
            url
                .removePrefix(appOrigin)
                .substringBefore('?')
                .substringBefore('#')
                .ifEmpty { "/" }

        var resolution = resolve(requestPath)
        if (resolution !is StaticSiteResolution.Resolved && acceptsHtml && requestPath != "/") {
            resolution = resolve("/")
        }
        if (resolution !is StaticSiteResolution.Resolved) return notFound()

        val (mime, charset) = splitContentType(resolution.contentType)
        val body = if (mime.equals(MIME_HTML, ignoreCase = true)) injectPrelude(resolution.bytes) else resolution.bytes
        // A locked napplet gets the strict app CSP; a site sets its own.
        val csp = profile.appCsp?.let { mapOf("Content-Security-Policy" to it) } ?: emptyMap()
        return Response(200, "OK", mime, charset, csp + NO_STORE, body)
    }

    /** Inserts host policy and the explicit-domain `window.napplet` prelude before authored code. */
    private fun injectPrelude(html: ByteArray): ByteArray =
        NappletWebContract.injectPrelude(
            html = html,
            shimJs = shimJs,
            declaredDomains = declaredDomains,
            locked = profile == HostProfile.NAPPLET,
            injectNip07 = profile.injectsNip07,
            imeProxy = imeProxy,
        )

    companion object {
        const val INDEX_PATH = "/index.html"
        const val MIME_HTML = "text/html"

        // Every response is built from verified bytes on each request; nothing is for the browser
        // to keep.
        private val NO_STORE = mapOf("Cache-Control" to "no-store")

        fun notFound(): Response = Response(404, "Not Found", "text/plain", "utf-8", emptyMap(), ByteArray(0))

        /** Splits a content type into its MIME type and charset (UTF-8 for text-like types). */
        fun splitContentType(contentType: String): Pair<String, String?> {
            val mime = contentType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
            val charset =
                contentType.substringAfter("charset=", "").trim().ifEmpty { null }
                    ?: if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "utf-8" else null
            return mime to charset
        }
    }
}
