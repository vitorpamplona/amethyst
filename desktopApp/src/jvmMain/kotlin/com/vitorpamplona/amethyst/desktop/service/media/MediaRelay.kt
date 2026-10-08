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
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.Executors

/**
 * A loopback HTTP relay that lets the native engine stream through the app's OkHttp clients.
 *
 * Android's player reads video through OkHttp, so a video the user routes over Tor goes through
 * the Tor proxy. The desktop engines (GStreamer, Media Foundation, AVFoundation) fetch a URL with
 * their own HTTP stacks, which know nothing of Tor. When a video should go over Tor (see
 * [MediaHttp.viaTor]), the engine is given `http://127.0.0.1:<port>/<secret>/<original>/<name>`
 * instead, where `<original>` is the whole original URL in base64url (so no character of it can
 * trip the server's URI parser) and `<name>` its file name, for engines that sniff the container
 * from the extension. This relay fetches the original through [MediaHttp.client], forwarding Range
 * so seeking still works, and rewrites HLS playlists so every segment and variant comes through
 * here too.
 *
 * It listens on loopback only, a random path prefix keeps it from being an open proxy for other
 * programs on the machine, and the Host check keeps a web page from reaching it by DNS rebinding.
 */
object MediaRelay {
    private const val TAG = "MediaRelay"

    // A playlist is read whole to rewrite it; a real one is a few KiB, so anything past this is not.
    private const val MAX_PLAYLIST_BYTES = 4L * 1024 * 1024

    // Request headers worth passing on: the engine's ranges and its idea of what it can play.
    private val forwardedRequestHeaders = listOf("Range", "If-Range", "Accept")

    // Response headers the engine needs to seek and to pick a demuxer.
    private val forwardedResponseHeaders =
        listOf("Content-Type", "Content-Range", "Accept-Ranges", "Last-Modified", "ETag", "Cache-Control")

    // A rewritten playlist is a different body: none of these describe it any more.
    private val playlistDroppedHeaders = listOf("Content-Range", "Accept-Ranges", "Last-Modified", "ETag")

    private val secret: String =
        ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    private val server: HttpServer by lazy {
        HttpServer.create(InetSocketAddress(loopback, 0), 0).apply {
            executor =
                Executors.newCachedThreadPool { runnable ->
                    Thread(runnable, "media-relay").apply { isDaemon = true }
                }
            createContext("/$secret/") { exchange ->
                try {
                    handle(exchange)
                } catch (e: Exception) {
                    // Whatever went wrong, the engine gets an answer rather than a dropped socket.
                    Log.d(TAG) { "Relay request failed: ${e.message}" }
                    runCatching { exchange.sendResponseHeaders(502, -1) }
                } finally {
                    exchange.close()
                }
            }
            start()
        }
    }

    private val hostHeader: String by lazy { "127.0.0.1:${server.address.port}" }

    /**
     * What the engine should open for [url]: through this relay when it goes over Tor, else [url]
     * itself. Null when [url] should go over Tor but cannot (not http(s)): handing it to the engine
     * would fetch it around Tor.
     */
    fun streamingUrl(url: String): String? = if (MediaHttp.viaTor(url)) relayUrlFor(url) else url

    /** [url] as a relay address, or null when it is not an http(s) URL. */
    fun relayUrlFor(url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(parsed.toString().toByteArray())
        return "http://$hostHeader/$secret/$encoded/${fileNameOf(parsed)}"
    }

    /** The original URL a relay [path] (what follows the secret) stands for, or null. */
    internal fun originalFor(path: String): String? {
        val encoded = path.substringBefore('/')
        val decoded =
            try {
                String(Base64.getUrlDecoder().decode(encoded))
            } catch (_: IllegalArgumentException) {
                return null
            }
        return decoded.toHttpUrlOrNull()?.toString()
    }

    // The last path segment, reduced to characters no URI parser objects to.
    private fun fileNameOf(url: HttpUrl): String =
        url.pathSegments
            .lastOrNull()
            ?.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            ?.takeIf { it.isNotEmpty() && it.length <= 100 }
            ?: "media"

    private fun handle(exchange: HttpExchange) {
        if (exchange.requestHeaders.getFirst("Host") != hostHeader) {
            exchange.sendResponseHeaders(403, -1)
            return
        }
        val method = exchange.requestMethod
        val original = originalFor(exchange.requestURI.rawPath.removePrefix("/$secret/"))
        if (original == null || (method != "GET" && method != "HEAD")) {
            exchange.sendResponseHeaders(400, -1)
            return
        }

        val request =
            Request
                .Builder()
                .url(original)
                .apply {
                    if (method == "HEAD") head()
                    forwardedRequestHeaders.forEach { name ->
                        exchange.requestHeaders.getFirst(name)?.let { header(name, it) }
                    }
                }.build()

        try {
            MediaHttp.client(original).newCall(request).execute().use { response ->
                val playlist = response.isSuccessful && isPlaylist(response.request.url, response.header("Content-Type"))
                forwardedResponseHeaders.forEach { name ->
                    if (playlist && name in playlistDroppedHeaders) return@forEach
                    response.header(name)?.let { exchange.responseHeaders.add(name, it) }
                }

                when {
                    method == "HEAD" -> {
                        if (!playlist) response.header("Content-Length")?.let { exchange.responseHeaders.add("Content-Length", it) }
                        exchange.sendResponseHeaders(response.code, -1)
                    }

                    playlist -> {
                        sendPlaylist(exchange, response)
                    }

                    else -> {
                        sendBody(exchange, response)
                    }
                }
            }
        } catch (e: IOException) {
            // The engine hung up mid-stream (a seek, a stop) or the upstream failed.
            Log.d(TAG) { "Relay of $original ended: ${e.message}" }
            runCatching { exchange.sendResponseHeaders(502, -1) }
        }
    }

    /**
     * Every URI in a playlist becomes an absolute relay address, resolved against where the
     * playlist really came from (after redirects), so no segment or variant escapes to the
     * engine's own HTTP stack. A playlist that is too big, or names anything but http(s), is refused.
     */
    private fun sendPlaylist(
        exchange: HttpExchange,
        response: Response,
    ) {
        val source = response.body.source()
        if (source.request(MAX_PLAYLIST_BYTES + 1)) {
            exchange.sendResponseHeaders(502, -1)
            return
        }
        val rewritten = rewritePlaylist(source.buffer.readUtf8(), response.request.url)
        if (rewritten == null) {
            exchange.sendResponseHeaders(502, -1)
            return
        }
        val body = rewritten.toByteArray()
        // Always the whole playlist, even if the engine asked for a range of it.
        exchange.sendResponseHeaders(200, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) exchange.responseBody.write(body)
    }

    private fun sendBody(
        exchange: HttpExchange,
        response: Response,
    ) {
        val length = response.body.contentLength()
        exchange.sendResponseHeaders(
            response.code,
            when {
                length == 0L -> -1
                length > 0 -> length
                else -> 0
            },
        )
        if (length != 0L) {
            response.body.byteStream().use { it.copyTo(exchange.responseBody, 64 * 1024) }
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

    /**
     * [playlist] with every segment, variant, key and map URI pointing at this relay, or null when
     * one of them is not http(s), which the relay cannot carry and the engine must not fetch itself.
     */
    internal fun rewritePlaylist(
        playlist: String,
        base: HttpUrl,
    ): String? {
        var refused = false
        val rewritten =
            playlist.lines().joinToString("\n") { line ->
                when {
                    line.isBlank() -> {
                        line
                    }

                    line.startsWith("#") -> {
                        uriAttribute.replace(line) { match ->
                            val relayed = toRelay(match.groupValues[1], base)
                            if (relayed == null) refused = true
                            "URI=\"${relayed.orEmpty()}\""
                        }
                    }

                    else -> {
                        toRelay(line.trim(), base) ?: "".also { refused = true }
                    }
                }
            }
        return if (refused) null else rewritten
    }

    private fun toRelay(
        uri: String,
        base: HttpUrl,
    ): String? = base.resolve(uri)?.let { relayUrlFor(it.toString()) }
}
