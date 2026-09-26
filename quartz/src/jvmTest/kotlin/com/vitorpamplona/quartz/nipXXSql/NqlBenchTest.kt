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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.DefaultIndexingStrategy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test

class NqlBenchTest {
    @Test
    fun bench() {
        if (System.getenv("NQL_BENCH") == null) return
        val tagValues = System.getenv("NQL_BENCH") == "tagvalues"
        val store = EventStore(dbName = null, relay = null, indexStrategy = DefaultIndexingStrategy(indexTagValues = tagValues))
        val rnd = Random(1)
        val authors = List(200) { NostrSignerSync() }
        val notes = ArrayList<String>()
        val hashtags = List(500) { "tag$it" }
        val t0 = System.currentTimeMillis()
        runBlocking {
            repeat(40_000) { i ->
                val a = authors[rnd.nextInt(authors.size)]
                val e = a.sign<Event>(1_700_000_000L + i, 1, arrayOf(arrayOf("t", hashtags[rnd.nextInt(500)]), arrayOf("t", hashtags[rnd.nextInt(50)])), "note $i")
                notes.add(e.id)
                store.insert(e)
            }
            repeat(20_000) { i ->
                val a = authors[rnd.nextInt(authors.size)]
                val target = notes[rnd.nextInt(notes.size)]
                store.insert(a.sign<Event>(1_700_100_000L + i, 7, arrayOf(arrayOf("e", target), arrayOf("p", authors[0].pubKey)), "+"))
            }
        }
        println("BENCH load ${System.currentTimeMillis() - t0} ms (tag values: $tagValues)")
        // Planner statistics, as a running relay has them.
        runBlocking { store.store.analyse() }
        val author = authors[3].pubKey
        val queries =
            listOf(
                "count all" to "SELECT count(*) AS n FROM events",
                "count kind" to "SELECT count(*) AS n FROM events WHERE kind = 1",
                "group by kind" to "SELECT kind, count(*) AS n FROM events GROUP BY kind",
                "top hashtags" to "SELECT t1 AS tag, count(*) AS n FROM tags WHERE t0 = 't' GROUP BY t1 ORDER BY n DESC, tag LIMIT 10",
                "one hashtag" to "SELECT count(*) AS n FROM tags WHERE t0 = 't' AND t1 = 'tag7'",
                "newest 50" to "SELECT id, content FROM events WHERE kind = 1 ORDER BY created_at DESC, id LIMIT 50",
                "per-author notes" to "SELECT id, content FROM events WHERE kind = 1 AND pubkey = '$author' ORDER BY created_at DESC, id",
                "reactions per note of author" to "SELECT notes.id, count(r.event_id) AS total FROM events AS notes LEFT JOIN tags AS r ON r.t1 = notes.id AND r.kind = 7 AND r.t0 = 'e' WHERE notes.kind = 1 AND notes.pubkey = '$author' GROUP BY notes.id ORDER BY total DESC, notes.id LIMIT 20",
                "most reacted notes" to "SELECT r.t1 AS note, count(*) AS n FROM tags AS r WHERE r.kind = 7 AND r.t0 = 'e' GROUP BY r.t1 ORDER BY n DESC, note LIMIT 10",
            )
        for ((name, q) in queries) {
            val times = ArrayList<Long>()
            var rows = 0
            repeat(6) {
                val s = System.nanoTime()
                rows = runBlocking { store.nql(q).rows.size }
                times.add((System.nanoTime() - s) / 1_000_000)
            }
            println("BENCH $name: median ${times.drop(1).sorted()[2]} ms (rows $rows)")
        }
        store.close()
    }
}
