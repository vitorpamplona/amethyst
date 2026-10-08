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
package com.vitorpamplona.amethyst.desktop.service.media

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.quartz.utils.Log
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * A loopback HTTP relay that lets the native engine stream through the app's OkHttp clients.
 *
 * Android's player reads video through OkHttp, so a video the user routes over Tor goes through
 * the Tor proxy. The desktop engines (GStreamer, Media Foundation, AVFoundation) fetch a URL with
 * their own HTTP stacks, which know nothing of Tor. When a video should go over Tor (see
 * [MediaHttp.viaTor]), the engine is given `http://127.0.0.1:<port>/<secret>/<scheme>/<host>/<path>`
 * instead: this relay fetches the original through [MediaHttp.client], forwarding Range so
 * seeking still works, and rewrites HLS playlists so every segment and variant comes through here
 * too.
 *
 * It listens on loopback only, and a random path prefix keeps it from being an open proxy for
 * other programs on the machine.
 */
object MediaRelay {
    private const val TAG = "MediaRelay"

    // Request headers worth passing on: the engine's ranges and its idea of what it can play.
    private val forwardedRequestHeaders = listOf("Range", "If-Range", "Accept")

    // Response headers the engine needs to seek and to pick a demuxer.
    private val forwardedResponseHeaders =
        listOf("Content-Type", "Content-Range", "Accept-Ranges", "Last-Modified", "ETag", "Cache-Control")

    private val secret: String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            executor =
                Executors.newCachedThreadPool { runnable ->
                    Thread(runnable, "media-relay").apply { isDaemon = true }
                }
            createContext("/$secret/") { exchange ->
                try {
                    handle(exchange)
                } finally {
                    exchange.close()
                }
            }
            start()
        }
    }

    /** What the engine should open for [url]: through this relay when it goes over Tor, else [url]. */
    fun streamingUrl(url: String): String = if (MediaHttp.viaTor(url)) relayUrlFor(url) ?: url else url

    /** [url] as a relay address, or null when it is not an http(s) URL. */
    fun relayUrlFor(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val authority = if (parsed.port == HttpUrl.defaultPort(parsed.scheme)) parsed.host else "${parsed.host}:${parsed.port}"
        val query = parsed.encodedQuery?.let { "?$it" } ?: ""
        return "http://127.0.0.1:${server.address.port}/$secret/${parsed.scheme}/${encode(authority)}${parsed.encodedPath}$query"
    }

    /** The original URL a relay [path] (after the secret) and [query] stand for. */
    internal fun originalFor(
        path: String,
        query: String?,
    ): String? {
        val parts = path.split('/', limit = 3)
        if (parts.size < 2 || (parts[0] != "http" && parts[0] != "https")) return null
        val authority = decode(parts[1])
        val rest = if (parts.size == 3) "/" + parts[2] else "/"
        return "${parts[0]}://$authority$rest${query?.let { "?$it" } ?: ""}"
    }

    private fun handle(exchange: HttpExchange) {
        val rawPath = exchange.requestURI.rawPath.removePrefix("/$secret/")
        val original = originalFor(rawPath, exchange.requestURI.rawQuery)
        if (original == null || (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD")) {
            exchange.sendResponseHeaders(400, -1)
            return
        }

        val request =
            Request
                .Builder()
                .url(original)
                .apply {
                    if (exchange.requestMethod == "HEAD") head()
                    forwardedRequestHeaders.forEach { name ->
                        exchange.requestHeaders.getFirst(name)?.let { header(name, it) }
                    }
                }.build()

        try {
            MediaHttp.client(original).newCall(request).execute().use { response ->
                forwardedResponseHeaders.forEach { name ->
                    response.header(name)?.let { exchange.responseHeaders.add(name, it) }
                }

                if (exchange.requestMethod == "HEAD") {
                    response.header("Content-Length")?.let { exchange.responseHeaders.add("Content-Length", it) }
                    exchange.sendResponseHeaders(response.code, -1)
                    return
                }

                if (response.isSuccessful && isPlaylist(response.request.url, response.header("Content-Type"))) {
                    // Every URI in a playlist becomes an absolute relay address, resolved against
                    // where the playlist really came from (after redirects), so no segment or
                    // variant escapes to the engine's own HTTP stack.
                    val body = rewritePlaylist(response.body.string(), response.request.url).toByteArray()
                    exchange.responseHeaders.remove("Content-Range")
                    exchange.sendResponseHeaders(response.code, if (body.isEmpty()) -1 else body.size.toLong())
                    if (body.isNotEmpty()) exchange.responseBody.write(body)
                    return
                }

                val length = response.body.contentLength()
                exchange.sendResponseHeaders(
                    response.code,
                    if (length == 0L) {
                        -1
                    } else if (length > 0) {
                        length
                    } else {
                        0
                    },
                )
                if (length != 0L) {
                    response.body.byteStream().use { it.copyTo(exchange.responseBody) }
                }
            }
        } catch (e: IOException) {
            // The engine hung up mid-stream (a seek, a stop) or the upstream failed.
            Log.d(TAG) { "Relay of $original ended: ${e.message}" }
            runCatching { exchange.sendResponseHeaders(502, -1) }
        }
    }

    private fun isPlaylist(
        url: HttpUrl,
        contentType: String?,
    ): Boolean {
        val type = contentType?.lowercase().orEmpty()
        return "mpegurl" in type || url.encodedPath.endsWith(".m3u8", ignoreCase = true)
    }

    private val uriAttribute = Regex("""URI="([^"]+)"""")

    /** [playlist] with every segment, variant, key and map URI pointing at this relay. */
    internal fun rewritePlaylist(
        playlist: String,
        base: HttpUrl,
    ): String =
        playlist.lines().joinToString("\n") { line ->
            when {
                line.isBlank() -> {
                    line
                }

                line.startsWith("#") -> {
                    uriAttribute.replace(line) { match ->
                        "URI=\"${toRelay(match.groupValues[1], base)}\""
                    }
                }

                else -> {
                    toRelay(line.trim(), base)
                }
            }
        }

    private fun toRelay(
        uri: String,
        base: HttpUrl,
    ): String {
        val absolute = base.resolve(uri) ?: return uri
        return relayUrlFor(absolute.toString()) ?: uri
    }

    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)

    private fun decode(text: String) = URLDecoder.decode(text, Charsets.UTF_8)
}
