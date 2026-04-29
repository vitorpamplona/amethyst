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
package com.vitorpamplona.amethyst.demo.net

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Ktor-based [WebSocket] for talking to a Nostr relay.
 *
 * Quartz exposes [WebsocketBuilder] as the only seam between its relay-pool
 * and the underlying transport, so all this class has to do is open a Ktor
 * websocket session, forward incoming text frames to [out], and let Quartz
 * drive sends.
 */
class KtorWebSocket(
    private val url: NormalizedRelayUrl,
    private val httpClient: HttpClient,
    private val out: WebSocketListener,
) : WebSocket {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var session: DefaultWebSocketSession? = null
    private var readerJob: Job? = null

    override fun needsReconnect(): Boolean = session == null

    override fun connect() {
        readerJob =
            scope.launch {
                try {
                    val s = httpClient.webSocketSession(urlString = url.url)
                    session = s
                    out.onOpen(0, false)

                    for (frame in s.incoming) {
                        if (frame is Frame.Text) {
                            out.onMessage(frame.readText())
                        }
                    }

                    val reason = s.closeReason.await()
                    out.onClosed(
                        code =
                            reason?.code?.toInt() ?: CloseReason.Codes.NORMAL.code
                                .toInt(),
                        reason = reason?.message ?: "",
                    )
                } catch (t: Throwable) {
                    out.onFailure(t, null, null)
                } finally {
                    session = null
                }
            }
    }

    override fun disconnect() {
        val s = session
        session = null
        readerJob?.cancel()
        readerJob = null
        if (s != null) {
            runBlocking { s.close(CloseReason(CloseReason.Codes.NORMAL, "client disconnect")) }
        }
        scope.cancel()
    }

    override fun send(msg: String): Boolean {
        val s = session ?: return false
        scope.launch { s.send(msg) }
        return true
    }

    /**
     * The factory Quartz hands to [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient].
     * One [HttpClient] is shared by every relay in the pool.
     */
    class Builder(
        private val httpClient: HttpClient = defaultClient(),
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket = KtorWebSocket(url, httpClient, out)

        companion object {
            fun defaultClient() =
                HttpClient(CIO) {
                    install(WebSockets)
                }
        }
    }
}
