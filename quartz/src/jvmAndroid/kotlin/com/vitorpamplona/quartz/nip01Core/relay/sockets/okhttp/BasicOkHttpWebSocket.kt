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
    val out: WebSocketListener,
    /**
     * Optional URL rewriter invoked once per [connect] before the OkHttp
     * `Request` is built. Returning a different URL routes the WebSocket
     * handshake to that endpoint while keeping [url] as the canonical
     * relay identifier (used by relay tags, UI, etc.).
     *
     * Defaults to identity (no rewrite). Throwing falls back to `url.url`.
     */
    val urlRewriter: (NormalizedRelayUrl) -> String = { it.url },
    /**
     * Optional per-connect decorator over the [httpClient]-resolved
     * `OkHttpClient`. Runs AFTER [httpClient] and AFTER [urlRewriter] so
     * any per-URL state populated by the rewriter (e.g. a TLSA-record
     * cache hit) is visible when the decorator overlays its pinning /
     * trust-manager configuration.
     *
     * Defaults to passthrough. Throwing falls back to the un-decorated
     * client.
     */
    val clientDecorator: (NormalizedRelayUrl, OkHttpClient) -> OkHttpClient = { _, c -> c },
    val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
) : WebSocket {
    companion object {
        // Exists to avoid exceptions stopping the coroutine
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e("BasicOkHttpWebSocket", "WebsocketListener Caught exception: ${throwable.message}", throwable)
            }
    }

    private var socket: OkHttpWebSocket? = null

    override fun needsReconnect() = socket == null

    override fun connect() {
        // Order matters here: the URL rewriter runs FIRST so any per-URL
        // resolution side-effect (e.g. populating a `.bit` TLSA cache)
        // has happened before the client decorator is asked to look up
        // its pinning data.
        val connectUrl =
            try {
                val rewritten = urlRewriter(url)
                if (rewritten != url.url) {
                    Log.d("BasicOkHttpWebSocket", "Rewriting connect URL ${url.url} -> $rewritten")
                }
                rewritten
            } catch (t: Throwable) {
                Log.w("BasicOkHttpWebSocket", "URL rewriter failed for ${url.url}: ${t.message}")
                url.url
            }

        val baseClient = httpClient(url)
        val client =
            try {
                clientDecorator(url, baseClient)
            } catch (t: Throwable) {
                Log.w("BasicOkHttpWebSocket", "Client decorator failed for ${url.url}: ${t.message}")
                baseClient
            }

        val request = Request.Builder().url(connectUrl).build()

        val listener =
            object : OkHttpWebSocketListener() {
                val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
                val incomingMessages: Channel<String> = Channel(Channel.UNLIMITED)
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
                    // Asynchronously send the received message to the channel.
                    // `trySendBlocking` is used here for simplicity within the callback,
                    // but it's important to understand potential thread blocking if the buffer is full.
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

        socket = client.newWebSocket(request, listener)
    }

    override fun disconnect() {
        socket?.cancel()
        socket = null
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false

    class Builder(
        /** Optional URL rewriter (e.g. for `.bit` relay resolution). */
        val urlRewriter: (NormalizedRelayUrl) -> String = { it.url },
        /**
         * Optional client decorator (e.g. for `.bit` TLSA pinning). Runs
         * AFTER [httpClient] and AFTER [urlRewriter] so per-URL state
         * populated by the rewriter is visible by the time the decorator
         * runs.
         */
        val clientDecorator: (NormalizedRelayUrl, OkHttpClient) -> OkHttpClient = { _, c -> c },
        // [httpClient] is last so the existing trailing-lambda call
        // pattern `BasicOkHttpWebSocket.Builder { url -> ... }` keeps
        // working without changes across all current callers.
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = BasicOkHttpWebSocket(url, out, urlRewriter, clientDecorator, httpClient)
    }
}
