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
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pages read in `d` order ([SqlStoreBackend.eventsInDOrder]) with batches of one:
 * every batch ends inside a tie group (four signers share each `d`), and a join
 * that drops cards without a rank leaves pages short, so both the tie completion
 * and the refill run. Each page must be the SQLite store's own.
 */
class NqlDOrderTest {
    private val signers = List(4) { NostrSignerSync() }
    private val store = EventStore(dbName = null, relay = null)

    @AfterTest
    fun close() {
        NqlExecutor.dOrderMinBatch = 100
        store.close()
    }

    @Test
    fun pagesMatchTheStore() =
        runBlocking {
            var t = 1000L
            for (d in listOf("a", "b", "c", "d", "e")) {
                signers.forEachIndexed { i, s ->
                    val tags = if ((i + d[0].code) % 3 == 0) arrayOf(arrayOf("d", d)) else arrayOf(arrayOf("d", d), arrayOf("rank", (i * 10 + d[0].code % 7).toString()))
                    store.insert(s.sign<Event>(t++, 30382, tags, ""))
                }
            }
            val backend = DOrderBackend(store)
            NqlExecutor.dOrderMinBatch = 1
            val queries =
                listOf(
                    "SELECT e.d AS target, e.id FROM events AS e WHERE e.kind = 30382 AND e.d > ? ORDER BY target, e.id LIMIT 2",
                    "SELECT e.d AS target, e.id, CAST(r.t1 AS INTEGER) AS rank FROM events AS e JOIN tags AS r ON r.event_id = e.id AND r.t0 = 'rank' " +
                        "WHERE e.kind = 30382 AND e.d > ? ORDER BY target, e.id LIMIT 3",
                    "SELECT e.d AS target, e.id FROM events AS e JOIN tags AS r ON r.event_id = e.id AND r.t0 = 'rank' " +
                        "WHERE e.kind = 30382 AND e.d > ? AND CAST(r.t1 AS INTEGER) > 15 ORDER BY target, e.id LIMIT 2 OFFSET 1",
                    "SELECT e.d AS target, r.t1 AS rank FROM events AS e LEFT JOIN tags AS r ON r.event_id = e.id AND r.t0 = 'rank' " +
                        "WHERE e.kind = 30382 AND e.d > ? ORDER BY target, rank LIMIT 3",
                )
            for (q in queries) {
                for (after in listOf("", "a", "b", "c", "d", "e")) {
                    val expected = store.nql(q, listOf(after)).rows
                    assertEquals(expected, Nql.run(q, listOf(after), backend).rows, "$q after '$after'")
                }
            }
            // One read per page (24), plus the refills of short pages.
            assertTrue(backend.ordered > 24, "the d-ordered read should be taken, and refill: ${backend.ordered}")
        }
}
