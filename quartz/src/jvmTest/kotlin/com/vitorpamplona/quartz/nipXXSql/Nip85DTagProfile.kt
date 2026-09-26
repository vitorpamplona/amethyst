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
 * A NIP-85 walk driven by the store's own `(kind, pubkey, d_tag)` addressable
 * index, the rank fetched per card from `event_tag_values` or the tag JSON.
 * Off unless `NIP85_DTAG=<cards>`.
 */
class Nip85DTagProfile {
    private inline fun ms(block: () -> Unit): Long {
        val s = System.nanoTime()
        block()
        return (System.nanoTime() - s) / 1_000_000
    }

    @Test
    fun profile() {
        val n = System.getenv("NIP85_DTAG")?.toIntOrNull() ?: return
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

        fun <T> db(block: (SQLiteConnection) -> T): T = runBlocking { store.store.pool.useReader(block) }

        fun plan(sql: String) = db { c -> c.prepare("EXPLAIN QUERY PLAN $sql").use { st -> buildList { while (st.step()) add(st.getText(3)) } } }

        fun walk(
            name: String,
            sql: String,
        ) {
            plan(sql.replace("?1", "'$svc'").replace("?2", "''")).forEach { println("DTAG     plan: $it") }
            var rows = 0
            val best =
                (1..3).minOf {
                    rows = 0
                    var last = ""
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
                                            if (!st.isNull(1)) st.getText(1)
                                            k++
                                        }
                                        k
                                    }
                                }
                            if (got == 0) break
                            rows += got
                        }
                    }
                }
            println("DTAG $name: $rows rows, $best ms (300 pages)")
        }

        val addressable = "h.kind = 30382 AND h.kind >= 30000 AND h.kind < 40000 AND h.pubkey = ?1 AND h.d_tag > ?2"
        walk(
            "d_tag index + rank from event_tag_values",
            "SELECT h.d_tag, (SELECT tv.t1 FROM event_tag_values tv WHERE tv.event_header_row_id = h.row_id AND tv.t0 = 'rank') " +
                "FROM event_headers h WHERE $addressable ORDER BY h.d_tag LIMIT 1000",
        )
        walk(
            "d_tag index + rank from the tag JSON",
            "SELECT h.d_tag, (SELECT j.value ->> 1 FROM json_each(h.tags) j WHERE j.value ->> 0 = 'rank') " +
                "FROM event_headers h WHERE $addressable ORDER BY h.d_tag LIMIT 1000",
        )
        walk(
            "d_tag index alone (floor, no rank)",
            "SELECT h.d_tag, NULL FROM event_headers h WHERE $addressable ORDER BY h.d_tag LIMIT 1000",
        )
        // The rank lookup read from the index alone.
        db { c -> c.prepare("CREATE INDEX event_tag_values_by_event_value ON event_tag_values (event_header_row_id, t0, t1)").use { it.step() } }
        db { c -> c.prepare("ANALYZE").use { it.step() } }
        walk(
            "d_tag index + rank from a covering event_tag_values index",
            "SELECT h.d_tag, (SELECT tv.t1 FROM event_tag_values tv WHERE tv.event_header_row_id = h.row_id AND tv.t0 = 'rank') " +
                "FROM event_headers h WHERE $addressable ORDER BY h.d_tag LIMIT 1000",
        )
        store.close()
    }
}
