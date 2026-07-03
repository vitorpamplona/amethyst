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
package com.vitorpamplona.relaybench

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * The thinnest possible NIP-01 wire client: one WebSocket, every inbound
 * text frame pushed into an unbounded [Channel]. Each benchmark phase owns
 * its sockets outright and parses frames itself, so no subscription router
 * sits between the relay and the measurement — what we time is the wire,
 * not this harness.
 */
class NostrSocket private constructor(
    private val ws: WebSocket,
    val incoming: Channel<String>,
) {
    fun send(text: String): Boolean = ws.send(text)

    fun publish(eventJson: String): Boolean = send("""["EVENT",$eventJson]""")

    fun req(
        subId: String,
        filterJson: String,
    ): Boolean = send("""["REQ","$subId",$filterJson]""")

    fun close(subId: String): Boolean = send("""["CLOSE","$subId"]""")

    fun disconnect() {
        ws.close(1000, "bench-done")
    }

    companion object {
        /**
         * Sockets with Nagle disabled. The JDK default (Nagle on) delays a
         * small write that follows another unacked small write — e.g. a REQ
         * sent right after the previous round's CLOSE — by a full delayed-ACK
         * interval (~40 ms), which would drown every latency this harness
         * measures. Verified: with the default factory every REQ→EOSE round
         * floors at ~44 ms against both geode and strfry; with TCP_NODELAY
         * the same rounds take ~0.3 ms.
         */
        val NO_DELAY_SOCKETS: SocketFactory =
            object : SocketFactory() {
                private fun socket() = Socket().apply { tcpNoDelay = true }

                override fun createSocket(): Socket = socket()

                override fun createSocket(
                    host: String?,
                    port: Int,
                ): Socket = socket().apply { connect(InetSocketAddress(host, port)) }

                override fun createSocket(
                    host: String?,
                    port: Int,
                    localHost: InetAddress?,
                    localPort: Int,
                ): Socket = throw UnsupportedOperationException()

                override fun createSocket(
                    host: InetAddress?,
                    port: Int,
                ): Socket = socket().apply { connect(InetSocketAddress(host, port)) }

                override fun createSocket(
                    address: InetAddress?,
                    port: Int,
                    localAddress: InetAddress?,
                    localPort: Int,
                ): Socket = throw UnsupportedOperationException()
            }

        suspend fun connect(
            http: OkHttpClient,
            wsUrl: String,
        ): NostrSocket {
            val incoming = Channel<String>(Channel.UNLIMITED)
            val opened = CompletableDeferred<Unit>()
            val httpUrl = wsUrl.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://")
            val ws =
                http.newWebSocket(
                    Request.Builder().url(httpUrl).build(),
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            opened.complete(Unit)
                        }

                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            incoming.trySend(text)
                        }

                        override fun onClosed(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            incoming.close()
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            opened.completeExceptionally(t)
                            incoming.close(t)
                        }
                    },
                )
            withTimeout(10_000) { opened.await() }
            return NostrSocket(ws, incoming)
        }
    }
}
