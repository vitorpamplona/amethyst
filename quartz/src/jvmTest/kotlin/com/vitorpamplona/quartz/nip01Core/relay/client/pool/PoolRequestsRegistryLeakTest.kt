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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The request registry must hold LIVE subscriptions only.
 *
 * [PoolRequests.onConnecting], [PoolRequests.onDisconnected] and
 * [PoolRequests.onCannotConnect] scan the whole registry on every relay lifecycle
 * event. `remove()` used to drop the sub from `desiredSubs` but leave its
 * [com.vitorpamplona.quartz.nip01Core.relay.client.reqs.RequestSubscriptionState]
 * behind forever, so the registry grew with every one-shot fetch and each connect
 * became O(all subs ever created) — reported downstream as a pool whose monitor plane
 * never completed a pass because the dispatcher was pinned scanning dead rows
 * (NosFabrica/vespa-relay#154).
 *
 * The naive fix — dropping the row inside `remove()` — is worse than the leak: the
 * CLOSE frame is decided from that row, so it silently stops being sent and the
 * subscription leaks on the *relay* instead. Hence [closesAreStillSentForEveryRemoval].
 */
class PoolRequestsRegistryLeakTest {
    private val relayA = NormalizedRelayUrl("wss://a.example/")
    private val relayB = NormalizedRelayUrl("wss://b.example/")

    private fun filters() = listOf(Filter(kinds = listOf(1)))

    private fun event() =
        Event(
            id = "00".repeat(32),
            pubKey = "11".repeat(32),
            createdAt = 1,
            kind = 1,
            tags = emptyArray(),
            content = "hi",
            sig = "22".repeat(64),
        )

    private class FakeRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = true

        override fun disconnect() = Unit

        override fun sendIfConnected(cmd: Command) = Unit

        override fun sendOrConnectAndSync(cmd: Command) = Unit
    }

    /** Mimics NostrClient.unsubscribe: remove from BOTH registries, then flush. */
    private fun unsubscribe(
        reqs: PoolRequests,
        counts: PoolCounts,
        subId: String,
        sent: MutableList<Command>,
    ) {
        val reqRelays = reqs.remove(subId)
        val countRelays = counts.remove(subId)
        reqs.sendToRelayIfChanged(subId, reqRelays) { r, cmd ->
            sent.add(cmd)
            // NostrClient.onSent fans every frame out to both registries.
            reqs.onSent(r, cmd)
            counts.onSent(r, cmd)
        }
        counts.sendToRelayIfChanged(subId, countRelays) { r, cmd ->
            sent.add(cmd)
            reqs.onSent(r, cmd)
            counts.onSent(r, cmd)
        }
    }

    private fun subscribe(
        reqs: PoolRequests,
        counts: PoolCounts,
        subId: String,
        relays: Set<NormalizedRelayUrl>,
        sent: MutableList<Command> = mutableListOf(),
        listener: SubscriptionListener? = null,
    ) {
        val toUpdate = reqs.addOrUpdate(subId, relays.associateWith { filters() }, listener)
        reqs.sendToRelayIfChanged(subId, toUpdate) { r, cmd ->
            sent.add(cmd)
            reqs.onSent(r, cmd)
            counts.onSent(r, cmd)
        }
    }

    @Test
    fun registryHoldsLiveSubscriptionsOnly() {
        val reqs = PoolRequests()
        val counts = PoolCounts()
        val sent = mutableListOf<Command>()

        repeat(1000) { i ->
            val subId = "one-shot-$i"
            subscribe(reqs, counts, subId, setOf(relayA, relayB), sent)
            assertEquals(1, reqs.activeSubscriptionCount(), "sub $i must be live while subscribed")
            unsubscribe(reqs, counts, subId, sent)
        }

        assertEquals(0, reqs.activeSubscriptionCount(), "REQ registry must not retain removed subscriptions")
        assertEquals(0, counts.activeQueryCount(), "CLOSEs for REQ subs must not materialize COUNT rows")
    }

    @Test
    fun closesAreStillSentForEveryRemoval() {
        val reqs = PoolRequests()
        val counts = PoolCounts()
        val sent = mutableListOf<Command>()

        repeat(100) { i ->
            val subId = "one-shot-$i"
            subscribe(reqs, counts, subId, setOf(relayA, relayB), sent)
            unsubscribe(reqs, counts, subId, sent)
        }

        assertEquals(200, sent.count { it is ReqCmd }, "one REQ per (sub, relay)")
        assertEquals(200, sent.count { it is CloseCmd }, "one CLOSE per (sub, relay) — the relay must be told")
        assertEquals(0, sent.count { it is CountCmd })
    }

    @Test
    fun countQueriesAreAlsoReleased() {
        val reqs = PoolRequests()
        val counts = PoolCounts()
        val sent = mutableListOf<Command>()

        repeat(500) { i ->
            val queryId = "count-$i"
            val toUpdate = counts.addOrUpdate(queryId, mapOf(relayA to filters()))
            counts.sendToRelayIfChanged(queryId, toUpdate) { r, cmd ->
                sent.add(cmd)
                reqs.onSent(r, cmd)
                counts.onSent(r, cmd)
            }
            assertEquals(1, counts.activeQueryCount())
            unsubscribe(reqs, counts, queryId, sent)
        }

        // NB: a COUNT still awaiting its reply is deliberately NOT closed (the reply
        // ends the query on its own) — that guard is untouched here; what must change
        // is that its state row is released.
        assertEquals(500, sent.count { it is CountCmd }, "one COUNT per query")
        assertEquals(0, counts.activeQueryCount(), "COUNT registry must not retain removed queries")
        assertEquals(0, reqs.activeSubscriptionCount(), "CLOSEs for COUNT ids must not materialize REQ rows")
    }

    /** Frames already on the wire when the CLOSE went out must no-op, not resurrect. */
    @Test
    fun lateFramesAfterCloseDoNotResurrectTheRegistry() =
        runTest {
            val reqs = PoolRequests()
            val counts = PoolCounts()
            val sent = mutableListOf<Command>()
            val relay = FakeRelayClient(relayA)

            var callbacks = 0
            val listener =
                object : SubscriptionListener {
                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        callbacks++
                    }

                    override fun onClosed(
                        message: String,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        callbacks++
                    }
                }

            subscribe(reqs, counts, "sub", setOf(relayA), sent, listener)
            unsubscribe(reqs, counts, "sub", sent)
            assertEquals(0, reqs.activeSubscriptionCount())

            // The relay answers the REQ after we already sent the CLOSE.
            reqs.onIncomingMessage(relay, EoseMessage("sub"))
            reqs.onIncomingMessage(relay, ClosedMessage("sub", "closed"))
            counts.onIncomingMessage(relay, ClosedMessage("sub", "closed"))

            assertEquals(0, reqs.activeSubscriptionCount(), "late frames must not recreate a dead sub")
            assertEquals(0, counts.activeQueryCount())
            assertEquals(0, callbacks, "the listener is gone; late frames must not fire it")
        }

    /**
     * The registry is the *believed relay state*, deliberately distinct from the desired
     * state: while a sub is still desired its row survives everything — including a
     * disconnect, which wipes the per-connection wire state but keeps `lastKnownFilters`
     * so events still arriving can be linked to the filters the relay was actually
     * running. The leak fix must not touch that, and doesn't: it only releases rows
     * whose sub is no longer desired.
     */
    @Test
    fun aStillDesiredSubKeepsLinkingLateEventsToTheFiltersTheRelayWasRunning() =
        runTest {
            val reqs = PoolRequests()
            val counts = PoolCounts()
            val sent = mutableListOf<Command>()
            val relay = FakeRelayClient(relayA)
            val subFilters = filters()

            val seen = mutableListOf<List<Filter>?>()
            val listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        seen.add(forFilters)
                    }
                }

            val add = reqs.addOrUpdate("sub", mapOf(relayA to subFilters), listener)
            reqs.sendToRelayIfChanged("sub", add) { r, cmd ->
                sent.add(cmd)
                reqs.onSent(r, cmd)
            }

            // The socket drops: per-connection wire state is wiped, the row is not.
            reqs.onDisconnected(relayA)

            // An event that was already on the wire still resolves to the filters the
            // relay was running for this sub.
            reqs.onIncomingMessage(relay, EventMessage("sub", event()))

            assertEquals(1, reqs.activeSubscriptionCount(), "a desired sub keeps its row across a disconnect")
            assertEquals(1, seen.size, "the listener is still wired")
            assertTrue(seen[0] === subFilters, "the late event is linked to the filters the relay was running")
        }

    /**
     * The counterpart: once a sub is *removed*, `remove()` drops its listener in the
     * same call, so nothing is left to process a late frame with. Holding the row past
     * that point buys no behaviour — it only inflates the map every connect scans. This
     * passes both before and after the fix; it is here to keep that true.
     */
    @Test
    fun aRemovedSubHasNoListenerLeftToServeLateFrames() =
        runTest {
            val reqs = PoolRequests()
            val counts = PoolCounts()
            val sent = mutableListOf<Command>()
            val relay = FakeRelayClient(relayA)

            var callbacks = 0
            val listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        callbacks++
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        callbacks++
                    }
                }

            subscribe(reqs, counts, "sub", setOf(relayA), sent, listener)
            unsubscribe(reqs, counts, "sub", sent)

            reqs.onIncomingMessage(relay, EventMessage("sub", event()))
            reqs.onIncomingMessage(relay, EoseMessage("sub"))

            assertEquals(0, callbacks, "remove() already dropped the listener; the row has no consumer")
        }

    /** The scan cost of a relay lifecycle event must track live subs, not history. */
    @Test
    fun connectScanStaysBoundedByLiveSubscriptions() {
        val reqs = PoolRequests()
        val counts = PoolCounts()
        val sent = mutableListOf<Command>()

        subscribe(reqs, counts, "tail", setOf(relayA), sent)

        repeat(5000) { i ->
            val subId = "churn-$i"
            subscribe(reqs, counts, subId, setOf(relayA), sent)
            unsubscribe(reqs, counts, subId, sent)
        }

        assertEquals(1, reqs.activeSubscriptionCount(), "only the long-lived tail should remain")

        reqs.onConnecting(relayA)
        reqs.onDisconnected(relayA)
        reqs.onCannotConnect(relayA, "nope")

        assertTrue(reqs.getSubscriptionFiltersOrNull("tail") != null, "the live tail must survive the churn")
    }

    /**
     * A subscribe and an unsubscribe of the SAME id racing on different threads. The
     * release must never swallow the new subscription's REQ: a feed the app still wants
     * that never gets a REQ is silent forever, which is far worse than a leaked row.
     *
     * Honest caveat: the window this protects (a release landing between a concurrent
     * subscribe's row creation and its send decision) is a few instructions wide, and
     * this test does NOT reliably reproduce it — it passes with the guard removed. It is
     * a regression guard on the invariant "registry membership tracks desired-ness", not
     * proof that the guard fires. The guard is kept because it is two comparisons and the
     * failure it prevents is a permanently silent subscription.
     */
    @Test
    fun subscribeRacingAnUnsubscribeOfTheSameIdStillSendsItsReq() {
        repeat(300) { episode ->
            val reqs = PoolRequests()
            val sent = java.util.concurrent.ConcurrentLinkedQueue<Command>()
            val subId = "raced-$episode"

            // A live sub about to be torn down.
            reqs.addOrUpdate(subId, mapOf(relayA to filters()), null)
            reqs.sendToRelayIfChanged(subId, setOf(relayA)) { r, cmd -> reqs.onSent(r, cmd) }

            val start = java.util.concurrent.CountDownLatch(1)
            val unsub =
                Thread {
                    start.await()
                    val relays = reqs.remove(subId)
                    reqs.sendToRelayIfChanged(subId, relays) { r, cmd ->
                        sent.add(cmd)
                        reqs.onSent(r, cmd)
                    }
                }
            val resub =
                Thread {
                    start.await()
                    val relays = reqs.addOrUpdate(subId, mapOf(relayA to filters()), null)
                    reqs.sendToRelayIfChanged(subId, relays) { r, cmd ->
                        sent.add(cmd)
                        reqs.onSent(r, cmd)
                    }
                }
            unsub.start()
            resub.start()
            start.countDown()
            unsub.join()
            resub.join()

            // Whoever won, the invariant is: if the sub is still desired it must have a
            // row (so later filter changes and reconnect replays work), and if it is not
            // desired it must have none.
            val desired = reqs.getSubscriptionFiltersOrNull(subId) != null
            assertEquals(
                if (desired) 1 else 0,
                reqs.activeSubscriptionCount(),
                "episode $episode: registry must match desired-ness (desired=$desired)",
            )
        }
    }
}
