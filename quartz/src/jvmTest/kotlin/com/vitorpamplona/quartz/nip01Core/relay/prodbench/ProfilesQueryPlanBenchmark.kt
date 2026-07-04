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
package com.vitorpamplona.quartz.nip01Core.relay.prodbench

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.explainQuery
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * relayBench's `profiles` scenario — `Filter(kinds=[0], authors=[…50…])`,
 * no `limit` (profile hydration) — was the one query where geode lost badly
 * to strfry on the 1M corpus: 99.5 ms p50 vs 0.94 ms (~100×), while geode
 * won or tied everywhere else.
 *
 * Root cause (proved below): the REQ path appends `ORDER BY created_at DESC`
 * **unconditionally**, even with no limit. `query_by_kind_created`
 * (kind, created_at) satisfies that order *for free* while scanning every
 * kind-0 row, so SQLite rationally prefers it over the ideal
 * `query_by_kind_pubkey_created` (kind, pubkey, …), which would need a sort.
 * The scan is O(all kind-0 profiles) — cheap at 2k, the 99.5 ms at 1M.
 *
 * `ANALYZE` does **not** help — even a connection that reads fresh stats
 * keeps the scan, because with the ORDER BY the scan genuinely avoids a
 * sort. Two fixes work (see `plans/2026-07-04-profiles-query-plan.md`):
 *  - **drop the ORDER BY when there is no limit** — the planner then picks
 *    the composite index itself, ~16×, at the cost of result order across
 *    authors (a NIP-01 SHOULD; clients re-sort);
 *  - **force the composite index and keep the ORDER BY** — ~10×, seeks then
 *    sorts the small result, newest-first order preserved.
 *
 * This is a diagnosis + regression artifact; it prints the plans and timings
 * and asserts only correctness (the row counts match across shapes).
 */
class ProfilesQueryPlanBenchmark {
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
        return String(out)
    }

    private val sig = "0".repeat(128)

    private fun ev(
        idIndex: Int,
        pubkey: String,
        createdAt: Long,
        kind: Int,
    ): Event = EventFactory.create(hex64(7, idIndex), pubkey, createdAt, kind, emptyArray(), "", sig)

    /** Only the plan tree lines (SEARCH/SCAN/USE …), not the echoed SQL. */
    private fun planTree(text: String) =
        text
            .lineSequence()
            .filter { l -> l.trimStart().let { it.startsWith("SEARCH") || it.startsWith("SCAN") || it.startsWith("USE") || it.contains("─ ") } }
            .joinToString("\n") { "    $it" }

    @Test
    fun profilesQueryPlan() =
        runBlocking {
            val store =
                EventStore(
                    dbName = null,
                    indexStrategy =
                        DefaultIndexingStrategy(
                            indexEventsByCreatedAtAlone = true,
                            indexEventsByPubkeyAlone = true,
                            indexFullTextSearch = false,
                        ),
                )

            val authorCount = 2_000
            val notesPerAuthor = 20
            val authors = (0 until authorCount).map { hex64(1, it) }

            var idIdx = 0
            var t = 1_700_000_000L
            val batch = ArrayList<Event>(10_000)
            for (a in authors) {
                batch.add(ev(idIdx++, a, t++, 0)) // one kind-0 profile per author
                repeat(notesPerAuthor) { batch.add(ev(idIdx++, a, t++, 1)) }
                if (batch.size >= 10_000) {
                    store.batchInsert(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) store.batchInsert(batch)

            val total = authorCount * (notesPerAuthor + 1)
            val profiles = Filter(kinds = listOf(0), authors = authors.take(50))
            val cols = "id, pubkey, created_at, kind, tags, content, sig"
            val inList = authors.take(50).joinToString(",") { "'$it'" }
            val base = "SELECT $cols FROM event_headers WHERE kind = 0 AND pubkey IN ($inList)"

            fun timeRaw(sql: String): Pair<Int, Double> {
                fun run() =
                    runBlocking {
                        store.store.pool.useReader { c ->
                            c.prepare(sql).use { s ->
                                var n = 0
                                while (s.step()) n++
                                n
                            }
                        }
                    }
                repeat(3) { run() }
                val runs = 20
                val start = System.nanoTime()
                var got = 0
                repeat(runs) { got = run() }
                return got to (System.nanoTime() - start) / 1e6 / runs
            }

            println("─ ProfilesQueryPlanBenchmark: $total events, $authorCount kind-0 profiles, filter kinds=[0]+50 authors ─")

            // 1. Current REQ shape: ORDER BY created_at DESC, no limit.
            val (baseN, baseMs) = timeRaw("$base ORDER BY created_at DESC")
            println("  current (ORDER BY, no limit):")
            println(planTree(store.store.explainQuery("$base ORDER BY created_at DESC")))
            println("    → $baseN events, ${"%.3f".format(baseMs)} ms/run")

            // 2. Fix A: drop ORDER BY (no limit) — planner picks composite itself.
            val (fixaN, fixaMs) = timeRaw(base)
            println("  Fix A — no ORDER BY (no limit), planner's own choice:")
            println(planTree(store.store.explainQuery(base)))
            println("    → $fixaN events, ${"%.3f".format(fixaMs)} ms/run  (${"%.1f".format(baseMs / fixaMs)}×)")

            // 3. Fix B: force composite index, keep ORDER BY (order preserved).
            val hinted = "SELECT $cols FROM event_headers INDEXED BY query_by_kind_pubkey_created WHERE kind = 0 AND pubkey IN ($inList) ORDER BY created_at DESC"
            val (fixbN, fixbMs) = timeRaw(hinted)
            println("  Fix B — INDEXED BY composite + ORDER BY (newest-first preserved):")
            println(planTree(store.store.explainQuery(hinted)))
            println("    → $fixbN events, ${"%.3f".format(fixbMs)} ms/run  (${"%.1f".format(baseMs / fixbMs)}×)")

            // Correctness: every shape returns the same events (all 50 here).
            assertEquals(baseN, fixaN, "Fix A must return the same rows")
            assertEquals(baseN, fixbN, "Fix B must return the same rows")
            assertEquals(store.query<Event>(profiles).size, baseN, "REQ path matches raw SQL")

            store.close()
        }
}
