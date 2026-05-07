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

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            val capped = db.snapshotIdsForNegentropy(listOf(filter), maxEntries = 10)
            assertEquals(11, capped.size)
            assertTrue(capped.size > 10, "caller relies on size > cap as overflow signal")

            // cap >= total: returns the whole set unchanged.
            val whole = db.snapshotIdsForNegentropy(listOf(filter), maxEntries = 100)
            assertEquals(30, whole.size)
        }
}
