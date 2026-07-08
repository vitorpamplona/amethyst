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
package com.vitorpamplona.quartz.experimental.graperank.sync2

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.FtsReindexProgress
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalGraphBFSTest {
    // A dummy signature — the BFS never verifies, it only reads tags + created_at.
    private val sig = "0".repeat(128)

    /** A distinct valid 64-char hex pubkey/id for a small integer, e.g. pk(1). */
    private fun pk(n: Int): HexKey = n.toString(16).padStart(64, '0')

    private fun contactList(
        author: Int,
        follows: List<Int>,
        createdAt: Long,
    ): ContactListEvent {
        val tags = follows.map { arrayOf("p", pk(it)) }.toTypedArray()
        return ContactListEvent(pk(9000 + author), pk(author), createdAt, tags, "", sig)
    }

    private fun relayList(
        author: Int,
        writeRelays: List<String>,
        createdAt: Long,
    ): AdvertisedRelayListEvent {
        val tags = writeRelays.map { arrayOf("r", it, "write") }.toTypedArray()
        return AdvertisedRelayListEvent(pk(8000 + author), pk(author), createdAt, tags, "", sig)
    }

    private suspend fun bootstrap(
        observer: Int,
        events: List<Event>,
    ): LocalGraph = LocalGraphBFS(ListStore(events)).traverse(pk(observer))

    @Test
    fun emptyStoreYieldsObserverAtHopZero() =
        runTest {
            val state = bootstrap(observer = 1, events = emptyList())
            assertEquals(mapOf(pk(1) to 0), state.hopOf)
            assertEquals(1, state.discoveredUsers)
            assertNull(state.watermarkFor(pk(1)))
            assertTrue(state.knownOutbox.isEmpty())
        }

    @Test
    fun bfsStampsTransitiveHopDistances() =
        runTest {
            // 1 -> {2,3}, 2 -> {4}, 4 -> {5}; 3 has no stored list (leaf at hop 1).
            val state =
                bootstrap(
                    observer = 1,
                    events =
                        listOf(
                            contactList(1, listOf(2, 3), createdAt = 100),
                            contactList(2, listOf(4), createdAt = 100),
                            contactList(4, listOf(5), createdAt = 100),
                        ),
                )
            assertEquals(0, state.hopOf[pk(1)])
            assertEquals(1, state.hopOf[pk(2)])
            assertEquals(1, state.hopOf[pk(3)])
            assertEquals(2, state.hopOf[pk(4)])
            assertEquals(3, state.hopOf[pk(5)])
            assertEquals(mapOf(0 to 1, 1 to 2, 2 to 1, 3 to 1), state.hopHistogram())
        }

    @Test
    fun bfsTakesShortestPathWhenAUserIsReachableTwoWays() =
        runTest {
            // 1 -> {2,3}, 2 -> {3}. User 3 is a direct follow (hop 1) AND 2's follow
            // (hop 2); BFS must keep the shorter hop 1.
            val state =
                bootstrap(
                    observer = 1,
                    events =
                        listOf(
                            contactList(1, listOf(2, 3), createdAt = 100),
                            contactList(2, listOf(3), createdAt = 100),
                        ),
                )
            assertEquals(1, state.hopOf[pk(3)])
        }

    @Test
    fun contactListSeedsWatermarkAndSinceFloor() =
        runTest {
            // The store holds one (replaceable) kind:3 per author; its created_at is
            // the watermark, and the since floor is created_at + 1.
            val state =
                bootstrap(
                    observer = 1,
                    events = listOf(contactList(1, listOf(2), createdAt = 200)),
                )
            assertEquals(200, state.watermarkFor(pk(1))?.newest(ContactListEvent.KIND))
            assertEquals(201, state.watermarkFor(pk(1))?.sinceFor(ContactListEvent.KIND))
        }

    @Test
    fun relayListSeedsWatermarkAndKnownOutbox() =
        runTest {
            val state =
                bootstrap(
                    observer = 1,
                    events =
                        listOf(
                            contactList(1, listOf(2), createdAt = 100),
                            relayList(2, listOf("wss://alice.example"), createdAt = 150),
                        ),
                )
            assertEquals(150, state.watermarkFor(pk(2))?.newest(AdvertisedRelayListEvent.KIND))
            val outbox = state.outboxOf(pk(2)).map { it.url }
            assertEquals(1, outbox.size)
            assertTrue(outbox.first().contains("alice.example"), "got $outbox")
        }

    /**
     * A read-only [IEventStore] over an in-memory list, matching via the real
     * [Filter.match] so kind/author/since semantics are exercised for real. Only
     * the query paths bootstrap uses are implemented; the write side errors.
     */
    private class ListStore(
        private val events: List<Event>,
    ) : IEventStore {
        override val relay: NormalizedRelayUrl? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Event> query(filter: Filter): List<T> = events.filter(filter::match) as List<T>

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Event> query(filters: List<Filter>): List<T> = events.filter { e -> filters.any { it.match(e) } } as List<T>

        override suspend fun <T : Event> query(
            filter: Filter,
            onEach: (T) -> Unit,
        ) = query<T>(filter).forEach(onEach)

        override suspend fun <T : Event> query(
            filters: List<Filter>,
            onEach: (T) -> Unit,
        ) = query<T>(filters).forEach(onEach)

        override suspend fun count(filter: Filter): Int = query<Event>(filter).size

        override suspend fun count(filters: List<Filter>): Int = query<Event>(filters).size

        override suspend fun insert(event: Event) = error("read-only store")

        override suspend fun transaction(body: IEventStore.ITransaction.() -> Unit) = error("read-only store")

        override suspend fun delete(filter: Filter) = error("read-only store")

        override suspend fun delete(filters: List<Filter>) = error("read-only store")

        override suspend fun deleteExpiredEvents() = Unit

        override suspend fun reindexFullTextSearch() = Unit

        override suspend fun reindexFullTextSearch(
            resumeFrom: String?,
            batchSize: Int,
        ): FtsReindexProgress = error("not supported")

        override fun close() = Unit
    }
}
