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

/**
 * Isolates the NIP-50 search-scaling curve the scale-curve report flagged
 * (FTS5 falling ~18× from 25k→400k events) and the two levers against it:
 *
 *  1. **segment compaction** (`INSERT INTO event_fts(event_fts) VALUES
 *     ('optimize')`). Incremental inserts leave the index as many small
 *     segments and a MATCH queries every one; measured fragmented vs
 *     optimized.
 *  2. **rowid ordering** ([DefaultIndexingStrategy.searchOrderByRowId]).
 *     `ORDER BY created_at DESC` must materialize + sort *every* document
 *     matching the term (cost grows with the corpus); `ORDER BY
 *     event_fts.rowid DESC LIMIT n` early-terminates — O(limit).
 *
 * The seed injects a common term into ~1% of events, so the match set — and
 * thus the created_at sort — grows with the corpus while the limit stays 50.
 *
 * Size with `-DftsBenchScale=N` (default 1). Not an assertion test; run
 * explicitly and read stdout.
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

    @Test
    fun searchScaling() =
        runBlocking {
            println("─ FtsSearchScalingBenchmark (scale=$SCALE, limit=50) ─")
            println("  %-9s %14s %14s %14s".format("corpus", "createdAt/frag", "createdAt/opt", "rowid/opt"))
            for (size in SIZES) {
                val events = seed(size)

                val createdAt = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy())
                val rowId = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy(searchOrderByRowId = true))
                try {
                    // Insert in 10k chunks → many FTS segments (fragmented).
                    events.chunked(10_000).forEach {
                        createdAt.batchInsert(it)
                        rowId.batchInsert(it)
                    }

                    val f = Filter(search = NEEDLE, limit = 50)
                    val frag = time(createdAt, f)

                    createdAt.store.reindexFullTextSearch() // rebuild + optimize()
                    rowId.store.reindexFullTextSearch()
                    val optCreatedAt = time(createdAt, f)
                    val optRowId = time(rowId, f)

                    println(
                        "  %-9s %12.2f ms %12.2f ms %12.2f ms".format(
                            if (size >= 1000) "${size / 1000}k" else "$size",
                            frag,
                            optCreatedAt,
                            optRowId,
                        ),
                    )
                } finally {
                    createdAt.close()
                    rowId.close()
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
