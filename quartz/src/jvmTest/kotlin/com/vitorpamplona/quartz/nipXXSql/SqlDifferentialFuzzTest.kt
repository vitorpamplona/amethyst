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
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Differential test: SQLite is the spec, so for any query the profile
 * accepts, the compiled statement must return exactly what SQLite returns
 * for the client's raw text run against `events` / `tags` views built from
 * the same [TableSource]s. Same rows, same column names, same errors.
 *
 * The generator writes operators without parentheses on purpose, so any
 * precedence the parser gets wrong shows up as a row mismatch.
 */
class SqlDifferentialFuzzTest {
    private lateinit var dbFile: Path
    private lateinit var store: EventStore

    /** Runs compiled queries. */
    private lateinit var profileConn: SQLiteConnection

    /** Runs the raw text against TEMP views: the reference. */
    private lateinit var oracleConn: SQLiteConnection

    /** What the reference views are built from: no pushdown. */
    private val sources = EventStoreTableSources.sources

    /** What the profile compiles against: with the `event_tags` pushdown. */
    private lateinit var pushdownSources: SqlTableSources

    @BeforeTest
    fun setup() {
        dbFile = Files.createTempFile("nostr-sql-fuzz-", ".db")
        Files.deleteIfExists(dbFile)
        store = EventStore(dbName = dbFile.toAbsolutePath().toString(), relay = null)

        val rnd = Random(7)
        val signers = List(4) { NostrSignerSync() }
        val words = listOf("nostr", "sql", "Nostr", "relay", "zap", "a_b", "50%", "")
        runBlocking {
            repeat(60) { i ->
                val s = signers[i % signers.size]
                val kind = listOf(0, 1, 1, 1, 3, 7, 20, 30023)[rnd.nextInt(8)]
                val tags = ArrayList<Array<String>>()
                repeat(rnd.nextInt(4)) { tags.add(arrayOf("t", words[rnd.nextInt(words.size)])) }
                if (rnd.nextBoolean()) tags.add(arrayOf("p", signers[rnd.nextInt(signers.size)].pubKey, "wss://r", "mention"))
                if (kind == 30023) tags.add(arrayOf("d", "slug-$i"))
                if (rnd.nextInt(5) == 0) tags.add(arrayOf("imeta", "url x", "m y", "a", "b", "c", "d"))
                val content = words[rnd.nextInt(words.size)] + " " + i
                // Older replaceables (kinds 0/3) are refused by the store; that's fine here.
                runCatching { store.insert(s.sign<Event>(1_700_000_000L + rnd.nextInt(1000), kind, tags.toTypedArray(), content)) }
            }
        }

        val driver = BundledSQLiteDriver()
        profileConn = driver.open(dbFile.toAbsolutePath().toString())
        profileConn.execSQL("PRAGMA query_only = ON")
        oracleConn = driver.open(dbFile.toAbsolutePath().toString())
        pushdownSources = EventStoreTableSources.forStore(TagNameValueHasher(store.store.seedModule.getSeed(profileConn)), store.store.indexStrategy)
        oracleConn.execSQL("CREATE TEMP VIEW events AS ${sources.events.sql}")
        oracleConn.execSQL("CREATE TEMP VIEW tags AS ${sources.tags.sql}")
    }

    @AfterTest
    fun tearDown() {
        profileConn.close()
        oracleConn.close()
        store.close()
        listOf("", "-wal", "-shm", "-journal").forEach { Path.of(dbFile.toString() + it).deleteIfExists() }
    }

    private class Outcome(
        val columns: List<String>,
        val rows: List<String>,
        val error: Boolean,
    ) {
        override fun toString() = if (error) "ERROR" else "$columns ${rows.size} rows ${rows.take(5)}"
    }

    private fun runOn(
        conn: SQLiteConnection,
        sql: String,
        args: List<Any?>,
    ): Outcome =
        try {
            SqlCursor(conn, CompiledQuery(sql, args)).use { c ->
                // Multiset comparison: generated queries rarely have a total order.
                Outcome(c.columns, c.fetch(100_000).map { it.toString() }.sorted(), false)
            }
        } catch (e: Exception) {
            if (e is SqlException) throw e
            Outcome(emptyList(), emptyList(), true)
        }

    @Test
    fun compiledQueriesMatchSqliteOnRawText() {
        val gen = QueryGen(Random(42))
        var compared = 0
        var engineErrors = 0
        var rejectedBoth = 0
        var pushedDown = 0
        repeat(3000) { n ->
            val sql = gen.query()
            val expected = runOn(oracleConn, sql, emptyList())
            val compiled =
                try {
                    SqlCompiler.compile(sql, pushdownSources)
                } catch (e: SqlException) {
                    // Refusing what SQLite refuses is fine; refusing what it runs is a parser bug.
                    if (!expected.error) fail("profile rejected a query SQLite runs, #$n: ${e.message}\n$sql")
                    rejectedBoth++
                    return@repeat
                }
            val actual = runOn(profileConn, compiled.sql, compiled.args)
            if (expected.error != actual.error || expected.columns != actual.columns || expected.rows != actual.rows) {
                fail("mismatch on #$n\n  query:    $sql\n  compiled: ${compiled.sql}\n  sqlite:   $expected\n  profile:  $actual")
            }
            compared++
            if (compiled.sql.contains("event_tags")) pushedDown++
            if (expected.error) engineErrors++
        }
        // Keep the generator honest: most queries must actually run.
        assertTrue(engineErrors + rejectedBoth < compared / 5, "too many invalid queries: $engineErrors + $rejectedBoth of $compared")
        // Keep the pushdown under test: enough queries must actually take it.
        assertTrue(pushedDown > compared / 10, "only $pushedDown of $compared queries used the tags pushdown")
    }

    private fun runPushdown(
        sql: String,
        backend: SqlStoreBackend,
    ): Outcome =
        try {
            runBlocking {
                SqlPushdown.open(sql, emptyList(), emptyMap(), backend).use { rows ->
                    Outcome(rows.columns, rows.fetch(100_000).map { it.toString() }.sorted(), false)
                }
            }
        } catch (e: Exception) {
            if (e is SqlException) throw e
            Outcome(emptyList(), emptyList(), true)
        }

    /**
     * The pushdown executor ([SqlPushdown]) must answer exactly what SQLite
     * does, both through per-reference scans (the generic backend) and
     * through native aggregates (a reference backend that computes them in
     * Kotlin from the plan alone).
     */
    @Test
    fun pushdownMatchesSqlite() {
        val selective = SelectiveBackend(store)
        val walks = IdWalkBackend(store)
        val backends =
            listOf("scans" to FilterStoreBackend(store), "native" to KotlinAggregateBackend(store), "selective" to selective, "idWalks" to walks)
        for ((label, backend) in backends) {
            val gen = QueryGen(Random(43))
            var compared = 0
            var native = 0
            var refused = 0
            repeat(2000) { n ->
                val sql = gen.query()
                val expected = runOn(oracleConn, sql, emptyList())
                val actual =
                    try {
                        runPushdown(sql, backend)
                    } catch (e: SqlException) {
                        // A store may refuse a scan; it may never answer wrong.
                        if (backend === selective && e.prefix == SqlException.UNSUPPORTED) {
                            refused++
                            return@repeat
                        }
                        if (!expected.error) fail("[$label] pushdown rejected a query SQLite runs, #$n: ${e.message}\n$sql")
                        return@repeat
                    }
                if (expected.error != actual.error || expected.columns != actual.columns || expected.rows != actual.rows) {
                    fail("[$label] mismatch on #$n\n  query:    $sql\n  sqlite:   $expected\n  pushdown: $actual")
                }
                compared++
            }
            if (backend is KotlinAggregateBackend) native = backend.answered
            if (backend === selective) {
                println("selective: compared=$compared refused=$refused joinNarrowed=${selective.narrowed} idWalks=${selective.idWalks}")
                assertTrue(selective.idWalks > 20, "only ${selective.idWalks} id walks")
                assertTrue(compared > 500, "[$label] only $compared compared")
                assertTrue(selective.narrowed > 10, "join keys narrowed only ${selective.narrowed} scans")
                continue
            }
            assertTrue(compared > 1500, "[$label] only $compared compared")
            if (backend === walks) assertTrue(walks.walks > 200, "only ${walks.walks} id walks")
            if (label == "native") assertTrue(native > 100, "only $native queries were answered natively")
        }
    }

    /** Accepts every scan and walks ids wherever the query allows, so every header-only reference takes that path. */
    private class IdWalkBackend(
        store: EventStore,
    ) : FilterStoreBackend(store) {
        var walks = 0

        override suspend fun idsAndTimes(
            spec: ScanSpec,
            onEach: (id: String, createdAt: Long) -> Unit,
        ): Boolean {
            walks++
            events(spec) { onEach(it.id, it.createdAt) }
            return true
        }
    }

    /** Refuses unselective scans, as an index-only store does; counts scans that only a join key made selective. */
    private class SelectiveBackend(
        store: EventStore,
    ) : FilterStoreBackend(store) {
        var narrowed = 0

        var idWalks = 0

        override fun acceptsScan(spec: ScanSpec) = spec.isSelective

        override suspend fun events(
            spec: ScanSpec,
            onEvent: (Event) -> Unit,
        ) {
            if (!spec.exact && spec.ids != null) narrowed++
            super.events(spec, onEvent)
        }

        /** An id walk: if a query that reads more than ids and times ever lands here, its rows go wrong and the diff shows it. */
        override suspend fun idsAndTimes(
            spec: ScanSpec,
            onEach: (id: String, createdAt: Long) -> Unit,
        ): Boolean {
            idWalks++
            super.events(spec) { onEach(it.id, it.createdAt) }
            return true
        }
    }

    /**
     * Answers [AggregatePlan]s from the plan alone, the way a store's own
     * engine would: fetch the spec's events, keep the rows the spec
     * describes, group and aggregate. Declines what it can't reproduce
     * exactly (JSON `rest`, `sum` over text).
     */
    private class KotlinAggregateBackend(
        store: EventStore,
    ) : FilterStoreBackend(store) {
        var answered = 0

        override suspend fun aggregate(plan: AggregatePlan): List<List<Any?>>? {
            val spec = plan.scan
            val touched = plan.groupBy + plan.aggregates.mapNotNull { it.column }
            if ("rest" in touched) return null
            if (plan.aggregates.any { it.function == "sum" && it.column !in setOf("kind", "created_at", "idx") }) return null

            val rows = ArrayList<Map<String, Any?>>()
            events(spec) { e ->
                if (spec.table == SqlProfile.EVENTS) {
                    rows.add(mapOf("id" to e.id, "pubkey" to e.pubKey, "created_at" to e.createdAt, "kind" to e.kind.toLong(), "content" to e.content, "sig" to e.sig))
                } else {
                    e.tags.forEachIndexed { i, t ->
                        rows.add(
                            mapOf(
                                "event_id" to e.id,
                                "idx" to i.toLong(),
                                "t0" to t.getOrNull(0),
                                "t1" to t.getOrNull(1),
                                "t2" to t.getOrNull(2),
                                "t3" to t.getOrNull(3),
                                "t4" to t.getOrNull(4),
                                "created_at" to e.createdAt,
                                "kind" to e.kind.toLong(),
                                "pubkey" to e.pubKey,
                            ),
                        )
                    }
                }
            }
            val kept =
                rows.filter { r ->
                    (spec.ids == null || r[if (spec.table == SqlProfile.EVENTS) "id" else "event_id"] in spec.ids!!) &&
                        (spec.authors == null || r["pubkey"] in spec.authors!!) &&
                        (spec.kinds == null || (r["kind"] as Long).toInt() in spec.kinds!!) &&
                        (spec.since == null || (r["created_at"] as Long) >= spec.since!!) &&
                        (spec.until == null || (r["created_at"] as Long) <= spec.until!!) &&
                        (spec.tagName == null || r["t0"] == spec.tagName) &&
                        (spec.tagValues == null || r["t1"] in spec.tagValues!!) &&
                        (!spec.valueNonEmpty || (r["t1"] != null && r["t1"] != ""))
                }
            val groups = if (plan.groupBy.isEmpty()) mapOf(emptyList<Any?>() to kept) else kept.groupBy { r -> plan.groupBy.map { r[it] } }
            answered++
            return groups.map { (key, members) ->
                key +
                    plan.aggregates.map { a ->
                        val values = if (a.column == null) members.map { 1 } else members.mapNotNull { it[a.column] }
                        when (a.function) {
                            "count" -> values.size.toLong()
                            "min" -> values.minWithOrNull(::sqliteCompare)
                            "max" -> values.maxWithOrNull(::sqliteCompare)
                            else -> if (values.isEmpty()) null else values.sumOf { (it as Long) }
                        }
                    }
            }
        }

        /** SQLite's order: integers before text, text by UTF-8 bytes. */
        private fun sqliteCompare(
            a: Any?,
            b: Any?,
        ): Int =
            when {
                a is Long && b is Long -> a.compareTo(b)
                a is Long -> -1
                b is Long -> 1
                else -> {
                    val x = a.toString().encodeToByteArray()
                    val y = b.toString().encodeToByteArray()
                    var i = 0
                    while (i < x.size && i < y.size && x[i] == y[i]) i++
                    if (i < x.size && i < y.size) (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff) else x.size - y.size
                }
            }
    }

    /**
     * Random profile queries over the virtual schema. Expressions are
     * emitted without parentheses so the parser's precedence is under test.
     */
    private class QueryGen(
        val r: Random,
    ) {
        private val eventsCols = listOf("id", "pubkey", "created_at", "kind", "content")
        private val tagsCols = listOf("event_id", "idx", "t0", "t1", "t2", "t3", "rest", "created_at", "kind")
        private val strings = listOf("'nostr'", "'t'", "'p'", "'%o%'", "'n_str'", "'Nostr'", "''", "'a''b'", "'mention'")
        private val binOps = listOf("+", "-", "*", "/", "%", "||", "=", "==", "<>", "!=", "<", "<=", ">", ">=", "AND", "OR", "IS", "IS NOT")
        private val funcs1 = listOf("length", "lower", "upper", "abs", "typeof", "trim", "hex", "unicode")
        private val math1 = listOf("sqrt", "ln", "log10", "log2", "exp", "floor", "ceil", "trunc", "sign", "sin", "cos", "atan", "degrees")
        private val math2 = listOf("pow", "mod", "atan2", "log")

        private fun <T> pick(list: List<T>) = list[r.nextInt(list.size)]

        fun atom(cols: List<String>): String =
            when (r.nextInt(10)) {
                in 0..4 -> pick(cols)
                5, 6 -> r.nextInt(0, 6).toString()
                7 -> pick(strings)
                8 -> "NULL"
                else -> (1_700_000_000 + r.nextInt(1000)).toString()
            }

        fun expr(
            cols: List<String>,
            depth: Int,
        ): String {
            if (depth <= 0) return atom(cols)
            val d = depth - 1
            return when (r.nextInt(17)) {
                0, 1, 2, 3 -> "${expr(cols, d)} ${pick(binOps)} ${expr(cols, d)}"
                4 -> "NOT ${expr(cols, d)}"
                5 -> "- ${expr(cols, d)}"
                6 -> "${pick(funcs1)}(${expr(cols, d)})"
                7 -> "coalesce(${expr(cols, d)}, ${expr(cols, d)})"
                8 -> "substr(${expr(cols, d)}, ${r.nextInt(0, 4)}, ${r.nextInt(0, 5)})"
                9 -> "CASE WHEN ${expr(cols, d)} THEN ${expr(cols, d)} ELSE ${expr(cols, d)} END"
                10 -> "${expr(cols, d)} ${if (r.nextBoolean()) "NOT " else ""}BETWEEN ${expr(cols, d)} AND ${expr(cols, d)}"
                11 -> "${expr(cols, d)} ${if (r.nextBoolean()) "NOT " else ""}IN (${expr(cols, d)}, ${atom(cols)})"
                12 -> "${expr(cols, d)} ${if (r.nextBoolean()) "NOT " else ""}${pick(listOf("LIKE", "GLOB"))} ${pick(strings)}"
                13 -> "${expr(cols, d)} ${pick(listOf("IS NULL", "ISNULL", "NOTNULL", "NOT NULL"))}"
                14 -> "CAST(${expr(cols, d)} AS ${pick(listOf("INTEGER", "TEXT", "REAL"))})"
                15 -> if (r.nextBoolean()) "${pick(math1)}(${expr(cols, d)})" else "${pick(math2)}(${expr(cols, d)}, ${expr(cols, d)})"
                else -> "(${expr(cols, d)})"
            }
        }

        private fun source(): Pair<String, List<String>> =
            when (r.nextInt(4)) {
                0 -> "events" to eventsCols
                1 -> "tags" to tagsCols
                2 -> "events e JOIN tags t ON t.event_id = e.id" to listOf("e.kind", "e.content", "t.t0", "t.t1", "e.created_at", "t.idx")
                else -> "(SELECT kind, content AS c, created_at FROM events WHERE kind ${pick(listOf("<", ">", "="))} ${r.nextInt(0, 8)}) s" to listOf("kind", "c", "created_at", "s.kind")
            }

        fun select(width: Int? = null): String {
            val (from, cols) = source()
            val n = width ?: r.nextInt(1, 4)
            val sb = StringBuilder("SELECT ")
            if (r.nextInt(5) == 0) sb.append("DISTINCT ")
            val grouped = r.nextInt(4) == 0
            if (grouped) {
                val key = pick(cols)
                val items = listOf(key) + List(n - 1) { pick(listOf("count(*)", "max(${pick(cols)})", "min(${expr(cols, 1)})", "sum(length(${pick(cols)}))", "total(${pick(cols)})")) }
                sb.append(items.joinToString(", "))
                sb.append(" FROM ").append(from)
                if (r.nextBoolean()) sb.append(" WHERE ").append(expr(cols, 2))
                sb.append(" GROUP BY ").append(key)
                if (r.nextBoolean()) sb.append(" HAVING count(*) > ").append(r.nextInt(0, 3))
            } else {
                sb.append(List(n) { if (r.nextInt(3) == 0) "${expr(cols, 2)} AS c$it" else expr(cols, 2) }.joinToString(", "))
                sb.append(" FROM ").append(from)
                if (r.nextInt(4) != 0) sb.append(" WHERE ").append(expr(cols, 3))
            }
            return sb.toString()
        }

        private val tagNames = listOf("'t'", "'t'", "'p'", "'d'", "'imeta'", "'T'")
        private val kindList = listOf(0, 1, 1, 3, 7, 20, 30023, 5)
        private val tagValues = listOf("'nostr'", "'sql'", "'Nostr'", "'relay'", "''", "'slug-3'", "'url x'", "'zap'")

        /** `t0 = … AND t1 = …` or `… t1 IN (…)`, in random order, on columns prefixed by [a]. */
        private fun tagEq(a: String): String {
            val name = "${a}t0 = ${pick(tagNames)}"
            val value =
                if (r.nextBoolean()) {
                    if (r.nextBoolean()) "${a}t1 = ${pick(tagValues)}" else "${pick(tagValues)} = ${a}t1"
                } else {
                    "${a}t1 IN (${List(r.nextInt(1, 4)) { pick(tagValues) }.joinToString(", ")})"
                }
            return if (r.nextBoolean()) "$name AND $value" else "$value AND $name"
        }

        fun query(): String =
            when (r.nextInt(23)) {
                0 -> {
                    val w = r.nextInt(1, 3)
                    select(w) + " " + pick(listOf("UNION", "UNION ALL", "INTERSECT", "EXCEPT")) + " " + select(w) +
                        " " + pick(listOf("UNION", "INTERSECT", "EXCEPT")) + " " + select(w)
                }

                1 -> {
                    "WITH x AS (SELECT kind, content, created_at FROM events WHERE ${expr(eventsCols, 2)}) " +
                        "SELECT kind, count(*) FROM x GROUP BY kind"
                }

                2 -> {
                    "SELECT kind, content, row_number() OVER (PARTITION BY kind ORDER BY created_at DESC, id) AS rn FROM events"
                }

                3 -> {
                    "SELECT e.kind, (SELECT count(*) FROM tags t WHERE t.event_id = e.id AND ${expr(listOf("t.t0", "t.t1", "t.idx"), 2)}) FROM events e"
                }

                4 -> {
                    "WITH RECURSIVE n(i) AS (SELECT ${r.nextInt(0, 3)} UNION ALL SELECT i + 1 FROM n WHERE i < ${r.nextInt(3, 20)}) " +
                        "SELECT i, ${expr(listOf("i"), 2)} FROM n"
                }

                5 -> {
                    // ORDER BY every output column so LIMIT picks a deterministic multiset.
                    select(2) + " ORDER BY 1, 2 LIMIT ${r.nextInt(0, 10)} OFFSET ${r.nextInt(0, 3)}"
                }

                // ---- tags pushdown shapes; the result must not change ----
                6 -> {
                    "SELECT t1, count(*) FROM tags WHERE ${tagEq("")}" +
                        (if (r.nextBoolean()) " AND ${expr(tagsCols, 1)}" else "") + " GROUP BY t1"
                }

                7 -> {
                    "SELECT e.kind, t.t1, t.idx FROM events e JOIN tags t ON t.event_id = e.id AND ${tagEq("t.")} WHERE ${expr(listOf("e.kind", "e.content", "t.t2"), 2)}"
                }

                8 -> {
                    // Right side of a LEFT JOIN: the ON constraint may be pushed.
                    "SELECT e.id, t.t1 FROM events e LEFT JOIN tags t ON t.event_id = e.id AND ${tagEq("t.")}"
                }

                9 -> {
                    // Preserved side of a LEFT JOIN: the ON constraint must NOT be pushed.
                    "SELECT t.t1, t.t0, e.kind FROM tags t LEFT JOIN events e ON e.id = t.event_id AND ${tagEq("t.")}"
                }

                10 -> {
                    "SELECT count(*) FROM events e WHERE EXISTS (SELECT 1 FROM tags t WHERE t.event_id = e.id AND ${tagEq("t.")})"
                }

                11 -> {
                    // A strict WHERE constraint on the null-extended side may be pushed.
                    "SELECT e.kind, t.idx FROM events e LEFT JOIN tags t ON t.event_id = e.id WHERE ${tagEq("t.")}"
                }

                12 -> {
                    // Top-level OR: not a conjunct, must not be pushed.
                    "SELECT count(*) FROM tags WHERE ${tagEq("")} OR kind = ${r.nextInt(0, 8)}"
                }

                13 -> {
                    "SELECT a.t1, b.t1 FROM tags a JOIN tags b ON a.event_id = b.event_id AND ${tagEq("a.")} WHERE ${tagEq("b.")}"
                }

                // ---- store pushdown shapes: limits, native aggregates ----
                14 -> {
                    "SELECT id, created_at FROM events WHERE kind = ${pick(kindList)} ORDER BY created_at DESC, id LIMIT ${r.nextInt(0, 8)}" +
                        (if (r.nextBoolean()) " OFFSET ${r.nextInt(0, 3)}" else "")
                }

                15 -> {
                    "SELECT count(*) FROM events WHERE kind IN (${pick(kindList)}, ${pick(kindList)})" +
                        (if (r.nextBoolean()) " AND created_at >= ${1_700_000_000 + r.nextInt(1000)}" else "")
                }

                16 -> {
                    "SELECT pubkey, count(*), max(created_at), min(kind) FROM events WHERE kind IN (1, ${pick(kindList)}) GROUP BY pubkey ORDER BY 2 DESC, 1" +
                        (if (r.nextBoolean()) " LIMIT ${r.nextInt(1, 4)}" else "")
                }

                17 -> {
                    "SELECT DISTINCT t1 FROM tags WHERE t0 = ${pick(tagNames)}" + (if (r.nextBoolean()) " AND kind = ${pick(kindList)}" else "") +
                        (if (r.nextBoolean()) " AND t1 <> ''" else "")
                }

                18 -> {
                    val a = 1_700_000_000 + r.nextInt(500)
                    "SELECT kind, count(*) AS n FROM events WHERE created_at BETWEEN $a AND ${a + r.nextInt(500)} GROUP BY kind ORDER BY n DESC, kind"
                }

                19 -> {
                    "SELECT t1, count(*), sum(idx) FROM tags WHERE t0 = ${pick(tagNames)} AND kind = ${pick(kindList)} GROUP BY t1 ORDER BY 2 DESC, 1 LIMIT 5"
                }

                20 -> {
                    "SELECT count(*), min(created_at), max(created_at) FROM events WHERE kind = ${pick(kindList)} AND pubkey IN (SELECT pubkey FROM events WHERE kind = 0)"
                }

                else -> {
                    select()
                }
            }
    }
}
