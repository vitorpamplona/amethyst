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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryEventStoreTest {
    private val pubkey1 = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val pubkey2 = "22aa81510ee63fe2b16cae16e0921f78e9ba9882e2868e7e63ad6d08ae9b5954"
    private val sig = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"

    private fun event(
        id: String,
        pubKey: String = pubkey1,
        createdAt: Long = 1000L,
        kind: Int = 1,
        tags: Array<Array<String>> = emptyArray(),
        content: String = "hello",
    ) = Event(id, pubKey, createdAt, kind, tags, content, sig)

    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    @Test
    fun storeAndQueryRegularEvent() =
        runTest {
            val store = InMemoryEventStore()
            val ev = event(hexId(1))

            assertTrue(store.store(ev))

            val results = store.query(Filter(ids = listOf(hexId(1))))
            assertEquals(1, results.size)
            assertEquals(ev.id, results[0].id)
        }

    @Test
    fun rejectsDuplicateEvent() =
        runTest {
            val store = InMemoryEventStore()
            val ev = event(hexId(1))

            assertTrue(store.store(ev))
            assertFalse(store.store(ev))
        }

    @Test
    fun replaceableEventKeepsNewest() =
        runTest {
            val store = InMemoryEventStore()
            // Kind 0 (metadata) is replaceable
            val older = event(hexId(1), kind = 0, createdAt = 100L)
            val newer = event(hexId(2), kind = 0, createdAt = 200L)

            assertTrue(store.store(older))
            assertTrue(store.store(newer))

            val results = store.query(Filter(kinds = listOf(0), authors = listOf(pubkey1)))
            assertEquals(1, results.size)
            assertEquals(newer.id, results[0].id)
        }

    @Test
    fun replaceableEventRejectsOlderVersion() =
        runTest {
            val store = InMemoryEventStore()
            val newer = event(hexId(1), kind = 0, createdAt = 200L)
            val older = event(hexId(2), kind = 0, createdAt = 100L)

            assertTrue(store.store(newer))
            assertFalse(store.store(older))

            val results = store.query(Filter(kinds = listOf(0)))
            assertEquals(1, results.size)
            assertEquals(newer.id, results[0].id)
        }

    @Test
    fun addressableEventKeepsNewestPerDTag() =
        runTest {
            val store = InMemoryEventStore()
            // Kind 30000 is addressable
            val older =
                event(
                    hexId(1),
                    kind = 30000,
                    createdAt = 100L,
                    tags = arrayOf(arrayOf("d", "mylist")),
                )
            val newer =
                event(
                    hexId(2),
                    kind = 30000,
                    createdAt = 200L,
                    tags = arrayOf(arrayOf("d", "mylist")),
                )
            val different =
                event(
                    hexId(3),
                    kind = 30000,
                    createdAt = 150L,
                    tags = arrayOf(arrayOf("d", "otherlist")),
                )

            assertTrue(store.store(older))
            assertTrue(store.store(newer))
            assertTrue(store.store(different))

            val results = store.query(Filter(kinds = listOf(30000)))
            assertEquals(2, results.size)
        }

    @Test
    fun ephemeralEventNotStored() =
        runTest {
            val store = InMemoryEventStore()
            // Kind 20000 is ephemeral
            val ev = event(hexId(1), kind = 20000)

            assertTrue(store.store(ev))

            val results = store.query(Filter(kinds = listOf(20000)))
            assertEquals(0, results.size)
        }

    @Test
    fun ephemeralEventEmittedOnNewEvents() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryEventStore()
            val ev = event(hexId(1), kind = 20000)

            val received =
                async {
                    store.newEvents.first()
                }

            store.store(ev)

            assertEquals(ev.id, received.await().id)
        }

    @Test
    fun queryWithKindFilter() =
        runTest {
            val store = InMemoryEventStore()
            store.store(event(hexId(1), kind = 1))
            store.store(event(hexId(2), kind = 4))
            store.store(event(hexId(3), kind = 1))

            val results = store.query(Filter(kinds = listOf(1)))
            assertEquals(2, results.size)
        }

    @Test
    fun queryWithAuthorFilter() =
        runTest {
            val store = InMemoryEventStore()
            store.store(event(hexId(1), pubKey = pubkey1))
            store.store(event(hexId(2), pubKey = pubkey2))

            val results = store.query(Filter(authors = listOf(pubkey1)))
            assertEquals(1, results.size)
            assertEquals(pubkey1, results[0].pubKey)
        }

    @Test
    fun queryWithLimit() =
        runTest {
            val store = InMemoryEventStore()
            for (i in 1..10) {
                store.store(event(hexId(i), createdAt = i.toLong()))
            }

            val results = store.query(Filter(limit = 3))
            assertEquals(3, results.size)
            // Newest first
            assertTrue(results[0].createdAt >= results[1].createdAt)
            assertTrue(results[1].createdAt >= results[2].createdAt)
        }

    @Test
    fun queryWithSinceAndUntil() =
        runTest {
            val store = InMemoryEventStore()
            store.store(event(hexId(1), createdAt = 100L))
            store.store(event(hexId(2), createdAt = 200L))
            store.store(event(hexId(3), createdAt = 300L))

            val results = store.query(Filter(since = 150L, until = 250L))
            assertEquals(1, results.size)
            assertEquals(hexId(2), results[0].id)
        }

    @Test
    fun countMatchingEvents() =
        runTest {
            val store = InMemoryEventStore()
            store.store(event(hexId(1), kind = 1))
            store.store(event(hexId(2), kind = 1))
            store.store(event(hexId(3), kind = 4))

            assertEquals(2, store.count(Filter(kinds = listOf(1))))
            assertEquals(1, store.count(Filter(kinds = listOf(4))))
            assertEquals(0, store.count(Filter(kinds = listOf(99))))
        }

    @Test
    fun queryWithTagFilter() =
        runTest {
            val store = InMemoryEventStore()
            store.store(
                event(
                    hexId(1),
                    tags = arrayOf(arrayOf("t", "nostr")),
                ),
            )
            store.store(
                event(
                    hexId(2),
                    tags = arrayOf(arrayOf("t", "bitcoin")),
                ),
            )

            val results = store.query(Filter(tags = mapOf("t" to listOf("nostr"))))
            assertEquals(1, results.size)
            assertEquals(hexId(1), results[0].id)
        }

    @Test
    fun newEventsFlowEmitsOnStore() =
        runTest(UnconfinedTestDispatcher()) {
            val store = InMemoryEventStore()
            val ev = event(hexId(1))

            val received =
                async {
                    store.newEvents.first()
                }

            store.store(ev)

            assertEquals(ev.id, received.await().id)
        }
}
