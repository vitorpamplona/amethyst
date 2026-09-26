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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `event_tag_values` ([DefaultIndexingStrategy.indexTagValues]) follows the
 * store: backfilled when the flag is turned on over existing events, kept
 * current on insert and delete, dropped when the flag is turned off. Checked by
 * answering the same NQL through it and through the interpreter.
 */
class NqlTagValuesTest {
    private val alice = NostrSignerSync()
    private val db: Path = Files.createTempFile("nql-tag-values-", ".db").also { Files.deleteIfExists(it) }

    @AfterTest
    fun cleanup() {
        listOf("", "-wal", "-shm", "-journal").forEach { Path.of(db.toString() + it).deleteIfExists() }
    }

    private fun open(tagValues: Boolean) = EventStore(dbName = db.toString(), relay = null, indexStrategy = DefaultIndexingStrategy(indexTagValues = tagValues))

    private val queries =
        listOf(
            "SELECT t1, count(*) AS n FROM tags WHERE t0 = 't' GROUP BY t1 ORDER BY t1",
            "SELECT e.content, t.t0, t.t1 FROM events AS e JOIN tags AS t ON t.event_id = e.id ORDER BY e.content, t.idx",
            "SELECT count(DISTINCT event_id) AS n FROM tags",
            "SELECT t.idx, t.t0 FROM tags AS t WHERE t.event_id IN (SELECT id FROM events WHERE content = 'n2') ORDER BY t.idx",
        )

    /** The compiled answers must be the interpreter's, over the same store. */
    private fun assertAgrees(store: EventStore) =
        runBlocking {
            for (q in queries) assertEquals(Nql.run(q, emptyList(), store.sqlBackend()).rows, store.nql(q).rows, q)
        }

    @Test
    fun followsTheStore() =
        runBlocking {
            open(tagValues = false).let { s ->
                repeat(4) { i -> s.insert(alice.sign<Event>(10L + i, 1, arrayOf(arrayOf("t", "x$i"), arrayOf("t", "all"), arrayOf("long", "1", "2", "3", "4", "5")), "n$i")) }
                s.close()
            }
            // Turned on over existing events: backfilled.
            open(tagValues = true).let { s ->
                assertAgrees(s)
                assertEquals(listOf(listOf<Any?>(4L)), s.nql("SELECT count(*) AS n FROM tags WHERE t0 = 't' AND t1 = 'all'").rows)
                // Kept current on insert and delete.
                s.insert(alice.sign<Event>(20L, 1, arrayOf(arrayOf("t", "all")), "n4"))
                s.delete(Filter(ids = listOf(s.query<Event>(Filter(search = null)).first { it.content == "n0" }.id)))
                assertAgrees(s)
                assertEquals(listOf(listOf<Any?>(4L)), s.nql("SELECT count(*) AS n FROM tags WHERE t0 = 't' AND t1 = 'all'").rows)
                s.close()
            }
            // Off: dropped, so turning it on again can't find a stale table.
            open(tagValues = false).let { s ->
                s.insert(alice.sign<Event>(30L, 1, arrayOf(arrayOf("t", "all")), "n5"))
                assertAgrees(s)
                s.close()
            }
            open(tagValues = true).let { s ->
                assertAgrees(s)
                assertEquals(listOf(listOf<Any?>(5L)), s.nql("SELECT count(*) AS n FROM tags WHERE t0 = 't' AND t1 = 'all'").rows)
                s.close()
            }
        }
}
