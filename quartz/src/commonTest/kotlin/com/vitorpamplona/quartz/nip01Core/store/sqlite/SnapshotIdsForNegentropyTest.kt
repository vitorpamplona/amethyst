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
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.FtsReindexProgress
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the NIP-77 negentropy id-and-time projection against the
 * full-event query path. Goal: same result set, ~25× lighter
 * footprint per row. Run across every indexing strategy via
 * [BaseDBTest.forEachDB] so plan changes don't silently break the
 * snapshot path.
 */
class SnapshotIdsForNegentropyTest : BaseDBTest() {
    private val signer = NostrSignerSync()

    private fun makeEvents(count: Int) =
        List(count) { i ->
            signer.sign(TextNoteEvent.build("event-$i", createdAt = 1_700_000_000L + i))
        }

    @Test
    fun matchesFullQueryForSimpleKindFilter() =
        forEachDB { db ->
            val events = makeEvents(50)
            for (e in events) db.insert(e)

            val filter = Filter(kinds = listOf(1))
            val full = db.query<com.vitorpamplona.quartz.nip01Core.core.Event>(filter)
            val ids = db.snapshotIdsForNegentropy(listOf(filter))

            assertEquals(full.size, ids.size, "snapshot must cover the same row set")
            assertEquals(
                full.map { it.id }.toSet(),
                ids.map { it.id }.toSet(),
                "snapshot ids must match the full-query ids",
            )
            // Every (createdAt, id) pair must round-trip.
            val byId = full.associate { it.id to it.createdAt }
            for (entry in ids) {
                assertEquals(byId[entry.id], entry.createdAt, "createdAt mismatch for ${entry.id}")
            }
        }

    @Test
    fun honorsSinceUntilLimit() =
        forEachDB { db ->
            val events = makeEvents(20) // createdAt 1_700_000_000..1_700_000_019
            for (e in events) db.insert(e)

            // since/until window: [+5, +14] inclusive
            val filter =
                Filter(
                    kinds = listOf(1),
                    since = 1_700_000_005L,
                    until = 1_700_000_014L,
                )
            val ids = db.snapshotIdsForNegentropy(listOf(filter))
            assertEquals(10, ids.size, "since/until window should yield 10 rows")
        }

    @Test
    fun maxEntriesPlusOneSentinelMarksOverflow() =
        forEachDB { db ->
            val events = makeEvents(30)
            for (e in events) db.insert(e)

            val filter = Filter(kinds = listOf(1))
            // cap = 10; we have 30 rows, so the result must be 11
            // (cap + 1 sentinel) — matches strfry's `maxSyncEvents`
            // overflow-detection idiom.
            // cap=10 with 30 rows → result must be the +1 sentinel
            // (11 rows). Caller compares `size > cap` to detect
            // overflow — matches strfry's `maxSyncEvents` idiom.
            val capped = db.snapshotIdsForNegentropy(listOf(filter), maxEntries = 10)
            assertEquals(11, capped.size)

            // cap >= total: returns the whole set unchanged.
            val whole = db.snapshotIdsForNegentropy(listOf(filter), maxEntries = 100)
            assertEquals(30, whole.size)
        }

    @Test
    fun reportsProgressWhileCollecting() =
        runBlocking {
            // Single store (not forEachDB): this exercises the row-loop
            // cadence, which is indexing-strategy independent, and needs
            // enough rows to cross the reporting interval twice.
            val db = EventStore(dbName = null)
            try {
                val total = IEventStore.NEGENTROPY_PROGRESS_EVERY * 2 + 500
                db.batchInsert(
                    List(total) { i ->
                        Event(
                            id = i.toString(16).padStart(64, '0'),
                            pubKey = PUBKEY,
                            createdAt = 1_700_000_000L + i,
                            kind = 1,
                            tags = emptyArray(),
                            content = "e$i",
                            sig = SIG,
                        )
                    },
                )

                val ticks = mutableListOf<Int>()
                val all =
                    db.snapshotIdsForNegentropy(
                        listOf(Filter(kinds = listOf(1))),
                        onProgress = { ticks.add(it) },
                    )
                assertEquals(total, all.size)
                assertEquals(
                    listOf(IEventStore.NEGENTROPY_PROGRESS_EVERY, IEventStore.NEGENTROPY_PROGRESS_EVERY * 2),
                    ticks,
                    "onProgress must tick the running count once per interval",
                )

                // Omitting the callback stays the zero-cost path.
                assertEquals(total, db.snapshotIdsForNegentropy(listOf(Filter(kinds = listOf(1)))).size)
            } finally {
                db.close()
            }
        }

    @Test
    fun interfaceDefaultReportsProgressForStoresWithoutAnOverride() =
        runBlocking {
            // A store that only streams events, so snapshotIdsForNegentropy
            // resolves to the IEventStore default implementation.
            val total = IEventStore.NEGENTROPY_PROGRESS_EVERY + 500
            val store = StreamOnlyStore(total)

            val ticks = mutableListOf<Int>()
            val all = store.snapshotIdsForNegentropy(listOf(Filter()), onProgress = { ticks.add(it) })
            assertEquals(total, all.size)
            assertEquals(listOf(IEventStore.NEGENTROPY_PROGRESS_EVERY), ticks)

            // maxEntries truncation still applies on the default path.
            val capped = store.snapshotIdsForNegentropy(listOf(Filter()), maxEntries = 10)
            assertEquals(11, capped.size)
        }

    private companion object {
        const val PUBKEY = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
        const val SIG = "4aa5264965018fa12a326686ad3d3bd8beae3218dcc83689b19ca1e6baeb791531943c15363aa6707c7c0c8b2d601deca1f20c32078b2872d356cdca03b04cce"
    }

    /** Minimal [IEventStore]: streams [total] synthetic events, nothing else. */
    private class StreamOnlyStore(
        private val total: Int,
    ) : IEventStore {
        override val relay = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Event> query(
            filters: List<Filter>,
            onEach: (T) -> Unit,
        ) {
            repeat(total) { i ->
                onEach(Event(i.toString(16).padStart(64, '0'), PUBKEY, 1_700_000_000L + i, 1, emptyArray(), "", SIG) as T)
            }
        }

        override suspend fun insert(event: Event) = error("unused")

        override suspend fun transaction(body: IEventStore.ITransaction.() -> Unit) = error("unused")

        override suspend fun <T : Event> query(filter: Filter): List<T> = error("unused")

        override suspend fun <T : Event> query(filters: List<Filter>): List<T> = error("unused")

        override suspend fun <T : Event> query(
            filter: Filter,
            onEach: (T) -> Unit,
        ) = error("unused")

        override suspend fun count(filter: Filter): Int = error("unused")

        override suspend fun count(filters: List<Filter>): Int = error("unused")

        override suspend fun delete(filter: Filter) = error("unused")

        override suspend fun delete(filters: List<Filter>) = error("unused")

        override suspend fun deleteExpiredEvents() = error("unused")

        override suspend fun reindexFullTextSearch() = error("unused")

        override suspend fun reindexFullTextSearch(
            resumeFrom: String?,
            batchSize: Int,
        ): FtsReindexProgress = error("unused")

        override fun close() {}
    }
}
