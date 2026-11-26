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
package com.vitorpamplona.amethyst.commons.service.http

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/** One SOCKS5 CONNECT a [FakeSocks5] saw: ATYP (3 is a domain name), the address, the port. */
data class SocksConnect(
    val addressType: Int,
    val host: String,
    val port: Int,
)

/**
 * How a [FakeSocks5] treats a client: [Succeed] is a working proxy; the others are the ways a proxy
 * can fail, which a client must never answer by going direct (the B12f leak tests).
 */
sealed interface SocksBehavior {
    /** Greeting, CONNECT success, then the tunnel. */
    data object Succeed : SocksBehavior

    /** Accepts the TCP connection and closes it before answering anything. */
    data object HangUp : SocksBehavior

    /** Answers the greeting with "no acceptable methods" (0xFF) and closes. */
    data object RefuseMethods : SocksBehavior

    /** Accepts the greeting, records the CONNECT, then answers it with [reply] (RFC 1928 §6) and closes. */
    data class FailConnect(
        val reply: Int,
    ) : SocksBehavior

    /** Accepts the TCP connection and never says a word. */
    data object Silent : SocksBehavior
}

/**
 * A SOCKS5 proxy (RFC 1928) that records every CONNECT and answers success. Then, on the tunnel,
 * it either plays a tiny HTTP server (every request gets `200 ok` and the connection closes) or,
 * given [upstreamPort], pipes the tunnel to a server there (on 127.0.0.1 unless told), whatever host the CONNECT
 * named: so `.invalid` hosts, which nothing but the proxy can reach, still get real answers.
 */
class FakeSocks5 private constructor(
    private val selector: SelectorManager,
    private val server: ServerSocket,
    private val upstreamPort: Int?,
    private val upstreamHost: String,
    private val behavior: SocksBehavior,
    scope: CoroutineScope,
) {
    val port = (server.localAddress as InetSocketAddress).port
    private val seen = MutableStateFlow<List<SocksConnect>>(emptyList())
    private val accepted = MutableStateFlow(0)

    /** TCP connections the proxy accepted, whatever it then did with them. */
    fun acceptedCount(): Int = accepted.value

    private val acceptor =
        scope.launch {
            while (true) {
                val socket = server.accept()
                accepted.update { it + 1 }
                launch { serve(socket) }
            }
        }

    fun connects(): List<SocksConnect> = seen.value

    suspend fun awaitConnect(host: String): SocksConnect =
        withTimeout(AWAIT_MS) {
            var found: SocksConnect? = null
            while (found == null) {
                found = seen.value.firstOrNull { it.host == host }
                if (found == null) delay(20)
            }
            found
        }

    private suspend fun serve(socket: Socket) {
        try {
            val input = socket.openReadChannel()
            val output = socket.openWriteChannel(autoFlush = true)
            when (behavior) {
                SocksBehavior.HangUp -> return
                SocksBehavior.Silent -> awaitCancellation()
                else -> {}
            }
            // Greeting: VER, NMETHODS, METHODS. Answer "no authentication".
            check(input.readByte().toInt() == 5) { "not SOCKS5" }
            input.skip(input.readByte().toInt() and 0xFF)
            if (behavior == SocksBehavior.RefuseMethods) {
                output.writeFully(byteArrayOf(5, 0xFF.toByte()))
                return
            }
            output.writeFully(byteArrayOf(5, 0))
            // Request: VER, CMD, RSV, ATYP, address, port.
            check(input.readByte().toInt() == 5) { "not SOCKS5" }
            val command = input.readByte().toInt()
            input.readByte()
            val addressType = input.readByte().toInt() and 0xFF
            val host =
                when (addressType) {
                    DOMAIN_NAME -> input.bytes(input.readByte().toInt() and 0xFF).toCharArray().concatToString()
                    IPV4 -> input.bytes(4).joinToString(".") { (it.toInt() and 0xFF).toString() }
                    IPV6 -> input.bytes(16).joinToString(":") { (it.toInt() and 0xFF).toString(16) }
                    else -> error("ATYP $addressType")
                }
            val portBytes = input.bytes(2)
            val port = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)
            check(command == 1) { "not a CONNECT: $command" }
            seen.update { it + SocksConnect(addressType, host, port) }
            if (behavior is SocksBehavior.FailConnect) {
                output.writeFully(byteArrayOf(5, behavior.reply.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
                return
            }
            output.writeFully(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))

            if (upstreamPort != null) tunnel(input, output, upstreamHost, upstreamPort) else answerOk(input, output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A client that hung up mid-exchange: what it sent is already recorded.
        } finally {
            socket.close()
        }
    }

    /** The tunnel: read one HTTP request and answer it. */
    private suspend fun answerOk(
        input: ByteReadChannel,
        output: ByteWriteChannel,
    ) {
        var contentLength = 0
        while (true) {
            val line = input.readUTF8Line() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        if (contentLength > 0) input.skip(contentLength)
        output.writeFully("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".encodeToByteArray())
        output.flushAndClose()
    }

    /** The tunnel: bytes both ways between the client and [host]:[port], until the server is done. */
    private suspend fun tunnel(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        host: String,
        port: Int,
    ) {
        val upstream = aSocket(selector).tcp().connect(host, port)
        try {
            val upstreamInput = upstream.openReadChannel()
            val upstreamOutput = upstream.openWriteChannel(autoFlush = true)
            CoroutineScope(Dispatchers.IO)
                .launch {
                    runCatching { input.copyTo(upstreamOutput) }
                    runCatching { upstreamOutput.flushAndClose() }
                }.let { toServer ->
                    try {
                        upstreamInput.copyTo(output)
                        output.flushAndClose()
                    } finally {
                        toServer.cancel()
                    }
                }
        } finally {
            upstream.close()
        }
    }

    private suspend fun ByteReadChannel.bytes(count: Int): ByteArray = ByteArray(count).also { readFully(it) }

    private suspend fun ByteReadChannel.skip(count: Int) {
        if (count > 0) bytes(count)
    }

    private fun ByteArray.toCharArray() = CharArray(size) { (this[it].toInt() and 0xFF).toChar() }

    fun close() {
        acceptor.cancel()
        server.close()
        selector.close()
    }

    companion object {
        const val DOMAIN_NAME = 3
        const val IPV4 = 1
        const val IPV6 = 4
        private const val AWAIT_MS = 15_000L

        suspend fun start(
            scope: CoroutineScope,
            upstreamPort: Int? = null,
            behavior: SocksBehavior = SocksBehavior.Succeed,
            port: Int = 0,
            upstreamHost: String = "127.0.0.1",
        ): FakeSocks5 {
            val selector = SelectorManager(Dispatchers.IO)
            val server = aSocket(selector).tcp().bind("127.0.0.1", port) { reuseAddress = true }
            return FakeSocks5(selector, server, upstreamPort, upstreamHost, behavior, scope)
        }
    }
}
