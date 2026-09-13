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
package com.vitorpamplona.quartz.nip01Core.relay.sockets

/**
 * One socket session towards a relay, as the relay client sees it.
 *
 * The contract every implementation keeps, and that [com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient]
 * relies on for its bookkeeping:
 *
 * - A session ends with **exactly one** terminal callback on its [WebSocketListener], `onClosed`
 *   or `onFailure`, however it ends.
 * - [disconnect] ends the session itself: it reports `onClosed` **synchronously**, before
 *   returning, and nothing from that socket reaches the listener afterwards. The relay client
 *   may dial a new socket immediately, so a late report from the old one -- which OkHttp
 *   delivers on its own threads for a cancel, and never delivers at all for a relay-initiated
 *   close it was not allowed to finish -- must be swallowed by the adapter, not forwarded.
 */
interface WebSocket {
    fun needsReconnect(): Boolean

    fun connect()

    /** Ends the session now. Reports `onClosed` synchronously if one was open; a no-op otherwise. */
    fun disconnect()

    fun send(msg: String): Boolean
}
