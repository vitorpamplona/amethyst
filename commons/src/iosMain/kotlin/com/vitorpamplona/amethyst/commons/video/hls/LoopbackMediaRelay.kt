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
package com.vitorpamplona.amethyst.commons.video.hls

import com.vitorpamplona.amethyst.commons.service.http.TorUnavailableException
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A loopback HTTP relay that lets AVFoundation play HLS (and anything else it fetches itself)
 * through the app's Ktor clients, so a stream the Tor settings route through Tor goes through Tor.
 * AVFoundation takes no proxy, and the resource loader that feeds the disk cache is reported not to
 * deliver HLS media segments, so the asset is opened on `http://127.0.0.1:<port>/…`
 * ([LoopbackRelayPath]) instead, and this relay fetches the original through [client]:
 *
 * - every playlist it relays is rewritten ([HlsPlaylistRewriter]) so each variant, segment, key,
 *   map and rendition it names, relative or absolute, is a relay address of the same stream too;
 *   a playlist naming anything but http(s) is refused rather than half-relayed;
 * - `Range` and `If-Range` go upstream, `Content-Range`/`Accept-Ranges` and the status come back,
 *   so byte-range segments (`#EXT-X-BYTERANGE`) and seeking work;
 * - nothing is cached, so a live playlist the player reloads is fetched again each time;
 * - an upstream failure is an error status to the player, never a redirect to the original, and is
 *   kept per stream ([failureOf]) for the player to explain.
 *
 * Desktop's `MediaRelay` does the same for its engines over OkHttp. This one is the iOS face, on
 * `ktor-network` sockets. It listens on 127.0.0.1 only, behind a per-process random path prefix and
 * a `Host` check (a web page that rebinds a DNS name to 127.0.0.1 sends its own name). Each
 * response closes its connection, so a body needs no framing beyond its end.
 *
 * The listener is opened on first use and opened again (on a new port) if it died, as it may when
 * iOS suspends the app; a stream opened before that fails and the player's retry gets a new one.
 */
class LoopbackMediaRelay(
    /** The client for a request of the stream: Tor for a stream that must go through Tor. */
    private val client: (url: String) -> HttpClient,
) {
    /** A relayed playback: what the player opens, and the stream to ask [failureOf] about. */
    data class Stream(
        val url: String,
        val stream: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val secret = RandomInstance.bytes(16).toHexKey()

    private val startLock = Mutex()
    private var listener: Listener? = null

    // The last failure of each stream, the oldest dropped first. Guarded by [lock].
    private val lock = KmpLock()
    private val failures = LinkedHashMap<String, RelayFailure>()

    private class Listener(
        val selector: SelectorManager,
        val server: ServerSocket,
        val port: Int,
        val acceptor: Job,
    ) {
        fun close() {
            acceptor.cancel()
            server.close()
            selector.close()
        }
    }

    /**
     * The relay address [original] plays from, in a new stream, or null when [original] is not
     * http(s) or the relay cannot listen.
     */
    suspend fun open(original: String): Stream? {
        if (!original.startsWith("http://", ignoreCase = true) && !original.startsWith("https://", ignoreCase = true)) return null
        val port = port() ?: return null
        val stream = RandomInstance.bytes(8).toHexKey()
        return Stream(addressOf(port, stream, original), stream)
    }

    /** What last went wrong in [stream], or null. */
    fun failureOf(stream: String): RelayFailure? = lock.withLock { failures[stream] }

    /** The port the relay listens on, opening it first if needed; null when it cannot. */
    suspend fun port(): Int? =
        startLock.withLock {
            listener?.let { if (it.acceptor.isActive) return@withLock it.port else it.close() }
            listener = null
            try {
                listen().also { listener = it }.port
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not open the loopback media relay", e)
                null
            }
        }

    fun close() {
        listener?.close()
        listener = null
        scope.cancel()
    }

    private suspend fun listen(): Listener {
        val selector = SelectorManager(Dispatchers.IO)
        val server =
            try {
                aSocket(selector).tcp().bind(LOOPBACK, 0)
            } catch (e: Exception) {
                selector.close()
                throw e
            }
        val port = (server.localAddress as InetSocketAddress).port
        val acceptor =
            scope.launch {
                try {
                    while (isActive) {
                        val socket = server.accept()
                        launch { serve(socket, port) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The next open() listens again.
                    Log.w(TAG, "The loopback media relay stopped listening", e)
                }
            }
        Log.d(TAG) { "Loopback media relay on $LOOPBACK:$port" }
        return Listener(selector, server, port, acceptor)
    }

    private fun addressOf(
        port: Int,
        stream: String,
        original: String,
    ) = "http://$LOOPBACK:$port${LoopbackRelayPath.encode(secret, stream, original)}"

    private fun forget(stream: String) {
        lock.withLock { failures.remove(stream) }
    }

    private fun record(
        stream: String,
        failure: RelayFailure,
    ) {
        Log.w(TAG) { "Relay of ${failure.url} failed: $failure" }
        lock.withLock {
            failures.remove(stream)
            failures[stream] = failure
            if (failures.size > MAX_REMEMBERED) failures.keys.firstOrNull()?.let { failures.remove(it) }
        }
    }

    private suspend fun serve(
        socket: Socket,
        port: Int,
    ) {
        val output = socket.openWriteChannel(autoFlush = false)
        try {
            val input = socket.openReadChannel()
            val head = withTimeoutOrNull(HEAD_TIMEOUT_MS) { readHead(input) }
            when {
                head == null -> {
                    respond(output, 400)
                }

                // The port it came in on: a page that rebinds a DNS name to 127.0.0.1 sends its own name.
                head.header("Host") != "$LOOPBACK:$port" -> {
                    respond(output, 403)
                }

                head.method != "GET" && head.method != "HEAD" -> {
                    respond(output, 405)
                }

                else -> {
                    val target = LoopbackRelayPath.decode(secret, head.target)
                    if (target == null) respond(output, 404) else relay(head, target, output, port)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The player hung up mid-body (a seek, a stop): nothing to answer.
            Log.d(TAG) { "Relay connection ended: ${e.message}" }
        } finally {
            sentHeads.remove(output)
            runCatching { output.flushAndClose() }
            socket.close()
        }
    }

    private suspend fun readHead(input: ByteReadChannel): HttpRequestHead? {
        val lines = ArrayList<String>()
        while (lines.size <= MAX_HEADER_LINES) {
            val line = input.readUTF8Line(MAX_LINE) ?: return null
            if (line.isEmpty()) return HttpRequestHead.parse(lines)
            lines += line
        }
        return null
    }

    private suspend fun relay(
        head: HttpRequestHead,
        target: LoopbackRelayPath.Target,
        output: ByteWriteChannel,
        port: Int,
    ) {
        val original = target.original
        val isHead = head.method == "HEAD"
        // A playlist is always sent whole (it is rewritten), so its range is not asked for.
        val range = head.header("Range")?.takeUnless { HlsPlaylistRewriter.looksLikePlaylist(original, null) }
        try {
            client(original)
                .prepareRequest {
                    url(original)
                    method = if (isHead) HttpMethod.Head else HttpMethod.Get
                    if (range != null) {
                        header(HttpHeaders.Range, range)
                        head.header("If-Range")?.let { header(HttpHeaders.IfRange, it) }
                        // A compressed body's offsets are not the file's.
                        header(HttpHeaders.AcceptEncoding, "identity")
                    }
                }.execute { response -> answer(response, target, range, isHead, output, port) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlayerHungUp) {
            // A seek, a rendition switch, a stop: nothing failed upstream.
            Log.d(TAG) { "Player hung up on $original" }
        } catch (e: TorUnavailableException) {
            record(target.stream, RelayFailure.TorUnavailable(original))
            respond(output, 502)
        } catch (e: Exception) {
            record(target.stream, RelayFailure.Network(original, e.message ?: e::class.simpleName ?: "error"))
            // Once the head is out, the player can only tell from the body ending short.
            if (!output.headSent) respond(output, 502)
        }
    }

    private suspend fun answer(
        response: HttpResponse,
        target: LoopbackRelayPath.Target,
        range: String?,
        isHead: Boolean,
        output: ByteWriteChannel,
        port: Int,
    ) {
        val status = response.status.value
        // Where the bytes really came from, after redirects: what relative URIs resolve against.
        val source =
            response.call.request.url
                .toString()
        val ok = status in 200..299
        // An answer clears an older failure of the stream, so a later decoder error is not blamed on it.
        if (ok) forget(target.stream) else record(target.stream, RelayFailure.Http(target.original, status))
        val declared = ok && HlsPlaylistRewriter.looksLikePlaylist(source, response.headers[HttpHeaders.ContentType])
        if (isHead) {
            writeHead(output, status, forwardedHeaders(response, withLength = !declared && response.isIdentityEncoded()))
            return
        }

        val body = response.bodyAsChannel()
        // An unlabelled playlist (`text/plain` from `/live?id=…`) would reach the player with its
        // absolute URIs untouched, and the player would fetch those itself: so the start of every
        // body a playlist could start is looked at.
        val sniffed = if (ok && !declared && startsAtZero(range)) body.readUpTo(SNIFF_BYTES) else EMPTY
        if (declared || HlsPlaylistRewriter.startsPlaylist(sniffed)) {
            sendPlaylist(sniffed, body, source, target, output, port)
            return
        }

        writeHead(output, status, forwardedHeaders(response, withLength = response.isIdentityEncoded()))
        if (sniffed.isNotEmpty()) toPlayer { output.writeFully(sniffed) }
        pump(body, output)
    }

    /**
     * Copies [body] to the player. A failed read is upstream's (it propagates as itself); a failed
     * write is the player hanging up ([PlayerHungUp]), which is no failure of the stream.
     */
    private suspend fun pump(
        body: ByteReadChannel,
        output: ByteWriteChannel,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = body.readAvailable(buffer, 0, buffer.size)
            if (read < 0) return
            if (read > 0) {
                toPlayer {
                    output.writeFully(buffer, 0, read)
                    output.flush()
                }
            }
        }
    }

    private suspend fun toPlayer(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw PlayerHungUp(e)
        }
    }

    private class PlayerHungUp(
        cause: Throwable,
    ) : Exception("the player closed the connection", cause)

    private suspend fun sendPlaylist(
        start: ByteArray,
        body: ByteReadChannel,
        source: String,
        target: LoopbackRelayPath.Target,
        output: ByteWriteChannel,
        port: Int,
    ) {
        val bytes = readWhole(start, body)
        if (bytes == null) {
            record(target.stream, RelayFailure.Network(target.original, "playlist over ${HlsPlaylistRewriter.MAX_PLAYLIST_BYTES} bytes"))
            return respond(output, 502)
        }
        val rewritten = HlsPlaylistRewriter.rewrite(bytes.decodeToString(), source) { addressOf(port, target.stream, it) }
        if (rewritten == null) {
            record(target.stream, RelayFailure.Unroutable(target.original))
            return respond(output, 502)
        }
        val encoded = rewritten.encodeToByteArray()
        writeHead(
            output,
            200,
            listOf(
                HttpHeaders.ContentType to HlsPlaylistRewriter.PLAYLIST_CONTENT_TYPE,
                HttpHeaders.ContentLength to encoded.size.toString(),
                // A live playlist changes: never let anything between keep an old one.
                HttpHeaders.CacheControl to "no-store",
            ),
        )
        toPlayer { output.writeFully(encoded) }
    }

    /** [start] and the rest of [body], or null past [HlsPlaylistRewriter.MAX_PLAYLIST_BYTES]. */
    private suspend fun readWhole(
        start: ByteArray,
        body: ByteReadChannel,
    ): ByteArray? {
        var bytes = start
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = body.readAvailable(buffer, 0, buffer.size)
            if (read < 0) return bytes
            if (bytes.size + read > HlsPlaylistRewriter.MAX_PLAYLIST_BYTES) return null
            bytes += buffer.copyOf(read)
        }
    }

    private suspend fun ByteReadChannel.readUpTo(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = readAvailable(buffer, filled, count - filled)
            if (read < 0) break
            filled += read
        }
        return if (filled == count) buffer else buffer.copyOf(filled)
    }

    private fun startsAtZero(range: String?): Boolean = range == null || range.replace(" ", "").startsWith("bytes=0-")

    private fun HttpResponse.isIdentityEncoded(): Boolean {
        // NSURLSession decodes gzip itself: the header's length is then the compressed one.
        val encoding = headers[HttpHeaders.ContentEncoding]?.trim()?.lowercase()
        return encoding.isNullOrEmpty() || encoding == "identity"
    }

    private fun forwardedHeaders(
        response: HttpResponse,
        withLength: Boolean,
    ): List<Pair<String, String>> =
        buildList {
            FORWARDED_RESPONSE_HEADERS.forEach { name -> response.headers[name]?.let { add(name to it) } }
            if (withLength) response.headers[HttpHeaders.ContentLength]?.let { add(HttpHeaders.ContentLength to it) }
        }

    private suspend fun respond(
        output: ByteWriteChannel,
        status: Int,
    ) = writeHead(output, status, listOf(HttpHeaders.ContentLength to "0"))

    private suspend fun writeHead(
        output: ByteWriteChannel,
        status: Int,
        headers: List<Pair<String, String>>,
    ) {
        sentHeads.add(output)
        val text =
            buildString {
                append("HTTP/1.1 ")
                    .append(status)
                    .append(' ')
                    .append(HttpStatusCode.fromValue(status).description)
                    .append("\r\n")
                headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
                append("Connection: close\r\n\r\n")
            }
        toPlayer { output.writeFully(text.encodeToByteArray()) }
    }

    // The connections whose response head is out. Only ever touched by their own serve().
    private val sentHeads = ConcurrentSet<ByteWriteChannel>()

    private val ByteWriteChannel.headSent: Boolean get() = this in sentHeads

    companion object {
        private const val TAG = "LoopbackMediaRelay"

        const val LOOPBACK = "127.0.0.1"

        private const val MAX_REMEMBERED = 200
        private const val MAX_HEADER_LINES = 64
        private const val MAX_LINE = 8 * 1024
        private const val HEAD_TIMEOUT_MS = 10_000L
        private const val SNIFF_BYTES = 16
        private const val BUFFER_BYTES = 16 * 1024
        private val EMPTY = ByteArray(0)

        // What the player needs to seek and to pick a demuxer, and the server's caching rules.
        private val FORWARDED_RESPONSE_HEADERS =
            listOf(
                HttpHeaders.ContentType,
                HttpHeaders.ContentRange,
                HttpHeaders.AcceptRanges,
                HttpHeaders.LastModified,
                HttpHeaders.ETag,
                HttpHeaders.CacheControl,
                HttpHeaders.Expires,
            )
    }
}
