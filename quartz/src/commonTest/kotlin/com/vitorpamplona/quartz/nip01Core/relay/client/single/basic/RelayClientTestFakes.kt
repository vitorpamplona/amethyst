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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.basic

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder

/**
 * No-op [WebSocket] for tests. connect/disconnect/send do nothing; the relay's
 * behavior is driven by invoking the [WebSocketListener] callbacks captured by
 * [FakeWebsocketBuilder].
 */
class FakeWebSocket : WebSocket {
    override fun needsReconnect() = false

    override fun connect() {}

    override fun disconnect() {}

    override fun send(msg: String) = true
}

/**
 * [WebsocketBuilder] that records how many sockets were built ([connectAttempts])
 * and exposes the most recently captured [WebSocketListener] so a test can drive
 * onOpen/onFailure/onClosed. [lastListener] is set on every [build]; read it only
 * after the client has attempted to connect.
 */
class FakeWebsocketBuilder : WebsocketBuilder {
    var connectAttempts = 0
    lateinit var lastListener: WebSocketListener

    override fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket {
        connectAttempts++
        lastListener = out
        return FakeWebSocket()
    }
}
