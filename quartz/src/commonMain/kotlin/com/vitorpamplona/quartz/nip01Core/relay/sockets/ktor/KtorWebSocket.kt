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
package com.vitorpamplona.quartz.nip01Core.relay.sockets.ktor

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class KtorWebSocket(
    val url: NormalizedRelayUrl,
    val httpClient: HttpClient,
    val out: WebSocketListener,
) : WebSocket {
    companion object {
        val exceptionHandler =
            CoroutineExceptionHandler { _, throwable ->
                Log.e("KtorWebSocket", "WebsocketListener Caught exception: ${throwable.message}", throwable)
            }
    }

    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)

    override fun needsReconnect() = session == null

    override fun connect() {
        connectionJob =
            scope.launch {
                try {
                    val wsSession = httpClient.webSocketSession(url.url)
                    session = wsSession

                    // Notify open — Ktor doesn't expose ping timing or compression headers
                    // the same way OkHttp does, so we use defaults.
                    out.onOpen(
                        pingMillis = 0,
                        compression = false,
                    )

                    // Process incoming frames sequentially to maintain message ordering
                    for (frame in wsSession.incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                out.onMessage(frame.readText())
                            }

                            else -> { /* ignore binary, ping, pong frames */ }
                        }
                    }

                    // If the loop ends normally, the connection was closed
                    val closeReason = wsSession.closeReason.await()
                    session = null
                    out.onClosed(
                        closeReason?.code?.toInt() ?: 1000,
                        closeReason?.message ?: "",
                    )
                } catch (e: Exception) {
                    session = null
                    out.onFailure(e, null, e.message)
                }
            }
    }

    override fun disconnect() {
        val currentSession = session
        session = null
        scope.launch {
            try {
                currentSession?.close(CloseReason(CloseReason.Codes.NORMAL, ""))
            } catch (e: Exception) {
                Log.e("KtorWebSocket", "Error closing WebSocket: ${e.message}", e)
            }
        }
        connectionJob?.cancel()
        connectionJob = null
    }

    override fun send(msg: String): Boolean {
        val currentSession = session ?: return false
        if (!currentSession.isActive) return false
        scope.launch {
            try {
                currentSession.send(Frame.Text(msg))
            } catch (e: Exception) {
                Log.e("KtorWebSocket", "Error sending message: ${e.message}", e)
            }
        }
        return true
    }

    class Builder(
        val httpClient: HttpClient,
    ) : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ) = KtorWebSocket(url, httpClient, out)
    }
}
