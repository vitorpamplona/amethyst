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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adapter between Amethyst's relay client and what ContextVM asks of one.
 *
 * Small enough to look obviously right and wrong in three ways that a test is
 * the only thing that catches, because each one fails as silence rather than
 * as an error: a REQ sent to the account's relays instead of the
 * coordinator's, a response dropped because it arrived as a stored event, and
 * a subscription left open after the call that made it returned.
 */
class NostrClientCvmRelayPoolTest {
    private val coordinatorRelay = relay("wss://coordinator.example")
    private val otherRelay = relay("wss://elsewhere.example")

    @Test
    fun `subscribes only to the relays it was given`() {
        val client = RecordingClient()
        val pool = NostrClientCvmRelayPool(client, setOf(coordinatorRelay))

        pool.subscribe(SERVER, intArrayOf(CvmKinds.MESSAGE)) { }

        // A coordinator has no address beyond its pubkey (spec/00.md §8.5), so
        // the relays whoever published it named are the only place its traffic
        // exists. Widening this to the account's own relays would miss the
        // coordinator AND tell relays with no part in the conversation that
        // this account talks to it.
        assertEquals(
            setOf(coordinatorRelay),
            client.subscribed
                .single()
                .filters.keys,
        )
    }

    @Test
    fun `filters on kind and on the recipient p-tag`() {
        val client = RecordingClient()
        val pool = NostrClientCvmRelayPool(client, setOf(coordinatorRelay, otherRelay))

        pool.subscribe(SERVER, intArrayOf(CvmKinds.MESSAGE, CvmKinds.GIFT_WRAP)) { }

        val filter =
            client.subscribed
                .single()
                .filters
                .getValue(coordinatorRelay)
                .single()
        assertEquals(listOf(CvmKinds.MESSAGE, CvmKinds.GIFT_WRAP), filter.kinds)
        // Addressed-to, not authored-by: a gift-wrapped response is signed by a
        // one-time key, so `authors` would match nothing.
        assertEquals(mapOf("p" to listOf(SERVER)), filter.tags)
        assertNull(filter.authors)
    }

    @Test
    fun `delivers a stored event as readily as a live one`() =
        runTest {
            val client = RecordingClient()
            val pool = NostrClientCvmRelayPool(client, setOf(coordinatorRelay))

            val seen = mutableListOf<Event>()
            pool.subscribe(SERVER, intArrayOf(CvmKinds.MESSAGE)) { seen += it }

            // Kind 25910 is ephemeral, so "stored" is not a state it can be
            // in — but a relay that replays its own send buffer reports
            // isLive=false anyway. Branching on it would drop the one response
            // the call is waiting for and the call would time out instead.
            client.subscribed
                .single()
                .listener
                .onEvent(response(), isLive = false, coordinatorRelay, null)

            assertEquals(1, seen.size)
        }

    @Test
    fun `closing a subscription unsubscribes that id and no other`() {
        val client = RecordingClient()
        val pool = NostrClientCvmRelayPool(client, setOf(coordinatorRelay))

        val first = pool.subscribe(SERVER, intArrayOf(CvmKinds.MESSAGE)) { }
        pool.subscribe(SERVER, intArrayOf(CvmKinds.MESSAGE)) { }

        val ids = client.subscribed.map { it.subId }
        assertEquals("two subscriptions must not share an id", 2, ids.toSet().size)

        first.close()

        // CvmTransport opens one of these per request and closes it in the same
        // call. Unsubscribing the wrong id would leave a REQ open on the
        // coordinator's relay for the life of the process and silently break
        // the next response that a shared id was still listening for.
        assertEquals(listOf(ids.first()), client.unsubscribed)
    }

    @Test
    fun `publishes to the coordinator relays`() =
        runTest {
            val client = RecordingClient()
            val pool = NostrClientCvmRelayPool(client, setOf(coordinatorRelay, otherRelay))

            pool.publish(response())

            val (event, relays) = client.published.single()
            assertEquals(setOf(coordinatorRelay, otherRelay), relays)
            assertTrue(event.kind == CvmKinds.MESSAGE)
        }

    private fun relay(url: String) = RelayUrlNormalizer.normalizeOrNull(url)!!

    private fun response() =
        Event(
            id = "aa".repeat(32),
            pubKey = SERVER,
            createdAt = 1,
            kind = CvmKinds.MESSAGE,
            tags = arrayOf(arrayOf("p", SERVER)),
            content = "",
            sig = "bb".repeat(32),
        )

    /** Records what the pool asked of the client, and nothing else. */
    private class RecordingClient : INostrClient by EmptyNostrClient() {
        class Req(
            val subId: String,
            val filters: Map<NormalizedRelayUrl, List<Filter>>,
            val listener: SubscriptionListener,
        )

        val subscribed = mutableListOf<Req>()
        val unsubscribed = mutableListOf<String>()
        val published = mutableListOf<Pair<Event, Set<NormalizedRelayUrl>>>()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            subscribed += Req(subId, filters, listener!!)
        }

        override fun unsubscribe(subId: String) {
            unsubscribed += subId
        }

        override fun publish(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            published += event to relayList
        }
    }

    companion object {
        private val SERVER = "cc".repeat(32)
    }
}
