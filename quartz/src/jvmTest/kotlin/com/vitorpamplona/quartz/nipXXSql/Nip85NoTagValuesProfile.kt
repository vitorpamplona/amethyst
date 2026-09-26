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

import androidx.sqlite.SQLiteConnection
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test

/**
 * Where a NIP-85 walk's time goes on a store without `event_tag_values`: the
 * author filter, the tag JSON, or the self-join. Off unless `NIP85_NOTV=<cards>`.
 */
class Nip85NoTagValuesProfile {
    private inline fun ms(block: () -> Unit): Long {
        val s = System.nanoTime()
        block()
        return (System.nanoTime() - s) / 1_000_000
    }

    @Test
    fun profile() {
        val n = System.getenv("NIP85_NOTV")?.toIntOrNull() ?: return
        val store = EventStore(dbName = null, relay = null)
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

        fun <T> db(block: (SQLiteConnection) -> T): T = runBlocking { store.store.pool.useReader(block) }

        fun plan(sql: String) = db { c -> c.prepare("EXPLAIN QUERY PLAN $sql").use { st -> buildList { while (st.step()) add(st.getText(3)) } } }

        /** Runs [sql] with [svc] bound to ?1, stepping every row; returns (rows, ms), best of 3. */
        fun run(
            name: String,
            sql: String,
        ) {
            var rows = 0
            val best =
                (1..3).minOf {
                    ms {
                        rows =
                            db { c ->
                                c.prepare(sql).use { st ->
                                    if (sql.contains("?1")) st.bindText(1, svc)
                                    var k = 0
                                    while (st.step()) {
                                        for (i in 0 until st.getColumnCount()) if (!st.isNull(i)) st.getText(i)
                                        k++
                                    }
                                    k
                                }
                            }
                    }
                }
            println("NOTV $name: $rows rows, $best ms")
            plan(sql.replace("?1", "'$svc'")).forEach { println("NOTV     plan: $it") }
        }

        val (checked, values) =
            Nql.prepare(
                "SELECT d.t1 AS target, CAST(r.t1 AS INTEGER) AS rank FROM tags AS d JOIN tags AS r ON r.event_id = d.event_id " +
                    "WHERE d.kind = 30382 AND d.pubkey = ? AND d.t0 = 'd' AND r.t0 = 'rank'",
                listOf(svc),
            )
        val compiled = NqlSqliteCompiler(values, null, false).compile(checked)
        println("NOTV compiled SQL: ${compiled.sql.replace("\n", " ")}")
        println("NOTV compiled binds: ${compiled.binds}")
        var nqlRows = 0
        val nqlMs =
            (1..3).minOf {
                ms {
                    nqlRows =
                        runBlocking {
                            store.nql(
                                "SELECT d.t1 AS target, CAST(r.t1 AS INTEGER) AS rank FROM tags AS d JOIN tags AS r ON r.event_id = d.event_id " +
                                    "WHERE d.kind = 30382 AND d.pubkey = ? AND d.t0 = 'd' AND r.t0 = 'rank'",
                                listOf(svc),
                            )
                        }.rows.size
                }
            }
        println("NOTV nql (store.nql) one query: $nqlRows rows, $nqlMs ms")
        run("compiled SQL as-is", compiled.sql.replace(Regex("\\?(\\d+)")) { m -> compiled.binds[m.groupValues[1].toInt() - 1].let { if (it is String) "'$it'" else it.toString() } })

        // Isolating the parts.
        run("1 author filter, index only (count)", "SELECT count(*) FROM event_headers WHERE kind = 30382 AND pubkey = ?1")
        run("2 full-string pubkey compare on every row (table scan)", "SELECT count(*) FROM event_headers NOT INDEXED WHERE pubkey = ?1")
        run("2b same scan, integer compare only", "SELECT count(*) FROM event_headers NOT INDEXED WHERE kind = 30382")
        run("3 read d_tag + tags JSON", "SELECT d_tag, tags FROM event_headers WHERE kind = 30382 AND pubkey = ?1")
        run(
            "4 unpack tags JSON, one pass, no join",
            "SELECT h.d_tag, j.value ->> 1 FROM event_headers h, json_each(h.tags) j WHERE h.kind = 30382 AND h.pubkey = ?1 AND j.value ->> 0 = 'rank'",
        )
        run(
            "5 unpack twice + self-join on the event",
            "SELECT a.value ->> 1, b.value ->> 1 FROM event_headers h, json_each(h.tags) a, json_each(h.tags) b " +
                "WHERE h.kind = 30382 AND h.pubkey = ?1 AND a.value ->> 0 = 'd' AND b.value ->> 0 = 'rank'",
        )
        store.close()
    }
}
