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

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * An HTTP/1.1 origin for HLS tests on 127.0.0.1, serving by the `Host` header and path, so one
 * server stands for several CDN hosts behind a SOCKS tunnel. Records every request, answers
 * `Range` with 206, closes each connection after its response.
 */
class FakeHlsServer private constructor(
    /** The address it listens on. */
    val host: String,
    private val selector: SelectorManager,
    private val server: ServerSocket,
    private val routes: (Hit) -> Answer?,
    scope: CoroutineScope,
) {
    data class Hit(
        val host: String,
        val path: String,
        val range: String?,
    )

    class Answer(
        val body: ByteArray,
        val contentType: String = "application/octet-stream",
        val status: Int = 200,
        val headers: List<Pair<String, String>> = emptyList(),
    ) {
        constructor(text: String, contentType: String) : this(text.encodeToByteArray(), contentType)
    }

    val port = (server.localAddress as InetSocketAddress).port
    private val seen = MutableStateFlow<List<Hit>>(emptyList())

    fun hits(): List<Hit> = seen.value

    private val acceptor =
        scope.launch {
            while (true) {
                val socket = server.accept()
                launch { serve(socket) }
            }
        }

    private suspend fun serve(socket: Socket) {
        try {
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = false)
            val requestLine = input.readUTF8Line() ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val line = input.readUTF8Line() ?: return
                if (line.isEmpty()) break
                headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
            }
            val hit = Hit(host = headers["host"].orEmpty().substringBefore(':'), path = requestLine.split(' ')[1], range = headers["range"])
            seen.update { it + hit }
            val answer = routes(hit) ?: Answer(ByteArray(0), status = 404)
            val range = hit.range?.takeIf { answer.status == 200 }?.let { parseRange(it, answer.body.size) }
            val status = if (range != null) 206 else answer.status
            val body = if (range != null) answer.body.copyOfRange(range.first, range.last + 1) else answer.body
            val head =
                buildString {
                    append("HTTP/1.1 $status X\r\n")
                    append("Content-Type: ${answer.contentType}\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    if (answer.status == 200) append("Accept-Ranges: bytes\r\n")
                    if (range != null) append("Content-Range: bytes ${range.first}-${range.last}/${answer.body.size}\r\n")
                    answer.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                    append("Connection: close\r\n\r\n")
                }
            output.writeFully(head.encodeToByteArray())
            output.writeFully(body)
            output.flushAndClose()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The client hung up.
        } finally {
            socket.close()
        }
    }

    private fun parseRange(
        header: String,
        size: Int,
    ): IntRange? {
        val spec = header.removePrefix("bytes=").split('-')
        val start = spec.getOrNull(0)?.toIntOrNull() ?: return null
        val end = spec.getOrNull(1)?.toIntOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
        return if (start <= end) start..end else null
    }

    fun close() {
        acceptor.cancel()
        server.close()
        selector.close()
    }

    companion object {
        suspend fun start(
            scope: CoroutineScope,
            host: String = "127.0.0.1",
            routes: (Hit) -> Answer?,
        ): FakeHlsServer {
            val selector = SelectorManager(Dispatchers.IO)
            val server = aSocket(selector).tcp().bind(host, 0)
            return FakeHlsServer(host, selector, server, routes, scope)
        }
    }
}
