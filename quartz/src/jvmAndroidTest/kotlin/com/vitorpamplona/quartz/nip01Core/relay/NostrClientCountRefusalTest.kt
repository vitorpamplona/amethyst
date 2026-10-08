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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.isCountRejectionNotice
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A relay without NIP-45 says so at once; a COUNT used to wait out its whole idle window
 * (15 s by default) on seven of 21 relays sampled before returning the same `null`.
 */
class NostrClientCountRefusalTest {
    @Test
    fun aClosedCountEndsAtOnce() =
        runBlocking {
            val hub = InProcessRelays(defaultPolicy = { RefusesCount() })
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(hub, scope)
            try {
                val started = System.currentTimeMillis()
                val result = client.count(InProcessRelays.DEFAULT_URL, Filter(kinds = listOf(1)), idleTimeoutMs = 15_000)
                val tookMs = System.currentTimeMillis() - started

                assertNull(result)
                assertTrue(tookMs < 5_000, "took ${tookMs}ms")

                val many = client.count(mapOf(InProcessRelays.DEFAULT_URL to listOf(Filter(kinds = listOf(1)))), idleTimeoutMs = 15_000)
                assertTrue(many.isEmpty())
                assertTrue(System.currentTimeMillis() - started < 10_000)
            } finally {
                client.disconnect()
                scope.cancel()
                hub.close()
            }
        }

    @Test
    fun anUnreachableRelayEndsAtOnce() =
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val client = NostrClient(BasicOkHttpWebSocket.Builder { OkHttpClient() }, scope)
            try {
                val started = System.currentTimeMillis()
                val result = client.count("ws://127.0.0.1:1/".normalizeRelayUrl(), Filter(kinds = listOf(1)), idleTimeoutMs = 15_000)

                assertNull(result)
                assertTrue(System.currentTimeMillis() - started < 5_000, "took ${System.currentTimeMillis() - started}ms")
            } finally {
                client.disconnect()
                scope.cancel()
            }
        }

    /** A relay that gave up and then answered (a reconnect re-sent its COUNT) is counted once. */
    @Test
    fun aRelayThatAnswersAfterGivingUpDoesNotEndTheFanOutEarly() =
        runBlocking {
            val a = NormalizedRelayUrl("wss://a.example/")
            val b = NormalizedRelayUrl("wss://b.example/")
            val c = NormalizedRelayUrl("wss://c.example/")
            val client =
                ScriptedCountClient(this) { relay, subId ->
                    when (relay) {
                        a -> listOf(0L to ClosedMessage(subId, "error: shutting down"), 50L to CountMessage(subId, CountResult(1)))
                        b -> listOf(100L to CountMessage(subId, CountResult(2)))
                        else -> listOf(300L to CountMessage(subId, CountResult(3)))
                    }
                }

            val results = client.count(listOf(a, b, c).associateWith { listOf(Filter(kinds = listOf(1))) }, idleTimeoutMs = 5_000)

            assertEquals(mapOf(a to 1, b to 2, c to 3), results.mapValues { it.value.count })
        }

    @Test
    fun theNoticesRelaysWithoutCountSendAreRecognised() {
        // Captured live: strfry, snort.social, nostr.wine, relay.conduit.market.
        assertTrue(isCountRejectionNotice("ERROR: bad msg: unknown cmd"))
        assertTrue(isCountRejectionNotice("Unknown message type: COUNT"))
        assertTrue(isCountRejectionNotice("ERROR: bad msg: invalid message: {'message_type': ['Invalid enum value COUNT']}"))
        assertTrue(isCountRejectionNotice("invalid message"))

        assertFalse(isCountRejectionNotice("rate-limited: slow down"))
        assertFalse(isCountRejectionNotice("ERROR: bad msg: invalid message: event too large"))
        assertFalse(isCountRejectionNotice("could not parse filter"))
        // About one query on a relay that does count: not a refusal of the verb.
        assertFalse(isCountRejectionNotice("rate-limited: too many concurrent COUNT requests"))
    }

    /** Answers each COUNT with the messages [script] gives for its relay, each after its delay. */
    private class ScriptedCountClient(
        private val scope: CoroutineScope,
        private val script: (NormalizedRelayUrl, String) -> List<Pair<Long, Message>>,
    ) : INostrClient by EmptyNostrClient() {
        private val listeners = CopyOnWriteArrayList<RelayConnectionListener>()

        override fun addConnectionListener(listener: RelayConnectionListener) {
            listeners.add(listener)
        }

        override fun removeConnectionListener(listener: RelayConnectionListener) {
            listeners.remove(listener)
        }

        override fun count(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
        ) {
            val relay = filters.keys.single()
            val relayClient = FakeRelayClient(relay)
            scope.launch {
                for ((delayMs, msg) in script(relay, subId)) {
                    delay(delayMs)
                    listeners.forEach { it.onIncomingMessage(relayClient, "", msg) }
                }
            }
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

    private class RefusesCount : PassThroughPolicy() {
        override fun accept(cmd: CountCmd): PolicyResult<CountCmd> = PolicyResult.Rejected("unsupported: this relay does not support NIP-45")
    }
}
