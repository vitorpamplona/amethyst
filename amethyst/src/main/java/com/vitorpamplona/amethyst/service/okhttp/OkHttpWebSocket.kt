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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket.Companion.exceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.Proxy

/**
 * Value-comparable fingerprint of the relevant parts of an [OkHttpClient] for a relay
 * connection: the proxy (Tor SOCKS or none) and the timeouts. Two clients with the same
 * fingerprint connect the same way, so a relay does not need to reconnect — and a forced
 * reconnect should not skip the backoff — when the fingerprint is unchanged.
 */
private data class RelayTransportConfig(
    val proxy: Proxy?,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
    val writeTimeoutMillis: Int,
    val callTimeoutMillis: Int,
)

private fun OkHttpClient.relayTransportConfig() =
    RelayTransportConfig(
        proxy = proxy,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis,
        writeTimeoutMillis = writeTimeoutMillis,
        callTimeoutMillis = callTimeoutMillis,
    )

class OkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (url: NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
) : WebSocket {
    private var usingOkHttp: OkHttpClient? = null
    private var socket: okhttp3.WebSocket? = null

    fun buildRequest() = Request.Builder().url(url.url).build()

    override fun needsReconnect(): Boolean {
        if (socket == null) return true

        val myUsingOkHttp = usingOkHttp ?: return true

        return myUsingOkHttp.relayTransportConfig() != httpClient(url).relayTransportConfig()
    }

    override fun connect() {
        usingOkHttp = httpClient(url)
        socket = usingOkHttp?.newWebSocket(buildRequest(), OkHttpWebsocketListener(out))
    }

    inner class OkHttpWebsocketListener(
        val out: WebSocketListener,
    ) : okhttp3.WebSocketListener() {
        val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
        val incomingMessages: Channel<String> = Channel(Channel.UNLIMITED)
        val job = // Launch a coroutine to process messages from the channel.
            scope.launch {
                for (message in incomingMessages) {
                    out.onMessage(message)
                }
            }

        override fun onOpen(
            webSocket: okhttp3.WebSocket,
            response: Response,
        ) = out.onOpen(
            (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt(),
            response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
        )

        override fun onMessage(
            webSocket: okhttp3.WebSocket,
            text: String,
        ) {
            // Asynchronously send the received message to the channel.
            // `trySendBlocking` is used here for simplicity within the callback,
            // but it's important to understand potential thread blocking if the buffer is full.
            incomingMessages.trySendBlocking(text)
        }

        override fun onClosed(
            webSocket: okhttp3.WebSocket,
            code: Int,
            reason: String,
        ) {
            // Close the channel on failure, and propagate the error.
            incomingMessages.close()
            job.cancel()
            scope.cancel()

            socket = null
            out.onClosed(code, reason)
        }

        override fun onFailure(
            webSocket: okhttp3.WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            // Close the channel on failure, and propagate the error.
            incomingMessages.close()
            job.cancel()
            scope.cancel()

            socket = null
            out.onFailure(t, response?.code, response?.message)
        }
    }

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = OkHttpWebSocket(url, httpClient, out)

        // Proxy + timeout fingerprint of the client this url would use right now.
        // BasicRelayClient compares it against the last attempt to decide whether a
        // forced reconnect may skip the exponential backoff.
        override fun connectionConfig(url: NormalizedRelayUrl): Any = httpClient(url).relayTransportConfig()
    }

    override fun disconnect() {
        // uses cancel to kill the SEND stack that might be waiting
        socket?.cancel()
        socket = null
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false
}
