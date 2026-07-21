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
 * Measures the two tag-path query shapes the client filter-assembler survey
 * (2026-07) found hot but that no existing benchmark covers:
 *
 * 1. **tag ∩ author (DM-room shape)** — `kinds=[4] AND authors=[peer] AND
 *    #p=[me] LIMIT n`. 65 assembler call sites build this shape (every
 *    NIP-04 chat room, reports-by-follows, follows-scoped community feeds).
 *    [com.vitorpamplona.quartz.nip01Core.store.sqlite.IndexingStrategy.indexTagsWithKindAndPubkey]
 *    gates a covering `(tag_hash, kind, pubkey_hash, created_at)` index for
 *    it, but the flag is off everywhere (including geode). Without it the
 *    plan seeks `(tag_hash, kind)` and reads EVERY DM the user has ever
 *    received before filtering to the one peer. This compares query latency
 *    with the flag off vs on, and the batch-insert cost the extra index adds.
 *
 * 2. **large-IN tag watcher (reactions shape)** — `kinds=[7] AND
 *    #e=[hundreds of note ids] LIMIT n`. The per-value streams come sorted
 *    off `(tag_hash, kind, created_at)`, but their union does not, so SQLite
 *    collects every matching row and TEMP-B-TREE sorts to the limit — the
 *    tag-index analogue of the follow-feed regression
 *    [MergeQueryExecutor] fixed for author streams. Reported with and
 *    without a `since` bound to show what EOSE-warm steady state hides.
 *
 * Size the seed with `-DtagBenchScale=N` (default 1 ≈ ~200k events).
 */
class TagAuthorIndexBenchmark {
    companion object {
        val SCALE = System.getProperty("tagBenchScale")?.toInt() ?: 1
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
        tags: Array<Array<String>>,
    ): Event = EventFactory.create(hex64(7, idSeq++), pubkey, createdAt, kind, tags, "", sig)

    private fun seedEvents(): List<Event> {
        idSeq = 0
        val base = 1_700_000_000L
        val span = 3_000_000L // ~35 days
        val me = hex64(9, 0)
        val events = ArrayList<Event>(220_000 * SCALE)

        // DM inbox: 200 peers, 300 DMs each → 60k kind-4 rows sharing the
        // same (p:me) tag hash. The room query wants one peer's 300.
        val peers = (0 until 200).map { hex64(2, it) }
        for ((i, peer) in peers.withIndex()) {
            repeat(300 * SCALE) {
                val ts = base + (mix(i * 131L + it) and 0x7fffffff) % span
                events.add(ev(peer, ts, 4, arrayOf(arrayOf("p", me))))
            }
        }

        // Notification noise: 2000 authors mention me in kind-1 notes, so
        // (p:me) spans multiple kinds like a real inbox does.
        repeat(40_000 * SCALE) {
            val author = hex64(3, it % 2_000)
            val ts = base + (mix(it * 17L) and 0x7fffffff) % span
            events.add(ev(author, ts, 1, arrayOf(arrayOf("p", me))))
        }

        // Reactions: 100k kind-7 events spread over 5000 target notes, for
        // the large-IN watcher shape.
        val noteIds = (0 until 5_000).map { hex64(5, it) }
        repeat(100_000 * SCALE) {
            val author = hex64(4, it % 3_000)
            val ts = base + (mix(it * 29L) and 0x7fffffff) % span
            events.add(ev(author, ts, 7, arrayOf(arrayOf("e", noteIds[it % noteIds.size]))))
        }
        return events
    }

    @Test
    fun compareTagAuthorIndex() =
        runBlocking {
            val events = seedEvents()
            val me = hex64(9, 0)
            val peers = (0 until 200).map { hex64(2, it) }
            val noteIds = (0 until 5_000).map { hex64(5, it) }

            println("─ TagAuthorIndexBenchmark: ${events.size} events (scale=$SCALE) ─")

            val strategies =
                listOf(
                    "flag-off" to DefaultIndexingStrategy(indexFullTextSearch = false),
                    "flag-on " to DefaultIndexingStrategy(indexFullTextSearch = false, indexTagsWithKindAndPubkey = true),
                )

            for ((label, strategy) in strategies) {
                val store = EventStore(dbName = null, indexStrategy = strategy)

                val t0 = System.nanoTime()
                events.chunked(10_000).forEach { store.batchInsert(it) }
                val insertMs = (System.nanoTime() - t0) / 1e6
                println("  ═ $label ═  insert: %.0f ms (%.1f µs/event)".format(insertMs, insertMs * 1000 / events.size))

                // 1. DM room: one peer's DMs out of the whole (p:me) inbox.
                val room = Filter(kinds = listOf(4), authors = listOf(peers[42]), tags = mapOf("p" to listOf(me)), limit = 100)
                time(store, "dm-room (#p ∩ author ∩ kind, limit 100)", room)

                // 2. Reactions watcher: 300 note ids, cold (no since).
                val watcher = Filter(kinds = listOf(7), tags = mapOf("e" to noteIds.take(300)), limit = 500)
                time(store, "reactions (#e IN 300, limit 500, cold)", watcher)

                // 3. Same watcher, EOSE-warm (since bounds the window).
                val warm = watcher.copy(since = 1_700_000_000L + 2_900_000L)
                time(store, "reactions (#e IN 300, limit 500, since)", warm)

                store.close()
            }
        }

    private suspend fun time(
        store: EventStore,
        label: String,
        filter: Filter,
    ) {
        repeat(3) { store.query<Event>(filter) }
        val runs = 10
        var rows = 0
        val start = System.nanoTime()
        repeat(runs) { rows = store.query<Event>(filter).size }
        val ms = (System.nanoTime() - start) / 1e6 / runs
        println("    %-42s %8.2f ms  (%d rows)".format(label, ms, rows))
    }
}
