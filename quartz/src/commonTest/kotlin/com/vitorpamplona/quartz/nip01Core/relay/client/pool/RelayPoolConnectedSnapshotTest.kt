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
 * Both views of "connected" -- the callback-fed [RelayPool.connectedRelays] flow and the
 * members-read [RelayPool.connectedRelayUrls] snapshot -- must agree the moment the pool lets a
 * relay go, even when the socket layer never confirms the close.
 *
 * [com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebSocket.disconnect] is a
 * no-op that never calls back, which is exactly what OkHttp did after a relay sent a CLOSE frame
 * the app never answered: `cancel()` then fired neither `onClosed` nor `onFailure`. The flow used
 * to keep such a relay for minutes; now the pool clears it on removal and on disconnect itself
 * (and the real transports report a disconnect synchronously, see [com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket]),
 * so neither view depends on a callback that may never come.
 */
class RelayPoolConnectedSnapshotTest {
    private val url = NormalizedRelayUrl("wss://relay.example.com/")

    private fun openedPool(): Pair<FakeWebsocketBuilder, RelayPool> {
        val sockets = FakeWebsocketBuilder()
        val pool = RelayPool(sockets)
        pool.getOrCreateRelay(url).connect()
        sockets.lastListener.onOpen(pingMillis = 10, compression = false)
        return sockets to pool
    }

    @Test
    fun snapshotMatchesTheFlowWhileTheSocketIsOpen() {
        val (_, pool) = openedPool()

        assertEquals(setOf(url), pool.connectedRelays.value)
        assertEquals(setOf(url), pool.connectedRelayUrls())
        assertEquals(1, pool.connectedRelaysCount())
    }

    @Test
    fun removingARelayClearsBothViewsEvenWhenTheCloseIsSilent() {
        val (_, pool) = openedPool()

        // No subscription wants it anymore: the pool drops it and cancels its socket, and the
        // socket layer stays silent.
        pool.removeRelay(url)

        assertEquals(emptySet(), pool.connectedRelays.value)
        assertEquals(emptySet(), pool.connectedRelayUrls())
        assertEquals(0, pool.connectedRelaysCount())
    }

    @Test
    fun disconnectingThePoolClearsBothViewsEvenWhenTheCloseIsSilent() {
        val (_, pool) = openedPool()

        // The host is putting the client down (app backgrounded, connectivity lost).
        pool.disconnect()

        assertEquals(emptySet(), pool.connectedRelays.value)
        assertEquals(emptySet(), pool.connectedRelayUrls())
        assertEquals(setOf(url), pool.availableRelays.value, "still a member, just not connected")
    }
}
