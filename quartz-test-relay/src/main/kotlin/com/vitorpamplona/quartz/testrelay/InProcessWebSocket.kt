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
package com.vitorpamplona.quartz.testrelay

import com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch

/**
 * In-memory implementation of [WebSocket] that talks to a [TestRelay] without
 * touching the network. Each instance opens one [RelaySession] on
 * [connect] and routes:
 *
 *  - Outbound (`send`) → server `RelaySession.receive()` via an inbound channel
 *    drained by a single coroutine, preserving message order per the
 *    [WebSocketListener] contract.
 *  - Server-side `send` callbacks → [WebSocketListener.onMessage].
 */
class InProcessWebSocket(
    private val relay: TestRelay,
    private val out: WebSocketListener,
) : WebSocket {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val incoming = Channel<String>(UNLIMITED)
    private var session: RelaySession? = null

    override fun needsReconnect(): Boolean = session == null

    override fun connect() {
        if (session != null) return
        val s = relay.server.connect { json -> out.onMessage(json) }
        session = s
        out.onOpen(0, false)
        scope.launch {
            for (msg in incoming) {
                s.receive(msg)
            }
        }
    }

    override fun disconnect() {
        val s = session ?: return
        session = null
        incoming.close()
        scope.cancel()
        s.close()
        out.onClosed(1000, "client disconnect")
    }

    override fun send(msg: String): Boolean {
        if (session == null) return false
        return incoming.trySend(msg).isSuccess
    }
}
