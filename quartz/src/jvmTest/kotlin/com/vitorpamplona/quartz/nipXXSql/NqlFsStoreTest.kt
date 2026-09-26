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
import com.vitorpamplona.quartz.nip01Core.store.fs.FsEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The filesystem store answers id walks, counts and tag-value listings off its
 * index trees (`FsSqlBackend`). Each query here must take that native path and
 * give the SQLite store's answer, over values stored raw and hashed (emoji,
 * URLs, the empty string), name-only tags and a deleted event.
 */
class NqlFsStoreTest {
    private val signers = List(3) { NostrSignerSync() }
    private val root = Files.createTempDirectory("nql-fs-native")
    private val fs = FsEventStore(root)
    private val sqlite = EventStore(dbName = null, relay = null)

    @AfterTest
    fun close() {
        fs.close()
        sqlite.close()
        root.toFile().deleteRecursively()
    }

    /** Counts the answers the store gave natively. */
    private class Spy(
        val inner: SqlStoreBackend,
    ) : SqlStoreBackend by inner {
        var native = 0

        override suspend fun aggregate(plan: AggregatePlan) = inner.aggregate(plan)?.also { native++ }

        override suspend fun idsAndTimes(
            spec: ScanSpec,
            onEach: (id: String, createdAt: Long) -> Unit,
        ) = inner.idsAndTimes(spec, onEach).also { if (it) native++ }
    }

    private val values = listOf("nostr", "Nostr", "💜", "", "https://a.b/c?d", "a b", "zap")

    private suspend fun insert(e: Event) {
        fs.insert(e)
        sqlite.insert(e)
    }

    private val native =
        listOf(
            "SELECT count(*) AS n FROM events",
            "SELECT kind, count(*) AS n, min(created_at) AS lo, max(created_at) AS hi FROM events GROUP BY kind ORDER BY kind",
            "SELECT pubkey, count(*) AS n FROM events WHERE kind IN (1, 7) AND created_at >= 1010 GROUP BY pubkey ORDER BY pubkey",
            "SELECT count(*) AS n FROM events WHERE pubkey = ? AND kind = 1",
            "SELECT kind, count(*) AS n FROM events WHERE pubkey = ? GROUP BY kind ORDER BY kind",
            "SELECT DISTINCT t1 FROM tags WHERE t0 = 't' AND t1 <> '' ORDER BY t1",
            "SELECT DISTINCT t1 FROM tags WHERE t0 = 't' AND t1 IN ('nostr', '', '💜', 'none') ORDER BY t1",
            "SELECT DISTINCT t1 FROM tags WHERE t0 = 't' AND kind = 7 AND t1 <> '' ORDER BY t1",
            "SELECT DISTINCT t1 FROM tags WHERE t0 = 'r' AND pubkey = ? AND t1 <> '' ORDER BY t1",
            "SELECT id, created_at FROM events WHERE kind = 1 ORDER BY created_at DESC, id LIMIT 5",
            "SELECT count(*) AS n FROM events WHERE id IN (SELECT event_id FROM tags WHERE t0 = 't' AND t1 = 'zap')",
        )

    @Test
    fun answersFromTheIndexTrees() =
        runBlocking {
            val ids = ArrayList<String>()
            repeat(60) { i ->
                val tags =
                    arrayOf(
                        arrayOf("t", values[i % values.size]),
                        arrayOf("t", values[(i * 3) % values.size]),
                        arrayOf("r", values[(i + 1) % values.size]),
                        arrayOf("x"),
                    )
                val e = signers[i % 3].sign<Event>(1000L + i % 25, listOf(1, 7, 1111)[i % 3], tags, "c$i")
                ids.add(e.id)
                insert(e)
            }
            fs.delete(Filter(ids = ids.take(3)))
            sqlite.delete(Filter(ids = ids.take(3)))

            val params = listOf(signers[1].pubKey)
            val spy = Spy(fs.sqlBackend())
            for (q in native) {
                val p = if ('?' in q) params else emptyList()
                val before = spy.native
                assertEquals(sqlite.nql(q, p).rows, Nql.run(q, p, spy).rows, q)
                assert(spy.native > before) { "not answered natively: $q" }
            }
        }
}
