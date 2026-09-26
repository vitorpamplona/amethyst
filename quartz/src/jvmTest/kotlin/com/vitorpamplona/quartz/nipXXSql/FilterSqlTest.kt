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
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FilterSql] must answer every filter exactly as the store's own `query` /
 * `count` does, over the store's backend and over one that only answers
 * filters (the shape a Vespa-like store has), paging past a small row cap,
 * and the rebuilt events must be byte-identical to the stored ones.
 */
class FilterSqlTest {
    private val store = EventStore(dbName = null, relay = null)
    private val signers = List(3) { NostrSignerSync() }
    private val words = listOf("a", "b", "c", "")
    private val kinds = listOf(1, 7, 20, 1111)

    /** Only answers filters and refuses nothing: every reference is a real scan. */
    private val filterOnly = FilterStoreBackend(store)

    init {
        val rnd = Random(11)
        runBlocking {
            repeat(80) { i ->
                val tags = ArrayList<Array<String>>()
                repeat(rnd.nextInt(5)) {
                    tags +=
                        when (rnd.nextInt(5)) {
                            0 -> arrayOf("t", words[rnd.nextInt(words.size)])
                            1 -> arrayOf("p", signers[rnd.nextInt(signers.size)].pubKey, "wss://r", "mention")
                            2 -> arrayOf("l", words[rnd.nextInt(words.size)], "ns", "x", "y", "z", "w")
                            3 -> arrayOf("e")
                            else -> arrayOf("tt", words[rnd.nextInt(words.size)])
                        }
                }
                // Distinct created_at: a limit then picks one set whatever the tie order.
                store.insert(signers[i % signers.size].sign<Event>(1000L + i, kinds[rnd.nextInt(kinds.size)], tags.toTypedArray(), "c$i"))
            }
        }
    }

    @AfterTest
    fun close() = store.close()

    private fun randomFilter(rnd: Random): Filter {
        fun <T> maybe(v: () -> T): T? = if (rnd.nextInt(3) == 0) v() else null

        fun tagMap() =
            maybe {
                val m = HashMap<String, List<String>>()
                repeat(1 + rnd.nextInt(2)) {
                    when (rnd.nextInt(3)) {
                        0 -> m["t"] = words.shuffled(rnd).take(1 + rnd.nextInt(2))
                        1 -> m["p"] = signers.shuffled(rnd).take(1 + rnd.nextInt(2)).map { it.pubKey }
                        else -> m["l"] = words.shuffled(rnd).take(1 + rnd.nextInt(2))
                    }
                }
                m
            }
        val since = maybe { 1000L + rnd.nextInt(80) }
        return Filter(
            authors = maybe { signers.shuffled(rnd).take(1 + rnd.nextInt(2)).map { it.pubKey } },
            kinds = maybe { kinds.shuffled(rnd).take(1 + rnd.nextInt(3)) },
            tags = tagMap(),
            tagsAll = if (rnd.nextInt(6) == 0) mapOf("t" to words.shuffled(rnd).take(2)) else null,
            since = since,
            until = maybe { (since ?: 1000L) + rnd.nextInt(80) },
            limit = maybe { 1 + rnd.nextInt(15) },
        )
    }

    private fun sqlite(
        q: FilterSql.Query,
        maxRows: Int? = null,
    ): NqlResult = runBlocking { store.nql(q.nql, q.params, maxRows) }

    private fun pushdown(
        q: FilterSql.Query,
        maxRows: Int? = null,
    ): NqlResult = runBlocking { Nql.run(q.nql, q.params, filterOnly, maxRows) }

    /** Every event [filter] matches, paging past a [cap] rows per answer as a client does; how many came whole from elsewhere. */
    private fun events(
        filter: Filter,
        run: (FilterSql.Query, Int?) -> NqlResult,
        cap: Int = 7,
    ): Pair<List<Event>, Int> {
        val ids = ArrayList<IdAndTime>()
        while (true) {
            val remaining = filter.limit?.let { it - ids.size }
            if (remaining != null && remaining <= 0) break
            val page = run(FilterSql.ids(filter, ids.lastOrNull(), remaining), cap)
            page.rows.forEach { ids.add(IdAndTime(it[1] as Long, it[0] as String)) }
            if (!page.truncated) break
        }
        val out = ArrayList<Event>()
        var fetchedWhole = 0
        for (chunk in ids.map { it.id }.chunked(FilterSql.HYDRATE_CHUNK)) {
            val collector = FilterSql.Collector()
            var lastId: String? = null
            while (true) {
                val page = run(FilterSql.events(chunk, lastId), cap)
                page.rows.forEach(collector::addEvent)
                if (!page.truncated) break
                lastId = page.rows.last()[0] as String
            }
            var after: Pair<String, Long>? = null
            while (true) {
                val page = run(FilterSql.tags(chunk, after), cap)
                page.rows.forEach(collector::addTag)
                if (!page.truncated) break
                after = page.rows.last()[0] as String to page.rows.last()[1] as Long
            }
            val result = collector.finish()
            val whole = runBlocking { store.query<Event>(Filter(ids = result.incomplete)) }.associateBy { it.id }
            fetchedWhole += whole.size
            val byId = result.events.associateBy { it.id }
            chunk.forEach { id -> (byId[id] ?: whole[id])?.let(out::add) }
        }
        return out to fetchedWhole
    }

    private fun Event.wire() = toJson()

    @Test
    fun randomFiltersMatchTheStore() {
        val rnd = Random(3)
        var nonEmpty = 0
        repeat(300) {
            val f = randomFilter(rnd)
            val expected = runBlocking { store.query<Event>(f) }.sortedByDescending { it.createdAt }.map { it.wire() }
            if (expected.isNotEmpty()) nonEmpty++
            val expectedCount = runBlocking { store.count(f.copy(limit = null)) }.toLong()

            assertEquals(expected, events(f, ::sqlite).first.map { it.wire() }, "sqlite: ${f.toJson()}")
            assertEquals(expected, events(f, ::pushdown).first.map { it.wire() }, "pushdown: ${f.toJson()}")
            assertEquals(expectedCount, sqlite(FilterSql.count(f)).rows.single()[0], "count: ${f.toJson()}")
            assertEquals(expectedCount, pushdown(FilterSql.count(f)).rows.single()[0], "pushdown count: ${f.toJson()}")
        }
        assertTrue(nonEmpty > 100, "the generator should mostly hit something: $nonEmpty")
    }

    @Test
    fun rebuiltEventsKeepEveryTagPosition() {
        val all = runBlocking { store.query<Event>(Filter()) }.sortedByDescending { it.createdAt }
        val (rebuilt, fetchedWhole) = events(Filter(), ::sqlite)
        assertEquals(all.map { it.wire() }, rebuilt.map { it.wire() })
        // NQL shows five elements of a tag: events with a longer one are caught by their id and fetched whole.
        assertTrue(rebuilt.any { e -> e.tags.any { it.size == 7 } }, "a tag past t4 should round-trip")
        assertEquals(all.count { e -> e.tags.any { it.size > 5 } }, fetchedWhole)
        assertTrue(rebuilt.any { e -> e.tags.any { it.size == 1 } }, "a name-only tag should round-trip")
    }

    @Test
    fun tagFiltersAreDrivenOffTheTagsTable() {
        val q = FilterSql.ids(Filter(kinds = listOf(1), tags = mapOf("t" to listOf("a"))))
        assertTrue(q.nql.startsWith("SELECT DISTINCT t.event_id"), q.nql)
    }
}
