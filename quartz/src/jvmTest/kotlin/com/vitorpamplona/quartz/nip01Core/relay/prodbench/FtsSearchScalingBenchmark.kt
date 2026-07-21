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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Two measurements around the contentless FTS index, motivating the v4→v5
 * schema change and being honest about what it does and does not fix.
 *
 *  1. **Delete scaling — the win.** The old `fts5(event_header_row_id,
 *     content)` schema had the `fts_foreign_key` trigger delete by a regular
 *     FTS column, which FTS5 cannot seek (it scans, O(n) per delete). The
 *     contentless schema keys deletes off the rowid (= event_headers.row_id),
 *     an O(log n) primary-key seek. Every event removal fires this trigger
 *     (replaceable rotation, kind-5, expiration, right-to-vanish).
 *  2. **Search scaling — the limit.** `MATCH … ORDER BY rank LIMIT n` (NIP-50
 *     relevance ordering, bm25) must score *every* matching document, so
 *     search cost grows with the match set regardless of ordering (created_at
 *     has the same shape). Segment `optimize` compacts the index but does not
 *     change that; corpus-independent search needs an external engine. Shown
 *     fragmented vs optimized to size the (secondary) compaction effect.
 *
 * Size search with `-DftsBenchScale=N` (default 1). Not an assertion test.
 */
class FtsSearchScalingBenchmark {
    companion object {
        val SCALE = System.getProperty("ftsBenchScale")?.toInt() ?: 1
        val SIZES = listOf(50_000, 100_000, 200_000).map { it * SCALE }
        const val NEEDLE = "zzneedle"
    }

    private val hex = "0123456789abcdef"

    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private fun hex64(index: Int): String {
        val out = CharArray(64)
        for (w in 0 until 4) {
            val v = mix(index.toLong() * 4 + w + 99)
            for (b in 0 until 8) {
                val byte = ((v ushr (b * 8)) and 0xFF).toInt()
                out[(w * 8 + b) * 2] = hex[byte ushr 4]
                out[(w * 8 + b) * 2 + 1] = hex[byte and 0xF]
            }
        }
        return String(out)
    }

    private val vocab = (0 until 400).map { "word$it" }
    private val sig = "0".repeat(128)

    private fun seed(n: Int): List<Event> {
        val base = 1_700_000_000L
        val events = ArrayList<Event>(n)
        for (i in 0 until n) {
            val r = mix(i.toLong())
            val content =
                buildString {
                    for (w in 0 until 8) append(vocab[((r ushr (w * 3)) and 0x1FF).toInt() % vocab.size]).append(' ')
                    // ~1% carry the searched term.
                    if (i % 100 == 0) append(NEEDLE)
                }
            events.add(EventFactory.create(hex64(i), hex64(i % 5000), base + i.toLong(), 1, emptyArray(), content, sig))
        }
        return events
    }

    private inline fun timeMs(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1e6
    }

    private fun SQLiteConnection.exec(sql: String) = prepare(sql).use { it.step() }

    @Test
    fun deleteByColumnVsByRowid() {
        // Old-schema (delete by FTS column) vs contentless (delete by rowid),
        // 500 deletes at two table sizes. By-column should grow with the
        // table; by-rowid should stay flat.
        println("─ FtsSearchScalingBenchmark.delete (500 deletes) ─")
        println("  %-9s %14s %14s".format("rows", "byColumn", "byRowid"))
        for (n in listOf(2_000 * SCALE, 8_000 * SCALE)) {
            val db = BundledSQLiteDriver().open(":memory:")
            try {
                db.exec("CREATE VIRTUAL TABLE col USING fts5(event_header_row_id, content)")
                db.exec("CREATE VIRTUAL TABLE row USING fts5(content, content='', contentless_delete=1)")
                for (i in 1..n) {
                    db.prepare("INSERT INTO col(event_header_row_id, content) VALUES (?, 'alpha beta gamma')").use {
                        it.bindLong(1, i.toLong())
                        it.step()
                    }
                    db.prepare("INSERT INTO row(rowid, content) VALUES (?, 'alpha beta gamma')").use {
                        it.bindLong(1, i.toLong())
                        it.step()
                    }
                }
                val byColumn =
                    timeMs {
                        for (i in 1..500) {
                            db.prepare("DELETE FROM col WHERE event_header_row_id = ?").use {
                                it.bindLong(1, i.toLong())
                                it.step()
                            }
                        }
                    }
                val byRowid =
                    timeMs {
                        for (i in 1..500) {
                            db.prepare("DELETE FROM row WHERE rowid = ?").use {
                                it.bindLong(1, i.toLong())
                                it.step()
                            }
                        }
                    }
                println("  %-9s %11.2f ms %11.2f ms".format("${n / 1000}k", byColumn, byRowid))
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun searchScaling() =
        runBlocking {
            println("─ FtsSearchScalingBenchmark.search (created_at DESC, limit=50) ─")
            println("  %-9s %16s %16s".format("corpus", "fragmented", "optimized"))
            for (size in SIZES) {
                val store = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy())
                try {
                    seed(size).chunked(10_000).forEach { store.batchInsert(it) }
                    val f = Filter(search = NEEDLE, limit = 50)
                    val frag = time(store, f)
                    store.store.reindexFullTextSearch() // rebuild + optimize()
                    val opt = time(store, f)
                    println("  %-9s %13.2f ms %13.2f ms".format("${size / 1000}k", frag, opt))
                } finally {
                    store.close()
                }
            }
        }

    private suspend fun time(
        store: EventStore,
        filter: Filter,
    ): Double {
        repeat(3) { store.query<Event>(filter) }
        val runs = 20
        val start = System.nanoTime()
        repeat(runs) { store.query<Event>(filter) }
        return (System.nanoTime() - start) / 1e6 / runs
    }
}
