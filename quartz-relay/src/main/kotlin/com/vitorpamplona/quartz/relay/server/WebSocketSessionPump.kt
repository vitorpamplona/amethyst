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
package com.vitorpamplona.quartz.relay.server

import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * Per-WebSocket pump that owns the bounded outbound queue and the
 * writer coroutine. Pulled out of `LocalRelayServer` so that file
 * stays focused on Ktor wiring; the slow-client / backpressure
 * policy now lives next to the data structures it manages.
 *
 * Lifecycle:
 *   1. `connect(server, registerSession)` opens a [RelaySession],
 *      registers it with the supplied callback, and starts the
 *      writer coroutine that drains [outQueue] into [outgoing].
 *   2. `pump()` reads inbound frames until the socket closes.
 *   3. `finally`-style teardown closes the queue, cancels the
 *      writer, unregisters the session, and closes it.
 *
 * Slow-client policy: when [outQueue] fills, [SESSION_OUTGOING_BUFFER]
 * frames behind, the connection is dropped rather than silently
 * losing EVENT/EOSE — silent drop would corrupt NIP-01.
 */
internal class WebSocketSessionPump(
    private val ws: DefaultWebSocketServerSession,
) {
    private val outQueue = Channel<String>(capacity = SESSION_OUTGOING_BUFFER)
    private var droppedForBackpressure = false

    suspend fun pump(
        server: NostrServer,
        registerSession: (RelaySession) -> Unit,
        unregisterSession: (RelaySession) -> Unit,
    ) {
        val writerJob =
            ws.launch {
                try {
                    for (json in outQueue) {
                        ws.outgoing.send(Frame.Text(json))
                    }
                } catch (_: ClosedSendChannelException) {
                    // socket closed — outer handler runs normal teardown.
                }
            }
        val session =
            server.connect { json ->
                val res = outQueue.trySend(json)
                if (!res.isSuccess && !res.isClosed) {
                    // Buffer is full → slow client. Mark + close the
                    // queue; the writer drains, then the outer handler
                    // closes the WS session.
                    droppedForBackpressure = true
                    outQueue.close()
                }
            }
        registerSession(session)
        try {
            ws.incoming.consumeEach { frame ->
                if (droppedForBackpressure) return@consumeEach
                if (frame is Frame.Text) {
                    session.receive(frame.readText())
                }
            }
        } finally {
            outQueue.close()
            writerJob.cancel()
            unregisterSession(session)
            session.close()
        }
    }

    companion object {
        /**
         * Per-session outbound buffer size. When a slow client falls
         * this many frames behind, we close their connection rather
         * than silently dropping further frames (which would corrupt
         * NIP-01 by missing EVENT/EOSE messages).
         *
         * Sized to hold fan-out for a connection holding several
         * thousand subscriptions when one event matches all of them
         * — the realistic upper bound for a relay client. At ~250B
         * per frame this caps per-session memory at ~2 MiB before
         * we drop the connection.
         */
        const val SESSION_OUTGOING_BUFFER: Int = 8192
    }
}
