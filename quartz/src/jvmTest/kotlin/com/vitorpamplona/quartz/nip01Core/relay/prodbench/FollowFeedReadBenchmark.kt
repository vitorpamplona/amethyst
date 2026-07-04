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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.MergeQueryExecutor
import com.vitorpamplona.quartz.nip01Core.store.sqlite.QueryBuilder
import com.vitorpamplona.quartz.nip01Core.store.sqlite.explainQuery
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Investigates the `follow-feed` query — `kinds=[1,6] AND authors=[150]
 * ORDER BY created_at DESC LIMIT 500` — which the 1M run showed geode losing
 * 5.5× on (97.7 ms vs strfry 17.6 ms). The current plan seeks the
 * (kind,pubkey) index for the 150 authors, **collects every matching event**,
 * then TEMP B-TREE sorts to 500 — no early termination, so it explodes when
 * followed authors are prolific.
 *
 * Compares three read strategies (all on **existing** indexes, so no
 * write/size cost) across two opposite author profiles:
 *  - **current**: composite seek + collect-all + sort.
 *  - **scan**: `INDEXED BY query_by_created_at_id` — walk newest-first,
 *    filter kind+author, stop at LIMIT (strfry's early-terminating shape).
 *  - **union**: per-(author,kind) `ORDER BY created_at DESC LIMIT 500`
 *    branches merged — bounds each branch's read to the LIMIT.
 *
 * Scenarios (same store, two disjoint follow sets + background):
 *  - **prolific-recent**: followed authors post densely & recently.
 *  - **sparse-old**: followed authors post rarely & long ago (scan must skip
 *    a lot of newer background to reach them).
 *
 * Size the seed with `-DfollowBenchScale=N` (default 1 ≈ ~130k events).
 */
class FollowFeedReadBenchmark {
    companion object {
        val SCALE = System.getProperty("followBenchScale")?.toInt() ?: 1
    }

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
    private var idSeq = 0

    private fun ev(
        pubkey: String,
        createdAt: Long,
        kind: Int,
    ): Event = EventFactory.create(hex64(7, idSeq++), pubkey, createdAt, kind, emptyArray(), "", sig)

    private val cols = "id, pubkey, created_at, kind, tags, content, sig"

    @Test
    fun compareFollowFeedStrategies() =
        runBlocking {
            val store = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy(indexEventsByCreatedAtAlone = true, indexEventsByPubkeyAlone = true, indexFullTextSearch = false))

            val base = 1_700_000_000L
            val span = 3_000_000L // ~35 days
            val batch = ArrayList<Event>(10_000)

            fun add(e: Event) {
                batch.add(e)
                if (batch.size >= 10_000) {
                    runBlocking { store.batchInsert(batch) }
                    batch.clear()
                }
            }

            // Background: many authors posting throughout the whole window.
            val bgAuthors = 3_000 * SCALE
            for (a in 0 until bgAuthors) {
                val pk = hex64(1, a)
                repeat(20) { add(ev(pk, base + (mix(a * 31L + it) and 0x7fffffff) % span, 1)) }
            }

            // prolific-recent: 150 authors, dense in the newest 10% of time.
            val prolific = (0 until 150).map { hex64(2, it) }
            val recentStart = base + span * 9 / 10
            for ((i, pk) in prolific.withIndex()) {
                repeat(1_000 * SCALE) {
                    val kind = if (it % 8 == 0) 6 else 1
                    add(ev(pk, recentStart + (mix(i * 131L + it) and 0x7fffffff) % (span / 10), kind))
                }
            }

            // sparse-old: 150 authors, a few events each, in the oldest 10%.
            val sparse = (0 until 150).map { hex64(3, it) }
            for ((i, pk) in sparse.withIndex()) {
                repeat(6) {
                    val kind = if (it % 3 == 0) 6 else 1
                    add(ev(pk, base + (mix(i * 17L + it) and 0x7fffffff) % (span / 10), kind))
                }
            }
            if (batch.isNotEmpty()) store.batchInsert(batch)

            val total = runBlocking { store.count(Filter()) }
            println("─ FollowFeedReadBenchmark: $total events (scale=$SCALE) ─")

            for ((scenario, authors) in listOf("prolific-recent" to prolific, "sparse-old" to sparse)) {
                val inList = authors.joinToString(",") { "'$it'" }
                val whereTail = "WHERE kind IN (1,6) AND pubkey IN ($inList) ORDER BY created_at DESC LIMIT 500"

                val current = "SELECT $cols FROM event_headers $whereTail"
                val scan = "SELECT $cols FROM event_headers INDEXED BY query_by_created_at_id $whereTail"
                // Per-(author,kind) branch, each wrapped in a subquery so its
                // ORDER BY/LIMIT is legal inside the UNION ALL; the outer merge
                // sorts the ≤ 300×500 collected rows down to 500.
                val union =
                    "SELECT $cols FROM (\n" +
                        authors.joinToString("\nUNION ALL\n") { pk ->
                            listOf(1, 6).joinToString("\nUNION ALL\n") { k ->
                                "SELECT $cols FROM (SELECT $cols FROM event_headers WHERE kind = $k AND pubkey = '$pk' ORDER BY created_at DESC LIMIT 500)"
                            }
                        } +
                        "\n) ORDER BY created_at DESC LIMIT 500"

                println("  ═ $scenario ═")
                for ((name, sql) in listOf("current" to current, "scan" to scan, "union" to union)) {
                    try {
                        val plan =
                            runBlocking { store.store.explainQuery(sql) }
                                .lineSequence()
                                .filter { it.contains("SEARCH") || it.contains("SCAN ") || (it.contains("USE ") && !it.contains("USING")) }
                                .joinToString(" | ") { it.trimStart('│', '├', '└', '─', ' ') }
                                .let { if (it.length > 90) it.take(90) + "…" else it }
                        val (n, ms) = timeRaw(store, sql)
                        println("    %-8s %6.1f ms  (%d rows)  %s".format(name, ms, n, plan))
                    } catch (e: Exception) {
                        println("    %-8s ERROR: %s".format(name, e.message?.take(80)))
                    }
                }
                // The app-level k-way merge is not a SQL string, so time it directly.
                try {
                    val (n, ms) = timeMerge(store, authors)
                    val streams = 2 * authors.size
                    println("    %-8s %6.1f ms  (%d rows)  k-way merge, %d streams".format("merge", ms, n, streams))
                } catch (e: Exception) {
                    println("    %-8s ERROR: %s".format("merge", e.message?.take(80)))
                }
            }
            store.close()
        }

    private fun timeMerge(
        store: EventStore,
        authors: List<String>,
    ): Pair<Int, Double> {
        val filter =
            QueryBuilder.FilterWithDTags(
                authors = authors,
                kinds = listOf(1, 6),
                limit = 500,
            )

        fun run() =
            runBlocking {
                store.store.pool.useReader { c ->
                    var n = 0
                    MergeQueryExecutor.run(c, filter) { n++ }
                    n
                }
            }
        repeat(3) { run() }
        val runs = 10
        val start = System.nanoTime()
        var got = 0
        repeat(runs) { got = run() }
        return got to (System.nanoTime() - start) / 1e6 / runs
    }

    private fun timeRaw(
        store: EventStore,
        sql: String,
    ): Pair<Int, Double> {
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
        val runs = 10
        val start = System.nanoTime()
        var got = 0
        repeat(runs) { got = run() }
        return got to (System.nanoTime() - start) / 1e6 / runs
    }
}
