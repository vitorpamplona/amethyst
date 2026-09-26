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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What the NQL executor asks a store for, per query shape. */
class NqlPushdownTest {
    private val alice = NostrSignerSync()
    private val bob = NostrSignerSync()
    private val store = EventStore(dbName = null, relay = null)

    init {
        runBlocking {
            // Five kind-1 notes, three tied at created_at = 10.
            listOf(10L, 10L, 10L, 9L, 8L).forEachIndexed { i, t ->
                store.insert(alice.sign<Event>(t, 1, arrayOf(arrayOf("p", bob.pubKey)), "n$i"))
            }
            store.insert(bob.sign<Event>(20, 7, arrayOf(arrayOf("e", "x".repeat(64)), arrayOf("p", alice.pubKey)), "+"))
        }
    }

    @AfterTest
    fun close() = store.close()

    /** Records every request; returns tie groups in reverse id order to defeat any accidental reliance on it. */
    private inner class Recording(
        private val native: Boolean = false,
        private val refuseBroad: Boolean = false,
        private val walksIds: Boolean = false,
    ) : FilterStoreBackend(store) {
        val scans = ArrayList<ScanSpec>()
        val plans = ArrayList<AggregatePlan>()
        val idWalks = ArrayList<ScanSpec>()

        override suspend fun idsAndTimes(
            spec: ScanSpec,
            onEach: (id: String, createdAt: Long) -> Unit,
        ): Boolean {
            if (!walksIds) return false
            idWalks.add(spec)
            store.query<Event>(spec.toFilter()).forEach { onEach(it.id, it.createdAt) }
            return true
        }

        override suspend fun events(
            spec: ScanSpec,
            onEvent: (Event) -> Unit,
        ) {
            scans.add(spec)
            val all = store.query<Event>(spec.toFilter().copy(limit = null)).sortedWith(compareByDescending<Event> { it.createdAt }.thenByDescending { it.id })
            (if (spec.limit != null) all.take(spec.limit!!) else all).forEach(onEvent)
        }

        override fun acceptsScan(spec: ScanSpec) = !refuseBroad || spec.isSelective

        override suspend fun aggregate(plan: AggregatePlan): List<List<Any?>>? {
            plans.add(plan)
            return if (native) super.aggregate(plan) else null
        }
    }

    private fun rows(
        query: String,
        backend: SqlStoreBackend,
        vararg params: Any?,
    ): List<List<Any?>> = runBlocking { Nql.run(query, params.toList(), backend).rows }

    /** The reference: the store's own backend, which scans whatever it is asked. */
    private fun sqlite(
        query: String,
        vararg params: Any?,
    ): List<List<Any?>> = runBlocking { store.nql(query, params.toList()).rows }

    @Test
    fun newestFirstLimitIsPushedAndTiesAreCompleted() {
        val q = "SELECT content FROM events WHERE kind = 1 ORDER BY created_at DESC, id LIMIT 2"
        val backend = Recording()
        assertEquals(sqlite(q), rows(q, backend))
        // First the newest 2, then the whole created_at = 10 tie group.
        assertEquals(2, backend.scans[0].limit)
        assertEquals(10L, backend.scans[1].since)
        assertEquals(10L, backend.scans[1].until)
        assertEquals(null, backend.scans[1].limit)
    }

    @Test
    fun limitIsNotPushedWhenOrderIsNotNewestFirst() {
        val q = "SELECT content FROM events WHERE kind = 1 ORDER BY created_at, id LIMIT 2"
        val backend = Recording()
        assertEquals(sqlite(q), rows(q, backend))
        assertEquals(listOf<Int?>(null), backend.scans.map { it.limit })
    }

    @Test
    fun eachReferenceGetsItsOwnConditions() {
        val q = "SELECT count(*) AS n FROM events AS e JOIN tags AS t ON t.event_id = e.id WHERE e.kind = 1 AND t.t0 = 'p' AND t.t1 = ?"
        val backend = Recording()
        assertEquals(sqlite(q, bob.pubKey), rows(q, backend, bob.pubKey))
        val (events, tags) = backend.scans
        assertEquals(SqlProfile.EVENTS, events.table)
        assertEquals(setOf(1), events.kinds)
        assertEquals(SqlProfile.TAGS, tags.table)
        assertEquals("p", tags.tagName)
        assertEquals(setOf(bob.pubKey), tags.tagValues)
        assertEquals(mapOf("p" to listOf(bob.pubKey)), tags.toFilter().tags)
    }

    @Test
    fun aggregatesAreOfferedToTheStoreFirst() {
        val q = "SELECT pubkey, count(*) AS n, max(created_at) AS newest FROM events WHERE kind = 1 GROUP BY pubkey ORDER BY n DESC"
        val backend = Recording()
        assertEquals(sqlite(q), rows(q, backend))
        val plan = assertNotNull(backend.plans.singleOrNull())
        assertEquals(listOf("pubkey"), plan.groupBy)
        assertEquals(listOf("count" to null, "max" to "created_at"), plan.aggregates.map { it.function to it.column })
        assertTrue(plan.scan.exact)
        assertEquals(setOf(1), plan.scan.kinds)
    }

    @Test
    fun countIsAnsweredNativelyByTheGenericBackend() {
        val q = "SELECT count(*) AS n FROM events WHERE kind = 1 AND created_at >= 9"
        val backend = Recording(native = true)
        assertEquals(listOf(listOf(4L)), rows(q, backend))
        assertEquals(sqlite(q), rows(q, Recording(native = true)))
        assertTrue(backend.scans.isEmpty(), "count must not scan: ${backend.scans}")
    }

    @Test
    fun conditionsTheSpecCantHoldKeepAggregatesOffTheNativePath() {
        // `content LIKE` isn't a store condition: the plan would be inexact.
        val backend = Recording(native = true)
        val q = "SELECT count(*) AS n FROM events WHERE kind = 1 AND content LIKE 'n1%'"
        assertEquals(sqlite(q), rows(q, backend))
        assertTrue(backend.plans.isEmpty())
        assertEquals(1, backend.scans.size)
    }

    @Test
    fun storesCanRefuseBroadScans() {
        val backend = Recording(refuseBroad = true)
        val e = assertFailsWith<SqlException> { rows("SELECT content FROM events WHERE content LIKE '%x%'", backend) }
        assertEquals(SqlException.UNSUPPORTED, e.prefix)
        // A selective reference in the same query is fine; a broad one is not.
        assertFailsWith<SqlException> { rows("SELECT a.id FROM events AS a JOIN tags AS b ON b.kind = a.kind WHERE a.kind = 1 AND b.t2 IS NULL", backend) }
        assertEquals(sqlite("SELECT content FROM events WHERE kind = 7"), rows("SELECT content FROM events WHERE kind = 7", backend))
    }

    @Test
    fun aReferenceTheStoreRefusesIsFetchedByTheJoinKey() {
        // `d` has only a tag name, which no index answers; the join pins it to `l`'s events.
        val q = "SELECT d.t1 FROM tags AS l JOIN tags AS d ON d.event_id = l.event_id AND d.t0 = 'e' WHERE l.kind = 7 AND l.t0 = 'p'"
        val backend = Recording(refuseBroad = true)
        assertEquals(sqlite(q), rows(q, backend))
        assertEquals(1, sqlite(q).size)
        val (first, second) = backend.scans
        assertEquals(setOf(7), first.kinds)
        assertEquals(setOf(runBlocking { store.query<Event>(Filter(kinds = listOf(7))) }.single().id), second.ids)
    }

    @Test
    fun joinKeysCanBeTagValues() {
        // The reactions' `e` values are the ids to fetch the notes by; the notes carry no condition of their own.
        val q = "SELECT count(*) AS n FROM tags AS t JOIN events AS n ON n.id = t.t1 WHERE t.kind = 7 AND t.t0 = 'e'"
        val backend = Recording(refuseBroad = true)
        assertEquals(sqlite(q), rows(q, backend))
        assertEquals(setOf("x".repeat(64)), backend.scans[1].ids)
    }

    @Test
    fun aLeftJoinsPreservedSideIsNotNarrowedByItsOnClause() {
        // `e` keeps every row whatever `t` holds, so it must be fetched on its own conditions or refused.
        val backend = Recording(refuseBroad = true)
        assertFailsWith<SqlException> { rows("SELECT e.id FROM events AS e LEFT JOIN tags AS t ON t.event_id = e.id AND t.kind = 7", backend) }
        val q = "SELECT e.content, t.t0 FROM events AS e LEFT JOIN tags AS t ON t.event_id = e.id WHERE e.kind = 1 ORDER BY e.content, t.idx"
        assertEquals(sqlite(q), rows(q, Recording(refuseBroad = true)))
    }

    @Test
    fun aReferenceReadOnlyForIdsAndTimesIsAnIdWalk() {
        val filter = Filter(kinds = listOf(1), since = 9)
        val backend = Recording(walksIds = true)
        val ids = FilterSql.ids(filter)
        assertEquals(sqlite(ids.nql, *ids.params.toTypedArray()), rows(ids.nql, backend, *ids.params.toTypedArray()))
        assertEquals(1, backend.idWalks.size)
        assertTrue(backend.scans.isEmpty(), "no documents for an id listing: ${backend.scans}")

        // Anything else read of the reference needs the documents.
        val withContent = "SELECT id, content FROM events WHERE kind = 1 ORDER BY id"
        val second = Recording(walksIds = true)
        assertEquals(sqlite(withContent), rows(withContent, second))
        assertTrue(second.idWalks.isEmpty())
        // A star, or a qualifier used anywhere in the query, counts too.
        for (q in listOf("SELECT * FROM events WHERE kind = 1 ORDER BY id", "SELECT e.id FROM events AS e WHERE e.kind = 1 AND e.pubkey <> '' ORDER BY id")) {
            val b = Recording(walksIds = true)
            assertEquals(sqlite(q), rows(q, b))
            assertTrue(b.idWalks.isEmpty(), q)
        }
    }

    @Test
    fun mathFunctionsRunOnTagValuesEverywhere() {
        runBlocking {
            listOf("21000", "5000", "1000000").forEachIndexed { i, msats ->
                store.insert(bob.sign<Event>(30L + i, 9735, arrayOf(arrayOf("amount", msats)), ""))
            }
        }
        val q =
            "SELECT count(*) AS n, sum(CAST(t1 AS INTEGER)) / 1000 AS sats, round(sqrt(avg(CAST(t1 AS REAL))) * 1000) / 1000 AS root, " +
                "floor(log10(max(CAST(t1 AS REAL)))) AS digits, pow(2, 10) AS p, 21 % 4 AS m, abs(-7) AS a, ceil(2.5) AS c " +
                "FROM tags WHERE kind = 9735 AND t0 = 'amount'"
        val expected = listOf(listOf(3L, 1026L, 584.808, 6.0, 1024.0, 1L, 7L, 3.0))
        assertEquals(expected, sqlite(q))
        // Any store, Vespa included, gets the same functions over its scanned rows.
        assertEquals(expected, rows(q, Recording(refuseBroad = true)))
        // Outside the domain, the query fails.
        assertEquals(SqlException.ERROR, assertFailsWith<SqlException> { sqlite("SELECT sqrt(-1) AS x") }.prefix)
    }

    @Test
    fun contradictionsFetchNothing() {
        val backend = Recording()
        assertEquals(listOf(listOf(0L)), rows("SELECT count(*) AS n FROM events WHERE kind = 1 AND kind = 7", backend))
        assertTrue(backend.scans.isEmpty())
    }
}
