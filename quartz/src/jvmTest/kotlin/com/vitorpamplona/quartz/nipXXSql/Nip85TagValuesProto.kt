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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test

/**
 * Prototype, in plain SQL, of `event_tag_values` carrying the event's author
 * (`pubkey_id`, through a pubkey dictionary) so a provider's tags page off one
 * index. Off unless `NIP85_PROTO=<cards>`.
 */
class Nip85TagValuesProto {
    private inline fun ms(block: () -> Unit): Long {
        val s = System.nanoTime()
        block()
        return (System.nanoTime() - s) / 1_000_000
    }

    @Test
    fun proto() {
        val n = System.getenv("NIP85_PROTO")?.toIntOrNull() ?: return
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
        }
        val svc = service.pubKey

        fun <T> db(block: (SQLiteConnection) -> T): T = runBlocking { store.store.pool.useReader(block) }

        fun exec(sql: String) = db { c -> c.prepare(sql).use { while (it.step()) Unit } }

        fun long(sql: String) =
            db { c ->
                c.prepare(sql).use {
                    it.step()
                    it.getLong(0)
                }
            }

        fun bytes() = long("PRAGMA page_count") * long("PRAGMA page_size")

        fun plan(sql: String) = db { c -> c.prepare("EXPLAIN QUERY PLAN $sql").use { st -> buildList { while (st.step()) add(st.getText(3)) } } }

        fun walk(sql: String): Pair<Int, Long> {
            var last = ""
            var rows = 0
            val t =
                ms {
                    while (true) {
                        val got =
                            db { c ->
                                c.prepare(sql).use { st ->
                                    st.bindText(1, svc)
                                    st.bindText(2, last)
                                    var k = 0
                                    while (st.step()) {
                                        last = st.getText(0)
                                        if (!st.isNull(1)) st.getLong(1)
                                        k++
                                    }
                                    k
                                }
                            }
                        if (got == 0) break
                        rows += got
                    }
                }
            return rows to t
        }
        val tagRows = long("SELECT count(*) FROM event_tag_values")
        println("PROTO tag rows: $tagRows, db ${bytes() / 1_000_000} MB before")

        // 1. The dictionary and the column, backfilled.
        var t =
            ms {
                exec("CREATE TABLE event_pubkeys (id INTEGER PRIMARY KEY, pubkey TEXT NOT NULL UNIQUE)")
                exec("INSERT OR IGNORE INTO event_pubkeys (pubkey) SELECT pubkey FROM event_headers")
                exec("ALTER TABLE event_tag_values ADD COLUMN pubkey_id INTEGER")
                exec("UPDATE event_tag_values SET pubkey_id = (SELECT p.id FROM event_headers h JOIN event_pubkeys p ON p.pubkey = h.pubkey WHERE h.row_id = event_tag_values.event_header_row_id)")
            }
        val afterCol = bytes()
        println("PROTO dictionary + column backfill: $t ms, db ${afterCol / 1_000_000} MB")
        t = ms { exec("CREATE INDEX event_tag_values_by_author ON event_tag_values (t0, kind, pubkey_id, t1, event_header_row_id)") }
        val afterIdx = bytes()
        println("PROTO author index: $t ms, +${(afterIdx - afterCol) / 1_000_000} MB")
        exec("ANALYZE")

        val guard =
            "CASE WHEN r.t1 GLOB '[0-9]*' AND r.t1 NOT GLOB '*[^0-9]*' AND (length(ltrim(r.t1, '0')) < 19 OR (length(ltrim(r.t1, '0')) = 19 AND ltrim(r.t1, '0') <= '9223372036854775807')) " +
                "THEN CAST(r.t1 AS INTEGER) WHEN r.t1 GLOB '[+-][0-9]*' AND substr(r.t1, 2) NOT GLOB '*[^0-9]*' AND (length(ltrim(substr(r.t1, 2), '0')) < 19 OR (length(ltrim(substr(r.t1, 2), '0')) = 19 AND ltrim(substr(r.t1, 2), '0') <= '9223372036854775807')) THEN CAST(r.t1 AS INTEGER) END"

        fun page(rank: String) =
            "SELECT d.t1, $rank FROM event_tag_values d JOIN event_tag_values r ON r.event_header_row_id = d.event_header_row_id AND r.t0 = 'rank' " +
                "WHERE d.t0 = 'd' AND d.kind = 30382 AND d.pubkey_id = (SELECT id FROM event_pubkeys WHERE pubkey = ?1) AND d.t1 > ?2 ORDER BY d.t1 LIMIT 1000"

        plan(page(guard)).forEach { println("PROTO   plan: $it") }
        walk(page(guard))
        var r = walk(page(guard))
        println("PROTO paged walk, strict CAST: ${r.first} rows in ${r.second} ms")
        r = walk(page("CAST(r.t1 AS INTEGER)"))
        println("PROTO paged walk, plain CAST: ${r.first} rows in ${r.second} ms")

        // 2. Covering the per-card rank lookup too.
        t = ms { exec("CREATE INDEX event_tag_values_by_event_value ON event_tag_values (event_header_row_id, t0, t1)") }
        println("PROTO covering event index: $t ms, +${(bytes() - afterIdx) / 1_000_000} MB")
        exec("ANALYZE")
        plan(page(guard)).forEach { println("PROTO   plan: $it") }
        walk(page(guard))
        r = walk(page(guard))
        println("PROTO paged walk + covering, strict CAST: ${r.first} rows in ${r.second} ms")
        r = walk(page("CAST(r.t1 AS INTEGER)"))
        println("PROTO paged walk + covering, plain CAST: ${r.first} rows in ${r.second} ms")
        store.close()
    }
}
