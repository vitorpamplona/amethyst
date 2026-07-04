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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live negentropy index's one correctness rule: at every point, a
 * snapshot must advertise **exactly** the `(created_at, id)` set a fresh
 * scan of the store would — never a deleted/displaced id (peers would
 * fetch dead events), never missing a committed one. Every mutation
 * pattern the write path knows is run against a store with the index on
 * and compared to `snapshotIdsForNegentropy`.
 */
class LiveNegentropyIndexStoreTest {
    private val strategy = DefaultIndexingStrategy(maintainLiveNegentropyIndex = true)

    private fun hexId(seed: Int): String = seed.toString().padStart(64, '0')

    private fun pubkey(seed: Int): String = seed.toString().padStart(64, 'a')

    private val sig = "0".repeat(128)

    private fun event(
        idSeed: Int,
        kind: Int = 1,
        createdAt: Long = idSeed.toLong(),
        pubKey: String = pubkey(1),
        tags: Array<Array<String>> = emptyArray(),
        content: String = "",
    ): Event = EventFactory.create(hexId(idSeed), pubKey, createdAt, kind, tags, content, sig)

    private fun newStore() = EventStore(dbName = null, indexStrategy = strategy)

    /** Index content and scan content must be identical, entry for entry. */
    private suspend fun assertIndexMatchesScan(store: EventStore) {
        val snapshot = assertNotNull(store.liveNegentropySnapshot(Int.MAX_VALUE), "index should serve a snapshot")
        val fromIndex = snapshot.map { IdAndTime(it.timestamp, it.id.toHexString()) }
        val fromScan =
            store
                .snapshotIdsForNegentropy(listOf(Filter()), null)
                .sortedWith(compareBy({ it.createdAt }, { it.id }))
        assertEquals(fromScan, fromIndex)
    }

    @Test
    fun populatesLazilyAndTracksPlainInserts() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            store.insert(event(2))

            // First snapshot rebuilds from a scan…
            assertIndexMatchesScan(store)

            // …and later inserts are tracked incrementally.
            store.insert(event(3))
            store.batchInsert(listOf(event(4), event(5)))
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun replaceableOverwriteDropsTheDisplacedId() =
        runTest {
            val store = newStore()
            store.insert(event(1, kind = 0, createdAt = 100, pubKey = pubkey(7)))
            assertIndexMatchesScan(store)

            // Newer kind-0 from the same author displaces the old row.
            store.insert(event(2, kind = 0, createdAt = 200, pubKey = pubkey(7)))
            assertIndexMatchesScan(store)
            assertEquals(1, store.count(Filter(kinds = listOf(0))))

            // An older one loses to the stored row and must not disturb
            // the index (the store rejects it).
            runCatching { store.insert(event(3, kind = 0, createdAt = 50, pubKey = pubkey(7))) }
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun addressableOverwriteDropsOnlyTheSameDTag() =
        runTest {
            val store = newStore()
            val author = pubkey(9)
            store.insert(event(1, kind = 30023, createdAt = 100, pubKey = author, tags = arrayOf(arrayOf("d", "post-a"))))
            store.insert(event(2, kind = 30023, createdAt = 100, pubKey = author, tags = arrayOf(arrayOf("d", "post-b"))))
            assertIndexMatchesScan(store)

            // Overwrites post-a; post-b must survive.
            store.insert(event(3, kind = 30023, createdAt = 200, pubKey = author, tags = arrayOf(arrayOf("d", "post-a"))))
            assertIndexMatchesScan(store)
            assertEquals(2, store.count(Filter(kinds = listOf(30023))))

            store.close()
        }

    @Test
    fun deletionEventInvalidatesAndRebuildMatches() =
        runTest {
            val store = newStore()
            val author = pubkey(3)
            val target = event(1, createdAt = 100, pubKey = author)
            store.insert(target)
            store.insert(event(2, createdAt = 110, pubKey = author))
            assertIndexMatchesScan(store)

            // Kind-5 removes the target AND stores itself; the index
            // can't itemize that, so it must rebuild — and match.
            store.insert(
                event(4, kind = 5, createdAt = 120, pubKey = author, tags = arrayOf(arrayOf("e", target.id))),
            )
            assertIndexMatchesScan(store)
            assertTrue(store.query<Event>(Filter(ids = listOf(target.id))).isEmpty())

            store.close()
        }

    @Test
    fun deleteByFilterInvalidatesAndRebuildMatches() =
        runTest {
            val store = newStore()
            store.insert(event(1, kind = 1))
            store.insert(event(2, kind = 7))
            assertIndexMatchesScan(store)

            store.delete(Filter(kinds = listOf(7)))
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun rejectedRowsInABatchNeverEnterTheIndex() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            assertIndexMatchesScan(store)

            // Duplicate id (rejected) mixed with a fresh row (accepted).
            val outcomes = store.batchInsert(listOf(event(1), event(2)))
            assertTrue(outcomes[0] is IEventStore.InsertOutcome.Rejected)
            assertEquals(IEventStore.InsertOutcome.Accepted, outcomes[1])
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun transactionInsertsLandAfterCommit() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            assertIndexMatchesScan(store)

            store.transaction {
                insert(event(2))
                insert(event(3))
            }
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun sameBatchReplaceableOverwriteDropsTheDisplacedId() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            // Populate the index first so the batch runs the DELTA path
            // (an unpopulated index would be covered by the rebuild scan
            // and could never expose this).
            assertIndexMatchesScan(store)

            // Three versions of one replaceable in ONE batch: each later
            // row displaces the previous one INSIDE the same transaction.
            // The displaced rows were never in the index, so their
            // pending adds must be cancelled — a remove would no-op and
            // leave the index advertising dead ids.
            val outcomes =
                store.batchInsert(
                    listOf(
                        event(2, kind = 0, createdAt = 100, pubKey = pubkey(7)),
                        event(3, kind = 0, createdAt = 200, pubKey = pubkey(7)),
                        event(4, kind = 0, createdAt = 300, pubKey = pubkey(7)),
                    ),
                )
            assertTrue(outcomes.all { it == IEventStore.InsertOutcome.Accepted })
            assertEquals(1, store.count(Filter(kinds = listOf(0))))
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun sameTransactionReplaceableOverwriteDropsTheDisplacedId() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            assertIndexMatchesScan(store)

            store.transaction {
                insert(event(2, kind = 10002, createdAt = 100, pubKey = pubkey(8)))
                insert(event(3, kind = 10002, createdAt = 200, pubKey = pubkey(8)))
            }
            assertEquals(1, store.count(Filter(kinds = listOf(10002))))
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun sameBatchAddressableOverwriteDropsTheDisplacedId() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            assertIndexMatchesScan(store)

            val author = pubkey(9)
            store.batchInsert(
                listOf(
                    event(2, kind = 30023, createdAt = 100, pubKey = author, tags = arrayOf(arrayOf("d", "post-a"))),
                    event(3, kind = 30023, createdAt = 200, pubKey = author, tags = arrayOf(arrayOf("d", "post-a"))),
                ),
            )
            assertEquals(1, store.count(Filter(kinds = listOf(30023))))
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun noOpDeletionKeepsTheIndexLive() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            assertIndexMatchesScan(store)

            // A kind-5 referencing an id this store never had — the
            // common case for broadcast deletes — removes nothing, so it
            // must NOT invalidate the index (that would be a free way to
            // force full-scan rebuilds); it lands as a plain row.
            store.insert(
                event(2, kind = 5, createdAt = 120, tags = arrayOf(arrayOf("e", hexId(999)))),
            )
            val index = assertNotNull(store.store.liveNegentropyIndex)
            assertTrue(index.isPopulated(), "no-op kind-5 must not invalidate the live index")
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun effectiveDeletionStillInvalidates() =
        runTest {
            val store = newStore()
            val author = pubkey(3)
            val target = event(1, createdAt = 100, pubKey = author)
            store.insert(target)
            assertIndexMatchesScan(store)

            store.insert(
                event(2, kind = 5, createdAt = 120, pubKey = author, tags = arrayOf(arrayOf("e", target.id))),
            )
            val index = assertNotNull(store.store.liveNegentropyIndex)
            assertTrue(!index.isPopulated(), "a kind-5 that deleted rows must invalidate")
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun overCapCorpusNeverBuildsTheIndex() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            store.insert(event(2))
            store.insert(event(3))

            // First-ever NEG-OPEN over a corpus above the serve cap: the
            // rebuild scan is capped, the index stays unpopulated (no
            // heap, no write-path deltas), and the caller falls back.
            assertNull(store.liveNegentropySnapshot(2))
            val index = assertNotNull(store.store.liveNegentropyIndex)
            assertTrue(!index.isPopulated(), "an over-cap corpus must not build the index")

            // A cap the corpus fits builds and serves as usual.
            assertNotNull(store.liveNegentropySnapshot(3))
            assertTrue(index.isPopulated())
            assertIndexMatchesScan(store)

            store.close()
        }

    @Test
    fun overCapFallsBackToNull() =
        runTest {
            val store = newStore()
            store.insert(event(1))
            store.insert(event(2))
            store.insert(event(3))
            assertIndexMatchesScan(store)

            assertNull(store.liveNegentropySnapshot(2))
            assertNotNull(store.liveNegentropySnapshot(3))

            store.close()
        }

    @Test
    fun defaultStrategyKeepsNoIndex() =
        runTest {
            val store = EventStore(dbName = null)
            store.insert(event(1))
            assertNull(store.liveNegentropySnapshot(Int.MAX_VALUE))
            store.close()
        }
}
