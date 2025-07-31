/**
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (url: NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
) : WebSocket {
    private val listener = OkHttpWebsocketListener()
    private var usingOkHttp: OkHttpClient? = null
    private var socket: okhttp3.WebSocket? = null

    fun buildRequest() = Request.Builder().url(url.url).build()

    override fun needsReconnect(): Boolean {
        val myUsingOkHttp = usingOkHttp
        if (myUsingOkHttp == null) return true

        val currentOkHttp = httpClient(url)

        val usingProxy = myUsingOkHttp.proxy
        val currentProxy = currentOkHttp.proxy

        if (usingProxy != null && currentProxy != null && usingProxy != currentProxy) return true
        if (usingProxy == null && currentProxy != null) return true
        if (usingProxy != null && currentProxy == null) return true

        if (currentOkHttp.readTimeoutMillis != myUsingOkHttp.readTimeoutMillis) return true
        if (currentOkHttp.writeTimeoutMillis != myUsingOkHttp.writeTimeoutMillis) return true
        if (currentOkHttp.connectTimeoutMillis != myUsingOkHttp.connectTimeoutMillis) return true
        if (currentOkHttp.callTimeoutMillis != myUsingOkHttp.callTimeoutMillis) return true

        return false
    }

    override fun connect() {
        usingOkHttp = httpClient(url)
        socket = usingOkHttp?.newWebSocket(buildRequest(), listener)
    }

    inner class OkHttpWebsocketListener : okhttp3.WebSocketListener() {
        override fun onOpen(
            webSocket: okhttp3.WebSocket,
            response: Response,
        ) = out.onOpen(
            response.receivedResponseAtMillis - response.sentRequestAtMillis,
            response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
        )

        override fun onMessage(
            webSocket: okhttp3.WebSocket,
            text: String,
        ) = out.onMessage(text)

        override fun onClosing(
            webSocket: okhttp3.WebSocket,
            code: Int,
            reason: String,
        ) = out.onClosing(code, reason)

        override fun onClosed(
            webSocket: okhttp3.WebSocket,
            code: Int,
            reason: String,
        ) = out.onClosed(code, reason)

        override fun onFailure(
            webSocket: okhttp3.WebSocket,
            t: Throwable,
            response: Response?,
        ) = out.onFailure(t, response?.code, response?.message)
    }

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = OkHttpWebSocket(url, httpClient, out)
    }

    override fun disconnect() {
        // uses cancel to kill the SEND stack that might be waiting
        socket?.cancel()
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false
}
