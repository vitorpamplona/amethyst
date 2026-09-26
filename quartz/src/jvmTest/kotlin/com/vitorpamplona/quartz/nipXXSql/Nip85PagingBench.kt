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
package com.vitorpamplona.quartz.nipXXSql

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test

/**
 * Paging through one service key's NIP-85 contact cards (kind 30382) for `(d, rank)`
 * over NQL, against REQ pages of whole events. Off unless `NIP85_BENCH=<cards>`;
 * `NIP85_TV=1` keeps `event_tag_values`, `NIP85_SLOW=1` adds the `ORDER BY CAST`
 * walk, which re-sorts every card per page. Needs `--rerun`.
 */
class Nip85PagingBench {
    @Test
    fun bench() {
        val n = System.getenv("NIP85_BENCH")?.toIntOrNull() ?: return
        val tagValues = System.getenv("NIP85_TV") == "1"
        val store = EventStore(dbName = null, relay = null, indexStrategy = DefaultIndexingStrategy(indexTagValues = tagValues))
        val service = NostrSignerSync()
        val other = NostrSignerSync()
        val rnd = Random(3)
        val targets = List(n) { Random(it).nextBytes(32).joinToString("") { b -> "%02x".format(b) } }
        var t = System.currentTimeMillis()
        runBlocking {
            store.transaction {
                targets.forEachIndexed { i, pk ->
                    fun card(s: NostrSignerSync) =
                        s.sign<Event>(
                            1_700_000_000L + i,
                            30382,
                            arrayOf(
                                arrayOf("d", pk),
                                arrayOf("rank", rnd.nextInt(101).toString()),
                                arrayOf("followers", rnd.nextInt(5000).toString()),
                                arrayOf("hops", rnd.nextInt(6).toString()),
                                arrayOf("post_cnt", rnd.nextInt(900).toString()),
                            ),
                            "",
                        )
                    insert(card(service))
                    if (i % 6 == 0) insert(card(other))
                }
            }
        }
        println("BENCH85 load $n (+${n / 6} noise) ${System.currentTimeMillis() - t} ms tagValues=$tagValues")
        runBlocking { store.store.analyse() }
        val svc = service.pubKey

        fun page(
            q: String,
            p: List<Any?>,
        ): NqlResult = runBlocking { store.nql(q, p) }

        // 1. Keyset by target pubkey.
        val byPk =
            "SELECT d.t1 AS target, CAST(r.t1 AS INTEGER) AS rank FROM tags AS d JOIN tags AS r ON r.event_id = d.event_id " +
                "WHERE d.kind = 30382 AND d.pubkey = ? AND d.t0 = 'd' AND r.t0 = 'rank' AND d.t1 > ? ORDER BY target LIMIT 1000"
        // 2. Keyset by rank, best first.
        val byRank =
            "SELECT d.t1 AS target, CAST(r.t1 AS INTEGER) AS rank FROM tags AS d JOIN tags AS r ON r.event_id = d.event_id " +
                "WHERE d.kind = 30382 AND d.pubkey = ? AND d.t0 = 'd' AND r.t0 = 'rank' " +
                "AND (CAST(r.t1 AS INTEGER) < ? OR (CAST(r.t1 AS INTEGER) = ? AND d.t1 > ?)) ORDER BY rank DESC, target LIMIT 1000"
        // 3. By rank, one rank value at a time (an indexed text equality), keyset by pubkey inside it.
        val inBucket =
            "SELECT d.t1 AS target, CAST(r.t1 AS INTEGER) AS rank FROM tags AS r JOIN tags AS d ON d.event_id = r.event_id " +
                "WHERE r.kind = 30382 AND r.pubkey = ? AND r.t0 = 'rank' AND r.t1 = ? AND d.t0 = 'd' AND d.t1 > ? ORDER BY target LIMIT 1000"
        for ((name, walk) in listOf<Pair<String, () -> Pair<Int, Long>>>(
            "nql by rank buckets" to {
                var rows = 0
                var bytes = 0L
                for (rank in 100 downTo 0) {
                    var last = ""
                    while (true) {
                        val r = page(inBucket, listOf(svc, rank.toString(), last))
                        if (r.rows.isEmpty()) break
                        rows += r.rows.size
                        bytes += r.rows.sumOf { 10L + it.joinToString(",").length }
                        last = r.rows.last()[0] as String
                        if (r.rows.size < 1000) break
                    }
                }
                rows to bytes
            },
            "nql by pubkey" to {
                var last = ""
                var rows = 0
                var bytes = 0L
                while (true) {
                    val r = page(byPk, listOf(svc, last))
                    if (r.rows.isEmpty()) break
                    rows += r.rows.size
                    bytes += r.rows.sumOf { 10L + it.joinToString(",").length }
                    last = r.rows.last()[0] as String
                }
                rows to bytes
            },
            "nql by rank (ORDER BY CAST)" to {
                if (System.getenv("NIP85_SLOW") == null) return@to 0 to 0L
                var lastRank = 101L
                var last = ""
                var rows = 0
                var bytes = 0L
                while (true) {
                    val r = page(byRank, listOf(svc, lastRank, lastRank, last))
                    if (r.rows.isEmpty()) break
                    rows += r.rows.size
                    bytes += r.rows.sumOf { 10L + it.joinToString(",").length }
                    last = r.rows.last()[0] as String
                    lastRank = r.rows.last()[1] as Long
                }
                rows to bytes
            },
            "REQ pages" to {
                var until: Long? = null
                var rows = 0
                var bytes = 0L
                val seen = HashSet<String>()
                while (true) {
                    val evs = runBlocking { store.query<Event>(Filter(kinds = listOf(30382), authors = listOf(svc), until = until, limit = 1000)) }
                    val fresh = evs.filter { seen.add(it.id) }
                    if (fresh.isEmpty()) break
                    rows += fresh.size
                    bytes += fresh.sumOf { it.toJson().length.toLong() }
                    until = evs.minOf { it.createdAt }
                }
                rows to bytes
            },
        )) {
            t = System.currentTimeMillis()
            val (rows, bytes) = walk()
            println("BENCH85 $name: $rows rows, ${bytes / 1_000_000} MB, ${System.currentTimeMillis() - t} ms total")
        }
        val s1 = System.nanoTime()
        page(byPk, listOf(svc, ""))
        val s2 = System.nanoTime()
        page(byRank, listOf(svc, 101L, 101L, ""))
        println("BENCH85 first page: by pubkey ${(s2 - s1) / 1_000_000} ms, by rank ${(System.nanoTime() - s2) / 1_000_000} ms")
        store.close()
    }
}
