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
import java.util.concurrent.atomic.AtomicBoolean

class OkHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: (url: NormalizedRelayUrl) -> OkHttpClient,
    val out: WebSocketListener,
) : WebSocket {
    private var usingOkHttp: OkHttpClient? = null

    @Volatile private var socket: okhttp3.WebSocket? = null

    /**
     * Set once, by whichever of `onClosed`, `onFailure` or [disconnect] ends the session first.
     * One adapter is one session (the relay client builds a fresh one per dial, and OkHttp binds
     * exactly one socket to the listener), so a callback only has to ask whether the session
     * already ended. See quartz's `BasicOkHttpWebSocket` for the full reasoning; the two adapters
     * differ only in how [needsReconnect] is decided.
     */
    private val ended = AtomicBoolean(false)

    fun buildRequest() = Request.Builder().url(url.url).build()

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
        if (socket != null || ended.get()) return
        val client = httpClient(url)
        usingOkHttp = client
        socket = client.newWebSocket(buildRequest(), OkHttpWebsocketListener(out))
    }

    inner class OkHttpWebsocketListener(
        val out: WebSocketListener,
    ) : okhttp3.WebSocketListener() {
        val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)

        // UNLIMITED on purpose — do NOT bound this channel. The app holds
        // 2000+ relay connections; a bounded buffer under a slow consumer
        // would block OkHttp reader threads and park the backlog on the
        // relay's outbound buffers — infrastructure that isn't ours. Drain
        // the remote as fast as it can send; consumer speed is handled
        // downstream.
        val incomingMessages: Channel<String> = Channel(Channel.UNLIMITED)
        val job = // Launch a coroutine to process messages from the channel.
            scope.launch {
                for (message in incomingMessages) {
                    out.onMessage(message)
                }
            }

        /** Claims the session's single terminal report. False if it already ended. */
        private fun endSession(): Boolean {
            if (!ended.compareAndSet(false, true)) return false
            socket = null
            incomingMessages.close()
            job.cancel()
            scope.cancel()
            return true
        }

        override fun onOpen(
            webSocket: okhttp3.WebSocket,
            response: Response,
        ) {
            if (ended.get()) return
            out.onOpen(
                (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt(),
                response.headers["Sec-WebSocket-Extensions"]?.contains("permessage-deflate") ?: false,
            )
        }

        override fun onMessage(
            webSocket: okhttp3.WebSocket,
            text: String,
        ) {
            if (ended.get()) return
            // Never blocks (unlimited channel): the OkHttp reader thread must
            // stay free to keep draining the socket.
            incomingMessages.trySendBlocking(text)
        }

        override fun onClosing(
            webSocket: okhttp3.WebSocket,
            code: Int,
            reason: String,
        ) {
            // The relay sent a CLOSE frame. OkHttp fires onClosed only once BOTH peers have sent
            // one, and sending ours is the application's job (WebSocketListener KDoc; its own
            // WebSocketEcho recipe does exactly this). Unanswered, the socket sat half-closed:
            // no onClosed, no onFailure, send() still accepted and discarded, a later cancel()
            // silent too -- so the relay client believed it was connected until the 120s ping
            // path failed up to two intervals later.
            //
            // Always 1000 rather than echoing `code`: close() validates the code it writes and
            // throws on the reserved ones (1005, 1006, 1015), and a relay may send anything.
            webSocket.close(1000, null)
        }

        override fun onClosed(
            webSocket: okhttp3.WebSocket,
            code: Int,
            reason: String,
        ) {
            if (!endSession()) return
            out.onClosed(code, reason)
        }

        override fun onFailure(
            webSocket: okhttp3.WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            if (!endSession()) return
            out.onFailure(t, response?.code, response?.message)
        }
    }

    class Builder(
        val httpClient: (NormalizedRelayUrl) -> OkHttpClient,
        val canDial: (NormalizedRelayUrl) -> Boolean = { true },
    ) : WebsocketBuilder {
        // Called when connecting.
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = OkHttpWebSocket(url, httpClient, out)

        // Gates the dial — false skips it (e.g. a Tor-routed relay before Tor is ready).
        override fun canConnect(url: NormalizedRelayUrl) = canDial(url)
    }

    override fun disconnect() {
        // Claim the session ourselves and cancel (which also kills a SEND stack that might be
        // waiting): OkHttp's cancel() raises no callback when no reader is left to fail, and when
        // it does the failure arrives later on its own thread. The relay client needs the answer
        // now, and must not hear from this socket again.
        val closing = socket ?: return
        if (!ended.compareAndSet(false, true)) return
        socket = null
        closing.cancel()
        out.onClosed(1000, "client disconnect")
    }

    override fun send(msg: String): Boolean = socket?.send(msg) ?: false
}
