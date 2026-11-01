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
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.quartz.utils.Log
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A loopback HTTP relay that lets the native engine stream through the app's OkHttp clients.
 *
 * Android's player reads video through OkHttp, so a video the user routes over Tor goes through
 * the Tor proxy. The desktop engines (GStreamer, Media Foundation, AVFoundation) fetch a URL with
 * their own HTTP stacks, which know nothing of Tor and keep nothing on disk. So a video that goes
 * over Tor (see [MediaHttp.viaTor]), or one whose bytes may be kept (see [VideoDiskCache.cacheable]),
 * is given to the engine as
 * `http://127.0.0.1:<port>/<secret>/<original>/<name>`, where `<original>` is the whole original URL in base64url (so no character of it can
 * trip the server's URI parser) and `<name>` its file name, for engines that sniff the container
 * from the extension. This relay fetches the original through [MediaHttp.client], forwarding Range
 * so seeking still works, and rewrites HLS playlists so every segment and variant comes through
 * here too. The bytes of plain videos also land in [VideoDiskCache], which answers the next
 * request for them from disk.
 *
 * It listens on [ORIGINS] loopback ports and hands each video it opens the next one. AVFoundation
 * keeps only about six connections open per origin (host and port), and a paused engine keeps its
 * stream open while it buffers ahead, so a feed's paused cards on one port left the video being
 * watched waiting for a connection, or with none at all.
 *
 * It listens on loopback only, a random path prefix keeps it from being an open proxy for other
 * programs on the machine, and the Host check keeps a web page from reaching it by DNS rebinding.
 */
object MediaRelay {
    private const val TAG = "MediaRelay"
    private const val SESSION_PREFIX = "s."
    private const val MAX_REVOKED = 4_096

    // The longest a stream gives way to the watched video in one go.
    private const val MAX_HOLD_MS = 10_000L

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

    // More than the engines a feed opens at once (GlobalMediaPlayer's card and warm engines, plus
    // the playing one), so no port carries more than a couple of them.
    private const val ORIGINS = 8

    private val executor =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "media-relay").apply { isDaemon = true }
        }

    private val servers: List<HttpServer> by lazy { List(ORIGINS) { newServer() } }

    private val hosts: List<String> by lazy { servers.map { "127.0.0.1:${it.address.port}" } }

    private val nextOrigin = AtomicInteger()

    private fun newServer(): HttpServer =
        HttpServer.create(InetSocketAddress(loopback, 0), 0).apply {
            executor = this@MediaRelay.executor
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

    /**
     * What the engine should open for [url]: through this relay when it goes over Tor, or when it
     * is a plain http(s) video whose bytes may land in [VideoDiskCache]; else [url] itself. HLS that
     * needn't go over Tor stays direct: it is never cached, so the playlist rewriting would only add
     * risk to live streams. Null when [url] should go over Tor but cannot (not http(s)): handing it
     * to the engine would fetch it around Tor.
     */
    fun streamingUrl(
        url: String,
        session: String? = null,
    ): String? =
        when {
            MediaHttp.viaTor(url) -> relayUrlFor(url, session = session)
            VideoDiskCache.cacheable(url) -> relayUrlFor(url, session = session) ?: url
            else -> url
        }

    /** A new name for one engine's requests, which [revoke] cuts off. */
    fun newSession(): String = ByteArray(9).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    // Upstream calls in flight, by session, so revoke can cancel them.
    private val sessionCalls = ConcurrentHashMap<String, MutableSet<Call>>()

    // Sessions cut off, newest last: their requests are refused. Bounded; a session is one engine.
    private val revoked: MutableSet<String> =
        Collections.synchronizedSet(
            Collections.newSetFromMap(
                object : LinkedHashMap<String, Boolean>() {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > MAX_REVOKED
                },
            ),
        )

    /**
     * Cuts off the engine that streams as [session]: what it is downloading stops now and anything
     * it asks for later is refused. A paused engine keeps buffering, and one retired after its card
     * scrolled away kept downloading for up to 17 s, from the same hosts, often over the same HTTP/2
     * connection, as the video the user stopped on.
     */
    fun revoke(session: String) {
        revoked += session
        pictured -= session
        sessionCalls.remove(session)?.let { calls -> synchronized(calls) { calls.toList() }.forEach { it.cancel() } }
    }

    private fun execute(
        original: String,
        request: Request,
        session: String?,
    ): Response {
        val call = MediaHttp.client(original).newCall(request)
        if (session == null) return call.execute()
        val calls = sessionCalls.computeIfAbsent(session) { Collections.synchronizedSet(HashSet()) }
        calls += call
        // Revoked while this was being set up.
        if (session in revoked) call.cancel()
        return call.execute()
    }

    private fun release(
        session: String?,
        request: Request,
    ) {
        if (session == null) return
        sessionCalls[session]?.removeIf { it.request() === request }
    }

    /**
     * [url] as a relay address on [host], by default the next of the relay's origins; null when it is
     * not an http(s) URL.
     */
    fun relayUrlFor(
        url: String,
        host: String = hosts[Math.floorMod(nextOrigin.getAndIncrement(), ORIGINS)],
        session: String? = null,
    ): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(parsed.toString().toByteArray())
        val named = session?.let { "$SESSION_PREFIX$it/" } ?: ""
        return "http://$host/$secret/$encoded/$named${fileNameOf(parsed)}"
    }

    /** The session a relay [path] (what follows the secret) streams as, or null. */
    internal fun sessionFor(path: String): String? =
        path
            .split('/')
            .getOrNull(1)
            ?.takeIf { it.startsWith(SESSION_PREFIX) }
            ?.removePrefix(SESSION_PREFIX)

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
        // The port it came in on: a page that rebinds a DNS name to 127.0.0.1 sends its own name.
        val host = "127.0.0.1:${exchange.localAddress.port}"
        if (exchange.requestHeaders.getFirst("Host") != host) {
            exchange.sendResponseHeaders(403, -1)
            return
        }
        val method = exchange.requestMethod
        val path = exchange.requestURI.rawPath.removePrefix("/$secret/")
        val original = originalFor(path)
        if (original == null || (method != "GET" && method != "HEAD")) {
            exchange.sendResponseHeaders(400, -1)
            return
        }
        val session = sessionFor(path)
        if (session != null && session in revoked) {
            exchange.sendResponseHeaders(410, -1)
            return
        }

        val range = exchange.requestHeaders.getFirst("Range")
        val keep = method == "GET" && VideoDiskCache.cacheable(original)
        val role =
            if (session == null) {
                "chromium"
            } else if (session == synchronized(foregroundLock) { foreground }) {
                "watched"
            } else {
                "background"
            }
        val started = System.currentTimeMillis()
        VideoTimeline.mark(original) { "relay $method ${range ?: "whole"} ($role engine)" }
        if (keep && runCatching { VideoDiskCache.serve(exchange, original, range) }.getOrDefault(false)) {
            VideoTimeline.mark(original) { "relay ${range ?: "whole"}: served from disk in ${System.currentTimeMillis() - started} ms" }
            return
        }
        if (keep) {
            val held = runCatching { VideoDiskCache.heldPrefix(original, range) }.getOrNull()
            if (held != null && sendStitched(exchange, original, held, session)) {
                VideoTimeline.mark(original) { "relay ${range ?: "whole"}: ${held.heldEnd / 1024} KiB from disk, then the network; ended after ${System.currentTimeMillis() - started} ms" }
                return
            }
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
            execute(original, request, session).use { response ->
                VideoTimeline.mark(original) { "relay ${range ?: "whole"}: host answered ${response.code} in ${System.currentTimeMillis() - started} ms" }
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
                        sendPlaylist(exchange, response, host)
                    }

                    else -> {
                        sendBody(exchange, original, response, keep, session)
                        VideoTimeline.mark(original) { "relay ${range ?: "whole"}: done after ${System.currentTimeMillis() - started} ms" }
                    }
                }
            }
        } catch (e: IOException) {
            // The engine hung up mid-stream (a seek, a stop), the upstream failed, or the engine was cut off.
            Log.d(TAG) { "Relay of $original ended: ${e.message}" }
            VideoTimeline.mark(original) { "relay ${range ?: "whole"}: ended after ${System.currentTimeMillis() - started} ms (${e.message})" }
            runCatching { exchange.sendResponseHeaders(502, -1) }
        } finally {
            release(session, request)
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
        host: String,
    ) {
        val source = response.body.source()
        if (source.request(MAX_PLAYLIST_BYTES + 1)) {
            exchange.sendResponseHeaders(502, -1)
            return
        }
        val rewritten = rewritePlaylist(source.buffer.readUtf8(), response.request.url, host)
        if (rewritten == null) {
            exchange.sendResponseHeaders(502, -1)
            return
        }
        val body = rewritten.toByteArray()
        // Always the whole playlist, even if the engine asked for a range of it.
        exchange.sendResponseHeaders(200, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) exchange.responseBody.write(body)
    }

    /**
     * Answers a GET whose range starts in bytes on disk ([held]) with those bytes, then the rest
     * fetched from the network, as one response. The engine asks for a whole file from its start: of
     * an episode whose index [VideoIndexPrefetch] fetched it then waits only for what follows.
     *
     * The bytes on disk go out before the network is asked for the rest: a slow host took up to 10 s
     * to answer at all, and the engine, waiting on that for an index already on disk, gave up on the
     * video's size. When the host won't give the rest as a range the response ends short, and the
     * engine asks again from there.
     */
    private fun sendStitched(
        exchange: HttpExchange,
        original: String,
        held: VideoDiskCache.HeldPrefix,
        session: String?,
    ): Boolean {
        val partial = exchange.requestHeaders.getFirst("Range") != null
        exchange.responseHeaders.add("Content-Type", held.contentType)
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        if (partial) exchange.responseHeaders.add("Content-Range", "bytes ${held.range.first}-${held.range.last}/${held.total}")
        exchange.sendResponseHeaders(if (partial) 206 else 200, held.range.last - held.range.first + 1)
        val out = exchange.responseBody
        try {
            VideoDiskCache.copyHeld(original, held.range.first, held.heldEnd, out)
            out.flush()
        } catch (e: IOException) {
            Log.d(TAG) { "Relay of $original ended: ${e.message}" }
            return true
        }
        val request =
            Request
                .Builder()
                .url(original)
                .header("Range", "bytes=${held.heldEnd}-${held.range.last}")
                .apply { exchange.requestHeaders.getFirst("Accept")?.let { header("Accept", it) } }
                .build()
        try {
            execute(original, request, session).use { response ->
                val rest =
                    response
                        .header("Content-Range")
                        ?.substringAfter("bytes ", "")
                        ?.substringBefore('-')
                        ?.toLongOrNull()
                if (response.code != 206 || rest != held.heldEnd) {
                    Log.d(TAG) { "Relay of $original ended after the bytes on disk: the host answered ${response.code}" }
                    return true
                }
                val cache = VideoDiskCache.writerFor(original, response)
                try {
                    response.body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            cache?.let { c -> runCatching { c.write(buf, n) } }
                            out.write(buf, 0, n)
                        }
                    }
                } finally {
                    cache?.let { c -> runCatching { c.close() } }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG) { "Relay of $original ended: ${e.message}" }
        } finally {
            release(session, request)
        }
        return true
    }

    /**
     * Sends [response]'s body to the engine. A stream of an engine other than the one the user
     * watches gives way while that one starts (see [setForeground]): its upstream request is dropped,
     * the engine's connection kept open, and the rest fetched from where it stopped once the watched
     * video moves. Merely not reading would not do: OkHttp lets each HTTP/2 stream buffer 16 MiB
     * before the host must stop sending.
     */
    private fun sendBody(
        exchange: HttpExchange,
        original: String,
        response: Response,
        keep: Boolean,
        session: String?,
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
        if (length == 0L) return
        // Where this body sits in the file, so a dropped stream can be fetched on from there.
        val contentRange = response.header("Content-Range")
        var offset =
            if (response.code == 206) contentRange?.substringAfter("bytes ", "")?.substringBefore('-')?.toLongOrNull() else 0L
        val last =
            if (response.code == 206) contentRange?.substringAfter('-', "")?.substringBefore('/')?.toLongOrNull() else length.takeIf { it > 0 }?.minus(1)
        val canYield = response.isSuccessful && offset != null && last != null
        var current = response
        var cache = if (keep && response.isSuccessful) VideoDiskCache.writerFor(original, response) else null
        val resumed = ArrayList<Request>()
        var exempt = false
        try {
            val buf = ByteArray(64 * 1024)
            while (true) {
                val input = current.body.byteStream()
                var yielded = false
                while (true) {
                    if (canYield && !exempt && mustYield(session)) {
                        yielded = true
                        break
                    }
                    val n = input.read(buf)
                    if (n < 0) break
                    // Kept first: what the engine read before hanging up is worth keeping too.
                    cache?.let { runCatching { it.write(buf, n) } }
                    exchange.responseBody.write(buf, 0, n)
                    offset = offset?.plus(n)
                }
                if (!yielded) break
                current.close()
                cache?.let { runCatching { it.close() } }
                cache = null
                val started = System.currentTimeMillis()
                exempt = !awaitTurn(session)
                Log.d(TAG) { "Held a stream of $original for ${System.currentTimeMillis() - started} ms while the watched video started" }
                VideoTimeline.mark(original) { "relay: background stream held ${System.currentTimeMillis() - started} ms at byte $offset while the watched video started" }
                if (session != null && session in revoked) throw IOException("cut off while held")
                if (offset!! > last!!) break
                val request =
                    Request
                        .Builder()
                        .url(original)
                        .header("Range", "bytes=$offset-$last")
                        .build()
                resumed += request
                current = execute(original, request, session)
                val from =
                    current
                        .header("Content-Range")
                        ?.substringAfter("bytes ", "")
                        ?.substringBefore('-')
                        ?.toLongOrNull()
                // The host won't give the rest as a range: the engine sees the stream end and asks again.
                if (current.code != 206 || from != offset) break
                if (keep) cache = VideoDiskCache.writerFor(original, current)
            }
        } finally {
            if (current !== response) current.close()
            cache?.let { runCatching { it.close() } }
            resumed.forEach { release(session, it) }
        }
    }

    // The engine the user watches, and whether its video is still starting: until it moves, every
    // other engine's stream gives way (see sendBody). Guarded by foregroundLock.
    private val foregroundLock = Object()
    private var foreground: String? = null
    private var foregroundStarting = false

    /**
     * The engine streaming as [session] plays the video the user watches; while [starting] (loading,
     * its clock not yet moving) the other engines' streams give way to it. Null when none plays.
     */
    fun setForeground(
        session: String?,
        starting: Boolean,
    ) {
        synchronized(foregroundLock) {
            if (foreground == session && foregroundStarting == (starting && session != null)) return
            foreground = session
            foregroundStarting = starting && session != null
            foregroundLock.notifyAll()
        }
    }

    private fun mustYield(session: String?): Boolean =
        synchronized(foregroundLock) {
            foregroundStarting && session != foreground && hasPicture(session)
        }

    // Sessions whose engine shows its first frame and whose start is on disk for a poster (see
    // markPictured). Until then a stream doesn't give way: holding it left the cards beside the
    // watched video blank, waiting for their first frame, and cut short the bytes their poster is
    // made from.
    private val pictured: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The engine streaming as [session] has its card's picture: from now on its streams give way. */
    fun markPictured(session: String) {
        pictured += session
    }

    /** Whether the engine streaming as [session] has its picture; Chromium's streams always give way. */
    fun hasPicture(session: String?): Boolean = session == null || session in pictured

    // Waits until the watched video moves; false when it waited the whole MAX_HOLD_MS, so a video
    // that never starts holds every other stream only once.
    private fun awaitTurn(session: String?): Boolean {
        val deadline = System.currentTimeMillis() + MAX_HOLD_MS
        synchronized(foregroundLock) {
            while (foregroundStarting && session != foreground && hasPicture(session)) {
                if (session != null && session in revoked) return true
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return false
                foregroundLock.wait(minOf(left, 500L))
            }
        }
        return true
    }

    private fun isPlaylist(
        url: HttpUrl,
        contentType: String?,
    ): Boolean {
        val type = contentType?.lowercase().orEmpty()
        // The header can carry parameters (`; charset=utf-8`) the shared mime check does not strip.
        return "mpegurl" in type || isHlsMedia(url.toString(), null)
    }

    private val uriAttribute = Regex("""URI="([^"]+)"""")

    /**
     * [playlist] with every segment, variant, key and map URI pointing at this relay, or null when
     * one of them is not http(s), which the relay cannot carry and the engine must not fetch itself.
     */
    internal fun rewritePlaylist(
        playlist: String,
        base: HttpUrl,
        host: String,
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
                            val relayed = toRelay(match.groupValues[1], base, host)
                            if (relayed == null) refused = true
                            "URI=\"${relayed.orEmpty()}\""
                        }
                    }

                    else -> {
                        toRelay(line.trim(), base, host) ?: "".also { refused = true }
                    }
                }
            }
        return if (refused) null else rewritten
    }

    // On the playlist's own origin: one stream's segments stay together, off the other videos' ports.
    private fun toRelay(
        uri: String,
        base: HttpUrl,
        host: String,
    ): String? = base.resolve(uri)?.let { relayUrlFor(it.toString(), host) }
}
