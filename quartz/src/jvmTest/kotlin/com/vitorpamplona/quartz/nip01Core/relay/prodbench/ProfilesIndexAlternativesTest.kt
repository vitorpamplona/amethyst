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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.explainQuery
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Answers "is there an index that beats the `INDEXED BY` pin without needing
 * the pin?" for the profiles shape (`kind=0 AND pubkey IN (…) ORDER BY
 * created_at DESC`). Adds candidate indexes and prints, for each, the plan
 * SQLite picks **unhinted** plus its timing — so the choice is measured, not
 * argued.
 */
class ProfilesIndexAlternativesTest {
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
    fun compareIndexAlternatives() =
        runBlocking {
            val store = EventStore(dbName = null, indexStrategy = DefaultIndexingStrategy(indexEventsByCreatedAtAlone = true, indexEventsByPubkeyAlone = true, indexFullTextSearch = false))
            val authors = (0 until 2_000).map { hex64(1, it) }
            var t = 1_700_000_000L
            val batch = ArrayList<Event>(10_000)
            for (a in authors) {
                batch.add(ev(a, t++, 0))
                repeat(20) { batch.add(ev(a, t++, 1)) }
                if (batch.size >= 10_000) {
                    store.batchInsert(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) store.batchInsert(batch)

            val cols = "id, pubkey, created_at, kind, tags, content, sig"
            val inList = authors.take(50).joinToString(",") { "'$it'" }
            val where = "WHERE kind = 0 AND pubkey IN ($inList) ORDER BY created_at DESC"

            fun addIndex(ddl: String) = runBlocking { store.store.pool.useWriter { it.prepare(ddl).use { s -> s.step() } } }

            fun tree(sql: String) =
                runBlocking { store.store.explainQuery(sql) }
                    .lineSequence()
                    .filter { it.contains("SEARCH") || it.contains("SCAN ") || it.contains("USE ") }
                    .joinToString(" | ") { it.trimStart('│', '├', '└', '─', ' ') }

            fun time(sql: String): Double {
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
                repeat(5) { run() }
                val runs = 30
                val start = System.nanoTime()
                repeat(runs) { run() }
                return (System.nanoTime() - start) / 1e6 / runs
            }

            val unhinted = "SELECT $cols FROM event_headers $where"
            val pinned = "SELECT $cols FROM event_headers INDEXED BY query_by_kind_pubkey_created $where"

            println("─ ProfilesIndexAlternatives: 42k events, 2k profiles, kind=0 + 50 authors + ORDER BY ─")
            println("  [stock indexes] unhinted:")
            println("    ${tree(unhinted)}  →  ${"%.3f".format(time(unhinted))} ms")
            println("  [stock indexes] pinned (kind,pubkey,created):")
            println("    ${tree(pinned)}  →  ${"%.3f".format(time(pinned))} ms")

            addIndex("CREATE INDEX alt_pubkey_kind_created ON event_headers (pubkey, kind, created_at DESC)")
            println("  + alt index (pubkey, kind, created_at) — unhinted:")
            println("    ${tree(unhinted)}  →  ${"%.3f".format(time(unhinted))} ms")

            addIndex("CREATE INDEX alt_kind_pubkey_ca ON event_headers (kind, pubkey, created_at)")
            println("  + alt index (kind, pubkey, created_at ASC) — unhinted:")
            println("    ${tree(unhinted)}  →  ${"%.3f".format(time(unhinted))} ms")

            // Does ANALYZE now let SQLite pick a selective index unhinted?
            addIndex("ANALYZE")
            println("  after ANALYZE (all indexes present) — unhinted:")
            println("    ${tree(unhinted)}  →  ${"%.3f".format(time(unhinted))} ms")

            store.close()
        }
}
