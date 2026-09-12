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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RelayPool.connectedRelayUrls] must report what the pool actually holds open, even when the
 * socket layer never confirms a close.
 *
 * [com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebSocket.disconnect] is a
 * no-op that never calls back, which is exactly what OkHttp does after a relay sent a WebSocket
 * CLOSE frame the app never answered: `cancel()` then fires neither `onClosed` nor `onFailure`.
 * The callback-driven [RelayPool.connectedRelays] flow keeps such a relay for minutes; the
 * snapshot drops it the moment the pool lets go of it.
 */
class RelayPoolConnectedSnapshotTest {
    private val url = NormalizedRelayUrl("wss://relay.example.com/")

    @Test
    fun snapshotMatchesTheFlowWhileTheSocketIsOpen() {
        val sockets = FakeWebsocketBuilder()
        val pool = RelayPool(sockets)

        pool.getOrCreateRelay(url).connect()
        sockets.lastListener.onOpen(pingMillis = 10, compression = false)

        assertEquals(setOf(url), pool.connectedRelays.value)
        assertEquals(setOf(url), pool.connectedRelayUrls())
        assertEquals(1, pool.connectedRelaysCount())
    }

    @Test
    fun removedRelayLeavesTheSnapshotEvenWhenTheCloseIsSilent() {
        val sockets = FakeWebsocketBuilder()
        val pool = RelayPool(sockets)

        pool.getOrCreateRelay(url).connect()
        sockets.lastListener.onOpen(pingMillis = 10, compression = false)

        // The pool drops the relay (no subscription wants it anymore) and cancels its socket,
        // but the socket layer stays silent.
        pool.removeRelay(url)

        // Documents the drift this test exists for: the flow still carries the relay...
        assertEquals(setOf(url), pool.connectedRelays.value)
        // ...while the snapshot already reflects that nothing is held open.
        assertEquals(emptySet(), pool.connectedRelayUrls())
        assertEquals(0, pool.connectedRelaysCount())
    }
}
