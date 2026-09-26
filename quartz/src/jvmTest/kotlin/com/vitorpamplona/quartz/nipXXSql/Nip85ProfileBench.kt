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

/** Where the time of a NIP-85 walk goes. Off unless `NIP85_PROFILE=<cards>`. */
class Nip85ProfileBench {
    private inline fun ms(block: () -> Unit): Long {
        val s = System.nanoTime()
        block()
        return (System.nanoTime() - s) / 1_000_000
    }

    @Test
    fun profile() {
        val n = System.getenv("NIP85_PROFILE")?.toIntOrNull() ?: return
        val store = EventStore(dbName = null, relay = null, indexStrategy = DefaultIndexingStrategy(indexTagValues = true))
        val service = NostrSignerSync()
        val other = NostrSignerSync()
        val rnd = Random(3)
        runBlocking {
            store.transaction {
                repeat(n) { i ->
                    val pk = Random(i).nextBytes(32).joinToString("") { b -> "%02x".format(b) }

                    fun card(s: NostrSignerSync) =
                        s.sign<Event>(
                            1_700_000_000L + i,
                            30382,
                            arrayOf(arrayOf("d", pk), arrayOf("rank", rnd.nextInt(101).toString()), arrayOf("followers", rnd.nextInt(5000).toString()), arrayOf("hops", "2"), arrayOf("post_cnt", "7")),
                            "",
                        )
                    insert(card(service))
                    if (i % 6 == 0) insert(card(other))
                }
            }
            store.store.analyse()
        }
        val svc = service.pubKey
        val sqlite = store.store

        fun raw(
            sql: String,
            vararg binds: String,
        ): Int =
            runBlocking {
                sqlite.pool.useReader { db ->
                    db.prepare(sql).use { st ->
                        binds.forEachIndexed { i, b -> st.bindText(i + 1, b) }
                        var c = 0
                        while (st.step()) {
                            st.getText(0)
                            st.getText(1)
                            c++
                        }
                        c
                    }
                }
            }

        fun plan(sql: String) =
            runBlocking {
                sqlite.pool.useReader { db ->
                    db.prepare("EXPLAIN QUERY PLAN $sql").use { st ->
                        buildList { while (st.step()) add(st.getText(3)) }
                    }
                }
            }

        val page =
            "SELECT d.t1 AS target, CAST(r.t1 AS INTEGER) AS rank FROM tags AS d JOIN tags AS r ON r.event_id = d.event_id " +
                "WHERE d.kind = 30382 AND d.pubkey = ? AND d.t0 = 'd' AND r.t0 = 'rank' AND d.t1 > ? ORDER BY target LIMIT 1000"
        val all =
            "SELECT d.t1 AS target, r.t1 AS rank FROM tags AS d JOIN tags AS r ON r.event_id = d.event_id " +
                "WHERE d.kind = 30382 AND d.pubkey = ? AND d.t0 = 'd' AND r.t0 = 'rank'"

        // The compiled SQL and its plan.
        val (checked, values) = Nql.prepare(page, listOf(svc, ""))
        val compiled = NqlSqliteCompiler(values, null, true).compile(checked)
        println("PROF compiled page SQL:\n${compiled.sql}")
        plan(compiled.sql.replace(Regex("\\?(\\d+)")) { m -> "'" + (compiled.binds[m.groupValues[1].toInt() - 1] ?: "") + "'" }).forEach { println("PROF   plan: $it") }

        // NQL overhead per page, outside SQLite.
        repeat(3) { runBlocking { store.nql(page, listOf(svc, "")) } }
        val prep = ms { repeat(300) { Nql.prepare(page, listOf(svc, "")) } }
        val comp = ms { repeat(300) { NqlSqliteCompiler(values, null, true).compile(checked) } }
        println("PROF 300x parse+check: $prep ms, 300x compile: $comp ms")

        // Paged walk through NQL, without any byte counting.
        var last = ""
        var rows = 0
        val walk =
            ms {
                while (true) {
                    val r = runBlocking { store.nql(page, listOf(svc, last)) }
                    if (r.rows.isEmpty()) break
                    rows += r.rows.size
                    last = r.rows.last()[0] as String
                }
            }
        println("PROF nql paged walk: $rows rows in $walk ms")

        // One NQL query, no paging.
        var one = 0
        println("PROF nql one query (no LIMIT): ${ms { one = runBlocking { store.nql(all, listOf(svc)) }.rows.size }} ms, $one rows")

        // Hand-written SQL on the same tables: the floor.
        val hand =
            "SELECT h.d_tag, tv.t1 FROM event_headers h JOIN event_tag_values tv ON tv.event_header_row_id = h.row_id " +
                "WHERE h.kind = 30382 AND h.pubkey = ? AND tv.t0 = 'rank'"
        plan(hand.replace("?", "'$svc'")).forEach { println("PROF   hand plan: $it") }
        var h = 0
        repeat(2) { raw(hand, svc) }
        println("PROF hand SQL one query: ${ms { h = raw(hand, svc) }} ms, $h rows")
        val handJson = "SELECT d_tag, tags FROM event_headers WHERE kind = 30382 AND pubkey = ?"
        println("PROF hand SQL d_tag + tags JSON: ${ms { h = raw(handJson, svc) }} ms, $h rows")

        // Floors: an index-only scan of 300k entries, and a purpose-built projection table.
        val idxOnly = "SELECT created_at, created_at FROM event_headers WHERE kind = 30382 AND pubkey = ?"
        plan(idxOnly.replace("?", "'$svc'")).forEach { println("PROF   index-only plan: $it") }
        println("PROF index-only scan, 2 columns: ${ms { h = raw(idxOnly, svc) }} ms, $h rows")
        runBlocking {
            sqlite.pool.useReader { db ->
                db.prepare("CREATE TABLE rank_proj (provider TEXT, target TEXT, rank TEXT, PRIMARY KEY (provider, target)) WITHOUT ROWID").use { it.step() }
                db
                    .prepare(
                        "INSERT INTO rank_proj SELECT h.pubkey, h.d_tag, tv.t1 FROM event_headers h JOIN event_tag_values tv ON tv.event_header_row_id = h.row_id WHERE h.kind = 30382 AND tv.t0 = 'rank'",
                    ).use { it.step() }
            }
        }
        val proj = "SELECT target, rank FROM rank_proj WHERE provider = ? ORDER BY target"
        println("PROF projection table, all rows: ${ms { h = raw(proj, svc) }} ms, $h rows")
        val projPage = "SELECT target, rank FROM rank_proj WHERE provider = ? AND target > ? ORDER BY target LIMIT 1000"
        var lastP = ""
        var pr = 0
        val tp =
            ms {
                while (true) {
                    val got =
                        runBlocking {
                            sqlite.pool.useReader { db ->
                                db.prepare(projPage).use { st ->
                                    st.bindText(1, svc)
                                    st.bindText(2, lastP)
                                    var c = 0
                                    while (st.step()) {
                                        lastP = st.getText(0)
                                        st.getText(1)
                                        c++
                                    }
                                    c
                                }
                            }
                        }
                    if (got == 0) break
                    pr += got
                }
            }
        println("PROF projection table, 300 pages: $tp ms, $pr rows")

        // REQ: whole events, paged by until, without and with toJson.
        for (withJson in listOf(false, true)) {
            var until: Long? = null
            var got = 0
            var bytes = 0L
            val t =
                ms {
                    while (true) {
                        val evs = runBlocking { store.query<Event>(Filter(kinds = listOf(30382), authors = listOf(svc), until = until, limit = 1000)) }
                        if (evs.isEmpty()) break
                        got += evs.size
                        if (withJson) bytes += evs.sumOf { it.toJson().length.toLong() }
                        until = evs.minOf { it.createdAt } - 1
                    }
                }
            println("PROF REQ pages (toJson=$withJson): $got events in $t ms")
        }
        var ev = 0
        println("PROF REQ one query, no limit: ${ms { ev = runBlocking { store.query<Event>(Filter(kinds = listOf(30382), authors = listOf(svc))) }.size }} ms, $ev events")
        store.close()
    }
}
