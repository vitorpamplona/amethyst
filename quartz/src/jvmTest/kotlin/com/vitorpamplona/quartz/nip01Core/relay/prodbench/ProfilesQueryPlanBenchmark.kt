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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
 * sort. **Applied fix** (`QueryBuilder.makeSimpleQuery`, see
 * `plans/2026-07-04-profiles-query-plan.md`): for a **multi-author**
 * (`pubkey IN (…)`) filter with `kinds`, no `limit`, and no d-tags, pin
 * `INDEXED BY query_by_kind_pubkey_created` — SQLite then seeks the authors
 * and sorts the small result, keeping the newest-first ORDER BY (zero
 * behavior change). Single-author queries already seek; limited feeds keep
 * the `created_at` scan + early LIMIT.
 *
 * This is now a regression guard: it asserts the live REQ plan for this
 * shape seeks the composite index (not the kind scan), and prints the
 * before/after timings.
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

            // The old (bad) shape: ORDER BY created_at DESC forces the kind scan.
            val (oldN, oldMs) = timeRaw("$base ORDER BY created_at DESC")
            println("  old shape (ORDER BY, no limit):")
            println(planTree(store.store.explainQuery("$base ORDER BY created_at DESC")))
            println("    → $oldN events, ${"%.3f".format(oldMs)} ms/run")

            // The fix, as the REQ path now builds it: INDEXED BY the composite
            // index for this multi-author, no-limit shape — seeks the authors,
            // keeps the newest-first ORDER BY (sorts the small result).
            val reqPlan = store.store.planQuery(profiles)
            val fixedSql = "SELECT $cols FROM event_headers INDEXED BY query_by_kind_pubkey_created WHERE kind = 0 AND pubkey IN ($inList) ORDER BY created_at DESC"
            val (newN, newMs) = timeRaw(fixedSql)
            println("  fixed REQ path (INDEXED BY composite, ORDER BY kept):")
            println(planTree(reqPlan))
            println("    → $newN events, ${"%.3f".format(newMs)} ms/run  (${"%.1f".format(oldMs / newMs)}× faster, grows with profile count)")

            // Regression guards: the live REQ path must seek the composite
            // index (not scan the kind-only one) and preserve the ORDER BY.
            assertEquals(oldN, newN, "row count unchanged by the fix")
            assertEquals(newN, store.query<Event>(profiles).size, "REQ path returns the match set")
            assertTrue(
                reqPlan.contains("query_by_kind_pubkey_created"),
                "REQ plan should seek the (kind, pubkey) index, was:\n$reqPlan",
            )
            assertFalse(
                reqPlan.contains("query_by_kind_created "),
                "REQ plan must not scan the kind-only index, was:\n$reqPlan",
            )
            assertTrue(reqPlan.contains("ORDER BY"), "newest-first order is preserved, was:\n$reqPlan")

            store.close()
        }
}
