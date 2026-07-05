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
import kotlin.test.assertTrue

/**
 * Correctness guard for [MergeQueryExecutor]: the app-level k-way merge that
 * serves the home-feed shape (`authors=[…] (+ kinds=[…]) [+ since/until]
 * limit=N`, newest-first). For every eligible filter, the merge must return
 * exactly the same top-N the single-SQL plan does.
 *
 * Two independent oracles per case:
 *  - a **reference** computed in Kotlin (filter, sort `created_at DESC, id
 *    ASC`, take limit) — the definition of a correct newest-N;
 *  - the **SQL path** (`QueryBuilder.toSql` executed directly), which the
 *    merge is replacing. With `useAndIndexIdOnOrderBy` on, both tie-break by
 *    `id ASC`, so on distinct-and-tied `created_at` alike the two paths must
 *    agree row-for-row.
 */
class MergeQueryCorrectnessTest {
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
        pubkey: String,
        createdAt: Long,
        kind: Int,
    ): Event = EventFactory.create(hex64(7, idSeq++), pubkey, createdAt, kind, emptyArray(), "", sig)

    /** newest-first, tie-break id ASC — the merge's contract. */
    private val newestFirst =
        Comparator<Event> { a, b ->
            if (a.createdAt != b.createdAt) {
                b.createdAt.compareTo(a.createdAt)
            } else {
                a.id.compareTo(b.id)
            }
        }

    private fun reference(
        all: List<Event>,
        filter: Filter,
        limit: Int,
    ): List<String> =
        all
            .asSequence()
            .filter { filter.authors == null || it.pubKey in filter.authors!! }
            .filter { filter.kinds == null || it.kind in filter.kinds!! }
            .filter { filter.since == null || it.createdAt >= filter.since!! }
            .filter { filter.until == null || it.createdAt <= filter.until!! }
            .sortedWith(newestFirst)
            .take(limit)
            .map { it.id }
            .toList()

    /** Runs the raw single-SQL plan (bypassing the merge) and returns its ids. */
    private fun sqlIds(
        store: EventStore,
        filter: Filter,
    ): List<String> =
        runBlocking {
            store.store.pool.useReader { conn ->
                val hasher = store.store.seedModule.hasher(conn)
                val spec = store.store.queryBuilder.toSql(filter, hasher)
                conn.prepare(spec.sql).use { stmt ->
                    spec.args.forEachIndexed { i, a -> stmt.bindText(i + 1, a) }
                    val ids = ArrayList<String>()
                    while (stmt.step()) ids.add(stmt.getText(0))
                    ids
                }
            }
        }

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
                    indexEventsByCreatedAtAlone = true,
                    indexEventsByPubkeyAlone = true,
                    useAndIndexIdOnOrderBy = true,
                    indexFullTextSearch = false,
                ),
        )

    @Test
    fun distinctCreatedAt_multiKind() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 8).map { hex64(2, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            // Each event a distinct created_at: ordering is fully determined.
            for (round in 0 until 40) {
                for ((i, pk) in authors.withIndex()) {
                    val kind = if (round % 3 == 0) 6 else 1
                    all.add(ev(pk, t++, kind))
                }
            }
            // Background noise from other authors/kinds the filter must exclude.
            for (i in 0 until 200) all.add(ev(hex64(9, i), t++, if (i % 2 == 0) 1 else 7))
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(1, 6), authors = authors, limit = 50)
            assertTrue(mergeEligible(store, filter), "home-feed filter must be merge-eligible")

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(reference(all, filter, 50), merged, "merge must equal the Kotlin reference")
            assertEquals(sqlIds(store, filter), merged, "merge must equal the single-SQL plan")
            store.close()
        }

    @Test
    fun authorsOnly_noKinds() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 6).map { hex64(3, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (round in 0 until 30) {
                for (pk in authors) all.add(ev(pk, t++, (round % 4) + 1))
            }
            for (i in 0 until 100) all.add(ev(hex64(8, i), t++, 1))
            store.batchInsert(all)

            val filter = Filter(authors = authors, limit = 40)
            assertTrue(mergeEligible(store, filter), "authors-only filter must be merge-eligible")

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(reference(all, filter, 40), merged)
            assertEquals(sqlIds(store, filter), merged)
            store.close()
        }

    @Test
    fun withSinceAndUntil() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 5).map { hex64(4, it) }
            val all = ArrayList<Event>()
            val base = 1_700_000_000L
            for (i in 0 until 300) {
                val pk = authors[i % authors.size]
                all.add(ev(pk, base + i.toLong(), if (i % 5 == 0) 6 else 1))
            }
            store.batchInsert(all)

            val since = base + 50
            val until = base + 250
            val filter = Filter(kinds = listOf(1, 6), authors = authors, since = since, until = until, limit = 500)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            val ref = reference(all, filter, 500)
            assertEquals(ref, merged)
            assertEquals(sqlIds(store, filter), merged)
            // Sanity: the window really did bound the result.
            assertTrue(merged.isNotEmpty() && ref.size < 300)
            store.close()
        }

    @Test
    fun tiedCreatedAt_setMatchesReferenceMultiset() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 6).map { hex64(5, it) }
            val all = ArrayList<Event>()
            // Many events share the same created_at across authors — exercises
            // the same-second tie-break (id ASC) on both paths.
            var t = 1_700_000_000L
            for (block in 0 until 20) {
                val ts = t
                for (pk in authors) {
                    all.add(ev(pk, ts, 1))
                    all.add(ev(pk, ts, 6))
                }
                t += 1
            }
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(1, 6), authors = authors, limit = 30)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            // With useAndIndexIdOnOrderBy both paths pin id ASC, so even under
            // ties the ordered result is deterministic and must match exactly.
            assertEquals(reference(all, filter, 30), merged)
            assertEquals(sqlIds(store, filter), merged)
            store.close()
        }

    @Test
    fun tiedCreatedAt_withinSingleStream_limitCutsTheTie() =
        runBlocking {
            val store = newStore()
            // Two authors so the filter is merge-eligible (>1 stream). Each
            // author posts SEVERAL kind-1 events at the SAME created_at — the
            // case a per-stream `ORDER BY created_at DESC` alone gets wrong:
            // the stream's head must be the id-MINIMUM of that second, not
            // whatever rowid the index happened to store first. The limit is
            // set to slice through the middle of the tie so an out-of-id-order
            // head would emit the wrong events.
            val authors = (0 until 3).map { hex64(11, it) }
            val all = ArrayList<Event>()
            val ts = 1_700_000_000L
            for (pk in authors) repeat(5) { all.add(ev(pk, ts, 1)) }
            // A newer second with one event each, so the tie isn't at the very top.
            for (pk in authors) all.add(ev(pk, ts + 1, 1))
            store.batchInsert(all)

            // 3 newest (ts+1) + 4 into the 15-event same-second tie.
            val filter = Filter(kinds = listOf(1), authors = authors, limit = 7)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(reference(all, filter, 7), merged, "within-stream same-second tie must be id-ordered")
            assertEquals(sqlIds(store, filter), merged, "merge must equal the single-SQL plan under a within-stream tie")
            store.close()
        }

    @Test
    fun duplicateAuthors_doNotDuplicateEvents() =
        runBlocking {
            val store = newStore()
            val a = hex64(12, 0)
            val b = hex64(12, 1)
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            repeat(10) { all.add(ev(a, t++, 1)) }
            repeat(10) { all.add(ev(b, t++, 1)) }
            store.batchInsert(all)

            // `a` listed twice: the merge must not open two cursors for it and
            // emit each of a's events twice (the single-SQL IN(…) path dedups).
            val filter = Filter(kinds = listOf(1), authors = listOf(a, a, b), limit = 500)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(merged.toSet().size, merged.size, "no event may be emitted twice")
            assertEquals(20, merged.size)
            assertEquals(reference(all, filter, 500), merged)
            assertEquals(sqlIds(store, filter), merged)
            store.close()
        }

    @Test
    fun fewerMatchesThanLimit() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 4).map { hex64(6, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (pk in authors) repeat(3) { all.add(ev(pk, t++, 1)) }
            // Unrelated authors so the store isn't tiny.
            for (i in 0 until 50) all.add(ev(hex64(1, i), t++, 1))
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(1), authors = authors, limit = 500)
            assertTrue(mergeEligible(store, filter))

            val merged = store.query<Event>(filter).map { it.id }
            assertEquals(12, merged.size)
            assertEquals(reference(all, filter, 500), merged)
            assertEquals(sqlIds(store, filter), merged)
            store.close()
        }

    @Test
    fun onEachCallbackMatchesListQuery() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 5).map { hex64(2, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (round in 0 until 20) for (pk in authors) all.add(ev(pk, t++, 1))
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(1), authors = authors, limit = 25)
            val listIds = store.query<Event>(filter).map { it.id }

            val streamedIds = ArrayList<String>()
            store.query<Event>(filter) { streamedIds.add(it.id) }

            assertEquals(listIds, streamedIds, "streaming onEach must match the list query")
            assertEquals(reference(all, filter, 25), listIds)
            store.close()
        }

    @Test
    fun rawQueryPathMatchesDecodedQuery() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 6).map { hex64(4, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (round in 0 until 25) for (pk in authors) all.add(ev(pk, t++, if (round % 4 == 0) 6 else 1))
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(1, 6), authors = authors, limit = 30)
            val decoded = store.query<Event>(filter).map { it.id }
            val raw = store.store.rawQuery(filter).map { it.id }

            assertEquals(decoded, raw, "the zero-decode raw path must match the decoded query")
            assertEquals(reference(all, filter, 30), raw)
            store.close()
        }

    @Test
    fun singleElementFilterListRoutesThroughMerge() =
        runBlocking {
            val store = newStore()
            val authors = (0 until 5).map { hex64(3, it) }
            val all = ArrayList<Event>()
            var t = 1_700_000_000L
            for (round in 0 until 20) for (pk in authors) all.add(ev(pk, t++, 1))
            store.batchInsert(all)

            val filter = Filter(kinds = listOf(1), authors = authors, limit = 25)
            val listOfOne = store.query<Event>(listOf(filter)).map { it.id }
            val single = store.query<Event>(filter).map { it.id }

            assertEquals(single, listOfOne, "single-element filter list must match the single-filter path")
            store.close()
        }
}
