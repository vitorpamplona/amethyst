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
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Correctness guard for the tag-stream path of [MergeQueryExecutor]: the
 * `#<x>=[values] (+ kinds) [+ since/until] limit=N` watcher shape
 * (reactions/replies). The merge must return the same newest-N a
 * `SELECT DISTINCT … ORDER BY created_at DESC LIMIT N` would, deduping events
 * that carry several of the queried tag values.
 *
 * Where `created_at` is distinct the order is fully determined and asserted
 * against a Kotlin reference. Where it ties, the tag cursors can only order by
 * `created_at` (event_tags has no id column), so the result is a valid
 * newest-N but not id-exact — those cases assert set + size instead.
 */
class TagMergeCorrectnessTest {
    private val hex = "0123456789abcdef"

    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun hex64(
        salt: Long,
        index: Int,
    ): String {
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(salt * 1_000_003 + index.toLong() * 4 + w)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                out[(w * 8 + b) * 2] = hex[byte ushr 4]
                out[(w * 8 + b) * 2 + 1] = hex[byte and 0xF]
            }
        }
        return out.concatToString()
    }

    private val sig = "0".repeat(128)
    private var idSeq = 0

    private fun ev(
        createdAt: Long,
        kind: Int,
        eTags: List<String>,
    ): Event =
        EventFactory.create(
            hex64(7, idSeq++),
            hex64(1, idSeq),
            createdAt,
            kind,
            eTags.map { arrayOf("e", it) }.toTypedArray(),
            "",
            sig,
        )

    private val newestFirst =
        Comparator<Event> { a, b ->
            if (a.createdAt != b.createdAt) b.createdAt.compareTo(a.createdAt) else a.id.compareTo(b.id)
        }

    private fun reference(
        all: List<Event>,
        values: Set<String>,
        kinds: Set<Int>?,
        since: Long?,
        until: Long?,
        limit: Int,
    ): List<String> =
        all
            .asSequence()
            .filter { e -> e.tags.any { it.size >= 2 && it[0] == "e" && it[1] in values } }
            .filter { kinds == null || it.kind in kinds }
            .filter { since == null || it.createdAt >= since }
            .filter { until == null || it.createdAt <= until }
            .sortedWith(newestFirst)
            .map { it.id }
            .distinct()
            .take(limit)
            .toList()

    private fun mergeEligible(
        store: EventStore,
        filter: Filter,
    ): Boolean =
        MergeQueryExecutor.streamCount(
            with(store.store.queryBuilder) { filter.toFilterWithDTags() },
            store.store.queryBuilder.indexStrategy,
        ) > 0

    private fun newStore() =
        EventStore(
            dbName = null,
            indexStrategy =
                DefaultIndexingStrategy(
                    indexTagsByCreatedAtAlone = true,
                    useAndIndexIdOnOrderBy = true,
                    indexFullTextSearch = false,
                ),
        )

    @Test
    fun distinctCreatedAt_withKinds_matchesReference() =
        runBlocking {
            val store = newStore()
            val notes = (0 until 6).map { hex64(2, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (round in 0 until 40) {
                // reactions (kind 7) and replies (kind 1) to a rotating note
                all.add(ev(t++, 7, listOf(notes[round % notes.size])))
                all.add(ev(t++, 1, listOf(notes[(round + 1) % notes.size])))
            }
            // Noise: kind-7 to notes NOT in the query set, and other tags.
            for (i in 0 until 100) all.add(ev(t++, 7, listOf(hex64(9, i))))
            store.batchInsert(all)

            val queried = notes.take(3)
            val filter = Filter(kinds = listOf(7), tags = mapOf("e" to queried), limit = 25)
            assertTrue(mergeEligible(store, filter), "tag watcher must be merge-eligible")

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(reference(all, queried.toSet(), setOf(7), null, null, 25), merged)
            store.close()
        }

    @Test
    fun crossStreamDuplicate_emittedOnce() =
        runBlocking {
            val store = newStore()
            val a = hex64(3, 0)
            val b = hex64(3, 1)
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            // Events tagging BOTH a and b — they appear in both value streams
            // and must be emitted exactly once.
            repeat(5) { all.add(ev(t++, 1, listOf(a, b))) }
            // Events tagging only one of them.
            repeat(5) { all.add(ev(t++, 1, listOf(a))) }
            repeat(5) { all.add(ev(t++, 1, listOf(b))) }
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(1), tags = mapOf("e" to listOf(a, b)), limit = 500)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(merged.size, merged.toSet().size, "no event may be emitted twice")
            assertEquals(15, merged.size, "5 both + 5 a-only + 5 b-only = 15 distinct")
            assertEquals(reference(all, setOf(a, b), setOf(1), null, null, 500), merged)
            store.close()
        }

    @Test
    fun noKinds_usesTagCreatedAtIndex() =
        runBlocking {
            val store = newStore()
            val notes = (0 until 5).map { hex64(4, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (round in 0 until 30) all.add(ev(t++, (round % 3) + 1, listOf(notes[round % notes.size])))
            for (i in 0 until 60) all.add(ev(t++, 1, listOf(hex64(8, i))))
            store.batchInsert(all)

            val queried = notes.take(3)
            val filter = Filter(tags = mapOf("e" to queried), limit = 20)
            assertTrue(mergeEligible(store, filter), "no-kind tag watcher must be merge-eligible with the tag index")

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(reference(all, queried.toSet(), null, null, null, 20), merged)
            store.close()
        }

    @Test
    fun withSinceAndUntil_boundsTheWindow() =
        runBlocking {
            val store = newStore()
            val notes = (0 until 4).map { hex64(5, it) }
            val all = ArrayList<Event>()
            val base = 1_700_000_000L
            for (i in 0 until 300) all.add(ev(base + i.toLong(), 7, listOf(notes[i % notes.size])))
            store.batchInsert(all)

            val since = base + 50
            val until = base + 250
            val filter = Filter(kinds = listOf(7), tags = mapOf("e" to notes), since = since, until = until, limit = 500)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            val ref = reference(all, notes.toSet(), setOf(7), since, until, 500)
            assertEquals(ref, merged)
            assertTrue(merged.isNotEmpty() && ref.size < 300)
            store.close()
        }

    @Test
    fun rawPathMatchesDecoded() =
        runBlocking {
            val store = newStore()
            val notes = (0 until 6).map { hex64(6, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (round in 0 until 25) all.add(ev(t++, 7, listOf(notes[round % notes.size])))
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(7), tags = mapOf("e" to notes.take(3)), limit = 15)
            val decoded = store.query<Event>(filter).map { it.id }
            val raw = store.store.rawQuery(filter).map { it.id }
            assertEquals(decoded, raw, "the zero-decode raw path must match the decoded query")
            store.close()
        }

    @Test
    fun tiedCreatedAt_matchesReferenceAsSet() =
        runBlocking {
            val store = newStore()
            val notes = (0 until 4).map { hex64(10, it) }
            val all = ArrayList<Event>()
            // Many events share a created_at — the tag cursor can't id-order
            // within a second, so assert a valid newest-N by set + size.
            var t = 1_700_000_000L
            for (block in 0 until 15) {
                val ts = t
                for (n in notes) all.add(ev(ts, 7, listOf(n)))
                t += 1
            }
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(7), tags = mapOf("e" to notes), limit = 22)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(22, merged.size)
            assertEquals(merged.size, merged.toSet().size)
            // The whole result must sit within the newest slice the reference
            // would return once ties are resolved either way: everything in
            // `merged` must be at or above the created_at cutoff.
            val ids = merged.toSet()
            val chosen = all.filter { it.id in ids }
            val cutoff = chosen.minOf { it.createdAt }
            val eligibleAboveCutoff = all.filter { it.createdAt > cutoff }.map { it.id }.toSet()
            assertTrue(eligibleAboveCutoff.all { it in ids }, "every event newer than the cutoff must be included")
            store.close()
        }

    @Test
    fun ineligibleShapesFallThrough() =
        runBlocking {
            val store = newStore()
            store.batchInsert(listOf(ev(1_700_000_000L, 7, listOf(hex64(2, 0)))))

            // Single value → single seek, not a merge.
            assertFalse(mergeEligible(store, Filter(kinds = listOf(7), tags = mapOf("e" to listOf(hex64(2, 0))), limit = 10)))
            // No limit → not merge-eligible.
            assertFalse(mergeEligible(store, Filter(kinds = listOf(7), tags = mapOf("e" to listOf(hex64(2, 0), hex64(2, 1))))))
            // Authors present → covered-index seek shape, not a tag merge.
            assertFalse(
                mergeEligible(
                    store,
                    Filter(kinds = listOf(7), authors = listOf(hex64(1, 1)), tags = mapOf("e" to listOf(hex64(2, 0), hex64(2, 1))), limit = 10),
                ),
            )
            store.close()
        }
}
