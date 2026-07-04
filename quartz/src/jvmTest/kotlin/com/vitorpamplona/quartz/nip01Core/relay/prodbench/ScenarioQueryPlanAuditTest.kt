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
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Audits the SQLite plan for every relayBench query scenario against a
 * realistic multi-kind, tagged store — the same `EXPLAIN QUERY PLAN`
 * method that caught the `profiles` regression (see
 * `plans/2026-07-04-profiles-query-plan.md`). Prints each scenario's plan
 * and flags the two shapes that mean "the planner is walking more than it
 * should": a full `SCAN` of `event_headers`/`event_tags`, or a
 * `USE TEMP B-TREE FOR ORDER BY` (an unindexed sort).
 *
 * Prints the full map and asserts the one hard invariant: **no scenario
 * full-table-scans** a base table (an index scan + LIMIT, like firehose's
 * created_at walk, is fine; small-result sorts are fine). As of the
 * `profiles` fix every scenario seeks an index — this guards that.
 */
class ScenarioQueryPlanAuditTest {
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
        tags: Array<Array<String>> = emptyArray(),
    ): Event = EventFactory.create(hex64(7, idSeq++), pubkey, createdAt, kind, tags, "", sig)

    @Test
    fun auditScenarioPlans() =
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

            val authors = (0 until 2_000).map { hex64(1, it) }
            val rootIds = (0 until 20).map { hex64(9, it) } // thread roots for `e` tags
            val topic = "nostr"
            var t = 1_700_000_000L

            val batch = ArrayList<Event>(10_000)

            fun flush() {
                if (batch.isNotEmpty()) {
                    runBlocking { store.batchInsert(batch) }
                    batch.clear()
                }
            }
            authors.forEachIndexed { ai, a ->
                batch.add(ev(a, t++, 0)) // profile
                repeat(10) { batch.add(ev(a, t++, 1)) } // notes
                // some replies (e-tag), mentions (p-tag), hashtags (t-tag)
                batch.add(ev(a, t++, 1, arrayOf(arrayOf("e", rootIds[ai % rootIds.size]))))
                batch.add(ev(a, t++, 1, arrayOf(arrayOf("t", topic))))
                batch.add(ev(a, t++, 7, arrayOf(arrayOf("e", rootIds[ai % rootIds.size]), arrayOf("p", authors[0]))))
                batch.add(ev(a, t++, 6, arrayOf(arrayOf("e", rootIds[ai % rootIds.size]))))
                batch.add(ev(a, t++, 9735, arrayOf(arrayOf("p", authors[0]))))
                if (batch.size >= 10_000) flush()
            }
            flush()

            val recentSince = t - 5_000
            val idSample = (0 until 100).map { hex64(7, it) }

            val scenarios: List<Pair<String, Filter>> =
                listOf(
                    "firehose" to Filter(limit = 500),
                    "global-feed" to Filter(kinds = listOf(1), limit = 500),
                    "profiles" to Filter(kinds = listOf(0), authors = authors.take(50)),
                    "author-archive" to Filter(authors = authors.takeLast(3), limit = 500),
                    "follow-feed" to Filter(kinds = listOf(1, 6), authors = authors.take(150), limit = 500),
                    "thread" to Filter(kinds = listOf(1, 6, 7, 9735), tags = mapOf("e" to listOf(rootIds[0]))),
                    "notifications" to Filter(kinds = listOf(1, 6, 7, 9735), tags = mapOf("p" to listOf(authors[0])), limit = 500),
                    "hashtag" to Filter(kinds = listOf(1), tags = mapOf("t" to listOf(topic)), limit = 500),
                    "by-ids" to Filter(ids = idSample),
                    "recent-window" to Filter(kinds = listOf(1), since = recentSince, limit = 500),
                )

            println("─ ScenarioQueryPlanAudit: ${store.count(Filter())} events ─")
            for ((name, filter) in scenarios) {
                val plan = store.store.planQuery(filter)
                val tree =
                    plan
                        .lineSequence()
                        .filter { it.contains("SEARCH") || it.contains("SCAN ") || it.contains("USE ") }
                        .joinToString(" | ") { it.trimStart('│', '├', '└', '─', ' ') }
                // A *bare* SCAN of a base table (no "USING INDEX") is the real
                // red flag — a full table walk. An index scan ("SCAN … USING
                // INDEX …", e.g. firehose's created_at scan + LIMIT) is fine.
                val bareScan = Regex("SCAN (event_headers|event_tags)(?! USING INDEX)").containsMatchIn(tree)
                val flag =
                    when {
                        bareScan -> " ⚠ FULL TABLE SCAN"
                        tree.contains("USE TEMP B-TREE") -> " · sort"
                        else -> ""
                    }
                println("  %-14s %s%s".format(name, tree, flag))
                assertFalse(bareScan, "$name should not full-table-scan; plan was:\n$plan")
            }

            store.close()
        }
}
