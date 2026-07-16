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
package com.vitorpamplona.quartz.nip01Core.relay.client.limits

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RelayLimitsTrackerTest {
    private class CapturingClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        var captured: RelayConnectionListener? = null

        override fun addConnectionListener(listener: RelayConnectionListener) {
            captured = listener
        }
    }

    private class FakeRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun sendOrConnectAndSync(cmd: Command) = Unit

        override fun sendIfConnected(cmd: Command) = Unit

        override fun disconnect() = Unit
    }

    private fun setup(): Pair<RelayLimitsTracker, RelayConnectionListener> {
        val client = CapturingClient()
        val limits = RelayLimitsTracker(client)
        val listener = client.captured ?: error("RelayLimitsTracker did not register a listener")
        return limits to listener
    }

    @Test
    fun cachesLimitsPerRelay() {
        val (limits, listener) = setup()
        val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

        assertNull(limits.get(relay.url), "No limits before any LIMITS message")

        listener.onIncomingMessage(relay, "", LimitsMessage(canWrite = true, maxLimit = 200))

        assertEquals(true, limits.get(relay.url)?.canWrite)
        assertEquals(200, limits.get(relay.url)?.maxLimit)
        assertEquals(limits.get(relay.url), limits.limitsFlow.value[relay.url])
    }

    @Test
    fun laterLimitsReplaceEarlierOnes() {
        val (limits, listener) = setup()
        val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

        listener.onIncomingMessage(relay, "", LimitsMessage(canWrite = false, maxLimit = 200))
        listener.onIncomingMessage(relay, "", LimitsMessage(canWrite = true, maxLimit = 500))

        // A relay re-advertises LIMITS when rights change (e.g. after AUTH flips can_write).
        assertEquals(true, limits.get(relay.url)?.canWrite)
        assertEquals(500, limits.get(relay.url)?.maxLimit)
    }

    @Test
    fun tracksLimitsForDistinctRelaysIndependently() {
        val (limits, listener) = setup()
        val relayA = FakeRelayClient(NormalizedRelayUrl("wss://a.example/"))
        val relayB = FakeRelayClient(NormalizedRelayUrl("wss://b.example/"))

        listener.onIncomingMessage(relayA, "", LimitsMessage(maxLimit = 100))
        listener.onIncomingMessage(relayB, "", LimitsMessage(maxLimit = 999))

        assertEquals(100, limits.get(relayA.url)?.maxLimit)
        assertEquals(999, limits.get(relayB.url)?.maxLimit)
        assertEquals(2, limits.snapshot().size)
    }

    @Test
    fun dropsCachedLimitsOnDisconnect() {
        val (limits, listener) = setup()
        val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

        listener.onIncomingMessage(relay, "", LimitsMessage(canRead = true))
        assertTrue(limits.get(relay.url) != null)

        listener.onDisconnected(relay)
        assertNull(limits.get(relay.url), "Limits are connection-scoped and cleared on disconnect")
        assertTrue(limits.snapshot().isEmpty())
    }

    @Test
    fun ignoresNonLimitsMessages() {
        val (limits, listener) = setup()
        val relay = FakeRelayClient(NormalizedRelayUrl("wss://relay.example/"))

        listener.onIncomingMessage(relay, "", EoseMessage("sub1"))

        assertNull(limits.get(relay.url))
        assertTrue(limits.snapshot().isEmpty())
    }
}
