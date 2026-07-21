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
package com.vitorpamplona.quartz.nip01Core.store.fs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test

/**
 * Quantifies [FsQueryPlanner]'s "first available driver wins" ordering
 * (tags → kinds → authors) on the `authors + kinds + limit` shape — the
 * most common CLI query (27 assembler call sites; every `amy feed`-style
 * author timeline over non-replaceable kinds).
 *
 * `Filter(authors=[pk], kinds=[1], limit=n)` drives from `idx/kind/1/`
 * (the biggest tree in any real store) and post-filters the author, even
 * though `idx/author/<pk>/` holds exactly that author's events. The
 * benchmark times:
 *
 *  - **kind-driver (current)**: the filter as the planner runs it today.
 *  - **author-driver (proposed)**: same result set, but driven from the
 *    author tree with the kind check as a post-filter — what a cost-based
 *    picker (compare candidate directory sizes) would choose.
 *
 * Also reports the author-only shape (`authors + limit`) as the floor: the
 * planner already picks the author tree there, so its time is the target.
 *
 * Size the seed with `-DfsBenchScale=N` (default 1 ≈ ~30k events; each
 * event is a file + ~3 hardlinks, so seeding dominates wall time).
 */
class FsDriverSelectionBenchmark {
    companion object {
        val SCALE = System.getProperty("fsBenchScale")?.toInt() ?: 1
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

    @Test
    fun compareDrivers() =
        runBlocking {
            val root: Path = Files.createTempDirectory("fs-driver-bench-")
            val store = FsEventStore(root)
            try {
                val base = 1_700_000_000L
                val span = 3_000_000L
                val target = hex64(9, 0)

                // Background: 300 authors × 100 kind-1 notes.
                val bg = ArrayList<Event>(30_000 * SCALE + 300)
                repeat(30_000 * SCALE) {
                    val author = hex64(1, it % 300)
                    bg.add(ev(author, base + (mix(it * 31L) and 0x7fffffff) % span, 1))
                }
                // Target author: 200 kind-1 notes + 50 kind-7 reactions.
                repeat(200) { bg.add(ev(target, base + (mix(it * 131L) and 0x7fffffff) % span, 1)) }
                repeat(50) { bg.add(ev(target, base + (mix(it * 61L) and 0x7fffffff) % span, 7)) }

                val t0 = System.nanoTime()
                store.transaction { bg.forEach { insert(it) } }
                val insertMs = (System.nanoTime() - t0) / 1e6
                println("─ FsDriverSelectionBenchmark: ${bg.size} events (scale=$SCALE), seed %.0f ms ─".format(insertMs))

                // Current planner: kinds present → kind tree drives, author
                // is a post-filter over the whole kind-1 listing.
                val kindDriven = Filter(authors = listOf(target), kinds = listOf(1), limit = 50)
                time(store, "kind-driver (current planner)") { store.query<Event>(kindDriven).size }

                // Proposed: drive from the author tree, post-filter kind —
                // same semantics, what a cost-based picker would run.
                time(store, "author-driver (proposed)") {
                    store
                        .query<Event>(Filter(authors = listOf(target), limit = 250))
                        .asSequence()
                        .filter { it.kind == 1 }
                        .take(50)
                        .count()
                }

                // Floor: author-only shape, planner already optimal here.
                val authorOnly = Filter(authors = listOf(target), limit = 50)
                time(store, "author-only (planner floor)") { store.query<Event>(authorOnly).size }
            } finally {
                store.close()
                if (root.exists()) {
                    Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
                }
            }
        }

    private inline fun time(
        store: FsEventStore,
        label: String,
        run: () -> Int,
    ) {
        repeat(3) { run() }
        val runs = 10
        var rows = 0
        val start = System.nanoTime()
        repeat(runs) { rows = run() }
        val ms = (System.nanoTime() - start) / 1e6 / runs
        println("    %-32s %8.2f ms  (%d rows)".format(label, ms, rows))
    }
}
