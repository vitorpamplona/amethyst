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
import com.vitorpamplona.quartz.nip01Core.relay.client.testing.FakeRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A `rate-limited:` CLOSED that states a back-off (NIP-01 `params`) means "no REQ before then".
 * The CLOSED handler normally re-sends at once when the sub's filters changed while its REQ was
 * in flight; under a back-off that has to wait for [PoolRequests.retryRateLimited] instead.
 */
class PoolRequestsBackOffTest {
    private val relay = NormalizedRelayUrl("wss://busy.example/")

    private fun filter(kind: Int) = listOf(Filter(kinds = listOf(kind), limit = 10))

    private fun sync(pool: PoolRequests): List<Command> {
        pool.onConnecting(relay)
        val sent = mutableListOf<Command>()
        pool.syncState(relay) { sent.add(it) }
        return sent
    }

    @Test
    fun aChangedSubWaitsOutTheBackOff() =
        runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("sub", mapOf(relay to filter(1)), null)
            assertEquals(1, sync(pool).size)

            // The feed moves on while the first REQ is in flight; the relay then throttles it.
            pool.addOrUpdate("sub", mapOf(relay to filter(7)), null)
            val socket = FakeRelayClient(relay)
            pool.onIncomingMessage(socket, ClosedMessage("sub", "rate-limited: slow down", "1000"))
            assertTrue(socket.sent.isEmpty(), "re-sent inside the back-off: ${socket.sent}")

            // Once it passes, the CURRENT filters go out.
            val retried = mutableListOf<Command>()
            pool.retryRateLimited(relay, setOf("sub")) { retried.add(it) }
            assertEquals(listOf(7), (retried.single() as ReqCmd).filters.single().kinds)
        }

    @Test
    fun aSubTheRelayAlreadyClosedIsNotClosedAgainButCanComeBack() =
        runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("sub", mapOf(relay to filter(1)), null)
            sync(pool)
            pool.onIncomingMessage(FakeRelayClient(relay), ClosedMessage("sub", "rate-limited: slow down"))

            // Dropped while CLOSED: no CLOSE, the relay has nothing to close.
            val dropped = mutableListOf<Command>()
            pool.addOrUpdate("sub", emptyMap(), null)
            pool.sendToRelayIfChanged("sub", setOf(relay)) { _, cmd -> dropped.add(cmd) }
            assertTrue(dropped.isEmpty(), "sent $dropped")

            // Wanted again with the very same filters: that is a new REQ.
            val back = mutableListOf<Command>()
            pool.addOrUpdate("sub", mapOf(relay to filter(1)), null)
            pool.sendToRelayIfChanged("sub", setOf(relay)) { _, cmd -> back.add(cmd) }
            assertEquals(listOf(1), (back.single() as ReqCmd).filters.single().kinds)
        }

    @Test
    fun aHeldReqRefreshedAtReleaseMatchesItsEventsToTheNewFilters() =
        runTest {
            // The relay's gate held the REQ (pre-marked with kind 1) while the feed moved to kind 7.
            // Released with the new filters, the relay may answer before onSent runs: its events
            // must be matched against kind 7, never against the kind-1 REQ that never went out.
            val seen = mutableListOf<List<Filter>?>()
            val pool = PoolRequests()
            pool.addOrUpdate(
                "sub",
                mapOf(relay to filter(1)),
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        seen.add(forFilters)
                    }
                },
            )
            val held = sync(pool).single() as ReqCmd
            pool.addOrUpdate("sub", mapOf(relay to filter(7)), null)
            pool.sendToRelayIfChanged("sub", setOf(relay)) { _, cmd -> error("a second REQ while one is in flight: $cmd") }

            val released = pool.refreshHeld(relay, held)
            assertEquals(listOf(7), released.filters.single().kinds)
            pool.onIncomingMessage(FakeRelayClient(relay), EventMessage("sub", Event("e", "p", 0, 7, emptyArray(), "", "s")))

            assertEquals(listOf(7), seen.single()?.single()?.kinds)
        }

    @Test
    fun aClosedThatBeatsTheSendReportIsNotErased() =
        runTest {
            // A fast relay answers before the transport reports the send: the CLOSED is handled
            // first, then onSent. The retry its back-off scheduled must still find it CLOSED.
            val pool = PoolRequests()
            pool.addOrUpdate("sub", mapOf(relay to filter(1)), null)
            val req = sync(pool).single()
            pool.onIncomingMessage(FakeRelayClient(relay), ClosedMessage("sub", "rate-limited: slow down", "500"))
            pool.onSent(relay, req)

            val retried = mutableListOf<Command>()
            assertTrue(pool.retryRateLimited(relay, setOf("sub")) { retried.add(it) })
            assertEquals(1, retried.size)
        }

    @Test
    fun anEoseThatBeatsTheSendReportKeepsTheSubLive() =
        runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("sub", mapOf(relay to filter(1)), null)
            val req = sync(pool).single()
            val socket = FakeRelayClient(relay)
            pool.onIncomingMessage(socket, EoseMessage("sub"))
            pool.onSent(relay, req)

            // Still LIVE, so a filter change goes out at once instead of waiting on an EOSE
            // that already came.
            pool.addOrUpdate("sub", mapOf(relay to filter(7)), null)
            val sent = mutableListOf<Command>()
            pool.sendToRelayIfChanged("sub", setOf(relay)) { _, cmd -> sent.add(cmd) }
            assertEquals(listOf(7), (sent.single() as ReqCmd).filters.single().kinds)
        }

    @Test
    fun withoutABackOffAChangedSubIsStillResentAtOnce() =
        runTest {
            val pool = PoolRequests()
            pool.addOrUpdate("sub", mapOf(relay to filter(1)), null)
            sync(pool)
            pool.addOrUpdate("sub", mapOf(relay to filter(7)), null)
            val socket = FakeRelayClient(relay)
            pool.onIncomingMessage(socket, ClosedMessage("sub", "error: shutting down idle subscription"))
            assertEquals(listOf(7), (socket.sent.single() as ReqCmd).filters.single().kinds)
        }
}
