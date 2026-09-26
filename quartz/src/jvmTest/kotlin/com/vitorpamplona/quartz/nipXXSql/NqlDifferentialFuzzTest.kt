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

import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Random queries over a random corpus, answered by the NQL engine over three
 * backends (the SQLite store's own, id walks, and a store that refuses
 * unselective scans and so exercises join-key propagation) and by SQLite
 * itself over plain `events` / `tags` tables. The generator stays inside the
 * part of NQL where SQLite agrees once each ORDER BY term states its NULL
 * placement: joins, subqueries, grouping, the aggregates, text comparisons and
 * INTEGER arithmetic that can't overflow.
 */
class NqlDifferentialFuzzTest {
    private val signers = List(3) { NostrSignerSync() }
    private val store = EventStore(dbName = null, relay = null)
    private val oracle: SQLiteConnection = BundledSQLiteDriver().open(":memory:")
    private val words = listOf("a", "b", "c", "", "B")
    private val kinds = listOf(1, 7, 20, 1111)
    private val ids = ArrayList<String>()

    init {
        oracle.execSQL("PRAGMA case_sensitive_like = ON")
        oracle.execSQL("CREATE TABLE events (id TEXT, pubkey TEXT, created_at INTEGER, kind INTEGER, content TEXT, sig TEXT)")
        oracle.execSQL("CREATE TABLE tags (event_id TEXT, idx INTEGER, t0 TEXT, t1 TEXT, t2 TEXT, t3 TEXT, t4 TEXT, created_at INTEGER, kind INTEGER, pubkey TEXT)")
        val rnd = Random(7)
        runBlocking {
            repeat(120) { i ->
                val tags = ArrayList<Array<String>>()
                repeat(rnd.nextInt(5)) {
                    tags +=
                        when (rnd.nextInt(6)) {
                            0 -> arrayOf("t", words[rnd.nextInt(words.size)])
                            1 -> arrayOf("p", signers[rnd.nextInt(signers.size)].pubKey, "wss://r", "mention")
                            2 -> arrayOf("e", if (ids.isEmpty() || rnd.nextInt(4) == 0) "f".repeat(64) else ids[rnd.nextInt(ids.size)])
                            3 -> arrayOf("l", words[rnd.nextInt(words.size)], "ns", "x", "y", "z")
                            4 -> arrayOf("x")
                            else -> arrayOf("r", "wss://" + words[rnd.nextInt(words.size)], if (rnd.nextBoolean()) "read" else "write")
                        }
                }
                val e = signers[i % signers.size].sign<Event>(1000L + rnd.nextInt(60), kinds[rnd.nextInt(kinds.size)], tags.toTypedArray(), "c" + rnd.nextInt(20))
                ids.add(e.id)
                store.insert(e)
                oracle.prepare("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?)").use {
                    it.bindText(1, e.id)
                    it.bindText(2, e.pubKey)
                    it.bindLong(3, e.createdAt)
                    it.bindLong(4, e.kind.toLong())
                    it.bindText(5, e.content)
                    it.bindText(6, e.sig)
                    it.step()
                }
                e.tags.forEachIndexed { idx, tag ->
                    oracle.prepare("INSERT INTO tags VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)").use {
                        it.bindText(1, e.id)
                        it.bindLong(2, idx.toLong())
                        for (k in 0..4) if (k < tag.size) it.bindText(3 + k, tag[k]) else it.bindNull(3 + k)
                        it.bindLong(8, e.createdAt)
                        it.bindLong(9, e.kind.toLong())
                        it.bindText(10, e.pubKey)
                        it.step()
                    }
                }
            }
        }
    }

    @AfterTest
    fun close() {
        store.close()
        oracle.close()
    }

    /** One query in NQL, and in SQLite with every ORDER BY term's NULL placement spelled out. */
    private class Case(
        val nql: String,
        val sqlite: String,
        val params: List<Any?>,
    )

    /** `ORDER BY` terms: NQL sorts NULLs last ascending and first descending, which SQLite needs told. */
    private fun order(vararg terms: Pair<String, Boolean>): Pair<String, String> =
        " ORDER BY " + terms.joinToString(", ") { (t, desc) -> t + if (desc) " DESC" else "" } to
            " ORDER BY " + terms.joinToString(", ") { (t, desc) -> t + if (desc) " DESC NULLS FIRST" else " NULLS LAST" }

    private fun Random.pick(list: List<String>) = list[nextInt(list.size)]

    private fun generate(rnd: Random): Case {
        val params = ArrayList<Any?>()

        fun eventPreds(a: String): String {
            val preds = ArrayList<String>()
            if (rnd.nextInt(3) > 0) preds += "$a.kind IN (" + kinds.shuffled(rnd).take(1 + rnd.nextInt(2)).joinToString(", ") + ")"
            if (rnd.nextInt(3) == 0) {
                preds += "$a.pubkey = ?"
                params += signers[rnd.nextInt(signers.size)].pubKey
            }
            if (rnd.nextInt(3) == 0) preds += "$a.created_at >= " + (1000 + rnd.nextInt(60))
            if (rnd.nextInt(5) == 0) preds += "$a.created_at BETWEEN 1010 AND " + (1010 + rnd.nextInt(50))
            if (rnd.nextInt(5) == 0) preds += "$a.content LIKE 'c1%'"
            if (rnd.nextInt(6) == 0) preds += "$a.content > 'c5'"
            return preds.joinToString(" AND ").ifEmpty { "$a.created_at > 0" }
        }

        fun tagPreds(a: String): String {
            val preds = ArrayList<String>()
            val name = rnd.pick(listOf("t", "p", "e", "l", "r", "x"))
            preds += if (rnd.nextInt(4) == 0) "$a.t0 IN ('t', 'l')" else "$a.t0 = '$name'"
            when (rnd.nextInt(5)) {
                0 -> preds += "$a.t1 IN (" + words.shuffled(rnd).take(2).joinToString(", ") { "'$it'" } + ")"
                1 -> preds += "$a.t1 <> ''"
                2 -> preds += "$a.t2 IS NULL"
                3 -> preds += "$a.kind IN (" + kinds.shuffled(rnd).take(2).joinToString(", ") + ")"
            }
            if (rnd.nextInt(4) == 0) preds += "$a.created_at < " + (1000 + rnd.nextInt(60))
            return preds.joinToString(" AND ")
        }

        val (nql, sqlite) =
            when (rnd.nextInt(10)) {
                0 -> {
                    val (o, so) = order("e.created_at" to true, "e.id" to false)
                    val limit = if (rnd.nextBoolean()) " LIMIT " + rnd.nextInt(8) + if (rnd.nextBoolean()) " OFFSET " + rnd.nextInt(4) else "" else ""
                    val base = "SELECT e.id, e.content, e.kind * 2 + 1 AS k, length(e.content) AS len FROM events AS e WHERE " + eventPreds("e")
                    (base + o + limit) to (base + so + limit)
                }

                1 -> {
                    val (o, so) = order("n" to true, "v" to false)
                    val base = "SELECT t.t1 AS v, count(*) AS n, count(DISTINCT t.pubkey) AS authors, min(t.created_at) AS first FROM tags AS t WHERE " + tagPreds("t") + " GROUP BY t.t1"
                    (base + o) to (base + so)
                }

                2 -> {
                    val (o, so) = order("e.id" to false, "p.idx" to false)
                    val base = "SELECT e.id, p.t1 AS target, p.t2 AS relay FROM events AS e JOIN tags AS p ON p.event_id = e.id AND p.t0 = 'p' WHERE " + eventPreds("e")
                    (base + o) to (base + so)
                }

                3 -> {
                    val (o, so) = order("total" to true, "e.id" to false)
                    val base =
                        "SELECT e.id, count(t.event_id) AS total, max(t.t1) AS top FROM events AS e LEFT JOIN tags AS t ON t.event_id = e.id AND " + tagPreds("t") +
                            " WHERE " + eventPreds("e") + " GROUP BY e.id"
                    (base + o) to (base + so)
                }

                4 -> {
                    val (o, so) = order("n.id" to false, "r.event_id" to false)
                    val base = "SELECT n.id, n.kind, r.event_id FROM tags AS r JOIN events AS n ON n.id = r.t1 WHERE r.t0 = 'e' AND r.kind IN (" + kinds.shuffled(rnd).take(2).joinToString(", ") + ")"
                    (base + o) to (base + so)
                }

                5 -> {
                    val (o, so) = order("e.id" to false)
                    val base = "SELECT e.id FROM events AS e WHERE " + eventPreds("e") + " AND e.id IN (SELECT x.event_id FROM tags AS x WHERE " + tagPreds("x") + ")"
                    (base + o) to (base + so)
                }

                6 -> {
                    val (o, so) = order("e.id" to false)
                    val not = if (rnd.nextBoolean()) "NOT " else ""
                    val base =
                        "SELECT e.id, (SELECT count(*) FROM tags AS c WHERE c.event_id = e.id) AS ntags FROM events AS e WHERE " + eventPreds("e") +
                            " AND ${not}EXISTS (SELECT x.idx FROM tags AS x WHERE x.event_id = e.id AND " + tagPreds("x") + ")"
                    (base + o) to (base + so)
                }

                7 -> {
                    val (o, so) = order("e.pubkey" to false, "e.kind" to false)
                    val base =
                        "SELECT e.pubkey, e.kind, count(*) AS n, sum(e.created_at) AS s, avg(e.kind) AS mean FROM events AS e WHERE " + eventPreds("e") +
                            " GROUP BY e.pubkey, e.kind HAVING count(*) > " + rnd.nextInt(3)
                    (base + o) to (base + so)
                }

                8 -> {
                    val (o, so) = order("v" to rnd.nextBoolean())
                    val base = "SELECT DISTINCT t.t2 AS v FROM tags AS t WHERE " + tagPreds("t")
                    (base + o) to (base + so)
                }

                else -> {
                    val (o, so) = order("a.event_id" to false, "b.idx" to false)
                    val base = "SELECT a.event_id, b.t0, b.t1 FROM tags AS a JOIN tags AS b ON b.event_id = a.event_id AND b.t0 = 'l' WHERE " + tagPreds("a")
                    (base + o) to (base + so)
                }
            }
        return Case(nql, sqlite, params)
    }

    private fun sqlite(c: Case): List<List<Any?>> =
        oracle.prepare(c.sqlite).use { st ->
            c.params.forEachIndexed { i, v -> st.bindText(i + 1, v as String) }
            val rows = ArrayList<List<Any?>>()
            while (st.step()) {
                rows +=
                    (0 until st.getColumnCount()).map { i ->
                        when (st.getColumnType(i)) {
                            SQLITE_DATA_NULL -> null
                            SQLITE_DATA_INTEGER -> st.getLong(i)
                            SQLITE_DATA_FLOAT -> st.getDouble(i)
                            else -> st.getText(i)
                        }
                    }
            }
            rows
        }

    @Test
    fun matchesSqliteOverEveryBackend() {
        val rnd = Random(42)
        val backends =
            listOf<Pair<String, suspend (Case) -> NqlResult>>(
                "compiled" to { c -> store.nql(c.nql, c.params) },
                "store" to { c -> Nql.run(c.nql, c.params, store.sqlBackend()) },
                "id walks" to { c -> Nql.run(c.nql, c.params, IdWalkBackend(store)) },
                "selective" to { c -> Nql.run(c.nql, c.params, SelectiveBackend(store)) },
            )
        val problems = ArrayList<String>()
        var nonEmpty = 0
        var selectiveAnswered = 0
        repeat(400) {
            val c = generate(rnd)
            val expected = sqlite(c)
            if (expected.isNotEmpty()) nonEmpty++
            for ((name, run) in backends) {
                val got =
                    try {
                        runBlocking { run(c) }.rows
                    } catch (e: SqlException) {
                        if (name == "selective" && e.prefix == SqlException.UNSUPPORTED) continue
                        problems += "$name refused: ${e.message}\n  ${c.nql}"
                        continue
                    }
                if (name == "selective") selectiveAnswered++
                if (got != expected) problems += "$name: ${c.nql}\n  params ${c.params}\n  nql    $got\n  sqlite $expected"
            }
        }
        if (problems.isNotEmpty()) fail("${problems.size} mismatches:\n" + problems.take(10).joinToString("\n"))
        assertTrue(nonEmpty > 250, "the generator should mostly hit something: $nonEmpty")
        assertTrue(selectiveAnswered > 150, "the selective store should answer most queries: $selectiveAnswered")
    }
}
