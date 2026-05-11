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
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (url: NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
    /**
     * Optional URL rewriter invoked once per [connect] before the OkHttp
     * `Request` is built. Returning a different URL routes the WebSocket
     * handshake to that endpoint while keeping [url] as the canonical
     * relay identifier (used by relay tags, UI, etc.).
     *
     * Defaults to identity (no rewrite).
     */
    val urlRewriter: (NormalizedRelayUrl) -> String = { it.url },
    /**
     * Optional client decorator invoked after the URL rewriter runs but
     * before the WebSocket handshake. Lets a caller swap in a
     * `sslSocketFactory` (etc.) for a specific connection without changing
     * the shared [httpClient] factory.
     *
     * The default is a no-op so non-decorated callers keep behaving
     * identically to the pre-decorator path. The expected production user
     * is `.bit` relay TLSA pinning.
     */
    val clientDecorator: (NormalizedRelayUrl, OkHttpClient) -> OkHttpClient = { _, c -> c },
) : WebSocket {
    private var usingOkHttp: OkHttpClient? = null
    private var socket: okhttp3.WebSocket? = null
    private var connectUrl: String = url.url

    fun buildRequest() = Request.Builder().url(connectUrl).build()

    override fun needsReconnect(): Boolean {
        if (socket == null) return true

        val myUsingOkHttp = usingOkHttp ?: return true

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
        // Order matters here: the URL rewriter runs FIRST so any per-URL
        // resolution side-effect (e.g. populating the BitRelayResolver TLSA
        // cache) has happened before the client decorator is asked to look
        // up its pinning data.
        connectUrl =
            try {
                val rewritten = urlRewriter(url)
                if (rewritten != url.url) {
                    Log.d("OkHttpWebSocket") { "Rewriting connect URL ${url.url} -> $rewritten" }
                }
                rewritten
            } catch (t: Throwable) {
                Log.w("OkHttpWebSocket") { "URL rewriter failed for ${url.url}: ${t.message}" }
                url.url
            }

        val baseClient = httpClient(url)
        usingOkHttp =
            try {
                clientDecorator(url, baseClient)
            } catch (t: Throwable) {
                Log.w("OkHttpWebSocket") { "Client decorator failed for ${url.url}: ${t.message}" }
                baseClient
            }
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
        /** Optional URL rewriter (e.g. for `.bit` relay resolution). */
        val urlRewriter: (NormalizedRelayUrl) -> String = { it.url },
        /**
         * Optional client decorator (e.g. for `.bit` TLSA pinning). Composed
         * AFTER [httpClient] and AFTER [urlRewriter] so per-URL state
         * populated by the rewriter (such as the resolver TLSA cache) is
         * visible by the time we run.
         */
        val clientDecorator: (NormalizedRelayUrl, OkHttpClient) -> OkHttpClient = { _, c -> c },
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = OkHttpWebSocket(url, httpClient, out, urlRewriter, clientDecorator)
    }

    override fun disconnect() {
        // uses cancel to kill the SEND stack that might be waiting
        socket?.cancel()
        socket = null
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false
}
