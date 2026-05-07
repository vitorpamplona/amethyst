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
package com.vitorpamplona.quartz.relay

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.relay.fixtures.SyntheticEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Compatibility suite that drives the in-process relay through the same
 * `NostrClient` + WebSocket abstraction production code uses. These tests
 * are how we validate that:
 *
 *  1. The relay implements NIP-01 correctly (REQ/EVENT/EOSE/CLOSE/COUNT,
 *     replaceable + parameterized-replaceable, filter matching, multi-relay
 *     pools).
 *  2. The bridge between [com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket]
 *     and [com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer] preserves
 *     wire ordering and lifecycle.
 *
 * The same suite should be runnable, conceptually, against any
 * spec-compliant relay (nostr-rs-relay, strfry, khatru, …) — only the
 * `socketBuilder` and the relay URL would change.
 */
class Nip01ComplianceTest {
    private lateinit var hub: RelayHub
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient

    private val relayUrl: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7770/")

    @BeforeTest
    fun setup() {
        hub = RelayHub()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        client = NostrClient(hub, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        hub.close()
    }

    private suspend fun preload(vararg events: Event) {
        hub.getOrCreate(relayUrl).preload(*events)
    }

    private fun fakeEvent(
        idSeed: Int,
        kind: Int = 1,
        pubKey: String = SyntheticEvents.hexId(0),
        createdAt: Long = idSeed.toLong(),
        tags: Array<Array<String>> = emptyArray(),
        content: String = "",
    ) = SyntheticEvents.fakeEvent(idSeed, kind, pubKey, createdAt, content, tags)

    // -- Subscriptions -------------------------------------------------------

    /** REQ returns events matching kinds, then EOSE, in order. */
    @Test
    fun reqByKindReturnsMatchesThenEose() =
        runBlocking {
            preload(
                fakeEvent(1, kind = 1),
                fakeEvent(2, kind = 4),
                fakeEvent(3, kind = 1),
            )

            val (events, eose) = collectUntilEose(Filter(kinds = listOf(1)))

            assertEquals(2, events.size)
            assertEquals(setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(3)), events.map { it.id }.toSet())
            assertTrue(eose, "EOSE should fire after stored events")
        }

    /** REQ honours `limit` — newest first, capped to limit. */
    @Test
    fun reqRespectsLimitAndOrdersNewestFirst() =
        runBlocking {
            preload(
                fakeEvent(1, kind = 1, createdAt = 100),
                fakeEvent(2, kind = 1, createdAt = 200),
                fakeEvent(3, kind = 1, createdAt = 300),
                fakeEvent(4, kind = 1, createdAt = 400),
            )

            val (events, _) = collectUntilEose(Filter(kinds = listOf(1), limit = 2))

            assertEquals(2, events.size)
            assertEquals(400L, events[0].createdAt)
            assertEquals(300L, events[1].createdAt)
        }

    /** REQ with `authors` filters by pubkey. */
    @Test
    fun reqFiltersByAuthors() =
        runBlocking {
            val alice = SyntheticEvents.hexId(101)
            val bob = SyntheticEvents.hexId(102)
            preload(
                fakeEvent(1, kind = 1, pubKey = alice),
                fakeEvent(2, kind = 1, pubKey = bob),
                fakeEvent(3, kind = 1, pubKey = alice),
            )

            val (events, _) = collectUntilEose(Filter(authors = listOf(alice)))

            assertEquals(2, events.size)
            assertTrue(events.all { it.pubKey == alice })
        }

    /** REQ with `ids` returns only the requested events. */
    @Test
    fun reqFiltersByIds() =
        runBlocking {
            preload(fakeEvent(1), fakeEvent(2), fakeEvent(3))

            val (events, _) = collectUntilEose(Filter(ids = listOf(SyntheticEvents.hexId(2))))

            assertEquals(1, events.size)
            assertEquals(SyntheticEvents.hexId(2), events[0].id)
        }

    /** REQ with `since`/`until` filters on createdAt. */
    @Test
    fun reqFiltersBySinceAndUntil() =
        runBlocking {
            preload(
                fakeEvent(1, createdAt = 100),
                fakeEvent(2, createdAt = 200),
                fakeEvent(3, createdAt = 300),
                fakeEvent(4, createdAt = 400),
            )

            val (events, _) = collectUntilEose(Filter(since = 150L, until = 350L))

            assertEquals(setOf(200L, 300L), events.map { it.createdAt }.toSet())
        }

    /** REQ with single-letter `#e` tag filter matches events whose tag values intersect. */
    @Test
    fun reqFiltersByETag() =
        runBlocking {
            val target = SyntheticEvents.hexId(999)
            preload(
                fakeEvent(1, tags = arrayOf(arrayOf("e", target))),
                fakeEvent(2, tags = arrayOf(arrayOf("e", SyntheticEvents.hexId(7)))),
                fakeEvent(3, tags = arrayOf(arrayOf("e", target), arrayOf("p", SyntheticEvents.hexId(8)))),
            )

            val (events, _) = collectUntilEose(Filter(tags = mapOf("e" to listOf(target))))

            assertEquals(setOf(SyntheticEvents.hexId(1), SyntheticEvents.hexId(3)), events.map { it.id }.toSet())
        }

    // -- Replaceable + addressable ------------------------------------------

    /** Kind 0 is replaceable by `(pubkey, kind)` — newer wins. */
    @Test
    fun replaceableEventsKeepNewestPerPubkey() =
        runBlocking {
            val pubkey = SyntheticEvents.hexId(50)
            preload(
                fakeEvent(1, kind = 0, pubKey = pubkey, createdAt = 100, content = "old"),
                fakeEvent(2, kind = 0, pubKey = pubkey, createdAt = 200, content = "new"),
            )

            val (events, _) = collectUntilEose(Filter(kinds = listOf(0), authors = listOf(pubkey)))

            assertEquals(1, events.size)
            assertEquals("new", events[0].content)
        }

    /**
     * Addressable events (kind 30000-39999, NIP-01 §"Kinds") are replaced
     * by `(pubkey, kind, d)`. Uses a real signed [LongTextNoteEvent] because
     * the SQLite store dispatches on the typed `AddressableEvent` subclass
     * to extract the d-tag — synthetic plain `Event`s aren't recognised.
     */
    @Test
    fun parameterizedReplaceableEventsKeepNewestPerDTag() =
        runBlocking {
            val signer = NostrSignerSync(KeyPair())
            val v1 = signer.sign(LongTextNoteEvent.build("old", "title", dTag = "list-a", createdAt = 100))
            val v2 = signer.sign(LongTextNoteEvent.build("new", "title", dTag = "list-a", createdAt = 200))
            val v3 = signer.sign(LongTextNoteEvent.build("list-b", "title", dTag = "list-b", createdAt = 100))
            preload(v1, v2, v3)

            val (events, _) = collectUntilEose(Filter(kinds = listOf(LongTextNoteEvent.KIND), authors = listOf(signer.pubKey)))

            assertEquals(2, events.size)
            assertEquals(setOf("new", "list-b"), events.map { it.content }.toSet())
        }

    // -- Live updates --------------------------------------------------------

    /** A subscription receives new matching events that arrive after EOSE. */
    @Test
    fun liveSubscriptionReceivesPostEoseEvents() =
        runBlocking {
            val ch = Channel<Event>(UNLIMITED)
            val gotEose = Channel<Unit>(UNLIMITED)
            client.subscribe(
                "live-1",
                mapOf(relayUrl to listOf(Filter(kinds = listOf(1)))),
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        ch.trySend(event)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gotEose.trySend(Unit)
                    }
                },
            )

            withTimeout(5000) { gotEose.receive() }

            // Inject an event through the wire path (not preload — that bypasses
            // the live broadcast that subscriptions feed off of).
            hub.getOrCreate(relayUrl).publish(fakeEvent(99, kind = 1, content = "live"))

            val received = withTimeout(5000) { ch.receive() }
            assertEquals("live", received.content)
            client.unsubscribe("live-1")
        }

    /** Non-matching live events are not pushed to a subscription. */
    @Test
    fun liveSubscriptionIgnoresNonMatchingEvents() =
        runBlocking {
            val ch = Channel<Event>(UNLIMITED)
            val gotEose = Channel<Unit>(UNLIMITED)
            client.subscribe(
                "live-2",
                mapOf(relayUrl to listOf(Filter(kinds = listOf(1)))),
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        ch.trySend(event)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        gotEose.trySend(Unit)
                    }
                },
            )
            withTimeout(5000) { gotEose.receive() }

            hub.getOrCreate(relayUrl).publish(fakeEvent(98, kind = 4, content = "off-topic"))

            val seen = withTimeoutOrNull(500) { ch.receive() }
            assertNull(seen, "kind 4 should not match a kind-1 subscription")
            client.unsubscribe("live-2")
        }

    // -- Multi-relay --------------------------------------------------------

    /** A single client can hold subscriptions against multiple relays simultaneously. */
    @Test
    fun multiRelayPoolReturnsContentFromEachRelay() =
        runBlocking {
            val relayA = RelayUrlNormalizer.normalize("ws://127.0.0.1:7771/")
            val relayB = RelayUrlNormalizer.normalize("ws://127.0.0.1:7772/")
            hub.getOrCreate(relayA).preload(fakeEvent(1, kind = 1, content = "from-a"))
            hub.getOrCreate(relayB).preload(fakeEvent(2, kind = 1, content = "from-b"))

            val received = mutableMapOf<NormalizedRelayUrl, String>()
            val eosed = mutableSetOf<NormalizedRelayUrl>()
            val ch = Channel<Unit>(UNLIMITED)
            client.subscribe(
                "multi-1",
                mapOf(
                    relayA to listOf(Filter(kinds = listOf(1))),
                    relayB to listOf(Filter(kinds = listOf(1))),
                ),
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        received[relay] = event.content
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        eosed += relay
                        if (eosed.size == 2) ch.trySend(Unit)
                    }
                },
            )

            withTimeout(5000) { ch.receive() }
            client.unsubscribe("multi-1")

            assertEquals("from-a", received[relayA])
            assertEquals("from-b", received[relayB])
        }

    // -- Helpers ------------------------------------------------------------

    /** Subscribes synchronously and returns the events received before EOSE. */
    private suspend fun collectUntilEose(filter: Filter): Pair<List<Event>, Boolean> {
        val ch = Channel<Either>(UNLIMITED)
        val subId = "sub-${System.nanoTime()}"
        client.subscribe(
            subId,
            mapOf(relayUrl to listOf(filter)),
            object : SubscriptionListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    ch.trySend(Either.Ev(event))
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    ch.trySend(Either.Eose)
                }
            },
        )

        val events = mutableListOf<Event>()
        var eose = false
        withTimeout(5000) {
            while (!eose) {
                when (val msg = ch.receive()) {
                    is Either.Ev -> events += msg.event
                    Either.Eose -> eose = true
                }
            }
        }
        client.unsubscribe(subId)
        return events to eose
    }

    private sealed interface Either {
        data class Ev(
            val event: Event,
        ) : Either

        object Eose : Either
    }
}
