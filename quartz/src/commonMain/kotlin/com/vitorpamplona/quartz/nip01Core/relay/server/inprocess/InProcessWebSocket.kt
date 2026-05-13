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
package com.vitorpamplona.quartz.nip01Core.relay.server.inprocess

import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch

/**
 * In-memory implementation of [WebSocket] that talks directly to a
 * [NostrServer] without touching the network. Each instance opens one
 * [RelaySession] on [connect] and routes:
 *
 *  - Outbound (`send`) → server `RelaySession.receive()` via an inbound
 *    channel drained by a single coroutine, preserving message order
 *    per the [WebSocketListener] contract.
 *  - Server-side `send` callbacks → [WebSocketListener.onMessage].
 *
 * Use this to wire a `NostrClient` to an embedded server in unit tests
 * or single-JVM scenarios without paying for a real TCP socket. Because
 * it implements [WebSocket], it slots into anywhere a `WebsocketBuilder`
 * expects.
 *
 * Reconnect-after-disconnect is supported: each [connect] creates a
 * fresh scope + drain channel so a previous [disconnect] (which
 * cancels both) doesn't leave a dead drainer behind.
 */
class InProcessWebSocket(
    private val server: NostrServer,
    private val out: WebSocketListener,
) : WebSocket {
    private var scope: CoroutineScope? = null
    private var incoming: Channel<String>? = null
    private var drainJob: Job? = null
    private var session: RelaySession? = null

    override fun needsReconnect(): Boolean = session == null

    override fun connect() {
        if (session != null) return
        val newScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val newIncoming = Channel<String>(UNLIMITED)
        val s = server.connect { json -> out.onMessage(json) }

        scope = newScope
        incoming = newIncoming
        session = s
        drainJob =
            newScope.launch {
                for (msg in newIncoming) {
                    s.receive(msg)
                }
            }

        out.onOpen(0, false)
    }

    override fun disconnect() {
        val s = session ?: return
        session = null
        incoming?.close()
        incoming = null
        drainJob = null
        scope?.cancel()
        scope = null
        s.close()
        out.onClosed(1000, "client disconnect")
    }

    override fun send(msg: String): Boolean {
        // Capture the current channel reference: we want to fail
        // (return false) if the socket was disconnected, even if a
        // racing thread is mid-`connect()`.
        val ch = incoming ?: return false
        return ch.trySend(msg).isSuccess
    }
}
