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

import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A relay that structurally refuses a REQ (e.g. CLOSING an over-large filter with
 * `unsupported: too many filters`) closes the same REQ on every
 * reconnect. [RequestSubscriptionState.onClosed] already blocks an *in-connection*
 * resend, but a reconnect clears the per-connection wire state and [PoolRequests.syncState]
 * replays every desired filter unconditionally — so the client re-sends the identical
 * refused REQ once per reconnect, forever (observed 14x in a 40s cold start).
 *
 * These pin the refusal memory that survives reconnects: after [PoolRequests] sees the
 * same filter refused enough times, `syncState` stops replaying it — until the desired
 * filter meaningfully changes (which re-enables it), and never for `auth-required` /
 * `rate-limited`, which the auth + adaptive-limiter subsystems resolve on their own.
 */
class PoolRequestsRefusalTest {
    private val relay = NormalizedRelayUrl("wss://search.example/")

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

    private fun plainFilter(kind: Int = 1) = listOf(Filter(kinds = listOf(kind), limit = 10))

    /** One reconnect cycle: the pool clears wire state, then replays desired filters. */
    private fun reconnectAndSync(pool: PoolRequests): List<Command> {
        pool.onConnecting(relay)
        val sent = mutableListOf<Command>()
        pool.syncState(relay) { sent.add(it) }
        return sent
    }

    private fun close(
        pool: PoolRequests,
        subId: String,
        reason: String,
    ) = pool.onIncomingMessage(FakeRelayClient(relay), ClosedMessage(subId, reason))

    @Test
    fun stopsReplayingAThriceRefusedFilterAcrossReconnects() {
        val pool = PoolRequests(maxRefusalsBeforeSuppress = 3)
        pool.addOrUpdate("sub", mapOf(relay to plainFilter()), null)

        // Under the threshold, each reconnect still replays the REQ (giving the relay a chance).
        repeat(3) { attempt ->
            val sent = reconnectAndSync(pool)
            assertEquals(1, sent.filterIsInstance<ReqCmd>().size, "reconnect #$attempt should replay the REQ")
            close(pool, "sub", "unsupported: too many filters")
        }

        // Once the same filter has been refused [maxRefusalsBeforeSuppress] times, stop replaying it.
        val suppressed = reconnectAndSync(pool)
        assertTrue(suppressed.filterIsInstance<ReqCmd>().isEmpty(), "a thrice-refused filter must not be replayed again")
    }

    @Test
    fun aMeaningfulFilterChangeReEnablesTheReq() {
        val pool = PoolRequests(maxRefusalsBeforeSuppress = 2)
        pool.addOrUpdate("sub", mapOf(relay to plainFilter(1)), null)

        repeat(2) {
            reconnectAndSync(pool)
            close(pool, "sub", "unsupported: too many filters")
        }
        assertTrue(reconnectAndSync(pool).filterIsInstance<ReqCmd>().isEmpty(), "refused filter is suppressed")

        // The app changes the subscription's filter (different kind) — the relay may now accept it.
        pool.addOrUpdate("sub", mapOf(relay to plainFilter(30023)), null)
        assertEquals(1, reconnectAndSync(pool).filterIsInstance<ReqCmd>().size, "a changed filter must be tried again")
    }

    @Test
    fun aSearchOnlyRelayStopsReceivingPlainReqsFromEveryNewSubOnOneConnection() {
        // The search.nos.today case: 6 different subscriptions, one connection, each a
        // distinct plain feed filter the relay CLOSES with `error: search filter is required`.
        // Per-filter memory can't help (the filters differ); the relay-wide block must.
        val pool = PoolRequests(relayRefusals = RelayReqRefusals(threshold = 2))

        val sent = mutableListOf<Pair<String, Boolean>>() // subId -> did a REQ go out

        fun mountSub(
            subId: String,
            filter: List<Filter>,
        ) {
            val affected = pool.addOrUpdate(subId, mapOf(relay to filter), null)
            var reqSent = false
            pool.sendToRelayIfChanged(subId, affected) { _, cmd -> if (cmd is ReqCmd) reqSent = true }
            sent.add(subId to reqSent)
            close(pool, subId, "error: search filter is required")
        }

        mountSub("sub1", plainFilter(1))
        mountSub("sub2", plainFilter(2))
        mountSub("sub3", plainFilter(3))
        mountSub("sub4", plainFilter(4))

        assertTrue(sent[0].second && sent[1].second, "the first two plain subs are sent (learning the relay is search-only)")
        assertTrue(!sent[2].second && !sent[3].second, "after two refusals, further plain subs are not sent to a search-only relay")
    }

    @Test
    fun aCapabilityBlockedRelayIsDroppedFromDesiredRelays() {
        // The socket-closing half: once a relay is capability-blocked and no desired sub can
        // use it, it leaves the desired-relay set so the pool disconnects it.
        val pool = PoolRequests(relayRefusals = RelayReqRefusals(threshold = 2))
        pool.addOrUpdate("sub", mapOf(relay to plainFilter()), null)
        assertTrue(relay in pool.desiredRelays.value, "the relay is wanted before it refuses anything")

        close(pool, "sub", "error: search filter is required")
        assertTrue(relay in pool.desiredRelays.value, "one refusal doesn't drop it yet")

        close(pool, "sub", "error: search filter is required")
        assertTrue(relay !in pool.desiredRelays.value, "a search-only relay with only plain subs is dropped (socket closes)")
    }

    @Test
    fun aSearchOnlyRelayStaysWantedWhileASearchSubNeedsIt() {
        val pool = PoolRequests(relayRefusals = RelayReqRefusals(threshold = 2))
        pool.addOrUpdate("plain", mapOf(relay to plainFilter()), null)
        pool.addOrUpdate("search", mapOf(relay to listOf(Filter(kinds = listOf(1), search = "nostr"))), null)

        close(pool, "plain", "error: search filter is required")
        close(pool, "plain", "error: search filter is required")

        assertTrue(relay in pool.desiredRelays.value, "the relay stays wanted: a search sub still has a usable filter for it")
    }

    @Test
    fun authRequiredAndRateLimitedAreNeverSuppressed() {
        val pool = PoolRequests(maxRefusalsBeforeSuppress = 2)
        pool.addOrUpdate("sub", mapOf(relay to plainFilter()), null)

        repeat(4) {
            reconnectAndSync(pool)
            close(pool, "sub", MachineReadablePrefix.AUTH_REQUIRED.format("authenticate first"))
        }
        assertEquals(1, reconnectAndSync(pool).filterIsInstance<ReqCmd>().size, "auth-required must keep replaying (auth resolves it)")

        val pool2 = PoolRequests(maxRefusalsBeforeSuppress = 2)
        pool2.addOrUpdate("sub", mapOf(relay to plainFilter()), null)
        repeat(4) {
            pool2.onConnecting(relay)
            val sent = mutableListOf<Command>()
            pool2.syncState(relay) { sent.add(it) }
            pool2.onIncomingMessage(FakeRelayClient(relay), ClosedMessage("sub", MachineReadablePrefix.RATE_LIMITED.format("slow down")))
        }
        pool2.onConnecting(relay)
        val sent = mutableListOf<Command>()
        pool2.syncState(relay) { sent.add(it) }
        assertEquals(1, sent.filterIsInstance<ReqCmd>().size, "rate-limited must keep replaying (the limiter spaces it out)")
    }
}
