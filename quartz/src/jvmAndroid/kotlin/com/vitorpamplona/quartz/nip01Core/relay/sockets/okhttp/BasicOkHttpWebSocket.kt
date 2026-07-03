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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket as OkHttpWebSocket
import okhttp3.WebSocketListener as OkHttpWebSocketListener

class BasicOkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
    val receiveBufferFrames: Int = RECEIVE_BUFFER_FRAMES,
) : WebSocket {
    companion object {
        // Exists to avoid exceptions stopping the coroutine
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e("BasicOkHttpWebSocket", "WebsocketListener Caught exception: ${throwable.message}", throwable)
            }

        /**
         * Cap on frames buffered between OkHttp's reader thread and the
         * consumer coroutine. Bounded ON PURPOSE: with an unlimited channel a
         * consumer slower than the socket accumulates heap without limit (a
         * multi-million-event bulk download can queue gigabytes of frame
         * Strings). When full, [trySendBlocking] blocks the OkHttp reader
         * thread, which stops reading the TCP socket — flow control then
         * pushes back on the relay instead of on our heap.
         *
         * Trade-off: a blocked reader thread also delays OkHttp's PING/PONG
         * handling, so the bound is generous (4096 frames ≈ 4–40 MB of text
         * at typical event sizes) — deep enough that only a pathologically
         * slow consumer ever hits it, and short stalls stay well under relay
         * ping timeouts.
         */
        const val RECEIVE_BUFFER_FRAMES = 4096
    }

    private var socket: OkHttpWebSocket? = null

    override fun needsReconnect() = socket == null

    override fun connect() {
        val request = Request.Builder().url(url.url).build()

        val listener =
            object : OkHttpWebSocketListener() {
                val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
                val incomingMessages: Channel<String> = Channel(receiveBufferFrames)
                val job = // Launch a coroutine to process messages from the channel.
                    scope.launch {
                        for (message in incomingMessages) {
                            out.onMessage(message)
                        }
                    }

                override fun onOpen(
                    webSocket: OkHttpWebSocket,
                    response: Response,
                ) = out.onOpen(
                    (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt(),
                    response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
                )

                override fun onMessage(
                    webSocket: OkHttpWebSocket,
                    text: String,
                ) {
                    // Blocks the OkHttp reader thread when the buffer is full —
                    // that's the backpressure mechanism, see RECEIVE_BUFFER_FRAMES.
                    incomingMessages.trySendBlocking(text)
                }

                override fun onClosed(
                    webSocket: OkHttpWebSocket,
                    code: Int,
                    reason: String,
                ) {
                    // Close the channel when the WebSocket connection is closed.
                    incomingMessages.close()
                    job.cancel()
                    scope.cancel()

                    out.onClosed(code, reason)
                }

                override fun onFailure(
                    webSocket: OkHttpWebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    // Close the channel on failure, and propagate the error.
                    incomingMessages.close()
                    job.cancel()
                    scope.cancel()

                    out.onFailure(t, response?.code, response?.message)
                }
            }

        socket = httpClient(url).newWebSocket(request, listener)
    }

    override fun disconnect() {
        socket?.cancel()
        socket = null
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = BasicOkHttpWebSocket(url, httpClient, out)
    }
}
